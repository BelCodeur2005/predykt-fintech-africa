package com.predykt.accounting.service;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.VATProrata;
import com.predykt.accounting.domain.entity.VATProrata.ProrataType;
import com.predykt.accounting.exception.ResourceNotFoundException;
import com.predykt.accounting.exception.ValidationException;
import com.predykt.accounting.repository.CompanyRepository;
import com.predykt.accounting.repository.VATProrataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service de gestion du prorata de TVA
 * Conforme au CGI Cameroun Art. 133
 *
 * Le prorata de TVA s'applique aux entreprises ayant des activités mixtes :
 * - Activités taxables (soumises à TVA) → TVA récupérable
 * - Activités exonérées (exports, hors champ) → TVA NON récupérable
 *
 * Formule : Prorata = (CA taxable ÷ CA total) × 100
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VATProratService {

    private final VATProrataRepository prorataRepository;
    private final CompanyRepository companyRepository;

    /**
     * Crée ou met à jour le prorata pour une entreprise/année
     */
    @Transactional
    public VATProrata createOrUpdateProrata(
            Long companyId,
            Integer fiscalYear,
            BigDecimal taxableTurnover,
            BigDecimal exemptTurnover,
            ProrataType prorataType,
            String notes,
            String createdBy) {

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        // Vérifier si un prorata actif existe déjà
        Optional<VATProrata> existingOpt = prorataRepository.findActiveByCompanyAndYear(company, fiscalYear);

        VATProrata prorata;

        if (existingOpt.isPresent()) {
            // Mise à jour
            prorata = existingOpt.get();

            if (prorata.getIsLocked()) {
                throw new ValidationException("Le prorata est verrouillé et ne peut être modifié");
            }

            log.info("Mise à jour du prorata {} pour {} année {}", prorata.getId(), company.getName(), fiscalYear);

            prorata.setTaxableTurnover(taxableTurnover);
            prorata.setExemptTurnover(exemptTurnover);
            prorata.setProrataType(prorataType);
            prorata.setNotes(notes);
            prorata.setUpdatedBy(createdBy);

        } else {
            // Création
            log.info("Création du prorata pour {} année {}", company.getName(), fiscalYear);

            prorata = VATProrata.builder()
                .company(company)
                .fiscalYear(fiscalYear)
                .taxableTurnover(taxableTurnover)
                .exemptTurnover(exemptTurnover)
                .prorataType(prorataType)
                .isActive(true)
                .isLocked(false)
                .calculationDate(LocalDateTime.now())
                .notes(notes)
                .build();
        }

        // Calculer le total et le prorata
        prorata.calculateTotalTurnover();
        prorata.calculateProrataRate();

        VATProrata saved = prorataRepository.save(prorata);

        log.info("✅ Prorata enregistré : {}% pour {} année {}",
            saved.getProrataPercentage(),
            company.getName(),
            fiscalYear);

        return saved;
    }

    /**
     * Crée un prorata provisoire basé sur l'année précédente
     */
    @Transactional
    public VATProrata createProvisionalProrata(
            Long companyId,
            Integer fiscalYear,
            String createdBy) {

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        // Chercher le prorata de l'année précédente
        Optional<VATProrata> previousYearOpt = prorataRepository
            .findActiveByCompanyAndYear(company, fiscalYear - 1);

        if (previousYearOpt.isEmpty()) {
            // Pas de prorata N-1 → créer prorata 100% par défaut
            log.warn("Aucun prorata trouvé pour {} année {} - Création prorata 100% par défaut",
                company.getName(), fiscalYear - 1);

            return createOrUpdateProrata(
                companyId,
                fiscalYear,
                BigDecimal.ZERO,  // À compléter
                BigDecimal.ZERO,
                ProrataType.PROVISIONAL,
                "Prorata provisoire par défaut (100%) - Aucune donnée année N-1",
                createdBy
            );
        }

        // Utiliser les valeurs de N-1 comme base
        VATProrata previousProrata = previousYearOpt.get();

        log.info("Création prorata provisoire {} basé sur année {} (prorata: {}%)",
            fiscalYear,
            fiscalYear - 1,
            previousProrata.getProrataPercentage());

        return createOrUpdateProrata(
            companyId,
            fiscalYear,
            previousProrata.getTaxableTurnover(),
            previousProrata.getExemptTurnover(),
            ProrataType.PROVISIONAL,
            String.format("Prorata provisoire basé sur année %d", fiscalYear - 1),
            createdBy
        );
    }

    /**
     * Convertit un prorata provisoire en définitif (avec régularisation si besoin)
     */
    @Transactional
    public VATProrata convertToDefinitive(
            Long companyId,
            Integer fiscalYear,
            BigDecimal definiteTaxableTurnover,
            BigDecimal definiteExemptTurnover,
            String updatedBy) {

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        VATProrata provisionalProrata = prorataRepository
            .findActiveByCompanyAndYear(company, fiscalYear)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Aucun prorata provisoire trouvé pour l'année " + fiscalYear));

        if (provisionalProrata.getProrataType() != ProrataType.PROVISIONAL) {
            throw new ValidationException("Le prorata est déjà définitif");
        }

        if (provisionalProrata.getIsLocked()) {
            throw new ValidationException("Le prorata est verrouillé");
        }

        // Calculer le nouveau prorata
        BigDecimal totalTurnover = definiteTaxableTurnover.add(definiteExemptTurnover);
        BigDecimal newProrataRate = BigDecimal.ZERO;

        if (totalTurnover.compareTo(BigDecimal.ZERO) > 0) {
            newProrataRate = definiteTaxableTurnover
                .divide(totalTurnover, 4, RoundingMode.HALF_UP);
        }

        // Vérifier si régularisation nécessaire
        boolean needsRegularization = provisionalProrata.needsRegularization(newProrataRate);

        if (needsRegularization) {
            log.warn("⚠️ RÉGULARISATION NÉCESSAIRE pour {} année {} : Provisoire {}% → Définitif {}%",
                company.getName(),
                fiscalYear,
                provisionalProrata.getProrataPercentage(),
                newProrataRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
        }

        // Mettre à jour
        provisionalProrata.setTaxableTurnover(definiteTaxableTurnover);
        provisionalProrata.setExemptTurnover(definiteExemptTurnover);
        provisionalProrata.setTotalTurnover(totalTurnover);
        provisionalProrata.setProrataRate(newProrataRate);
        provisionalProrata.setProrataType(ProrataType.DEFINITIVE);
        provisionalProrata.setNotes(
            provisionalProrata.getNotes() + "\n\nConverti en définitif le " + LocalDateTime.now() +
            (needsRegularization ? " - RÉGULARISATION EFFECTUÉE" : "")
        );
        provisionalProrata.setUpdatedBy(updatedBy);

        VATProrata saved = prorataRepository.save(provisionalProrata);

        log.info("✅ Prorata {} converti en DÉFINITIF : {}%",
            fiscalYear,
            saved.getProrataPercentage());

        return saved;
    }

    /**
     * Récupère le prorata actif pour une entreprise/année
     */
    @Transactional(readOnly = true)
    public Optional<VATProrata> getActiveProrata(Long companyId, Integer fiscalYear) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        return prorataRepository.findActiveByCompanyAndYear(company, fiscalYear);
    }

    /**
     * Récupère tous les prorata d'une entreprise
     */
    @Transactional(readOnly = true)
    public List<VATProrata> getAllProrata(Long companyId) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        return prorataRepository.findByCompanyOrderByFiscalYearDesc(company);
    }

    /**
     * Applique le prorata à un montant de TVA
     *
     * @param companyId ID de l'entreprise
     * @param fiscalYear Année fiscale
     * @param vatAmount Montant de TVA à récupérer (avant prorata)
     * @return Montant de TVA récupérable après application du prorata
     */
    @Transactional(readOnly = true)
    public BigDecimal applyProrata(Long companyId, Integer fiscalYear, BigDecimal vatAmount) {
        if (vatAmount == null || vatAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        Optional<VATProrata> prorataOpt = prorataRepository
            .findActiveByCompanyIdAndYear(companyId, fiscalYear);

        if (prorataOpt.isEmpty()) {
            // Pas de prorata → 100% récupérable (activités totalement taxables)
            log.debug("Aucun prorata pour company {} année {} → 100% récupérable",
                companyId, fiscalYear);
            return vatAmount;
        }

        VATProrata prorata = prorataOpt.get();

        // Si prorata = 100%, pas d'impact
        if (prorata.getProrataRate().compareTo(BigDecimal.ONE) == 0) {
            return vatAmount;
        }

        BigDecimal recoverable = prorata.applyToVAT(vatAmount);

        log.debug("Application prorata {}% : {} FCFA → {} FCFA récupérable",
            prorata.getProrataPercentage(),
            vatAmount,
            recoverable);

        return recoverable;
    }

    /**
     * Vérifie si un prorata existe pour une entreprise/année
     */
    @Transactional(readOnly = true)
    public boolean hasProrataForYear(Long companyId, Integer fiscalYear) {
        return prorataRepository.existsActiveByCompanyIdAndYear(companyId, fiscalYear);
    }

    /**
     * Verrouille un prorata (après clôture fiscale)
     */
    @Transactional
    public VATProrata lockProrata(Long prorataId, String lockedBy) {
        VATProrata prorata = prorataRepository.findById(prorataId)
            .orElseThrow(() -> new ResourceNotFoundException("Prorata non trouvé"));

        if (prorata.getIsLocked()) {
            throw new ValidationException("Le prorata est déjà verrouillé");
        }

        prorata.lock(lockedBy);
        VATProrata saved = prorataRepository.save(prorata);

        log.info("🔒 Prorata {} verrouillé par {}", prorataId, lockedBy);

        return saved;
    }

    /**
     * Calcule le prorata à partir des écritures comptables
     * (Scan automatique des comptes 70x pour détecter CA taxable vs exonéré)
     */
    @Transactional
    public VATProrata calculateProrataFromLedger(
            Long companyId,
            Integer fiscalYear,
            String calculatedBy) {

        // TODO: Implémenter le calcul automatique à partir du grand livre
        // 1. Scanner les comptes 70x (ventes) pour l'année
        // 2. Identifier le CA taxable (avec TVA collectée)
        // 3. Identifier le CA exonéré (exports, hors champ)
        // 4. Calculer le prorata

        throw new UnsupportedOperationException(
            "Calcul automatique du prorata depuis le grand livre - À implémenter");
    }

    /**
     * Supprime un prorata (si non verrouillé)
     */
    @Transactional
    public void deleteProrata(Long prorataId) {
        VATProrata prorata = prorataRepository.findById(prorataId)
            .orElseThrow(() -> new ResourceNotFoundException("Prorata non trouvé"));

        if (prorata.getIsLocked()) {
            throw new ValidationException("Le prorata est verrouillé et ne peut être supprimé");
        }

        prorataRepository.delete(prorata);

        log.info("🗑️ Prorata {} supprimé", prorataId);
    }
}

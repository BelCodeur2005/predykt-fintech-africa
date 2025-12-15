package com.predykt.accounting.service;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.FixedAsset;
import com.predykt.accounting.domain.enums.AssetCategory;
import com.predykt.accounting.dto.request.FixedAssetCreateRequest;
import com.predykt.accounting.dto.request.FixedAssetDisposalRequest;
import com.predykt.accounting.dto.request.FixedAssetUpdateRequest;
import com.predykt.accounting.dto.response.FixedAssetResponse;
import com.predykt.accounting.exception.AccountingException;
import com.predykt.accounting.exception.ResourceNotFoundException;
import com.predykt.accounting.exception.ValidationException;
import com.predykt.accounting.mapper.FixedAssetMapper;
import com.predykt.accounting.repository.CompanyRepository;
import com.predykt.accounting.repository.FixedAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service de gestion des immobilisations (actifs fixes)
 * Conforme OHADA et fiscalité camerounaise (CGI)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FixedAssetService {

    private final FixedAssetRepository fixedAssetRepository;
    private final CompanyRepository companyRepository;
    private final FixedAssetMapper fixedAssetMapper;
    private final DepreciationService depreciationService;
    private final JournalEntryGenerationService journalEntryGenerationService;

    // ========================================
    // CREATE
    // ========================================

    /**
     * Créer une nouvelle immobilisation
     */
    @Transactional
    public FixedAssetResponse createFixedAsset(Long companyId, FixedAssetCreateRequest request) {
        log.info("Création d'une immobilisation pour l'entreprise {}: {}", companyId, request.getAssetNumber());

        // Vérifier que l'entreprise existe
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        // Vérifier que le numéro d'immobilisation n'existe pas déjà
        if (fixedAssetRepository.existsByCompanyAndAssetNumber(company, request.getAssetNumber())) {
            throw new ValidationException(
                "Le numéro d'immobilisation " + request.getAssetNumber() + " existe déjà pour cette entreprise");
        }

        // Valider la cohérence fiscale
        validateFiscalCompliance(request);

        // Convertir la requête en entité
        FixedAsset asset = fixedAssetMapper.toEntity(request);
        asset.setCompany(company);

        // Valeurs par défaut
        if (asset.getAcquisitionVat() == null) {
            asset.setAcquisitionVat(BigDecimal.ZERO);
        }
        if (asset.getInstallationCost() == null) {
            asset.setInstallationCost(BigDecimal.ZERO);
        }
        if (asset.getResidualValue() == null) {
            asset.setResidualValue(BigDecimal.ZERO);
        }

        // Sauvegarder (totalCost et depreciationRate seront calculés par @PrePersist)
        FixedAsset savedAsset = fixedAssetRepository.save(asset);

        log.info("Immobilisation créée avec succès: ID={}, Numéro={}, VNC={}",
                 savedAsset.getId(), savedAsset.getAssetNumber(), savedAsset.getTotalCost());

        return enrichResponse(savedAsset);
    }

    // ========================================
    // READ
    // ========================================

    /**
     * Récupérer toutes les immobilisations d'une entreprise (avec filtres optionnels)
     */
    @Transactional(readOnly = true)
    public List<FixedAssetResponse> getCompanyAssets(
            Long companyId,
            AssetCategory category,
            Boolean isActive,
            String location,
            String department) {

        log.info("Récupération des immobilisations - Entreprise: {}, Catégorie: {}, Actif: {}",
                 companyId, category, isActive);

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        List<FixedAsset> assets;

        // Appliquer les filtres
        if (category != null && isActive != null) {
            assets = isActive
                ? fixedAssetRepository.findByCompanyAndCategoryAndIsActiveTrue(company, category)
                : fixedAssetRepository.findByCompanyAndCategory(company, category);
        } else if (category != null) {
            assets = fixedAssetRepository.findByCompanyAndCategory(company, category);
        } else if (isActive != null) {
            assets = isActive
                ? fixedAssetRepository.findByCompanyAndIsActiveTrue(company)
                : fixedAssetRepository.findByCompanyAndIsActiveFalse(company);
        } else if (location != null) {
            assets = fixedAssetRepository.findByCompanyAndLocation(company, location);
        } else if (department != null) {
            assets = fixedAssetRepository.findByCompanyAndDepartment(company, department);
        } else {
            assets = fixedAssetRepository.findByCompany(company);
        }

        log.info("Nombre d'immobilisations trouvées: {}", assets.size());

        return assets.stream()
            .map(this::enrichResponse)
            .collect(Collectors.toList());
    }

    /**
     * Récupérer une immobilisation par son ID
     */
    @Transactional(readOnly = true)
    public FixedAssetResponse getAssetById(Long companyId, Long assetId) {
        log.info("Récupération de l'immobilisation ID={} pour l'entreprise {}", assetId, companyId);

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        FixedAsset asset = fixedAssetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée: " + assetId));

        // Vérifier que l'immobilisation appartient à l'entreprise (sécurité multi-tenant)
        if (!asset.getCompany().getId().equals(company.getId())) {
            throw new ValidationException("Cette immobilisation n'appartient pas à l'entreprise spécifiée");
        }

        return enrichResponse(asset);
    }

    /**
     * Récupérer une immobilisation par son numéro
     */
    @Transactional(readOnly = true)
    public FixedAssetResponse getAssetByNumber(Long companyId, String assetNumber) {
        log.info("Récupération de l'immobilisation {} pour l'entreprise {}", assetNumber, companyId);

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        FixedAsset asset = fixedAssetRepository.findByCompanyAndAssetNumber(company, assetNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Immobilisation non trouvée: " + assetNumber));

        return enrichResponse(asset);
    }

    // ========================================
    // UPDATE
    // ========================================

    /**
     * Mettre à jour une immobilisation
     */
    @Transactional
    public FixedAssetResponse updateFixedAsset(
            Long companyId,
            Long assetId,
            FixedAssetUpdateRequest request) {

        log.info("Mise à jour de l'immobilisation ID={} pour l'entreprise {}", assetId, companyId);

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        FixedAsset asset = fixedAssetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée: " + assetId));

        // Vérifier appartenance (sécurité multi-tenant)
        if (!asset.getCompany().getId().equals(company.getId())) {
            throw new ValidationException("Cette immobilisation n'appartient pas à l'entreprise spécifiée");
        }

        // Vérifier que l'immobilisation n'est pas cédée
        if (asset.isDisposed()) {
            throw new ValidationException(
                "Impossible de modifier une immobilisation cédée (date de cession: " +
                asset.getDisposalDate() + ")");
        }

        // Appliquer les modifications (seuls les champs non-null sont mis à jour)
        fixedAssetMapper.updateEntity(asset, request);

        // Sauvegarder (totalCost et depreciationRate seront recalculés par @PreUpdate)
        FixedAsset updatedAsset = fixedAssetRepository.save(asset);

        log.info("Immobilisation mise à jour avec succès: ID={}", updatedAsset.getId());

        return enrichResponse(updatedAsset);
    }

    // ========================================
    // DELETE (Soft Delete)
    // ========================================

    /**
     * Supprimer une immobilisation (soft delete: marquer comme inactive)
     */
    @Transactional
    public void deleteFixedAsset(Long companyId, Long assetId) {
        log.info("Suppression (soft) de l'immobilisation ID={} pour l'entreprise {}", assetId, companyId);

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        FixedAsset asset = fixedAssetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée: " + assetId));

        // Vérifier appartenance
        if (!asset.getCompany().getId().equals(company.getId())) {
            throw new ValidationException("Cette immobilisation n'appartient pas à l'entreprise spécifiée");
        }

        // Vérifier qu'elle n'est pas déjà cédée
        if (asset.isDisposed()) {
            throw new ValidationException(
                "Impossible de supprimer une immobilisation déjà cédée (utilisez la cession si besoin)");
        }

        // Soft delete: marquer comme inactive
        asset.setIsActive(false);
        fixedAssetRepository.save(asset);

        log.info("Immobilisation marquée comme inactive: ID={}", assetId);
    }

    // ========================================
    // CESSION (DISPOSAL)
    // ========================================

    /**
     * Céder une immobilisation (vente, mise au rebut, don, destruction)
     * Conforme OHADA - Génère les écritures de cession
     */
    @Transactional
    public FixedAssetResponse disposeAsset(
            Long companyId,
            Long assetId,
            FixedAssetDisposalRequest request) {

        log.info("Cession de l'immobilisation ID={} - Type: {}, Montant: {}",
                 assetId, request.getDisposalType(), request.getDisposalAmount());

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        FixedAsset asset = fixedAssetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée: " + assetId));

        // Vérifier appartenance
        if (!asset.getCompany().getId().equals(company.getId())) {
            throw new ValidationException("Cette immobilisation n'appartient pas à l'entreprise spécifiée");
        }

        // Vérifier qu'elle n'est pas déjà cédée
        if (asset.isDisposed()) {
            throw new ValidationException(
                "Cette immobilisation a déjà été cédée le " + asset.getDisposalDate());
        }

        // Vérifier que la date de cession est après l'acquisition
        if (request.getDisposalDate().isBefore(asset.getAcquisitionDate())) {
            throw new ValidationException(
                "La date de cession ne peut être antérieure à la date d'acquisition (" +
                asset.getAcquisitionDate() + ")");
        }

        // Appliquer la cession
        asset.setDisposalDate(request.getDisposalDate());
        asset.setDisposalAmount(request.getDisposalAmount());
        asset.setDisposalReason(request.getDisposalReason());
        asset.setIsActive(false);

        // Calculer la plus-value ou moins-value
        BigDecimal vnc = calculateNetBookValue(asset, request.getDisposalDate());
        BigDecimal gainLoss = request.getDisposalAmount().subtract(vnc);

        log.info("VNC à la cession: {} - Prix de cession: {} - Plus/Moins-value: {}",
                 vnc, request.getDisposalAmount(), gainLoss);

        // Sauvegarder
        FixedAsset disposedAsset = fixedAssetRepository.save(asset);

        // Générer automatiquement les écritures comptables de cession (OHADA)
        try {
            List<com.predykt.accounting.domain.entity.GeneralLedger> entries =
                journalEntryGenerationService.generateDisposalJournalEntries(
                    disposedAsset, vnc, gainLoss, request);

            // Valider l'équilibre des écritures
            journalEntryGenerationService.validateEntriesBalance(entries);

            log.info("✅ Écritures de cession générées automatiquement: {} écriture(s)", entries.size());
        } catch (Exception e) {
            log.error("❌ Erreur génération écritures de cession: {}", e.getMessage());
            // La cession est sauvegardée mais les écritures ont échoué
            // Dans un cas réel, on pourrait rollback ou créer une alerte
            throw new AccountingException(
                "Immobilisation cédée mais erreur lors de la génération des écritures: " + e.getMessage());
        }

        log.info("Immobilisation cédée avec succès: ID={}, Plus/Moins-value: {}", assetId, gainLoss);

        return enrichResponse(disposedAsset);
    }

    // ========================================
    // MÉTHODES UTILITAIRES
    // ========================================

    /**
     * Générer le prochain numéro d'immobilisation pour une entreprise
     * Format: IMM-YYYY-NNN (ex: IMM-2024-001)
     */
    public String generateNextAssetNumber(Long companyId, Integer fiscalYear) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        if (fiscalYear == null) {
            fiscalYear = LocalDate.now().getYear();
        }

        // Trouver le dernier numéro de l'année
        String prefix = "IMM-" + fiscalYear + "-";
        List<FixedAsset> assetsOfYear = fixedAssetRepository.findByCompany(company).stream()
            .filter(a -> a.getAssetNumber().startsWith(prefix))
            .toList();

        int maxNumber = assetsOfYear.stream()
            .map(a -> {
                String num = a.getAssetNumber().replace(prefix, "");
                try {
                    return Integer.parseInt(num);
                } catch (NumberFormatException e) {
                    return 0;
                }
            })
            .max(Integer::compareTo)
            .orElse(0);

        return String.format("%s%03d", prefix, maxNumber + 1);
    }

    /**
     * Marquer une immobilisation comme totalement amortie
     */
    @Transactional
    public void markAsFullyDepreciated(Long assetId) {
        FixedAsset asset = fixedAssetRepository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException("Immobilisation non trouvée: " + assetId));

        asset.setIsFullyDepreciated(true);
        fixedAssetRepository.save(asset);

        log.info("Immobilisation {} marquée comme totalement amortie", assetId);
    }

    // ========================================
    // MÉTHODES PRIVÉES
    // ========================================

    /**
     * Enrichir la réponse avec les calculs en temps réel
     */
    private FixedAssetResponse enrichResponse(FixedAsset asset) {
        FixedAssetResponse response = fixedAssetMapper.toResponse(asset);

        // Calculs en temps réel
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();

        // Amortissements cumulés à ce jour
        BigDecimal accumulatedDepreciation = asset.isDepreciable()
            ? depreciationService.calculateAccumulatedDepreciation(asset, currentYear)
            : BigDecimal.ZERO;

        // VNC actuelle
        BigDecimal netBookValue = (asset.getTotalCost() != null ? asset.getTotalCost() : asset.getAcquisitionCost())
            .subtract(accumulatedDepreciation);

        // Âge de l'immobilisation
        long ageInMonths = ChronoUnit.MONTHS.between(asset.getAcquisitionDate(), now);
        int ageInYears = (int) ChronoUnit.YEARS.between(asset.getAcquisitionDate(), now);

        // Progrès d'amortissement (%)
        BigDecimal depreciationProgress = BigDecimal.ZERO;
        if (asset.isDepreciable() && asset.getDepreciableAmount().compareTo(BigDecimal.ZERO) > 0) {
            depreciationProgress = accumulatedDepreciation
                .divide(asset.getDepreciableAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        // Plus-value / Moins-value si cédé
        BigDecimal disposalGainLoss = null;
        if (asset.isDisposed() && asset.getDisposalAmount() != null) {
            BigDecimal vncAtDisposal = calculateNetBookValue(asset, asset.getDisposalDate());
            disposalGainLoss = asset.getDisposalAmount().subtract(vncAtDisposal);
        }

        // Statut et label
        String statusLabel;
        String statusIcon;
        boolean needsRenewal = false;

        if (asset.isDisposed()) {
            statusLabel = "Cédé";
            statusIcon = "📤";
        } else if (asset.getIsFullyDepreciated()) {
            statusLabel = "Totalement amorti";
            statusIcon = "⚠️";
            needsRenewal = true;
        } else if (ageInYears > asset.getUsefulLifeYears()) {
            statusLabel = "Obsolète";
            statusIcon = "🔴";
            needsRenewal = true;
        } else if (depreciationProgress.compareTo(BigDecimal.valueOf(75)) > 0) {
            statusLabel = "En fin de vie";
            statusIcon = "⚠️";
        } else {
            statusLabel = "Actif";
            statusIcon = "✅";
        }

        // Enrichir la réponse
        response.setCurrentAccumulatedDepreciation(accumulatedDepreciation);
        response.setCurrentNetBookValue(netBookValue);
        response.setAgeInYears(ageInYears);
        response.setAgeInMonths((int) ageInMonths);
        response.setDepreciationProgress(depreciationProgress);
        response.setDisposalGainLoss(disposalGainLoss);
        response.setStatusLabel(statusLabel);
        response.setStatusIcon(statusIcon);
        response.setNeedsRenewal(needsRenewal);

        return response;
    }

    /**
     * Calculer la VNC à une date donnée
     */
    private BigDecimal calculateNetBookValue(FixedAsset asset, LocalDate asOfDate) {
        int year = asOfDate.getYear();
        BigDecimal accumulatedDepreciation = depreciationService.calculateAccumulatedDepreciation(asset, year);
        return (asset.getTotalCost() != null ? asset.getTotalCost() : asset.getAcquisitionCost())
            .subtract(accumulatedDepreciation);
    }

    /**
     * Valider la cohérence fiscale (conformité CGI Cameroun)
     */
    private void validateFiscalCompliance(FixedAssetCreateRequest request) {
        // Vérifier que les terrains ne sont pas amortissables
        if (request.getCategory() == AssetCategory.LAND && request.getDepreciationMethod() != null) {
            throw new ValidationException(
                "Les terrains ne sont pas amortissables selon le plan comptable OHADA");
        }

        // Vérifier que les immobilisations financières ne sont pas amorties
        if (request.getCategory() == AssetCategory.FINANCIAL && request.getDepreciationMethod() != null) {
            throw new ValidationException(
                "Les immobilisations financières ne sont pas amortissables");
        }

        // Avertir si la durée de vie diffère beaucoup des normes fiscales
        Integer defaultLife = request.getCategory().getDefaultUsefulLifeYears();
        if (defaultLife > 0) {
            int minLife = (int) (defaultLife * 0.5);
            int maxLife = (int) (defaultLife * 1.5);

            if (request.getUsefulLifeYears() < minLife || request.getUsefulLifeYears() > maxLife) {
                log.warn("La durée de vie {} ans pour {} diffère des normes fiscales ({} ans)",
                         request.getUsefulLifeYears(), request.getCategory().getDisplayName(), defaultLife);
            }
        }
    }

    // ========================================
    // GÉNÉRATION AUTOMATIQUE DES ÉCRITURES
    // ========================================

    /**
     * Générer les dotations aux amortissements mensuelles pour toutes les immobilisations
     * À appeler manuellement ou via un job planifié en fin de mois
     */
    @Transactional
    public void generateMonthlyDepreciationEntries(Long companyId, Integer year, Integer month) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée: " + companyId));

        log.info("Génération des dotations mensuelles - Entreprise: {} - Période: {}/{}",
                 companyId, month, year);

        List<com.predykt.accounting.domain.entity.GeneralLedger> entries =
            journalEntryGenerationService.generateMonthlyDepreciationEntries(company, year, month);

        if (!entries.isEmpty()) {
            journalEntryGenerationService.validateEntriesBalance(entries);
            log.info("✅ Dotations mensuelles générées: {} écriture(s)", entries.size());
        } else {
            log.info("Aucune dotation à enregistrer pour cette période");
        }
    }
}

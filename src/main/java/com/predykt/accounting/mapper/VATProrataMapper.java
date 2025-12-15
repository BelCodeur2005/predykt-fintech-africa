package com.predykt.accounting.mapper;

import com.predykt.accounting.domain.entity.VATProrata;
import com.predykt.accounting.domain.entity.VATRecoveryCalculation;
import com.predykt.accounting.dto.response.VATProrataResponse;
import com.predykt.accounting.dto.response.VATRecoveryCalculationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

/**
 * Mapper pour les entités de prorata de TVA
 */
@Mapper(componentModel = "spring")
public interface VATProrataMapper {

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "prorataPercentage", expression = "java(calculatePercentage(prorata.getProrataRate()))")
    @Mapping(target = "needsRegularization", expression = "java(false)")
    @Mapping(target = "infoMessage", expression = "java(buildInfoMessage(prorata))")
    VATProrataResponse toResponse(VATProrata prorata);

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "generalLedgerId", source = "generalLedger.id")
    @Mapping(target = "recoveryCategoryName", source = "recoveryCategory.displayName")
    @Mapping(target = "prorataId", source = "prorata.id")
    @Mapping(target = "prorataPercentage", expression = "java(calculatePercentage(calculation.getProrataRate()))")
    @Mapping(target = "recoveryPercentage", expression = "java(calculateRecoveryPercentage(calculation))")
    @Mapping(target = "hasProrataImpact", expression = "java(hasProrataImpact(calculation))")
    @Mapping(target = "appliedRule", expression = "java(mapAppliedRule(calculation.getAppliedRule()))")
    @Mapping(target = "calculationExplanation", expression = "java(buildCalculationExplanation(calculation))")
    VATRecoveryCalculationResponse toResponse(VATRecoveryCalculation calculation);

    /**
     * Convertit un taux (0.0 à 1.0) en pourcentage (0.0 à 100.0)
     */
    default BigDecimal calculatePercentage(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        return rate.multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calcule le pourcentage de récupération final
     */
    default BigDecimal calculateRecoveryPercentage(VATRecoveryCalculation calculation) {
        if (calculation.getVatAmount() == null || calculation.getVatAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return calculation.getRecoverableVat()
                .divide(calculation.getVatAmount(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Vérifie si le prorata a un impact
     */
    default Boolean hasProrataImpact(VATRecoveryCalculation calculation) {
        if (calculation.getRecoverableByNature() == null || calculation.getRecoverableWithProrata() == null) {
            return false;
        }
        return calculation.getRecoverableByNature().compareTo(calculation.getRecoverableWithProrata()) != 0;
    }

    /**
     * Mappe la règle appliquée vers une chaîne
     */
    default String mapAppliedRule(com.predykt.accounting.domain.entity.RecoverabilityRule rule) {
        if (rule == null) {
            return null;
        }
        return rule.getName() != null ? rule.getName() : "Règle #" + rule.getId();
    }

    /**
     * Construit un message d'information sur le prorata
     */
    default String buildInfoMessage(VATProrata prorata) {
        if (prorata == null) {
            return null;
        }

        StringBuilder message = new StringBuilder();

        if (prorata.getIsLocked()) {
            message.append("🔒 Verrouillé");
        } else if (prorata.getProrataType() == VATProrata.ProrataType.PROVISIONAL) {
            message.append("⏳ Provisoire");
        } else {
            message.append("✅ Définitif");
        }

        message.append(" - ").append(prorata.getProrataPercentage()).append("% récupérable");

        if (prorata.getProrataRate().compareTo(BigDecimal.ONE) == 0) {
            message.append(" (100% activités taxables)");
        }

        return message.toString();
    }

    /**
     * Construit une explication du calcul
     */
    default String buildCalculationExplanation(VATRecoveryCalculation calculation) {
        if (calculation == null) {
            return null;
        }

        StringBuilder explanation = new StringBuilder();

        // ÉTAPE 1: Nature
        explanation.append("ÉTAPE 1 (Nature): ")
                .append(calculation.getRecoveryCategory().getDisplayName())
                .append(" → ")
                .append(calculatePercentage(calculation.getRecoveryByNatureRate()))
                .append("% = ")
                .append(calculation.getRecoverableByNature())
                .append(" FCFA");

        // ÉTAPE 2: Prorata (si applicable)
        if (calculation.getProrata() != null && calculation.getProrataRate() != null) {
            explanation.append("\nÉTAPE 2 (Prorata): ")
                    .append(calculatePercentage(calculation.getProrataRate()))
                    .append("% × ")
                    .append(calculation.getRecoverableByNature())
                    .append(" FCFA = ")
                    .append(calculation.getRecoverableWithProrata())
                    .append(" FCFA");
        } else {
            explanation.append("\nÉTAPE 2 (Prorata): Aucun prorata → 100% activités taxables");
        }

        // RÉSULTAT FINAL
        explanation.append("\nRÉSULTAT: ")
                .append(calculation.getRecoverableVat())
                .append(" FCFA récupérable sur ")
                .append(calculation.getVatAmount())
                .append(" FCFA");

        return explanation.toString();
    }
}

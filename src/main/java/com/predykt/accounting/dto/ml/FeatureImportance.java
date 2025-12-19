package com.predykt.accounting.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente l'importance d'une feature dans une prédiction
 * Utilisé par SHAP pour expliquer les prédictions ML
 *
 * @author PREDYKT ML Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureImportance {

    /**
     * Nom de la feature (ex: "amount_difference")
     */
    private String featureName;

    /**
     * Nom lisible de la feature (ex: "Différence de montant")
     */
    private String featureDisplayName;

    /**
     * Valeur de la feature pour cette prédiction
     */
    private Double featureValue;

    /**
     * SHAP value (contribution à la prédiction)
     * Positif = augmente la probabilité de match
     * Négatif = diminue la probabilité de match
     */
    private Double shapValue;

    /**
     * Impact en pourcentage (|shapValue| * 100)
     */
    private Double impactPercent;

    /**
     * Direction de l'impact
     */
    private String direction; // "INCREASE" ou "DECREASE"

    /**
     * Tier de la feature (1-4)
     */
    private String tier;

    /**
     * Description de l'impact
     */
    private String description;

    /**
     * Unité de la valeur (ex: "XAF", "%", "jours")
     */
    private String unit;

    /**
     * Retourne si cette feature augmente la probabilité
     */
    public boolean isPositiveImpact() {
        return shapValue != null && shapValue > 0;
    }

    /**
     * Retourne l'impact absolu
     */
    public Double getAbsoluteImpact() {
        return shapValue != null ? Math.abs(shapValue) : 0.0;
    }
}

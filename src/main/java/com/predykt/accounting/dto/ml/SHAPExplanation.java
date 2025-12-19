package com.predykt.accounting.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Explication SHAP (SHapley Additive exPlanations) d'une prédiction ML
 *
 * SHAP quantifie l'impact de chaque feature sur la prédiction finale.
 * Basé sur la théorie des jeux de Shapley.
 *
 * @author PREDYKT ML Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SHAPExplanation {

    /**
     * SHAP values pour toutes les features (30 features)
     * Index correspond à l'ordre des features dans le modèle
     */
    private double[] shapValues;

    /**
     * Top N features les plus importantes (triées par |shapValue|)
     */
    private List<FeatureImportance> topFeatures;

    /**
     * Valeur de base du modèle (expected value)
     * C'est la prédiction moyenne du modèle sans features
     */
    private Double baseValue;

    /**
     * Prédiction finale
     * prediction = baseValue + sum(shapValues)
     */
    private Double prediction;

    /**
     * Probabilité finale (0-1)
     */
    private Double probability;

    /**
     * Confiance (0-100%)
     */
    private Double confidence;

    /**
     * Version du modèle utilisé
     */
    private String modelVersion;

    /**
     * Nombre total de features analysées
     */
    private Integer totalFeatures;

    /**
     * Map des features par tier pour affichage structuré
     */
    private Map<String, List<FeatureImportance>> featuresByTier;

    /**
     * Temps de calcul SHAP en ms
     */
    private Long computationTimeMs;

    /**
     * Retourne la somme des SHAP values positives
     */
    public double getPositiveContribution() {
        if (shapValues == null) return 0.0;
        double sum = 0.0;
        for (double val : shapValues) {
            if (val > 0) sum += val;
        }
        return sum;
    }

    /**
     * Retourne la somme des SHAP values négatives
     */
    public double getNegativeContribution() {
        if (shapValues == null) return 0.0;
        double sum = 0.0;
        for (double val : shapValues) {
            if (val < 0) sum += val;
        }
        return sum;
    }

    /**
     * Vérifie la cohérence de l'explication
     * prediction devrait être proche de baseValue + sum(shapValues)
     */
    public boolean isConsistent() {
        if (shapValues == null || baseValue == null || prediction == null) {
            return false;
        }

        double sumShap = 0.0;
        for (double val : shapValues) {
            sumShap += val;
        }

        double expectedPrediction = baseValue + sumShap;
        double diff = Math.abs(expectedPrediction - prediction);

        return diff < 0.01; // Tolérance de 1%
    }
}

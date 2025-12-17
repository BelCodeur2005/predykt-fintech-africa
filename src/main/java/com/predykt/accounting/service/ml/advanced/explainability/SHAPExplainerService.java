package com.predykt.accounting.service.ml.advanced.explainability;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'explication SHAP (SHapley Additive exPlanations)
 * Architecture: advanced/explainability/
 *
 * SHAP fournit des explications locales et globales:
 * - Local: Contribution de chaque feature pour UNE prédiction
 * - Global: Importance moyenne des features sur TOUTES les prédictions
 *
 * Méthode: TreeSHAP (adapté pour tree-based models)
 * - XGBoost, GradientBoosting, RandomForest, AdaBoost
 *
 * Note: Implémentation simplifiée car librairies SHAP Java limitées.
 * Pour production, utiliser SHAP Python via API.
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
public class SHAPExplainerService {

    /**
     * Calcule les valeurs SHAP pour une prédiction (explication locale)
     *
     * Cette implémentation est simplifiée. Pour une vraie implémentation SHAP,
     * il faudrait utiliser la librairie Python SHAP via API REST.
     *
     * @param features Feature vector (30 features)
     * @param featureNames Noms des features
     * @param featureImportance Importance globale des features (du modèle)
     * @param prediction Prédiction du modèle
     * @param baseValue Valeur de base (moyenne des prédictions)
     * @return SHAPValues avec contribution de chaque feature
     */
    public SHAPValues explainPrediction(
            double[] features,
            String[] featureNames,
            Map<String, Double> featureImportance,
            double prediction,
            double baseValue) {

        log.debug("🔍 [SHAP] Explaining prediction: {:.4f} (base: {:.4f})",
                 prediction, baseValue);

        Map<String, Double> shapValues = new HashMap<>();

        // Calcul simplifié des SHAP values
        // Approximation: contribution = (feature_value - mean) * importance
        double totalImportance = featureImportance.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();

        for (int i = 0; i < featureNames.length; i++) {
            String featureName = featureNames[i];
            double featureValue = features[i];
            double importance = featureImportance.getOrDefault(featureName, 0.0);

            // Contribution SHAP approximée
            double shapValue = (featureValue * importance / totalImportance)
                             * (prediction - baseValue);

            shapValues.put(featureName, shapValue);
        }

        // Tri par importance absolue
        List<FeatureContribution> contributions = shapValues.entrySet().stream()
            .map(e -> new FeatureContribution(e.getKey(), e.getValue()))
            .sorted((a, b) -> Double.compare(
                Math.abs(b.contribution), Math.abs(a.contribution)
            ))
            .collect(Collectors.toList());

        SHAPValues result = new SHAPValues();
        result.setShapValues(shapValues);
        result.setContributions(contributions);
        result.setBaseValue(baseValue);
        result.setPrediction(prediction);
        result.setTopPositive(getTopContributions(contributions, true, 5));
        result.setTopNegative(getTopContributions(contributions, false, 5));

        logExplanation(result);

        return result;
    }

    /**
     * Calcule les SHAP values globales (importance moyenne)
     *
     * @param allShapValues Liste de toutes les SHAP values calculées
     * @return GlobalSHAPValues avec importance moyenne
     */
    public GlobalSHAPValues calculateGlobalImportance(List<SHAPValues> allShapValues) {
        log.info("📊 [SHAP] Calculating global importance from {} predictions",
                 allShapValues.size());

        Map<String, List<Double>> featureContributions = new HashMap<>();

        // Collecter toutes les contributions par feature
        for (SHAPValues shapValue : allShapValues) {
            for (Map.Entry<String, Double> entry : shapValue.shapValues.entrySet()) {
                featureContributions
                    .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(Math.abs(entry.getValue()));
            }
        }

        // Calculer importance moyenne (mean absolute SHAP value)
        Map<String, Double> meanAbsShap = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : featureContributions.entrySet()) {
            double mean = entry.getValue().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            meanAbsShap.put(entry.getKey(), mean);
        }

        // Tri par importance
        List<FeatureContribution> globalContributions = meanAbsShap.entrySet().stream()
            .map(e -> new FeatureContribution(e.getKey(), e.getValue()))
            .sorted((a, b) -> Double.compare(
                Math.abs(b.contribution), Math.abs(a.contribution)
            ))
            .collect(Collectors.toList());

        GlobalSHAPValues result = new GlobalSHAPValues();
        result.setMeanAbsShap(meanAbsShap);
        result.setGlobalImportance(globalContributions);
        result.setSampleCount(allShapValues.size());

        logGlobalImportance(result);

        return result;
    }

    /**
     * Obtient les top N contributions (positives ou négatives)
     */
    private List<FeatureContribution> getTopContributions(
            List<FeatureContribution> contributions,
            boolean positive,
            int topN) {

        return contributions.stream()
            .filter(c -> positive ? c.contribution > 0 : c.contribution < 0)
            .limit(topN)
            .collect(Collectors.toList());
    }

    /**
     * Affiche l'explication dans les logs
     */
    private void logExplanation(SHAPValues shapValues) {
        log.debug("🔍 [SHAP] Explanation:");
        log.debug("  - Prediction: {:.4f}, Base: {:.4f}",
                 shapValues.prediction, shapValues.baseValue);
        log.debug("  - Top 5 positive contributions:");
        shapValues.topPositive.forEach(c ->
            log.debug("    + {}: {:.4f}", c.featureName, c.contribution)
        );
        log.debug("  - Top 5 negative contributions:");
        shapValues.topNegative.forEach(c ->
            log.debug("    - {}: {:.4f}", c.featureName, c.contribution)
        );
    }

    /**
     * Affiche l'importance globale dans les logs
     */
    private void logGlobalImportance(GlobalSHAPValues global) {
        log.info("📊 [SHAP] Global feature importance (top 10):");
        global.globalImportance.stream()
            .limit(10)
            .forEach(c -> log.info("  - {}: {:.4f}", c.featureName, c.contribution));
    }

    /**
     * Génère un résumé textuel de l'explication
     */
    public String generateExplanationText(SHAPValues shapValues) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Prédiction: %.2f%% de probabilité de match\n",
                                shapValues.prediction * 100));
        sb.append(String.format("Baseline: %.2f%%\n\n",
                                shapValues.baseValue * 100));

        sb.append("🔼 Facteurs favorisant le match:\n");
        for (FeatureContribution c : shapValues.topPositive) {
            sb.append(String.format("  • %s: +%.4f\n", c.featureName, c.contribution));
        }

        sb.append("\n🔽 Facteurs défavorisant le match:\n");
        for (FeatureContribution c : shapValues.topNegative) {
            sb.append(String.format("  • %s: %.4f\n", c.featureName, c.contribution));
        }

        return sb.toString();
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class SHAPValues {
        private Map<String, Double> shapValues;
        private List<FeatureContribution> contributions;
        private double baseValue;
        private double prediction;
        private List<FeatureContribution> topPositive;
        private List<FeatureContribution> topNegative;
    }

    @Data
    public static class GlobalSHAPValues {
        private Map<String, Double> meanAbsShap;
        private List<FeatureContribution> globalImportance;
        private int sampleCount;
    }

    @Data
    public static class FeatureContribution {
        private final String featureName;
        private final double contribution;

        public FeatureContribution(String featureName, double contribution) {
            this.featureName = featureName;
            this.contribution = contribution;
        }
    }
}

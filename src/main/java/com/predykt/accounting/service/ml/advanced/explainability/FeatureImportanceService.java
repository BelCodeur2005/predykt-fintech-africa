package com.predykt.accounting.service.ml.advanced.explainability;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.stereotype.Service;
import smile.classification.AdaBoost;
import smile.classification.GradientTreeBoost;
import smile.classification.RandomForest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'extraction de l'importance des features
 * Architecture: advanced/explainability/
 *
 * Méthodes d'importance supportées:
 * 1. Gain: Total gain (amélioration de loss) par feature
 * 2. Cover: Nombre d'échantillons affectés par la feature
 * 3. Weight: Nombre de fois que la feature est utilisée dans les splits
 *
 * Compatible avec:
 * - XGBoost (gain, cover, weight)
 * - GradientBoosting (gain via Smile)
 * - RandomForest (gain via Smile)
 * - AdaBoost (gain via Smile)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureImportanceService {

    /**
     * Extrait l'importance des features depuis XGBoost
     *
     * @param booster Modèle XGBoost entraîné
     * @param featureNames Noms des 30 features
     * @param importanceType Type: "gain", "cover", "weight"
     * @return FeatureImportance avec scores normalisés
     */
    public FeatureImportance extractXGBoostImportance(
            Booster booster,
            String[] featureNames,
            String importanceType) throws XGBoostError {

        log.info("📊 [FeatureImportance] Extracting XGBoost importance (type={})",
                 importanceType);

        // XGBoost getScore() retourne Map<String, ?> (Integer ou Double selon version)
        Map<String, ?> rawScores = booster.getScore("", importanceType);

        // Convertir en Map<String, Double>
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, ?> entry : rawScores.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Integer) {
                scores.put(entry.getKey(), ((Integer) value).doubleValue());
            } else if (value instanceof Double) {
                scores.put(entry.getKey(), (Double) value);
            } else {
                scores.put(entry.getKey(), Double.valueOf(value.toString()));
            }
        }

        // Normaliser les scores [0-1]
        Map<String, Double> normalizedScores = normalizeScoresDouble(scores);

        // Trier par importance
        List<FeatureScore> rankedFeatures = normalizedScores.entrySet().stream()
            .map(e -> new FeatureScore(e.getKey(), e.getValue()))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .collect(Collectors.toList());

        FeatureImportance result = new FeatureImportance();
        result.setModelType("XGBoost");
        result.setImportanceType(importanceType);
        result.setScores(normalizedScores);
        result.setRankedFeatures(rankedFeatures);
        result.setTopFeatures(rankedFeatures.stream().limit(10).collect(Collectors.toList()));

        logTopFeatures(result);

        return result;
    }

    /**
     * Extrait l'importance des features depuis Gradient Boosting
     */
    public FeatureImportance extractGradientBoostingImportance(
            GradientTreeBoost model,
            String[] featureNames) {

        log.info("📊 [FeatureImportance] Extracting GradientBoosting importance");

        double[] importance = model.importance();

        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < featureNames.length; i++) {
            scores.put(featureNames[i], importance[i]);
        }

        List<FeatureScore> rankedFeatures = scores.entrySet().stream()
            .map(e -> new FeatureScore(e.getKey(), e.getValue()))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .collect(Collectors.toList());

        FeatureImportance result = new FeatureImportance();
        result.setModelType("GradientBoosting");
        result.setImportanceType("gain");
        result.setScores(scores);
        result.setRankedFeatures(rankedFeatures);
        result.setTopFeatures(rankedFeatures.stream().limit(10).collect(Collectors.toList()));

        logTopFeatures(result);

        return result;
    }

    /**
     * Extrait l'importance des features depuis Random Forest
     */
    public FeatureImportance extractRandomForestImportance(
            RandomForest model,
            String[] featureNames) {

        log.info("📊 [FeatureImportance] Extracting RandomForest importance");

        double[] importance = model.importance();

        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < featureNames.length; i++) {
            scores.put(featureNames[i], importance[i]);
        }

        List<FeatureScore> rankedFeatures = scores.entrySet().stream()
            .map(e -> new FeatureScore(e.getKey(), e.getValue()))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .collect(Collectors.toList());

        FeatureImportance result = new FeatureImportance();
        result.setModelType("RandomForest");
        result.setImportanceType("gain");
        result.setScores(scores);
        result.setRankedFeatures(rankedFeatures);
        result.setTopFeatures(rankedFeatures.stream().limit(10).collect(Collectors.toList()));

        logTopFeatures(result);

        return result;
    }

    /**
     * Extrait l'importance des features depuis AdaBoost
     */
    public FeatureImportance extractAdaBoostImportance(
            AdaBoost model,
            String[] featureNames) {

        log.info("📊 [FeatureImportance] Extracting AdaBoost importance");

        double[] importance = model.importance();

        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < featureNames.length; i++) {
            scores.put(featureNames[i], importance[i]);
        }

        List<FeatureScore> rankedFeatures = scores.entrySet().stream()
            .map(e -> new FeatureScore(e.getKey(), e.getValue()))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .collect(Collectors.toList());

        FeatureImportance result = new FeatureImportance();
        result.setModelType("AdaBoost");
        result.setImportanceType("gain");
        result.setScores(scores);
        result.setRankedFeatures(rankedFeatures);
        result.setTopFeatures(rankedFeatures.stream().limit(10).collect(Collectors.toList()));

        logTopFeatures(result);

        return result;
    }

    /**
     * Combine l'importance de plusieurs modèles (moyenne pondérée)
     */
    public FeatureImportance combineImportance(
            List<FeatureImportance> importanceList,
            double[] weights) {

        log.info("📊 [FeatureImportance] Combining {} models importance",
                 importanceList.size());

        if (importanceList.size() != weights.length) {
            throw new IllegalArgumentException("Weights count must match models count");
        }

        // Normaliser les poids
        double totalWeight = Arrays.stream(weights).sum();
        double[] normalizedWeights = Arrays.stream(weights)
            .map(w -> w / totalWeight)
            .toArray();

        Map<String, Double> combinedScores = new HashMap<>();

        // Pour chaque feature, calculer moyenne pondérée
        Set<String> allFeatures = new HashSet<>();
        importanceList.forEach(fi -> allFeatures.addAll(fi.scores.keySet()));

        for (String feature : allFeatures) {
            double weightedSum = 0.0;

            for (int i = 0; i < importanceList.size(); i++) {
                double score = importanceList.get(i).scores.getOrDefault(feature, 0.0);
                weightedSum += score * normalizedWeights[i];
            }

            combinedScores.put(feature, weightedSum);
        }

        List<FeatureScore> rankedFeatures = combinedScores.entrySet().stream()
            .map(e -> new FeatureScore(e.getKey(), e.getValue()))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .collect(Collectors.toList());

        FeatureImportance result = new FeatureImportance();
        result.setModelType("Ensemble");
        result.setImportanceType("weighted_average");
        result.setScores(combinedScores);
        result.setRankedFeatures(rankedFeatures);
        result.setTopFeatures(rankedFeatures.stream().limit(10).collect(Collectors.toList()));

        logTopFeatures(result);

        return result;
    }

    /**
     * Normalise les scores [0-1]
     */
    private Map<String, Double> normalizeScores(Map<String, Integer> rawScores) {
        if (rawScores.isEmpty()) {
            return new HashMap<>();
        }

        int maxScore = rawScores.values().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(1);

        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Integer> entry : rawScores.entrySet()) {
            normalized.put(entry.getKey(), (double) entry.getValue() / maxScore);
        }

        return normalized;
    }

    /**
     * Normalise les scores Double [0-1]
     */
    private Map<String, Double> normalizeScoresDouble(Map<String, Double> rawScores) {
        if (rawScores.isEmpty()) {
            return new HashMap<>();
        }

        double maxScore = rawScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(1.0);

        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : rawScores.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / maxScore);
        }

        return normalized;
    }

    /**
     * Affiche les top features dans les logs
     */
    private void logTopFeatures(FeatureImportance importance) {
        log.info("📊 [FeatureImportance] Top 10 features ({}):",
                 importance.modelType);

        importance.topFeatures.forEach(fs ->
            log.info("  {} - {}: {:.4f}",
                     importance.topFeatures.indexOf(fs) + 1,
                     fs.featureName,
                     fs.score)
        );
    }

    /**
     * Génère un rapport textuel de l'importance
     */
    public String generateImportanceReport(FeatureImportance importance) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("=== Feature Importance Report (%s) ===\n\n",
                                importance.modelType));
        sb.append(String.format("Importance Type: %s\n", importance.importanceType));
        sb.append(String.format("Total Features: %d\n\n", importance.scores.size()));

        sb.append("Top 15 Features:\n");
        sb.append(String.format("%-5s %-35s %s\n", "Rank", "Feature", "Score"));
        sb.append("-".repeat(60) + "\n");

        List<FeatureScore> top15 = importance.rankedFeatures.stream()
            .limit(15)
            .collect(Collectors.toList());

        for (int i = 0; i < top15.size(); i++) {
            FeatureScore fs = top15.get(i);
            sb.append(String.format("%-5d %-35s %.4f\n",
                                    i + 1, fs.featureName, fs.score));
        }

        return sb.toString();
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class FeatureImportance {
        private String modelType;
        private String importanceType;
        private Map<String, Double> scores;
        private List<FeatureScore> rankedFeatures;
        private List<FeatureScore> topFeatures;
    }

    @Data
    public static class FeatureScore {
        private final String featureName;
        private final double score;

        public FeatureScore(String featureName, double score) {
            this.featureName = featureName;
            this.score = score;
        }
    }
}

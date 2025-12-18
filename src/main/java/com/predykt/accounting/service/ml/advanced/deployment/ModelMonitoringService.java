package com.predykt.accounting.service.ml.advanced.deployment;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de monitoring des modèles ML en production
 * Architecture: advanced/deployment/
 *
 * Fonctionnalités:
 * 1. Détection de drift (data drift, concept drift)
 * 2. Monitoring des métriques en temps réel
 * 3. Alertes automatiques si dégradation
 * 4. Statistiques de prédictions
 * 5. Performance tracking
 *
 * Data Drift: Distribution des features change
 * Concept Drift: Relation features-target change
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelMonitoringService {

    // Cache des statistiques par modèle (company_id + model_type)
    private final Map<String, ModelStatistics> modelStats = new ConcurrentHashMap<>();

    // Seuils par défaut pour alertes
    private static final double ACCURACY_DROP_THRESHOLD = 0.05;  // 5% drop
    private static final double F1_DROP_THRESHOLD = 0.05;
    private static final int MIN_PREDICTIONS_FOR_DRIFT = 100;

    /**
     * Enregistre une prédiction pour monitoring
     *
     * @param companyId ID société
     * @param modelType Type de modèle
     * @param features Features utilisées
     * @param prediction Prédiction (0 ou 1)
     * @param probability Probabilité [0-1]
     * @param actualLabel Label réel (si disponible, sinon null)
     */
    public void recordPrediction(
            Long companyId,
            String modelType,
            double[] features,
            int prediction,
            double probability,
            Integer actualLabel) {

        String modelKey = getModelKey(companyId, modelType);

        ModelStatistics stats = modelStats.computeIfAbsent(modelKey, k -> new ModelStatistics());

        // Enregistrer prédiction
        PredictionRecord record = new PredictionRecord();
        record.setTimestamp(LocalDateTime.now());
        record.setFeatures(features);
        record.setPrediction(prediction);
        record.setProbability(probability);
        record.setActualLabel(actualLabel);

        stats.addPrediction(record);

        // Détecter drift si assez de prédictions
        if (stats.getPredictionCount() % 100 == 0) {
            DriftDetectionResult driftResult = detectDrift(stats);

            if (driftResult.isDriftDetected()) {
                log.warn("⚠️ [Monitoring] DRIFT DETECTED - Model: {}", modelType);
                log.warn("   Drift type: {}", driftResult.getDriftType());
                log.warn("   Drift score: {:.4f}", driftResult.getDriftScore());

                // Émettre alerte
                emitDriftAlert(companyId, modelType, driftResult);
            }
        }

        // Vérifier dégradation performance si label disponible
        if (actualLabel != null && stats.getPredictionCount() % 50 == 0) {
            PerformanceMetrics recentMetrics = calculateRecentMetrics(stats, 50);

            if (recentMetrics.getAccuracy() < stats.getBaselineAccuracy() - ACCURACY_DROP_THRESHOLD) {
                log.warn("⚠️ [Monitoring] PERFORMANCE DROP - Model: {}", modelType);
                log.warn("   Current accuracy: {:.4f}, Baseline: {:.4f}",
                         recentMetrics.getAccuracy(), stats.getBaselineAccuracy());

                emitPerformanceAlert(companyId, modelType, recentMetrics, stats);
            }
        }
    }

    /**
     * Détection de drift (data drift)
     * Utilise statistiques des features
     */
    public DriftDetectionResult detectDrift(ModelStatistics stats) {

        if (stats.getPredictionCount() < MIN_PREDICTIONS_FOR_DRIFT) {
            DriftDetectionResult result = new DriftDetectionResult();
            result.setDriftDetected(false);
            result.setMessage("Not enough predictions for drift detection");
            return result;
        }

        // Calculer statistiques baseline vs recent
        List<PredictionRecord> recentPredictions = stats.getRecentPredictions(100);
        List<PredictionRecord> baselinePredictions = stats.getBaselinePredictions(100);

        // Calculer distance KL (Kullback-Leibler divergence)
        // ou distance de Kolmogorov-Smirnov entre distributions

        double driftScore = calculateFeatureDrift(baselinePredictions, recentPredictions);

        boolean driftDetected = driftScore > 0.3;  // Seuil arbitraire

        DriftDetectionResult result = new DriftDetectionResult();
        result.setDriftDetected(driftDetected);
        result.setDriftScore(driftScore);
        result.setDriftType(driftDetected ? "DATA_DRIFT" : "NO_DRIFT");
        result.setDetectedAt(LocalDateTime.now());

        return result;
    }

    /**
     * Calcule drift score entre deux distributions de features
     */
    private double calculateFeatureDrift(
            List<PredictionRecord> baseline,
            List<PredictionRecord> recent) {

        if (baseline.isEmpty() || recent.isEmpty()) {
            return 0.0;
        }

        int nFeatures = baseline.get(0).getFeatures().length;
        double totalDrift = 0.0;

        // Pour chaque feature, calculer différence de distribution
        for (int j = 0; j < nFeatures; j++) {
            final int featureIndex = j;

            // Calculer mean et std baseline
            double baselineMean = baseline.stream()
                .mapToDouble(p -> p.getFeatures()[featureIndex])
                .average()
                .orElse(0.0);

            double baselineStd = calculateStd(
                baseline.stream().mapToDouble(p -> p.getFeatures()[featureIndex]).toArray(),
                baselineMean
            );

            // Calculer mean et std recent
            double recentMean = recent.stream()
                .mapToDouble(p -> p.getFeatures()[featureIndex])
                .average()
                .orElse(0.0);

            double recentStd = calculateStd(
                recent.stream().mapToDouble(p -> p.getFeatures()[featureIndex]).toArray(),
                recentMean
            );

            // Drift score = différence normalisée
            double meanDiff = Math.abs(recentMean - baselineMean);
            double stdDiff = Math.abs(recentStd - baselineStd);

            double featureDrift = (meanDiff / (baselineStd + 1e-10)) + (stdDiff / (baselineStd + 1e-10));

            totalDrift += featureDrift;
        }

        return totalDrift / nFeatures;
    }

    /**
     * Calcule écart-type
     */
    private double calculateStd(double[] values, double mean) {
        double variance = 0.0;

        for (double value : values) {
            double diff = value - mean;
            variance += diff * diff;
        }

        return Math.sqrt(variance / values.length);
    }

    /**
     * Calcule métriques sur les N dernières prédictions
     */
    public PerformanceMetrics calculateRecentMetrics(ModelStatistics stats, int n) {
        List<PredictionRecord> recent = stats.getRecentPredictions(n);

        // Filtrer prédictions avec labels
        List<PredictionRecord> withLabels = recent.stream()
            .filter(p -> p.getActualLabel() != null)
            .toList();

        if (withLabels.isEmpty()) {
            return new PerformanceMetrics();
        }

        int tp = 0, tn = 0, fp = 0, fn = 0;

        for (PredictionRecord pred : withLabels) {
            int predicted = pred.getPrediction();
            int actual = pred.getActualLabel();

            if (predicted == 1 && actual == 1) tp++;
            else if (predicted == 0 && actual == 0) tn++;
            else if (predicted == 1 && actual == 0) fp++;
            else if (predicted == 0 && actual == 1) fn++;
        }

        double accuracy = (double) (tp + tn) / withLabels.size();
        double precision = tp + fp > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall = tp + fn > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0.0;

        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setAccuracy(accuracy);
        metrics.setPrecision(precision);
        metrics.setRecall(recall);
        metrics.setF1Score(f1);
        metrics.setSampleSize(withLabels.size());

        return metrics;
    }

    /**
     * Obtient statistiques d'un modèle
     */
    public ModelStatistics getModelStatistics(Long companyId, String modelType) {
        String modelKey = getModelKey(companyId, modelType);
        return modelStats.getOrDefault(modelKey, new ModelStatistics());
    }

    /**
     * Réinitialise baseline après retraining
     */
    public void resetBaseline(Long companyId, String modelType, double accuracy, double f1Score) {
        String modelKey = getModelKey(companyId, modelType);

        ModelStatistics stats = modelStats.computeIfAbsent(modelKey, k -> new ModelStatistics());
        stats.setBaselineAccuracy(accuracy);
        stats.setBaselineF1Score(f1Score);
        stats.setBaselineSetAt(LocalDateTime.now());

        log.info("📊 [Monitoring] Baseline reset - Model: {}, Accuracy: {:.4f}, F1: {:.4f}",
                 modelType, accuracy, f1Score);
    }

    /**
     * Génère rapport de monitoring
     */
    public MonitoringReport generateReport(Long companyId, String modelType) {
        ModelStatistics stats = getModelStatistics(companyId, modelType);

        MonitoringReport report = new MonitoringReport();
        report.setCompanyId(companyId);
        report.setModelType(modelType);
        report.setTotalPredictions(stats.getPredictionCount());
        report.setBaselineAccuracy(stats.getBaselineAccuracy());
        report.setBaselineF1Score(stats.getBaselineF1Score());

        // Métriques récentes (derniers 100 prédictions)
        PerformanceMetrics recentMetrics = calculateRecentMetrics(stats, 100);
        report.setRecentMetrics(recentMetrics);

        // Détection drift
        DriftDetectionResult driftResult = detectDrift(stats);
        report.setDriftDetectionResult(driftResult);

        // Distribution prédictions
        long positives = stats.getRecentPredictions(1000).stream()
            .filter(p -> p.getPrediction() == 1)
            .count();
        report.setPositiveRate((double) positives / Math.min(1000, stats.getPredictionCount()));

        return report;
    }

    // ========== ALERTES ==========

    private void emitDriftAlert(Long companyId, String modelType, DriftDetectionResult driftResult) {
        // TODO: Implémenter système d'alertes (email, Slack, etc.)
        log.error("🚨 [ALERT] Data drift detected - Company: {}, Model: {}, Score: {:.4f}",
                  companyId, modelType, driftResult.getDriftScore());
    }

    private void emitPerformanceAlert(
            Long companyId,
            String modelType,
            PerformanceMetrics recentMetrics,
            ModelStatistics stats) {

        log.error("🚨 [ALERT] Performance degradation - Company: {}, Model: {}",
                  companyId, modelType);
        log.error("   Current accuracy: {:.4f}, Baseline: {:.4f}, Drop: {:.4f}",
                  recentMetrics.getAccuracy(),
                  stats.getBaselineAccuracy(),
                  stats.getBaselineAccuracy() - recentMetrics.getAccuracy());
    }

    private String getModelKey(Long companyId, String modelType) {
        return companyId + "_" + modelType;
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class ModelStatistics {
        private long predictionCount = 0;
        private List<PredictionRecord> predictions = Collections.synchronizedList(new ArrayList<>());
        private double baselineAccuracy = 0.0;
        private double baselineF1Score = 0.0;
        private LocalDateTime baselineSetAt;

        public synchronized void addPrediction(PredictionRecord record) {
            predictions.add(record);
            predictionCount++;

            // Garder seulement les 10000 dernières prédictions
            if (predictions.size() > 10000) {
                predictions.remove(0);
            }
        }

        public List<PredictionRecord> getRecentPredictions(int n) {
            int size = predictions.size();
            int start = Math.max(0, size - n);
            return new ArrayList<>(predictions.subList(start, size));
        }

        public List<PredictionRecord> getBaselinePredictions(int n) {
            int size = Math.min(n, predictions.size() / 2);
            return new ArrayList<>(predictions.subList(0, size));
        }
    }

    @Data
    public static class PredictionRecord {
        private LocalDateTime timestamp;
        private double[] features;
        private int prediction;
        private double probability;
        private Integer actualLabel;
    }

    @Data
    public static class DriftDetectionResult {
        private boolean driftDetected;
        private String driftType;
        private double driftScore;
        private LocalDateTime detectedAt;
        private String message;
    }

    @Data
    public static class PerformanceMetrics {
        private double accuracy;
        private double precision;
        private double recall;
        private double f1Score;
        private int sampleSize;
    }

    @Data
    public static class MonitoringReport {
        private Long companyId;
        private String modelType;
        private long totalPredictions;
        private double baselineAccuracy;
        private double baselineF1Score;
        private PerformanceMetrics recentMetrics;
        private DriftDetectionResult driftDetectionResult;
        private double positiveRate;
    }
}

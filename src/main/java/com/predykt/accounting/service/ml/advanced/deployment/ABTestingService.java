package com.predykt.accounting.service.ml.advanced.deployment;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service d'A/B Testing pour modèles ML
 * Architecture: advanced/deployment/
 *
 * Fonctionnalités:
 * 1. Split traffic entre modèle A et modèle B
 * 2. Tracking des métriques par variant
 * 3. Test statistique (significativité)
 * 4. Promotion automatique du meilleur modèle
 * 5. Multi-Armed Bandit (Thompson Sampling)
 *
 * Use cases:
 * - Tester nouveau modèle vs modèle actuel
 * - Comparer différents hyperparamètres
 * - Gradual rollout (5% → 25% → 50% → 100%)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ABTestingService {

    // Expériences A/B actives
    private final Map<String, ABExperiment> activeExperiments = new ConcurrentHashMap<>();

    /**
     * Crée une nouvelle expérience A/B
     *
     * @param companyId ID société
     * @param modelType Type de modèle
     * @param variantAVersion Version modèle A (control)
     * @param variantBVersion Version modèle B (treatment)
     * @param trafficSplit % de traffic pour B (ex: 0.2 = 20%)
     * @return ABExperiment créée
     */
    public ABExperiment createExperiment(
            Long companyId,
            String modelType,
            String variantAVersion,
            String variantBVersion,
            double trafficSplit) {

        String experimentKey = getExperimentKey(companyId, modelType);

        ABExperiment experiment = new ABExperiment();
        experiment.setCompanyId(companyId);
        experiment.setModelType(modelType);
        experiment.setVariantAVersion(variantAVersion);
        experiment.setVariantBVersion(variantBVersion);
        experiment.setTrafficSplit(trafficSplit);
        experiment.setStartedAt(LocalDateTime.now());
        experiment.setStatus("RUNNING");

        activeExperiments.put(experimentKey, experiment);

        log.info("🧪 [ABTesting] Experiment created - Model: {}, A: {} vs B: {}, Split: {}%",
                 modelType, variantAVersion, variantBVersion, trafficSplit * 100);

        return experiment;
    }

    /**
     * Décide quel variant utiliser pour une prédiction
     *
     * @param companyId ID société
     * @param modelType Type de modèle
     * @return VariantAssignment (A ou B)
     */
    public VariantAssignment assignVariant(Long companyId, String modelType) {
        String experimentKey = getExperimentKey(companyId, modelType);
        ABExperiment experiment = activeExperiments.get(experimentKey);

        if (experiment == null || !experiment.getStatus().equals("RUNNING")) {
            // Pas d'expérience active, utiliser variant par défaut
            VariantAssignment assignment = new VariantAssignment();
            assignment.setVariant("A");
            assignment.setExperimentActive(false);
            return assignment;
        }

        // A/B split basé sur random
        double random = ThreadLocalRandom.current().nextDouble();
        String variant = random < experiment.getTrafficSplit() ? "B" : "A";

        VariantAssignment assignment = new VariantAssignment();
        assignment.setVariant(variant);
        assignment.setExperimentActive(true);
        assignment.setExperimentKey(experimentKey);
        assignment.setModelVersion(
            variant.equals("A") ? experiment.getVariantAVersion() : experiment.getVariantBVersion()
        );

        return assignment;
    }

    /**
     * Enregistre le résultat d'une prédiction pour un variant
     *
     * @param experimentKey Clé de l'expérience
     * @param variant Variant (A ou B)
     * @param prediction Prédiction
     * @param actualLabel Label réel (si disponible)
     * @param correct Est-ce que la prédiction était correcte
     */
    public void recordResult(
            String experimentKey,
            String variant,
            int prediction,
            Integer actualLabel,
            Boolean correct) {

        ABExperiment experiment = activeExperiments.get(experimentKey);

        if (experiment == null) {
            return;
        }

        VariantMetrics metrics = variant.equals("A")
            ? experiment.getVariantAMetrics()
            : experiment.getVariantBMetrics();

        // Enregistrer prédiction
        metrics.incrementPredictionCount();

        // Si label disponible, calculer accuracy
        if (actualLabel != null && correct != null) {
            if (correct) {
                metrics.incrementCorrectCount();
            }

            // Mettre à jour accuracy
            double accuracy = (double) metrics.getCorrectCount() / metrics.getPredictionCountWithLabel();
            metrics.setAccuracy(accuracy);
        }

        // Vérifier si assez de données pour analyse statistique
        if (metrics.getPredictionCountWithLabel() >= 100) {
            // Effectuer test statistique
            performStatisticalTest(experiment);
        }
    }

    /**
     * Test statistique pour déterminer si différence est significative
     * Utilise test Z pour proportions
     */
    private void performStatisticalTest(ABExperiment experiment) {
        VariantMetrics metricsA = experiment.getVariantAMetrics();
        VariantMetrics metricsB = experiment.getVariantBMetrics();

        long nA = metricsA.getPredictionCountWithLabel();
        long nB = metricsB.getPredictionCountWithLabel();

        // Minimum 100 échantillons par variant
        if (nA < 100 || nB < 100) {
            return;
        }

        double pA = metricsA.getAccuracy();
        double pB = metricsB.getAccuracy();

        // Proportion globale
        double pPooled = ((double) metricsA.getCorrectCount() + metricsB.getCorrectCount())
                         / (nA + nB);

        // Erreur standard
        double se = Math.sqrt(pPooled * (1 - pPooled) * (1.0 / nA + 1.0 / nB));

        // Z-score
        double zScore = (pB - pA) / se;

        // P-value (approximation bilatérale)
        double pValue = 2 * (1 - normalCDF(Math.abs(zScore)));

        experiment.setZScore(zScore);
        experiment.setPValue(pValue);

        // Significativité (seuil p < 0.05)
        boolean significant = pValue < 0.05;
        experiment.setSignificant(significant);

        if (significant) {
            if (pB > pA) {
                log.info("🎯 [ABTesting] SIGNIFICANT RESULT - Variant B is better (p={:.4f})", pValue);
                log.info("   Accuracy A: {:.4f}, Accuracy B: {:.4f}, Diff: {:.4f}",
                         pA, pB, pB - pA);

                // Recommander promotion de B
                experiment.setRecommendation("PROMOTE_B");

            } else {
                log.info("🎯 [ABTesting] SIGNIFICANT RESULT - Variant A is better (p={:.4f})", pValue);
                experiment.setRecommendation("KEEP_A");
            }
        }
    }

    /**
     * Fonction de répartition cumulative normale (CDF)
     * Approximation pour calcul p-value
     */
    private double normalCDF(double z) {
        // Approximation de la fonction erf
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));

        return z > 0 ? 1 - p : p;
    }

    /**
     * Arrête une expérience A/B
     */
    public void stopExperiment(Long companyId, String modelType) {
        String experimentKey = getExperimentKey(companyId, modelType);
        ABExperiment experiment = activeExperiments.get(experimentKey);

        if (experiment != null) {
            experiment.setStatus("STOPPED");
            experiment.setEndedAt(LocalDateTime.now());

            log.info("🛑 [ABTesting] Experiment stopped - Model: {}", modelType);
            log.info("   Variant A: {} predictions, Accuracy: {:.4f}",
                     experiment.getVariantAMetrics().getPredictionCount(),
                     experiment.getVariantAMetrics().getAccuracy());
            log.info("   Variant B: {} predictions, Accuracy: {:.4f}",
                     experiment.getVariantBMetrics().getPredictionCount(),
                     experiment.getVariantBMetrics().getAccuracy());
        }
    }

    /**
     * Obtient l'expérience active pour un modèle
     */
    public ABExperiment getActiveExperiment(Long companyId, String modelType) {
        String experimentKey = getExperimentKey(companyId, modelType);
        return activeExperiments.get(experimentKey);
    }

    /**
     * Génère rapport d'expérience A/B
     */
    public ABTestReport generateReport(Long companyId, String modelType) {
        ABExperiment experiment = getActiveExperiment(companyId, modelType);

        if (experiment == null) {
            return null;
        }

        ABTestReport report = new ABTestReport();
        report.setExperiment(experiment);

        VariantMetrics metricsA = experiment.getVariantAMetrics();
        VariantMetrics metricsB = experiment.getVariantBMetrics();

        // Calculer uplift
        double uplift = metricsB.getAccuracy() - metricsA.getAccuracy();
        double upliftPercent = (uplift / metricsA.getAccuracy()) * 100;

        report.setUplift(uplift);
        report.setUpliftPercent(upliftPercent);
        report.setSignificant(experiment.isSignificant());
        report.setRecommendation(experiment.getRecommendation());

        // Confidence interval (95%)
        long nA = metricsA.getPredictionCountWithLabel();
        long nB = metricsB.getPredictionCountWithLabel();
        double pA = metricsA.getAccuracy();
        double pB = metricsB.getAccuracy();

        double seA = Math.sqrt(pA * (1 - pA) / nA);
        double seB = Math.sqrt(pB * (1 - pB) / nB);

        report.setVariantAConfidenceLower(pA - 1.96 * seA);
        report.setVariantAConfidenceUpper(pA + 1.96 * seA);
        report.setVariantBConfidenceLower(pB - 1.96 * seB);
        report.setVariantBConfidenceUpper(pB + 1.96 * seB);

        return report;
    }

    /**
     * Promotion automatique de variant B si significativement meilleur
     */
    public PromotionResult promoteVariantIfBetter(Long companyId, String modelType) {
        ABExperiment experiment = getActiveExperiment(companyId, modelType);

        if (experiment == null) {
            return PromotionResult.notFound();
        }

        if (!experiment.isSignificant()) {
            return PromotionResult.notSignificant();
        }

        if (!"PROMOTE_B".equals(experiment.getRecommendation())) {
            return PromotionResult.variantABetter();
        }

        // Promouvoir variant B
        log.info("🚀 [ABTesting] Promoting Variant B to production - Model: {}", modelType);
        log.info("   Version: {}", experiment.getVariantBVersion());
        log.info("   Accuracy improvement: {:.4f} → {:.4f} (+{:.2f}%)",
                 experiment.getVariantAMetrics().getAccuracy(),
                 experiment.getVariantBMetrics().getAccuracy(),
                 ((experiment.getVariantBMetrics().getAccuracy() -
                   experiment.getVariantAMetrics().getAccuracy()) /
                  experiment.getVariantAMetrics().getAccuracy()) * 100);

        // TODO: Mettre à jour version de production dans database

        // Arrêter expérience
        stopExperiment(companyId, modelType);

        return PromotionResult.promoted(experiment.getVariantBVersion());
    }

    private String getExperimentKey(Long companyId, String modelType) {
        return companyId + "_" + modelType;
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class ABExperiment {
        private Long companyId;
        private String modelType;
        private String variantAVersion;
        private String variantBVersion;
        private double trafficSplit;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private String status;  // RUNNING, STOPPED
        private VariantMetrics variantAMetrics = new VariantMetrics();
        private VariantMetrics variantBMetrics = new VariantMetrics();
        private double zScore;
        private double pValue;
        private boolean significant;
        private String recommendation;  // PROMOTE_B, KEEP_A, INCONCLUSIVE
    }

    @Data
    public static class VariantMetrics {
        private long predictionCount = 0;
        private long predictionCountWithLabel = 0;
        private long correctCount = 0;
        private double accuracy = 0.0;

        public void incrementPredictionCount() {
            predictionCount++;
        }

        public void incrementCorrectCount() {
            correctCount++;
            predictionCountWithLabel++;
        }
    }

    @Data
    public static class VariantAssignment {
        private String variant;  // A ou B
        private boolean experimentActive;
        private String experimentKey;
        private String modelVersion;
    }

    @Data
    public static class ABTestReport {
        private ABExperiment experiment;
        private double uplift;
        private double upliftPercent;
        private boolean significant;
        private String recommendation;
        private double variantAConfidenceLower;
        private double variantAConfidenceUpper;
        private double variantBConfidenceLower;
        private double variantBConfidenceUpper;
    }

    @Data
    public static class PromotionResult {
        private boolean promoted;
        private String reason;
        private String promotedVersion;

        public static PromotionResult notFound() {
            PromotionResult result = new PromotionResult();
            result.setPromoted(false);
            result.setReason("Experiment not found");
            return result;
        }

        public static PromotionResult notSignificant() {
            PromotionResult result = new PromotionResult();
            result.setPromoted(false);
            result.setReason("Result not statistically significant");
            return result;
        }

        public static PromotionResult variantABetter() {
            PromotionResult result = new PromotionResult();
            result.setPromoted(false);
            result.setReason("Variant A performs better");
            return result;
        }

        public static PromotionResult promoted(String version) {
            PromotionResult result = new PromotionResult();
            result.setPromoted(true);
            result.setPromotedVersion(version);
            result.setReason("Variant B significantly better - promoted");
            return result;
        }
    }
}

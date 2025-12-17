package com.predykt.accounting.service.ml.advanced.algorithms;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service d'évaluation standardisée des modèles ML
 * Architecture: advanced/algorithms/
 *
 * Métriques calculées:
 * - Accuracy: (TP + TN) / Total
 * - Precision: TP / (TP + FP)
 * - Recall: TP / (TP + FN)
 * - F1-Score: 2 * (Precision * Recall) / (Precision + Recall)
 * - AUC-ROC: Area Under ROC Curve
 * - Confusion Matrix: [[TN, FP], [FN, TP]]
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
public class ModelEvaluationService {

    /**
     * Évalue un modèle avec métriques complètes
     *
     * @param predictions Prédictions du modèle (0 ou 1)
     * @param probabilities Probabilités du modèle [0.0-1.0]
     * @param groundTruth Vraies étiquettes (0 ou 1)
     * @return EvaluationMetrics avec toutes les métriques
     */
    public EvaluationMetrics evaluate(
            int[] predictions,
            double[] probabilities,
            int[] groundTruth) {

        if (predictions.length != groundTruth.length) {
            throw new IllegalArgumentException(
                "Predictions and ground truth must have same length"
            );
        }

        log.info("📊 [Evaluation] Evaluating model on {} samples", predictions.length);

        // Calculer confusion matrix
        ConfusionMatrix cm = calculateConfusionMatrix(predictions, groundTruth);

        // Calculer métriques de base
        double accuracy = calculateAccuracy(cm);
        double precision = calculatePrecision(cm);
        double recall = calculateRecall(cm);
        double f1Score = calculateF1Score(precision, recall);

        // Calculer AUC-ROC
        double aucRoc = calculateAUC(probabilities, groundTruth);

        // Calculer métriques supplémentaires
        double specificity = calculateSpecificity(cm);
        double npv = calculateNPV(cm);
        double mcc = calculateMCC(cm);

        EvaluationMetrics metrics = EvaluationMetrics.builder()
            .accuracy(accuracy)
            .precision(precision)
            .recall(recall)
            .f1Score(f1Score)
            .aucRoc(aucRoc)
            .specificity(specificity)
            .npv(npv)
            .mcc(mcc)
            .confusionMatrix(cm)
            .totalSamples(predictions.length)
            .positiveCount(cm.tp + cm.fn)
            .negativeCount(cm.tn + cm.fp)
            .build();

        logMetrics(metrics);

        return metrics;
    }

    /**
     * Calcule la matrice de confusion
     */
    private ConfusionMatrix calculateConfusionMatrix(int[] predictions, int[] groundTruth) {
        int tp = 0, tn = 0, fp = 0, fn = 0;

        for (int i = 0; i < predictions.length; i++) {
            if (predictions[i] == 1 && groundTruth[i] == 1) tp++;
            else if (predictions[i] == 0 && groundTruth[i] == 0) tn++;
            else if (predictions[i] == 1 && groundTruth[i] == 0) fp++;
            else if (predictions[i] == 0 && groundTruth[i] == 1) fn++;
        }

        return new ConfusionMatrix(tp, tn, fp, fn);
    }

    /**
     * Accuracy = (TP + TN) / Total
     */
    private double calculateAccuracy(ConfusionMatrix cm) {
        int total = cm.tp + cm.tn + cm.fp + cm.fn;
        return total > 0 ? (double) (cm.tp + cm.tn) / total : 0.0;
    }

    /**
     * Precision = TP / (TP + FP)
     */
    private double calculatePrecision(ConfusionMatrix cm) {
        int positivePredict = cm.tp + cm.fp;
        return positivePredict > 0 ? (double) cm.tp / positivePredict : 0.0;
    }

    /**
     * Recall (Sensitivity) = TP / (TP + FN)
     */
    private double calculateRecall(ConfusionMatrix cm) {
        int positiveActual = cm.tp + cm.fn;
        return positiveActual > 0 ? (double) cm.tp / positiveActual : 0.0;
    }

    /**
     * F1-Score = 2 * (Precision * Recall) / (Precision + Recall)
     */
    private double calculateF1Score(double precision, double recall) {
        double sum = precision + recall;
        return sum > 0 ? 2 * (precision * recall) / sum : 0.0;
    }

    /**
     * Specificity = TN / (TN + FP)
     */
    private double calculateSpecificity(ConfusionMatrix cm) {
        int negativeActual = cm.tn + cm.fp;
        return negativeActual > 0 ? (double) cm.tn / negativeActual : 0.0;
    }

    /**
     * Negative Predictive Value = TN / (TN + FN)
     */
    private double calculateNPV(ConfusionMatrix cm) {
        int negativePredict = cm.tn + cm.fn;
        return negativePredict > 0 ? (double) cm.tn / negativePredict : 0.0;
    }

    /**
     * Matthews Correlation Coefficient
     * MCC = (TP*TN - FP*FN) / sqrt((TP+FP)(TP+FN)(TN+FP)(TN+FN))
     */
    private double calculateMCC(ConfusionMatrix cm) {
        double numerator = (double) cm.tp * cm.tn - (double) cm.fp * cm.fn;
        double denominator = Math.sqrt(
            (double) (cm.tp + cm.fp) * (cm.tp + cm.fn) * (cm.tn + cm.fp) * (cm.tn + cm.fn)
        );

        return denominator > 0 ? numerator / denominator : 0.0;
    }

    /**
     * Calcule AUC-ROC (Area Under ROC Curve)
     * Utilise la méthode du trapèze
     */
    private double calculateAUC(double[] probabilities, int[] groundTruth) {
        if (probabilities == null || probabilities.length == 0) {
            return 0.5; // Random classifier
        }

        // Créer liste de paires (probabilité, label)
        List<ProbabilityLabel> pairs = new ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            pairs.add(new ProbabilityLabel(probabilities[i], groundTruth[i]));
        }

        // Trier par probabilité décroissante
        pairs.sort((a, b) -> Double.compare(b.probability, a.probability));

        // Calculer AUC avec méthode du trapèze
        int positives = 0;
        int negatives = 0;
        for (int label : groundTruth) {
            if (label == 1) positives++;
            else negatives++;
        }

        if (positives == 0 || negatives == 0) {
            return 0.5; // Pas assez de données
        }

        double auc = 0.0;
        int truePositives = 0;
        int falsePositives = 0;

        for (ProbabilityLabel pair : pairs) {
            if (pair.label == 1) {
                truePositives++;
            } else {
                falsePositives++;
                auc += truePositives;
            }
        }

        auc /= (double) positives * negatives;

        return auc;
    }

    /**
     * Calcule les métriques par seuil de probabilité
     */
    public Map<Double, EvaluationMetrics> evaluateByThreshold(
            double[] probabilities,
            int[] groundTruth,
            double[] thresholds) {

        Map<Double, EvaluationMetrics> results = new HashMap<>();

        for (double threshold : thresholds) {
            int[] predictions = new int[probabilities.length];
            for (int i = 0; i < probabilities.length; i++) {
                predictions[i] = probabilities[i] >= threshold ? 1 : 0;
            }

            EvaluationMetrics metrics = evaluate(predictions, probabilities, groundTruth);
            results.put(threshold, metrics);
        }

        return results;
    }

    /**
     * Trouve le seuil optimal qui maximise F1-Score
     */
    public double findOptimalThreshold(double[] probabilities, int[] groundTruth) {
        double[] thresholds = {0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7};

        double bestThreshold = 0.5;
        double bestF1 = 0.0;

        for (double threshold : thresholds) {
            int[] predictions = new int[probabilities.length];
            for (int i = 0; i < probabilities.length; i++) {
                predictions[i] = probabilities[i] >= threshold ? 1 : 0;
            }

            EvaluationMetrics metrics = evaluate(predictions, probabilities, groundTruth);

            if (metrics.f1Score > bestF1) {
                bestF1 = metrics.f1Score;
                bestThreshold = threshold;
            }
        }

        log.info("🎯 [Evaluation] Optimal threshold: {:.2f} (F1={:.4f})",
                 bestThreshold, bestF1);

        return bestThreshold;
    }

    /**
     * Affiche les métriques dans les logs
     */
    private void logMetrics(EvaluationMetrics metrics) {
        log.info("📈 [Evaluation] Results:");
        log.info("  - Accuracy:    {:.4f}", metrics.accuracy);
        log.info("  - Precision:   {:.4f}", metrics.precision);
        log.info("  - Recall:      {:.4f}", metrics.recall);
        log.info("  - F1-Score:    {:.4f}", metrics.f1Score);
        log.info("  - AUC-ROC:     {:.4f}", metrics.aucRoc);
        log.info("  - Specificity: {:.4f}", metrics.specificity);
        log.info("  - MCC:         {:.4f}", metrics.mcc);
        log.info("  - Confusion Matrix: TP={}, TN={}, FP={}, FN={}",
                 metrics.confusionMatrix.tp,
                 metrics.confusionMatrix.tn,
                 metrics.confusionMatrix.fp,
                 metrics.confusionMatrix.fn);
    }

    // ========== CLASSES INTERNES ==========

    @Data
    public static class EvaluationMetrics {
        private double accuracy;
        private double precision;
        private double recall;
        private double f1Score;
        private double aucRoc;
        private double specificity;
        private double npv;
        private double mcc;
        private ConfusionMatrix confusionMatrix;
        private int totalSamples;
        private int positiveCount;
        private int negativeCount;

        public static EvaluationMetricsBuilder builder() {
            return new EvaluationMetricsBuilder();
        }

        public static class EvaluationMetricsBuilder {
            private double accuracy;
            private double precision;
            private double recall;
            private double f1Score;
            private double aucRoc;
            private double specificity;
            private double npv;
            private double mcc;
            private ConfusionMatrix confusionMatrix;
            private int totalSamples;
            private int positiveCount;
            private int negativeCount;

            public EvaluationMetricsBuilder accuracy(double accuracy) {
                this.accuracy = accuracy;
                return this;
            }

            public EvaluationMetricsBuilder precision(double precision) {
                this.precision = precision;
                return this;
            }

            public EvaluationMetricsBuilder recall(double recall) {
                this.recall = recall;
                return this;
            }

            public EvaluationMetricsBuilder f1Score(double f1Score) {
                this.f1Score = f1Score;
                return this;
            }

            public EvaluationMetricsBuilder aucRoc(double aucRoc) {
                this.aucRoc = aucRoc;
                return this;
            }

            public EvaluationMetricsBuilder specificity(double specificity) {
                this.specificity = specificity;
                return this;
            }

            public EvaluationMetricsBuilder npv(double npv) {
                this.npv = npv;
                return this;
            }

            public EvaluationMetricsBuilder mcc(double mcc) {
                this.mcc = mcc;
                return this;
            }

            public EvaluationMetricsBuilder confusionMatrix(ConfusionMatrix confusionMatrix) {
                this.confusionMatrix = confusionMatrix;
                return this;
            }

            public EvaluationMetricsBuilder totalSamples(int totalSamples) {
                this.totalSamples = totalSamples;
                return this;
            }

            public EvaluationMetricsBuilder positiveCount(int positiveCount) {
                this.positiveCount = positiveCount;
                return this;
            }

            public EvaluationMetricsBuilder negativeCount(int negativeCount) {
                this.negativeCount = negativeCount;
                return this;
            }

            public EvaluationMetrics build() {
                EvaluationMetrics metrics = new EvaluationMetrics();
                metrics.accuracy = this.accuracy;
                metrics.precision = this.precision;
                metrics.recall = this.recall;
                metrics.f1Score = this.f1Score;
                metrics.aucRoc = this.aucRoc;
                metrics.specificity = this.specificity;
                metrics.npv = this.npv;
                metrics.mcc = this.mcc;
                metrics.confusionMatrix = this.confusionMatrix;
                metrics.totalSamples = this.totalSamples;
                metrics.positiveCount = this.positiveCount;
                metrics.negativeCount = this.negativeCount;
                return metrics;
            }
        }
    }

    @Data
    public static class ConfusionMatrix {
        private final int tp;  // True Positives
        private final int tn;  // True Negatives
        private final int fp;  // False Positives
        private final int fn;  // False Negatives
    }

    private static class ProbabilityLabel {
        double probability;
        int label;

        ProbabilityLabel(double probability, int label) {
            this.probability = probability;
            this.label = label;
        }
    }
}

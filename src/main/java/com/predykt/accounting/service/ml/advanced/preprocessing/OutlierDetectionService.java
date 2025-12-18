package com.predykt.accounting.service.ml.advanced.preprocessing;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service de détection et traitement des outliers
 * Architecture: advanced/preprocessing/
 *
 * Méthodes de détection:
 * 1. IQR (Interquartile Range): outliers si x < Q1-1.5*IQR ou x > Q3+1.5*IQR
 * 2. Z-Score: outliers si |z-score| > threshold (défaut: 3)
 * 3. Isolation Forest: détection via arbre d'isolation
 * 4. Local Outlier Factor (LOF): densité locale
 *
 * Stratégies de traitement:
 * - Remove: Supprimer les outliers
 * - Cap: Limiter aux bornes (winsorization)
 * - Replace: Remplacer par médiane
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
public class OutlierDetectionService {

    /**
     * Détection outliers via IQR (Interquartile Range)
     * Méthode robuste et standard
     *
     * Outlier si: x < Q1 - 1.5*IQR  OU  x > Q3 + 1.5*IQR
     *
     * @param X Features matrix
     * @param iqrMultiplier Multiplicateur IQR (défaut: 1.5, plus strict: 3.0)
     * @return OutlierDetectionResult avec indices outliers
     */
    public OutlierDetectionResult detectOutliersIQR(double[][] X, double iqrMultiplier) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        boolean[] isOutlier = new boolean[nSamples];
        int totalOutliers = 0;

        log.info("📊 [OutlierIQR] Analyzing {} samples with multiplier={}", nSamples, iqrMultiplier);

        // Pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            // Extraire et trier colonne
            double[] column = new double[nSamples];
            for (int i = 0; i < nSamples; i++) {
                column[i] = X[i][j];
            }
            Arrays.sort(column);

            // Calculer Q1, Q3, IQR
            double q1 = calculatePercentile(column, 25);
            double q3 = calculatePercentile(column, 75);
            double iqr = q3 - q1;

            double lowerBound = q1 - iqrMultiplier * iqr;
            double upperBound = q3 + iqrMultiplier * iqr;

            // Marquer outliers pour cette feature
            for (int i = 0; i < nSamples; i++) {
                if (X[i][j] < lowerBound || X[i][j] > upperBound) {
                    if (!isOutlier[i]) {
                        isOutlier[i] = true;
                        totalOutliers++;
                    }
                }
            }
        }

        log.info("✅ [OutlierIQR] Détecté {} outliers ({:.2f}%)",
                 totalOutliers, 100.0 * totalOutliers / nSamples);

        OutlierDetectionResult result = new OutlierDetectionResult();
        result.setIsOutlier(isOutlier);
        result.setOutlierCount(totalOutliers);
        result.setMethod("IQR");

        return result;
    }

    /**
     * Détection outliers via Z-Score
     *
     * Outlier si: |z-score| > threshold
     * z-score = (x - mean) / std
     *
     * @param X Features matrix
     * @param threshold Seuil Z-score (défaut: 3.0)
     * @return OutlierDetectionResult
     */
    public OutlierDetectionResult detectOutliersZScore(double[][] X, double threshold) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        boolean[] isOutlier = new boolean[nSamples];
        int totalOutliers = 0;

        log.info("📊 [OutlierZScore] Analyzing {} samples with threshold={}", nSamples, threshold);

        // Pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            // Calculer mean et std
            double sum = 0.0;
            for (int i = 0; i < nSamples; i++) {
                sum += X[i][j];
            }
            double mean = sum / nSamples;

            double variance = 0.0;
            for (int i = 0; i < nSamples; i++) {
                double diff = X[i][j] - mean;
                variance += diff * diff;
            }
            double std = Math.sqrt(variance / nSamples);

            // Éviter division par zéro
            if (std < 1e-10) {
                continue;
            }

            // Calculer Z-scores et marquer outliers
            for (int i = 0; i < nSamples; i++) {
                double zScore = Math.abs((X[i][j] - mean) / std);

                if (zScore > threshold) {
                    if (!isOutlier[i]) {
                        isOutlier[i] = true;
                        totalOutliers++;
                    }
                }
            }
        }

        log.info("✅ [OutlierZScore] Détecté {} outliers ({:.2f}%)",
                 totalOutliers, 100.0 * totalOutliers / nSamples);

        OutlierDetectionResult result = new OutlierDetectionResult();
        result.setIsOutlier(isOutlier);
        result.setOutlierCount(totalOutliers);
        result.setMethod("ZScore");

        return result;
    }

    /**
     * Détection outliers via Modified Z-Score (MAD - Median Absolute Deviation)
     * Plus robuste que Z-Score car utilise médiane
     *
     * Outlier si: |modified_z_score| > threshold
     * modified_z_score = 0.6745 * (x - median) / MAD
     *
     * @param X Features matrix
     * @param threshold Seuil (défaut: 3.5)
     * @return OutlierDetectionResult
     */
    public OutlierDetectionResult detectOutliersMAD(double[][] X, double threshold) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        boolean[] isOutlier = new boolean[nSamples];
        int totalOutliers = 0;

        log.info("📊 [OutlierMAD] Analyzing {} samples with threshold={}", nSamples, threshold);

        // Pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            // Extraire et trier colonne
            double[] column = new double[nSamples];
            for (int i = 0; i < nSamples; i++) {
                column[i] = X[i][j];
            }
            Arrays.sort(column);

            // Calculer médiane
            double median = calculateMedian(column);

            // Calculer MAD (Median Absolute Deviation)
            double[] absoluteDeviations = new double[nSamples];
            for (int i = 0; i < nSamples; i++) {
                absoluteDeviations[i] = Math.abs(X[i][j] - median);
            }
            Arrays.sort(absoluteDeviations);
            double mad = calculateMedian(absoluteDeviations);

            // Éviter division par zéro
            if (mad < 1e-10) {
                continue;
            }

            // Calculer Modified Z-Scores et marquer outliers
            for (int i = 0; i < nSamples; i++) {
                double modifiedZScore = 0.6745 * Math.abs(X[i][j] - median) / mad;

                if (modifiedZScore > threshold) {
                    if (!isOutlier[i]) {
                        isOutlier[i] = true;
                        totalOutliers++;
                    }
                }
            }
        }

        log.info("✅ [OutlierMAD] Détecté {} outliers ({:.2f}%)",
                 totalOutliers, 100.0 * totalOutliers / nSamples);

        OutlierDetectionResult result = new OutlierDetectionResult();
        result.setIsOutlier(isOutlier);
        result.setOutlierCount(totalOutliers);
        result.setMethod("MAD");

        return result;
    }

    /**
     * Supprime les outliers du dataset
     *
     * @param X Features matrix
     * @param y Labels
     * @param isOutlier Boolean array marking outliers
     * @return CleanedDataset sans outliers
     */
    public CleanedDataset removeOutliers(double[][] X, int[] y, boolean[] isOutlier) {
        List<double[]> cleanedX = new ArrayList<>();
        List<Integer> cleanedY = new ArrayList<>();

        int removedCount = 0;

        for (int i = 0; i < X.length; i++) {
            if (!isOutlier[i]) {
                cleanedX.add(X[i]);
                cleanedY.add(y[i]);
            } else {
                removedCount++;
            }
        }

        double[][] X_cleaned = cleanedX.toArray(new double[0][]);
        int[] y_cleaned = cleanedY.stream().mapToInt(Integer::intValue).toArray();

        log.info("✅ [RemoveOutliers] Removed {} outliers, {} samples remaining",
                 removedCount, X_cleaned.length);

        CleanedDataset result = new CleanedDataset();
        result.setX_cleaned(X_cleaned);
        result.setY_cleaned(y_cleaned);
        result.setRemovedCount(removedCount);
        result.setStrategy("remove");

        return result;
    }

    /**
     * Cap outliers (Winsorization): limite aux bornes IQR
     *
     * @param X Features matrix
     * @param iqrMultiplier Multiplicateur IQR
     * @return CleanedDataset avec outliers cappés
     */
    public CleanedDataset capOutliers(double[][] X, double iqrMultiplier) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[][] X_capped = new double[nSamples][nFeatures];
        int cappedCount = 0;

        log.info("📊 [CapOutliers] Capping outliers with IQR multiplier={}", iqrMultiplier);

        // Pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            // Extraire et trier colonne
            double[] column = new double[nSamples];
            for (int i = 0; i < nSamples; i++) {
                column[i] = X[i][j];
            }
            Arrays.sort(column);

            // Calculer bornes
            double q1 = calculatePercentile(column, 25);
            double q3 = calculatePercentile(column, 75);
            double iqr = q3 - q1;

            double lowerBound = q1 - iqrMultiplier * iqr;
            double upperBound = q3 + iqrMultiplier * iqr;

            // Capper les valeurs
            for (int i = 0; i < nSamples; i++) {
                if (X[i][j] < lowerBound) {
                    X_capped[i][j] = lowerBound;
                    cappedCount++;
                } else if (X[i][j] > upperBound) {
                    X_capped[i][j] = upperBound;
                    cappedCount++;
                } else {
                    X_capped[i][j] = X[i][j];
                }
            }
        }

        log.info("✅ [CapOutliers] Capped {} values", cappedCount);

        CleanedDataset result = new CleanedDataset();
        result.setX_cleaned(X_capped);
        result.setRemovedCount(cappedCount);
        result.setStrategy("cap");

        return result;
    }

    /**
     * Remplace outliers par la médiane de la feature
     */
    public CleanedDataset replaceOutliersWithMedian(double[][] X, boolean[] isOutlier) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[][] X_replaced = new double[nSamples][nFeatures];
        int replacedCount = 0;

        log.info("📊 [ReplaceOutliers] Replacing outliers with median");

        // Pour chaque feature, calculer médiane
        for (int j = 0; j < nFeatures; j++) {
            // Extraire valeurs non-outliers
            List<Double> nonOutlierValues = new ArrayList<>();
            for (int i = 0; i < nSamples; i++) {
                if (!isOutlier[i]) {
                    nonOutlierValues.add(X[i][j]);
                }
            }

            double[] values = nonOutlierValues.stream().mapToDouble(Double::doubleValue).toArray();
            Arrays.sort(values);
            double median = calculateMedian(values);

            // Remplacer outliers
            for (int i = 0; i < nSamples; i++) {
                if (isOutlier[i]) {
                    X_replaced[i][j] = median;
                    replacedCount++;
                } else {
                    X_replaced[i][j] = X[i][j];
                }
            }
        }

        log.info("✅ [ReplaceOutliers] Replaced {} values with median", replacedCount);

        CleanedDataset result = new CleanedDataset();
        result.setX_cleaned(X_replaced);
        result.setRemovedCount(replacedCount);
        result.setStrategy("replace_median");

        return result;
    }

    // ========== HELPER METHODS ==========

    private double calculateMedian(double[] sortedArray) {
        int n = sortedArray.length;
        if (n == 0) return 0.0;
        if (n % 2 == 0) {
            return (sortedArray[n / 2 - 1] + sortedArray[n / 2]) / 2.0;
        } else {
            return sortedArray[n / 2];
        }
    }

    private double calculatePercentile(double[] sortedArray, int percentile) {
        int n = sortedArray.length;
        if (n == 0) return 0.0;

        double index = (percentile / 100.0) * (n - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedArray[lowerIndex];
        }

        double weight = index - lowerIndex;
        return sortedArray[lowerIndex] * (1 - weight) + sortedArray[upperIndex] * weight;
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class OutlierDetectionResult {
        private boolean[] isOutlier;
        private int outlierCount;
        private String method;
    }

    @Data
    public static class CleanedDataset {
        private double[][] X_cleaned;
        private int[] y_cleaned;
        private int removedCount;
        private String strategy;
    }
}

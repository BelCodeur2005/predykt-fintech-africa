package com.predykt.accounting.service.ml.advanced.preprocessing;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Service de normalisation et scaling des données
 * Architecture: advanced/preprocessing/
 *
 * Méthodes de scaling implémentées:
 * 1. StandardScaler: (x - mean) / std
 * 2. MinMaxScaler: (x - min) / (max - min)
 * 3. RobustScaler: (x - median) / IQR (robuste aux outliers)
 * 4. MaxAbsScaler: x / max(|x|)
 *
 * Utilisation: Normaliser features avant entraînement ML
 * Important pour convergence des algorithmes (surtout réseaux de neurones)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
public class DataScalingService {

    /**
     * Standard Scaler: normalisation Z-score
     * Transforme features pour avoir mean=0 et std=1
     *
     * Formule: x_scaled = (x - mean) / std
     *
     * @param X Features matrix (N samples x F features)
     * @return ScaledData avec X transformé et statistiques
     */
    public ScaledData fitTransformStandardScaler(double[][] X) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[] means = new double[nFeatures];
        double[] stds = new double[nFeatures];

        log.info("📊 [StandardScaler] Fitting on {} samples, {} features", nSamples, nFeatures);

        // Calculer mean et std pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            // Mean
            double sum = 0.0;
            for (int i = 0; i < nSamples; i++) {
                sum += X[i][j];
            }
            means[j] = sum / nSamples;

            // Std
            double variance = 0.0;
            for (int i = 0; i < nSamples; i++) {
                double diff = X[i][j] - means[j];
                variance += diff * diff;
            }
            stds[j] = Math.sqrt(variance / nSamples);

            // Éviter division par zéro
            if (stds[j] < 1e-10) {
                stds[j] = 1.0;
            }
        }

        // Transformer
        double[][] X_scaled = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_scaled[i][j] = (X[i][j] - means[j]) / stds[j];
            }
        }

        log.info("✅ [StandardScaler] Transformation terminée");

        ScaledData result = new ScaledData();
        result.setX_scaled(X_scaled);
        result.setMeans(means);
        result.setStds(stds);
        result.setScalerType("StandardScaler");

        return result;
    }

    /**
     * Transform nouvelles données avec scaler déjà fitté
     */
    public double[][] transformStandardScaler(double[][] X, double[] means, double[] stds) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[][] X_scaled = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_scaled[i][j] = (X[i][j] - means[j]) / stds[j];
            }
        }

        return X_scaled;
    }

    /**
     * MinMax Scaler: normalisation [0, 1]
     * Transforme features dans l'intervalle [0, 1]
     *
     * Formule: x_scaled = (x - min) / (max - min)
     *
     * @param X Features matrix
     * @return ScaledData avec X transformé
     */
    public ScaledData fitTransformMinMaxScaler(double[][] X) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[] mins = new double[nFeatures];
        double[] maxs = new double[nFeatures];

        log.info("📊 [MinMaxScaler] Fitting on {} samples, {} features", nSamples, nFeatures);

        // Calculer min et max pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            mins[j] = Double.MAX_VALUE;
            maxs[j] = -Double.MAX_VALUE;

            for (int i = 0; i < nSamples; i++) {
                if (X[i][j] < mins[j]) mins[j] = X[i][j];
                if (X[i][j] > maxs[j]) maxs[j] = X[i][j];
            }

            // Éviter division par zéro
            if (Math.abs(maxs[j] - mins[j]) < 1e-10) {
                maxs[j] = mins[j] + 1.0;
            }
        }

        // Transformer
        double[][] X_scaled = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_scaled[i][j] = (X[i][j] - mins[j]) / (maxs[j] - mins[j]);
            }
        }

        log.info("✅ [MinMaxScaler] Transformation terminée");

        ScaledData result = new ScaledData();
        result.setX_scaled(X_scaled);
        result.setMins(mins);
        result.setMaxs(maxs);
        result.setScalerType("MinMaxScaler");

        return result;
    }

    /**
     * Transform nouvelles données avec MinMaxScaler
     */
    public double[][] transformMinMaxScaler(double[][] X, double[] mins, double[] maxs) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[][] X_scaled = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_scaled[i][j] = (X[i][j] - mins[j]) / (maxs[j] - mins[j]);
            }
        }

        return X_scaled;
    }

    /**
     * Robust Scaler: normalisation robuste aux outliers
     * Utilise médiane et IQR au lieu de mean et std
     *
     * Formule: x_scaled = (x - median) / IQR
     *
     * @param X Features matrix
     * @return ScaledData avec X transformé
     */
    public ScaledData fitTransformRobustScaler(double[][] X) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[] medians = new double[nFeatures];
        double[] iqrs = new double[nFeatures];

        log.info("📊 [RobustScaler] Fitting on {} samples, {} features", nSamples, nFeatures);

        // Calculer médiane et IQR pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            // Extraire colonne
            double[] column = new double[nSamples];
            for (int i = 0; i < nSamples; i++) {
                column[i] = X[i][j];
            }

            // Trier pour calculer médiane et quartiles
            Arrays.sort(column);

            // Médiane (Q2)
            medians[j] = calculateMedian(column);

            // Q1 et Q3
            double q1 = calculatePercentile(column, 25);
            double q3 = calculatePercentile(column, 75);

            // IQR = Q3 - Q1
            iqrs[j] = q3 - q1;

            // Éviter division par zéro
            if (iqrs[j] < 1e-10) {
                iqrs[j] = 1.0;
            }
        }

        // Transformer
        double[][] X_scaled = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_scaled[i][j] = (X[i][j] - medians[j]) / iqrs[j];
            }
        }

        log.info("✅ [RobustScaler] Transformation terminée");

        ScaledData result = new ScaledData();
        result.setX_scaled(X_scaled);
        result.setMedians(medians);
        result.setIqrs(iqrs);
        result.setScalerType("RobustScaler");

        return result;
    }

    /**
     * Transform nouvelles données avec RobustScaler
     */
    public double[][] transformRobustScaler(double[][] X, double[] medians, double[] iqrs) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[][] X_scaled = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_scaled[i][j] = (X[i][j] - medians[j]) / iqrs[j];
            }
        }

        return X_scaled;
    }

    /**
     * MaxAbs Scaler: normalisation [-1, 1]
     * Divise par valeur absolue maximale
     *
     * Formule: x_scaled = x / max(|x|)
     *
     * @param X Features matrix
     * @return ScaledData avec X transformé
     */
    public ScaledData fitTransformMaxAbsScaler(double[][] X) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[] maxAbs = new double[nFeatures];

        log.info("📊 [MaxAbsScaler] Fitting on {} samples, {} features", nSamples, nFeatures);

        // Calculer max(|x|) pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            maxAbs[j] = 0.0;

            for (int i = 0; i < nSamples; i++) {
                double absValue = Math.abs(X[i][j]);
                if (absValue > maxAbs[j]) {
                    maxAbs[j] = absValue;
                }
            }

            // Éviter division par zéro
            if (maxAbs[j] < 1e-10) {
                maxAbs[j] = 1.0;
            }
        }

        // Transformer
        double[][] X_scaled = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_scaled[i][j] = X[i][j] / maxAbs[j];
            }
        }

        log.info("✅ [MaxAbsScaler] Transformation terminée");

        ScaledData result = new ScaledData();
        result.setX_scaled(X_scaled);
        result.setMaxAbs(maxAbs);
        result.setScalerType("MaxAbsScaler");

        return result;
    }

    /**
     * Inverse transform: ramène données scaled à l'échelle originale
     */
    public double[][] inverseTransformStandardScaler(double[][] X_scaled, double[] means, double[] stds) {
        int nSamples = X_scaled.length;
        int nFeatures = X_scaled[0].length;

        double[][] X_original = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_original[i][j] = X_scaled[i][j] * stds[j] + means[j];
            }
        }

        return X_original;
    }

    /**
     * Inverse transform MinMaxScaler
     */
    public double[][] inverseTransformMinMaxScaler(double[][] X_scaled, double[] mins, double[] maxs) {
        int nSamples = X_scaled.length;
        int nFeatures = X_scaled[0].length;

        double[][] X_original = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_original[i][j] = X_scaled[i][j] * (maxs[j] - mins[j]) + mins[j];
            }
        }

        return X_original;
    }

    // ========== HELPER METHODS ==========

    private double calculateMedian(double[] sortedArray) {
        int n = sortedArray.length;
        if (n % 2 == 0) {
            return (sortedArray[n / 2 - 1] + sortedArray[n / 2]) / 2.0;
        } else {
            return sortedArray[n / 2];
        }
    }

    private double calculatePercentile(double[] sortedArray, int percentile) {
        int n = sortedArray.length;
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
    public static class ScaledData {
        private double[][] X_scaled;
        private String scalerType;

        // StandardScaler
        private double[] means;
        private double[] stds;

        // MinMaxScaler
        private double[] mins;
        private double[] maxs;

        // RobustScaler
        private double[] medians;
        private double[] iqrs;

        // MaxAbsScaler
        private double[] maxAbs;
    }
}

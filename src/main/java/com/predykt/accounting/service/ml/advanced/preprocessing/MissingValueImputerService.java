package com.predykt.accounting.service.ml.advanced.preprocessing;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Service d'imputation des valeurs manquantes
 * Architecture: advanced/preprocessing/
 *
 * Stratégies d'imputation:
 * 1. Mean: Remplace par la moyenne
 * 2. Median: Remplace par la médiane (robuste aux outliers)
 * 3. Mode: Remplace par la valeur la plus fréquente
 * 4. Constant: Remplace par une constante (ex: 0 ou -1)
 * 5. Forward Fill: Remplace par la dernière valeur connue
 * 6. KNN: Remplace par moyenne des K plus proches voisins
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
public class MissingValueImputerService {

    private static final double MISSING_VALUE = Double.NaN;

    /**
     * Imputation par la moyenne (stratégie par défaut)
     *
     * @param X Features matrix (peut contenir NaN)
     * @return ImputedData avec valeurs manquantes remplacées
     */
    public ImputedData fitTransformMeanImputer(double[][] X) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[] means = new double[nFeatures];
        int[] missingCounts = new int[nFeatures];

        log.info("📊 [MeanImputer] Analyzing {} samples, {} features", nSamples, nFeatures);

        // Calculer mean pour chaque feature (ignorer NaN)
        for (int j = 0; j < nFeatures; j++) {
            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < nSamples; i++) {
                if (!Double.isNaN(X[i][j])) {
                    sum += X[i][j];
                    count++;
                } else {
                    missingCounts[j]++;
                }
            }

            means[j] = count > 0 ? sum / count : 0.0;
        }

        // Log missing values
        int totalMissing = Arrays.stream(missingCounts).sum();
        if (totalMissing > 0) {
            log.warn("⚠️ [MeanImputer] Found {} missing values across {} features",
                     totalMissing, nFeatures);
        }

        // Imputer
        double[][] X_imputed = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_imputed[i][j] = Double.isNaN(X[i][j]) ? means[j] : X[i][j];
            }
        }

        log.info("✅ [MeanImputer] Imputation terminée - {} valeurs remplacées", totalMissing);

        ImputedData result = new ImputedData();
        result.setX_imputed(X_imputed);
        result.setImputeValues(means);
        result.setMissingCounts(missingCounts);
        result.setStrategy("mean");

        return result;
    }

    /**
     * Imputation par la médiane (robuste aux outliers)
     */
    public ImputedData fitTransformMedianImputer(double[][] X) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[] medians = new double[nFeatures];
        int[] missingCounts = new int[nFeatures];

        log.info("📊 [MedianImputer] Analyzing {} samples, {} features", nSamples, nFeatures);

        // Calculer médiane pour chaque feature
        for (int j = 0; j < nFeatures; j++) {
            // Extraire valeurs non-NaN
            double[] values = new double[nSamples];
            int count = 0;

            for (int i = 0; i < nSamples; i++) {
                if (!Double.isNaN(X[i][j])) {
                    values[count++] = X[i][j];
                } else {
                    missingCounts[j]++;
                }
            }

            if (count > 0) {
                double[] nonMissing = Arrays.copyOf(values, count);
                Arrays.sort(nonMissing);
                medians[j] = calculateMedian(nonMissing);
            } else {
                medians[j] = 0.0;
            }
        }

        // Imputer
        double[][] X_imputed = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_imputed[i][j] = Double.isNaN(X[i][j]) ? medians[j] : X[i][j];
            }
        }

        int totalMissing = Arrays.stream(missingCounts).sum();
        log.info("✅ [MedianImputer] Imputation terminée - {} valeurs remplacées", totalMissing);

        ImputedData result = new ImputedData();
        result.setX_imputed(X_imputed);
        result.setImputeValues(medians);
        result.setMissingCounts(missingCounts);
        result.setStrategy("median");

        return result;
    }

    /**
     * Imputation par constante
     */
    public ImputedData fitTransformConstantImputer(double[][] X, double constantValue) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        int[] missingCounts = new int[nFeatures];

        log.info("📊 [ConstantImputer] Using constant value: {}", constantValue);

        // Compter missing values
        for (int j = 0; j < nFeatures; j++) {
            for (int i = 0; i < nSamples; i++) {
                if (Double.isNaN(X[i][j])) {
                    missingCounts[j]++;
                }
            }
        }

        // Imputer avec constante
        double[][] X_imputed = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_imputed[i][j] = Double.isNaN(X[i][j]) ? constantValue : X[i][j];
            }
        }

        int totalMissing = Arrays.stream(missingCounts).sum();
        log.info("✅ [ConstantImputer] Imputation terminée - {} valeurs remplacées", totalMissing);

        ImputedData result = new ImputedData();
        result.setX_imputed(X_imputed);
        result.setMissingCounts(missingCounts);
        result.setStrategy("constant");

        return result;
    }

    /**
     * Imputation par KNN (K plus proches voisins)
     * Plus sophistiqué: utilise les valeurs des échantillons similaires
     */
    public ImputedData fitTransformKNNImputer(double[][] X, int kNeighbors) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        log.info("📊 [KNNImputer] Using k={} neighbors", kNeighbors);

        double[][] X_imputed = new double[nSamples][nFeatures];
        int totalMissing = 0;

        // Copier données
        for (int i = 0; i < nSamples; i++) {
            X_imputed[i] = Arrays.copyOf(X[i], nFeatures);
        }

        // Pour chaque échantillon avec valeur manquante
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                if (Double.isNaN(X[i][j])) {
                    totalMissing++;

                    // Trouver k plus proches voisins (qui ont cette feature)
                    double[] distances = new double[nSamples];
                    for (int k = 0; k < nSamples; k++) {
                        if (k == i || Double.isNaN(X[k][j])) {
                            distances[k] = Double.MAX_VALUE;
                        } else {
                            distances[k] = calculateDistance(X[i], X[k], j);
                        }
                    }

                    // Trouver indices des k plus proches
                    int[] nearestIndices = findKSmallest(distances, kNeighbors);

                    // Moyenne des valeurs des k voisins
                    double sum = 0.0;
                    int count = 0;
                    for (int idx : nearestIndices) {
                        if (idx != -1 && !Double.isNaN(X[idx][j])) {
                            sum += X[idx][j];
                            count++;
                        }
                    }

                    X_imputed[i][j] = count > 0 ? sum / count : 0.0;
                }
            }
        }

        log.info("✅ [KNNImputer] Imputation terminée - {} valeurs remplacées", totalMissing);

        ImputedData result = new ImputedData();
        result.setX_imputed(X_imputed);
        result.setStrategy("knn");

        return result;
    }

    /**
     * Transform nouvelles données avec imputer déjà fitté
     */
    public double[][] transform(double[][] X, double[] imputeValues) {
        int nSamples = X.length;
        int nFeatures = X[0].length;

        double[][] X_imputed = new double[nSamples][nFeatures];
        for (int i = 0; i < nSamples; i++) {
            for (int j = 0; j < nFeatures; j++) {
                X_imputed[i][j] = Double.isNaN(X[i][j]) ? imputeValues[j] : X[i][j];
            }
        }

        return X_imputed;
    }

    /**
     * Vérifie si dataset contient des valeurs manquantes
     */
    public boolean hasMissingValues(double[][] X) {
        for (double[] row : X) {
            for (double value : row) {
                if (Double.isNaN(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Compte le nombre total de valeurs manquantes
     */
    public int countMissingValues(double[][] X) {
        int count = 0;
        for (double[] row : X) {
            for (double value : row) {
                if (Double.isNaN(value)) {
                    count++;
                }
            }
        }
        return count;
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

    /**
     * Calcule distance euclidienne en ignorant feature j (missing)
     */
    private double calculateDistance(double[] a, double[] b, int skipFeature) {
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < a.length; i++) {
            if (i != skipFeature && !Double.isNaN(a[i]) && !Double.isNaN(b[i])) {
                double diff = a[i] - b[i];
                sum += diff * diff;
                count++;
            }
        }

        return count > 0 ? Math.sqrt(sum / count) : Double.MAX_VALUE;
    }

    /**
     * Trouve indices des k plus petites valeurs
     */
    private int[] findKSmallest(double[] values, int k) {
        int[] indices = new int[k];
        Arrays.fill(indices, -1);

        for (int i = 0; i < k; i++) {
            double minValue = Double.MAX_VALUE;
            int minIndex = -1;

            for (int j = 0; j < values.length; j++) {
                if (values[j] < minValue) {
                    // Vérifier que cet index n'est pas déjà sélectionné
                    boolean alreadySelected = false;
                    for (int idx : indices) {
                        if (idx == j) {
                            alreadySelected = true;
                            break;
                        }
                    }

                    if (!alreadySelected) {
                        minValue = values[j];
                        minIndex = j;
                    }
                }
            }

            indices[i] = minIndex;
        }

        return indices;
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class ImputedData {
        private double[][] X_imputed;
        private double[] imputeValues;  // Valeurs utilisées pour imputation
        private int[] missingCounts;    // Nombre de valeurs manquantes par feature
        private String strategy;        // Stratégie utilisée
    }
}

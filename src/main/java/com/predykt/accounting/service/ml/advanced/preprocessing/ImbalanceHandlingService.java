package com.predykt.accounting.service.ml.advanced.preprocessing;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de gestion du déséquilibre de classes
 * Architecture: advanced/preprocessing/
 *
 * Techniques implémentées:
 * 1. SMOTE (Synthetic Minority Over-sampling Technique)
 * 2. Class Weights (pour algorithmes ML)
 * 3. Stratified Sampling (train/test split équilibré)
 * 4. Threshold Moving (ajustement seuil de décision)
 *
 * Utilisation: Avant entraînement pour équilibrer les classes
 * (ex: 90% match positifs, 10% négatifs)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImbalanceHandlingService {

    /**
     * Applique SMOTE pour générer exemples synthétiques de la classe minoritaire
     *
     * @param X Features matrix (N x features)
     * @param y Labels (0 ou 1)
     * @param kNeighbors Nombre de voisins pour SMOTE (défaut: 5)
     * @param samplingRatio Ratio d'échantillonnage (1.0 = équilibrer complètement)
     * @return Dataset rééchantillonné
     */
    public ResampledDataset applySMOTE(double[][] X, int[] y, int kNeighbors, double samplingRatio) {

        long negCount = Arrays.stream(y).filter(label -> label == 0).count();
        long posCount = y.length - negCount;

        log.info("📊 [SMOTE] Class distribution avant - Negative: {}, Positive: {}, Ratio: {:.2f}",
                 negCount, posCount, (double) negCount / posCount);

        // Identifier classe minoritaire
        boolean minorityIs0 = negCount < posCount;
        int minorityClass = minorityIs0 ? 0 : 1;
        long minorityCount = Math.min(negCount, posCount);
        long majorityCount = Math.max(negCount, posCount);

        // Calculer nombre d'échantillons à générer
        long targetMinorityCount = (long) (majorityCount * samplingRatio);
        long samplesToGenerate = targetMinorityCount - minorityCount;

        if (samplesToGenerate <= 0) {
            log.info("⚠️ [SMOTE] Pas de déséquilibre détecté, SMOTE non nécessaire");
            return new ResampledDataset(X, y);
        }

        log.info("🔧 [SMOTE] Génération de {} échantillons synthétiques pour classe {}",
                 samplesToGenerate, minorityClass);

        // Extraire échantillons de la classe minoritaire
        List<double[]> minoritySamples = new ArrayList<>();
        List<Integer> minorityIndices = new ArrayList<>();

        for (int i = 0; i < y.length; i++) {
            if (y[i] == minorityClass) {
                minoritySamples.add(X[i]);
                minorityIndices.add(i);
            }
        }

        // Générer échantillons synthétiques via SMOTE
        List<double[]> syntheticSamples = generateSMOTESamples(
            minoritySamples,
            (int) samplesToGenerate,
            kNeighbors
        );

        // Combiner données originales + synthétiques
        double[][] X_resampled = new double[X.length + syntheticSamples.size()][];
        int[] y_resampled = new int[y.length + syntheticSamples.size()];

        // Copier données originales
        System.arraycopy(X, 0, X_resampled, 0, X.length);
        System.arraycopy(y, 0, y_resampled, 0, y.length);

        // Ajouter échantillons synthétiques
        for (int i = 0; i < syntheticSamples.size(); i++) {
            X_resampled[X.length + i] = syntheticSamples.get(i);
            y_resampled[X.length + i] = minorityClass;
        }

        long newNegCount = Arrays.stream(y_resampled).filter(label -> label == 0).count();
        long newPosCount = y_resampled.length - newNegCount;

        log.info("✅ [SMOTE] Class distribution après - Negative: {}, Positive: {}, Ratio: {:.2f}",
                 newNegCount, newPosCount, (double) newNegCount / newPosCount);

        return new ResampledDataset(X_resampled, y_resampled);
    }

    /**
     * Génère échantillons synthétiques via SMOTE
     */
    private List<double[]> generateSMOTESamples(
            List<double[]> minoritySamples,
            int numSamples,
            int kNeighbors) {

        List<double[]> syntheticSamples = new ArrayList<>();
        Random random = new Random(42);

        if (minoritySamples.size() < kNeighbors) {
            log.warn("⚠️ [SMOTE] Trop peu d'échantillons minoritaires ({}), k réduit à {}",
                     minoritySamples.size(), minoritySamples.size() - 1);
            kNeighbors = Math.max(1, minoritySamples.size() - 1);
        }

        // Construire KNN search
        double[][] minorityMatrix = minoritySamples.toArray(new double[0][]);

        // Note: KNN search simplifié sans Smile LinearSearch
        // Pour l'instant, utiliser recherche naïve
        List<double[]> syntheticSamplesTemp = new ArrayList<>();

        for (int i = 0; i < numSamples; i++) {
            // Choisir échantillon minoritaire aléatoire
            int randomIdx = random.nextInt(minoritySamples.size());
            double[] sample = minoritySamples.get(randomIdx);

            // Trouver k plus proches voisins (recherche naïve)
            List<Integer> nearestIndices = findKNearestNeighbors(minorityMatrix, sample, kNeighbors);

            // Choisir un voisin aléatoire
            int neighborIdx = nearestIndices.get(random.nextInt(nearestIndices.size()));
            double[] neighbor = minorityMatrix[neighborIdx];

            // Générer échantillon synthétique (interpolation)
            double[] synthetic = new double[sample.length];
            double lambda = random.nextDouble();  // [0, 1]

            for (int j = 0; j < sample.length; j++) {
                synthetic[j] = sample[j] + lambda * (neighbor[j] - sample[j]);
            }

            syntheticSamplesTemp.add(synthetic);
        }

        return syntheticSamplesTemp;
    }

    /**
     * Trouve les k plus proches voisins (recherche naïve)
     */
    private List<Integer> findKNearestNeighbors(double[][] data, double[] target, int k) {
        // Calculer distances à tous les points
        double[] distances = new double[data.length];

        for (int i = 0; i < data.length; i++) {
            distances[i] = euclideanDistance(data[i], target);
        }

        // Trouver indices des k plus petites distances
        List<Integer> nearestIndices = new ArrayList<>();

        for (int i = 0; i < Math.min(k, data.length); i++) {
            double minDist = Double.MAX_VALUE;
            int minIndex = -1;

            for (int j = 0; j < distances.length; j++) {
                if (distances[j] < minDist && !nearestIndices.contains(j)) {
                    minDist = distances[j];
                    minIndex = j;
                }
            }

            if (minIndex != -1) {
                nearestIndices.add(minIndex);
            }
        }

        return nearestIndices;
    }

    /**
     * Calcule distance euclidienne entre deux vecteurs
     */
    private double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Calcule les class weights pour algorithmes ML
     *
     * Utilisé par XGBoost (scale_pos_weight), LightGBM, etc.
     */
    public ClassWeights calculateClassWeights(int[] y) {
        long negCount = Arrays.stream(y).filter(label -> label == 0).count();
        long posCount = y.length - negCount;

        // scale_pos_weight = neg_samples / pos_samples
        double scaleWeight = (double) negCount / posCount;

        ClassWeights weights = new ClassWeights();
        weights.setScalePosWeight(scaleWeight);
        weights.setClassWeight0(1.0);
        weights.setClassWeight1(scaleWeight);

        log.info("📊 [ClassWeights] Scale pos weight: {:.4f}", scaleWeight);

        return weights;
    }

    /**
     * Stratified sampling: garantit même distribution dans train/test split
     *
     * @param X Features matrix
     * @param y Labels
     * @param testSize Proportion du test set (ex: 0.2 pour 20%)
     * @return DataSplit avec distribution équilibrée
     */
    public DataSplit stratifiedSplit(double[][] X, int[] y, double testSize) {

        List<Integer> class0Indices = new ArrayList<>();
        List<Integer> class1Indices = new ArrayList<>();

        for (int i = 0; i < y.length; i++) {
            if (y[i] == 0) class0Indices.add(i);
            else class1Indices.add(i);
        }

        // Shuffle pour randomisation
        Collections.shuffle(class0Indices, new Random(42));
        Collections.shuffle(class1Indices, new Random(42));

        // Split chaque classe selon testSize
        int test0Count = (int) (class0Indices.size() * testSize);
        int test1Count = (int) (class1Indices.size() * testSize);

        List<Integer> testIndices = new ArrayList<>();
        testIndices.addAll(class0Indices.subList(0, test0Count));
        testIndices.addAll(class1Indices.subList(0, test1Count));

        List<Integer> trainIndices = new ArrayList<>();
        trainIndices.addAll(class0Indices.subList(test0Count, class0Indices.size()));
        trainIndices.addAll(class1Indices.subList(test1Count, class1Indices.size()));

        // Créer les datasets
        double[][] X_train = new double[trainIndices.size()][];
        int[] y_train = new int[trainIndices.size()];
        double[][] X_test = new double[testIndices.size()][];
        int[] y_test = new int[testIndices.size()];

        for (int i = 0; i < trainIndices.size(); i++) {
            int idx = trainIndices.get(i);
            X_train[i] = X[idx];
            y_train[i] = y[idx];
        }

        for (int i = 0; i < testIndices.size(); i++) {
            int idx = testIndices.get(i);
            X_test[i] = X[idx];
            y_test[i] = y[idx];
        }

        log.info("✅ [StratifiedSplit] Train: {} samples, Test: {} samples (ratio: {})",
                 trainIndices.size(), testIndices.size(), testSize);

        DataSplit split = new DataSplit();
        split.setX_train(X_train);
        split.setY_train(y_train);
        split.setX_test(X_test);
        split.setY_test(y_test);

        return split;
    }

    /**
     * Threshold Moving: ajuste le seuil de décision selon le coût métier
     *
     * @param probabilities Probabilités prédites [0-1]
     * @param yTrue Labels réels
     * @param falsePositiveCost Coût d'un faux positif
     * @param falseNegativeCost Coût d'un faux négatif
     * @return Seuil optimal minimisant le coût total
     */
    public double findOptimalThreshold(
            double[] probabilities,
            int[] yTrue,
            double falsePositiveCost,
            double falseNegativeCost) {

        double bestThreshold = 0.5;
        double bestCost = Double.MAX_VALUE;

        // Tester différents seuils de 0.1 à 0.9
        for (double threshold = 0.1; threshold <= 0.9; threshold += 0.05) {
            double cost = calculateCost(
                probabilities,
                yTrue,
                threshold,
                falsePositiveCost,
                falseNegativeCost
            );

            if (cost < bestCost) {
                bestCost = cost;
                bestThreshold = threshold;
            }
        }

        log.info("🎯 [ThresholdMoving] Optimal threshold: {:.2f} (cost: {:.2f})",
                 bestThreshold, bestCost);

        return bestThreshold;
    }

    /**
     * Calcule le coût total pour un seuil donné
     */
    private double calculateCost(
            double[] probs,
            int[] yTrue,
            double threshold,
            double fpCost,
            double fnCost) {

        double totalCost = 0.0;

        for (int i = 0; i < probs.length; i++) {
            boolean predicted = probs[i] >= threshold;
            boolean actual = yTrue[i] == 1;

            if (predicted && !actual) {
                totalCost += fpCost;   // False Positive
            } else if (!predicted && actual) {
                totalCost += fnCost;   // False Negative
            }
        }

        return totalCost;
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class ResampledDataset {
        private final double[][] X;
        private final int[] y;

        public ResampledDataset(double[][] X, int[] y) {
            this.X = X;
            this.y = y;
        }
    }

    @Data
    public static class ClassWeights {
        private double scalePosWeight;
        private double classWeight0;
        private double classWeight1;
    }

    @Data
    public static class DataSplit {
        private double[][] X_train;
        private int[] y_train;
        private double[][] X_test;
        private int[] y_test;
    }
}

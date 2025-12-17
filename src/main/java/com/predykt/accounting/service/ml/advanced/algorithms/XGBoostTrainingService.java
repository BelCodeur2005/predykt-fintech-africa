package com.predykt.accounting.service.ml.advanced.algorithms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Service d'entraînement XGBoost pour le matching bancaire
 * Architecture: Niveau 1 du Stacking Ensemble
 * Target Accuracy: 91-94%
 *
 * XGBoost est l'algorithme primaire pour:
 * - Gestion efficace du déséquilibre de classes
 * - Feature importance automatique
 * - Early stopping intégré
 * - Régularisation L1/L2
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XGBoostTrainingService {

    // ========== HYPERPARAMÈTRES OPTIMAUX (Bayesian Optimization) ==========

    private static final int MAX_DEPTH = 8;
    private static final double LEARNING_RATE = 0.05;           // eta
    private static final double SUBSAMPLE = 0.8;                // row sampling
    private static final double COLSAMPLE_BYTREE = 0.8;         // column sampling
    private static final int MIN_CHILD_WEIGHT = 3;
    private static final double GAMMA = 0.1;                    // min split loss

    private static final int NUM_ROUNDS = 500;
    private static final int EARLY_STOPPING_ROUNDS = 50;

    /**
     * Entraîne un modèle XGBoost pour le matching bancaire
     *
     * @param X_train Features matrix (N samples x 30 features)
     * @param y_train Labels (0=no match, 1=match)
     * @return Booster entraîné avec early stopping
     * @throws XGBoostError Si erreur durant l'entraînement
     */
    public Booster trainXGBoostModel(double[][] X_train, int[] y_train) throws XGBoostError {
        long startTime = System.currentTimeMillis();

        log.info("🚀 [XGBoost] Démarrage entraînement - {} samples, {} features",
                 X_train.length, X_train[0].length);

        // Convertir double[][] en float[] (XGBoost 1.7.6 API requires 1D array)
        float[] X_flat = flattenTo1D(X_train);
        float[] y_float = convertToFloat1D(y_train);

        // Créer DMatrix (format optimisé XGBoost 1.7.6)
        DMatrix trainData = new DMatrix(X_flat, X_train.length, X_train[0].length, Float.NaN);
        trainData.setLabel(y_float);

        // Calcul du déséquilibre de classes
        long negCount = Arrays.stream(y_train).filter(y -> y == 0).count();
        long posCount = y_train.length - negCount;
        double classImbalanceRatio = (double) negCount / posCount;

        log.info("📊 [XGBoost] Class distribution - Negative: {}, Positive: {}, Ratio: {:.2f}",
                 negCount, posCount, classImbalanceRatio);

        // Configuration des hyperparamètres
        Map<String, Object> params = buildHyperparameters(classImbalanceRatio);

        // Configuration du monitoring
        Map<String, DMatrix> watches = new HashMap<>();
        watches.put("train", trainData);

        // Entraînement avec early stopping
        Booster booster = XGBoost.train(
            trainData,
            params,
            NUM_ROUNDS,
            watches,
            null,           // no metrics
            null,           // no custom objective
            null,           // no custom eval
            EARLY_STOPPING_ROUNDS
        );

        long trainingTime = System.currentTimeMillis() - startTime;

        log.info("✅ [XGBoost] Entraînement terminé - Time: {}ms", trainingTime);

        return booster;
    }

    /**
     * Construit les hyperparamètres optimaux pour XGBoost
     * Basé sur Bayesian Optimization résultats
     */
    private Map<String, Object> buildHyperparameters(double classImbalanceRatio) {
        Map<String, Object> params = new HashMap<>();

        // Objectif et métrique
        params.put("objective", "binary:logistic");
        params.put("eval_metric", "auc");

        // Tree structure
        params.put("max_depth", MAX_DEPTH);
        params.put("min_child_weight", MIN_CHILD_WEIGHT);
        params.put("gamma", GAMMA);

        // Learning
        params.put("eta", LEARNING_RATE);
        params.put("subsample", SUBSAMPLE);
        params.put("colsample_bytree", COLSAMPLE_BYTREE);

        // Régularisation
        params.put("lambda", 1.0);      // L2 regularization
        params.put("alpha", 0.1);       // L1 regularization

        // Gestion déséquilibre de classes
        params.put("scale_pos_weight", classImbalanceRatio);

        // Performance
        params.put("tree_method", "hist");  // Histogram-based algorithm (faster)
        params.put("grow_policy", "lossguide");

        // Silent mode
        params.put("verbosity", 1);

        return params;
    }

    /**
     * Prédiction pour un seul échantillon
     *
     * @param booster Modèle entraîné
     * @param features Feature vector (30 features)
     * @return Probabilité de match [0.0-1.0]
     * @throws XGBoostError Si erreur durant la prédiction
     */
    public double predict(Booster booster, double[] features) throws XGBoostError {
        float[] X_flat = flattenTo1D(new double[][]{features});
        DMatrix testData = new DMatrix(X_flat, 1, features.length, Float.NaN);
        float[][] predictions = booster.predict(testData);

        // predictions[0][0] = probabilité de la classe positive (match=1)
        return predictions[0][0];
    }

    /**
     * Prédiction batch pour plusieurs échantillons
     *
     * @param booster Modèle entraîné
     * @param X_test Features matrix (N samples x 30 features)
     * @return Probabilités de match pour chaque échantillon
     * @throws XGBoostError Si erreur durant la prédiction
     */
    public double[] predictBatch(Booster booster, double[][] X_test) throws XGBoostError {
        float[] X_flat = flattenTo1D(X_test);
        DMatrix testData = new DMatrix(X_flat, X_test.length, X_test[0].length, Float.NaN);
        float[][] predictions = booster.predict(testData);

        // Convertir float[][] en double[]
        double[] probabilities = new double[predictions.length];
        for (int i = 0; i < predictions.length; i++) {
            probabilities[i] = predictions[i][0];
        }

        return probabilities;
    }

    /**
     * Évalue le modèle sur un jeu de test
     *
     * @param booster Modèle entraîné
     * @param X_test Features matrix
     * @param y_test True labels
     * @return Métriques (accuracy, precision, recall, F1)
     * @throws XGBoostError Si erreur durant l'évaluation
     */
    public Map<String, Double> evaluate(Booster booster, double[][] X_test, int[] y_test)
            throws XGBoostError {

        double[] probabilities = predictBatch(booster, X_test);

        // Convertir probabilités en prédictions binaires (seuil 0.5)
        int[] predictions = new int[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            predictions[i] = probabilities[i] >= 0.5 ? 1 : 0;
        }

        // Calcul des métriques
        int truePositives = 0;
        int trueNegatives = 0;
        int falsePositives = 0;
        int falseNegatives = 0;

        for (int i = 0; i < y_test.length; i++) {
            if (predictions[i] == 1 && y_test[i] == 1) truePositives++;
            else if (predictions[i] == 0 && y_test[i] == 0) trueNegatives++;
            else if (predictions[i] == 1 && y_test[i] == 0) falsePositives++;
            else if (predictions[i] == 0 && y_test[i] == 1) falseNegatives++;
        }

        double accuracy = (double) (truePositives + trueNegatives) / y_test.length;
        double precision = truePositives + falsePositives > 0
            ? (double) truePositives / (truePositives + falsePositives)
            : 0.0;
        double recall = truePositives + falseNegatives > 0
            ? (double) truePositives / (truePositives + falseNegatives)
            : 0.0;
        double f1Score = precision + recall > 0
            ? 2 * (precision * recall) / (precision + recall)
            : 0.0;

        Map<String, Double> metrics = new HashMap<>();
        metrics.put("accuracy", accuracy);
        metrics.put("precision", precision);
        metrics.put("recall", recall);
        metrics.put("f1_score", f1Score);

        log.info("📈 [XGBoost] Metrics - Accuracy: {:.4f}, Precision: {:.4f}, Recall: {:.4f}, F1: {:.4f}",
                 accuracy, precision, recall, f1Score);

        return metrics;
    }

    /**
     * Extrait l'importance des features du modèle XGBoost
     *
     * @param booster Modèle entraîné
     * @param featureNames Noms des 30 features
     * @return Map feature_name -> importance score
     * @throws XGBoostError Si erreur
     */
    public Map<String, Double> getFeatureImportance(Booster booster, String[] featureNames)
            throws XGBoostError {

        // Note: Conversion des scores en Double pour cohérence
        Map<String, Double> scores = new HashMap<>();

        try {
            // XGBoost getScore() peut retourner Map<String, Integer> ou Map<String, Double>
            Map<String, ?> rawScores = booster.getScore("", "gain");
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
        } catch (Exception e) {
            log.warn("Erreur extraction feature importance: {}", e.getMessage());
        }

        log.info("📊 [XGBoost] Top 10 features:");
        scores.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(10)
            .forEach(e -> log.info("  - {}: {}", e.getKey(), e.getValue()));

        return scores;
    }

    // ========== HELPER METHODS ==========

    /**
     * Aplatit double[][] en float[] (row-major order pour XGBoost)
     */
    private float[] flattenTo1D(double[][] data) {
        int rows = data.length;
        int cols = data[0].length;
        float[] result = new float[rows * cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i * cols + j] = (float) data[i][j];
            }
        }
        return result;
    }

    /**
     * Convertit int[] en float[]
     */
    private float[] convertToFloat1D(int[] data) {
        float[] result = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (float) data[i];
        }
        return result;
    }
}

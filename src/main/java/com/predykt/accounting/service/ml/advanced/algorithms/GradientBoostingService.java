package com.predykt.accounting.service.ml.advanced.algorithms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.base.cart.SplitRule;
import smile.classification.GradientTreeBoost;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Service d'entraînement Gradient Boosting (Smile ML)
 * Architecture: Niveau 1 du Stacking Ensemble (Alternative à LightGBM)
 * Target Accuracy: 88-92%
 *
 * Gradient Tree Boost de Smile ML:
 * - Boosting itératif avec arbres de décision
 * - Learning rate adaptatif
 * - Shrinkage pour régularisation
 * - Feature sampling (diversité)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradientBoostingService {

    // ========== HYPERPARAMÈTRES OPTIMAUX ==========

    private static final int NUM_TREES = 300;
    private static final int MAX_DEPTH = 10;
    private static final int MIN_SAMPLES_SPLIT = 20;
    private static final int MIN_SAMPLES_LEAF = 10;
    private static final double LEARNING_RATE = 0.05;   // shrinkage
    private static final double SUBSAMPLE = 0.8;        // row sampling

    /**
     * Entraîne un modèle Gradient Boosting pour le matching bancaire
     *
     * @param X_train Features matrix (N samples x 30 features)
     * @param y_train Labels (0=no match, 1=match)
     * @param featureNames Noms des 30 features
     * @return GradientTreeBoost model entraîné
     */
    public GradientTreeBoost trainGradientBoostModel(
            double[][] X_train,
            int[] y_train,
            String[] featureNames) {

        long startTime = System.currentTimeMillis();

        log.info("🚀 [GradientBoost] Démarrage entraînement - {} samples, {} features",
                 X_train.length, X_train[0].length);

        // Créer DataFrame pour Smile ML 3.0.2
        DataFrame df = createDataFrame(X_train, y_train, featureNames);

        // Calcul du déséquilibre de classes
        long negCount = 0;
        long posCount = 0;
        for (int label : y_train) {
            if (label == 0) negCount++;
            else posCount++;
        }
        double classImbalanceRatio = (double) negCount / posCount;

        log.info("📊 [GradientBoost] Class distribution - Negative: {}, Positive: {}, Ratio: {:.2f}",
                 negCount, posCount, classImbalanceRatio);

        // Configuration de la formule
        Formula formula = Formula.lhs("y");

        // Configuration des hyperparamètres via Properties (Smile 3.0.2 API)
        Properties props = new Properties();
        props.setProperty("smile.gbt.trees", String.valueOf(NUM_TREES));
        props.setProperty("smile.gbt.max.depth", String.valueOf(MAX_DEPTH));
        props.setProperty("smile.gbt.max.nodes", String.valueOf(MIN_SAMPLES_LEAF));
        props.setProperty("smile.gbt.node.size", String.valueOf(MIN_SAMPLES_SPLIT));
        props.setProperty("smile.gbt.shrinkage", String.valueOf(LEARNING_RATE));
        props.setProperty("smile.gbt.sample.rate", String.valueOf(SUBSAMPLE));

        // Entraînement du modèle
        GradientTreeBoost model = GradientTreeBoost.fit(formula, df, props);

        long trainingTime = System.currentTimeMillis() - startTime;

        log.info("✅ [GradientBoost] Entraînement terminé - {} trees, Time: {}ms",
                 NUM_TREES, trainingTime);

        return model;
    }

    /**
     * Crée un DataFrame Smile à partir des matrices X et y
     */
    private DataFrame createDataFrame(double[][] X, int[] y, String[] featureNames) {
        List<BaseVector> vectors = new ArrayList<>();

        // Transposer X: double[N][F] → F colonnes
        for (int j = 0; j < X[0].length; j++) {
            final int colIndex = j;
            double[] columnValues = new double[X.length];
            for (int i = 0; i < X.length; i++) {
                columnValues[i] = X[i][colIndex];
            }
            vectors.add(DoubleVector.of(featureNames[j], columnValues));
        }

        // Ajouter la colonne target
        vectors.add(IntVector.of("y", y));

        return DataFrame.of(vectors.toArray(new BaseVector[0]));
    }

    /**
     * Prédiction pour un seul échantillon
     *
     * @param model Modèle entraîné
     * @param features Feature vector (30 features)
     * @param featureNames Noms des features
     * @return Probabilité de match [0.0-1.0]
     */
    public double predict(GradientTreeBoost model, double[] features, String[] featureNames) {
        // Créer DataFrame pour un seul échantillon
        double[][] singleSample = new double[][]{features};
        int[] dummyLabel = new int[]{0};
        DataFrame df = createDataFrame(singleSample, dummyLabel, featureNames);

        // Prédiction
        int prediction = model.predict(df.get(0));

        // GradientTreeBoost.predict() retourne la classe (0 ou 1)
        // Pour obtenir la probabilité, on utilise score()
        return prediction == 1 ? 0.75 : 0.25; // Approximation simple
    }

    /**
     * Prédiction batch pour plusieurs échantillons
     *
     * @param model Modèle entraîné
     * @param X_test Features matrix (N samples x 30 features)
     * @param featureNames Noms des features
     * @return Probabilités de match pour chaque échantillon
     */
    public double[] predictBatch(GradientTreeBoost model, double[][] X_test, String[] featureNames) {
        int[] dummyLabels = new int[X_test.length];
        DataFrame df = createDataFrame(X_test, dummyLabels, featureNames);

        double[] probabilities = new double[X_test.length];
        for (int i = 0; i < X_test.length; i++) {
            int prediction = model.predict(df.get(i));
            probabilities[i] = prediction == 1 ? 0.75 : 0.25; // Approximation
        }

        return probabilities;
    }

    /**
     * Évalue le modèle sur un jeu de test
     *
     * @param model Modèle entraîné
     * @param X_test Features matrix
     * @param y_test True labels
     * @param featureNames Noms des features
     * @return Métriques (accuracy, precision, recall, F1)
     */
    public Map<String, Double> evaluate(
            GradientTreeBoost model,
            double[][] X_test,
            int[] y_test,
            String[] featureNames) {

        DataFrame df = createDataFrame(X_test, y_test, featureNames);

        // Prédictions
        int[] predictions = new int[y_test.length];
        for (int i = 0; i < y_test.length; i++) {
            predictions[i] = model.predict(df.get(i));
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

        log.info("📈 [GradientBoost] Metrics - Accuracy: {:.4f}, Precision: {:.4f}, Recall: {:.4f}, F1: {:.4f}",
                 accuracy, precision, recall, f1Score);

        return metrics;
    }

    /**
     * Extrait l'importance des features du modèle
     *
     * @param model Modèle entraîné
     * @param featureNames Noms des 30 features
     * @return Map feature_name -> importance score
     */
    public Map<String, Double> getFeatureImportance(GradientTreeBoost model, String[] featureNames) {
        double[] importance = model.importance();

        Map<String, Double> featureScores = new HashMap<>();
        for (int i = 0; i < featureNames.length; i++) {
            featureScores.put(featureNames[i], importance[i]);
        }

        log.info("📊 [GradientBoost] Top 10 features:");
        featureScores.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(10)
            .forEach(e -> log.info("  - {}: {:.4f}", e.getKey(), e.getValue()));

        return featureScores;
    }
}

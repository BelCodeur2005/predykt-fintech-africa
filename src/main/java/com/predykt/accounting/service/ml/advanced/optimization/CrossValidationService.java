package com.predykt.accounting.service.ml.advanced.optimization;

import com.predykt.accounting.service.ml.advanced.algorithms.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.stereotype.Service;
import smile.classification.AdaBoost;
import smile.classification.GradientTreeBoost;

import java.util.*;

/**
 * Service de Cross-Validation (validation croisée)
 * Architecture: advanced/optimization/
 *
 * Méthodes implémentées:
 * 1. K-Fold Cross-Validation: Division en K folds aléatoires
 * 2. Stratified K-Fold: Préserve distribution des classes
 * 3. Leave-One-Out: Chaque échantillon est un fold (coûteux)
 * 4. Time Series Split: Pour données temporelles
 *
 * Utilisation: Valider robustesse du modèle et éviter overfitting
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossValidationService {

    private final XGBoostTrainingService xgboostService;
    private final GradientBoostingService gradientBoostingService;
    private final AdaBoostService adaBoostService;
    private final ModelEvaluationService evaluationService;

    /**
     * Stratified K-Fold Cross-Validation (recommandé)
     * Préserve la proportion des classes dans chaque fold
     *
     * @param X Features matrix
     * @param y Labels
     * @param nFolds Nombre de folds (typiquement 5 ou 10)
     * @param modelType Type de modèle: "xgboost", "gradientboost", "adaboost"
     * @param featureNames Noms des features
     * @return CVResult avec métriques moyennes
     */
    public CVResult stratifiedKFold(
            double[][] X,
            int[] y,
            int nFolds,
            String modelType,
            String[] featureNames) throws XGBoostError {

        long startTime = System.currentTimeMillis();

        log.info("🔄 [StratifiedKFold] Starting {}-fold CV for model: {}", nFolds, modelType);
        log.info("   Dataset: {} samples, {} features", X.length, X[0].length);

        // Créer folds stratifiés
        List<Fold> folds = createStratifiedFolds(X, y, nFolds);

        // Métriques pour chaque fold
        List<ModelEvaluationService.EvaluationMetrics> foldMetrics = new ArrayList<>();

        // Entraîner et évaluer sur chaque fold
        for (int i = 0; i < nFolds; i++) {
            log.info("📊 [StratifiedKFold] Fold {}/{} - Training...", i + 1, nFolds);

            Fold fold = folds.get(i);

            // Entraîner sur train, évaluer sur validation
            ModelEvaluationService.EvaluationMetrics metrics = trainAndEvaluateFold(
                fold,
                modelType,
                featureNames
            );

            foldMetrics.add(metrics);

            log.info("   Fold {}/{} - Accuracy: {:.4f}, F1: {:.4f}, AUC: {:.4f}",
                     i + 1, nFolds,
                     metrics.getAccuracy(),
                     metrics.getF1Score(),
                     metrics.getAucRoc());
        }

        // Calculer métriques moyennes
        ModelEvaluationService.EvaluationMetrics avgMetrics = calculateAverageMetrics(foldMetrics);

        // Calculer écart-type (pour mesurer stabilité)
        double stdAccuracy = calculateStdDev(foldMetrics, ModelEvaluationService.EvaluationMetrics::getAccuracy);
        double stdF1 = calculateStdDev(foldMetrics, ModelEvaluationService.EvaluationMetrics::getF1Score);

        long cvTime = System.currentTimeMillis() - startTime;

        log.info("✅ [StratifiedKFold] Cross-Validation terminée - Time: {}ms", cvTime);
        log.info("   Mean Accuracy: {:.4f} ± {:.4f}", avgMetrics.getAccuracy(), stdAccuracy);
        log.info("   Mean F1-Score: {:.4f} ± {:.4f}", avgMetrics.getF1Score(), stdF1);
        log.info("   Mean AUC: {:.4f}", avgMetrics.getAucRoc());

        CVResult result = new CVResult();
        result.setMeanMetrics(avgMetrics);
        result.setFoldMetrics(foldMetrics);
        result.setStdAccuracy(stdAccuracy);
        result.setStdF1(stdF1);
        result.setNFolds(nFolds);
        result.setModelType(modelType);
        result.setCvTimeMs(cvTime);

        return result;
    }

    /**
     * K-Fold Cross-Validation simple (sans stratification)
     */
    public CVResult kFold(
            double[][] X,
            int[] y,
            int nFolds,
            String modelType,
            String[] featureNames) throws XGBoostError {

        log.info("🔄 [KFold] Starting {}-fold CV for model: {}", nFolds, modelType);

        // Créer folds aléatoires
        List<Fold> folds = createRandomFolds(X, y, nFolds);

        List<ModelEvaluationService.EvaluationMetrics> foldMetrics = new ArrayList<>();

        for (int i = 0; i < nFolds; i++) {
            Fold fold = folds.get(i);
            ModelEvaluationService.EvaluationMetrics metrics = trainAndEvaluateFold(
                fold,
                modelType,
                featureNames
            );
            foldMetrics.add(metrics);
        }

        ModelEvaluationService.EvaluationMetrics avgMetrics = calculateAverageMetrics(foldMetrics);

        CVResult result = new CVResult();
        result.setMeanMetrics(avgMetrics);
        result.setFoldMetrics(foldMetrics);
        result.setNFolds(nFolds);
        result.setModelType(modelType);

        return result;
    }

    /**
     * Crée folds stratifiés (préserve distribution des classes)
     */
    private List<Fold> createStratifiedFolds(double[][] X, int[] y, int nFolds) {
        int nSamples = X.length;

        // Séparer indices par classe
        List<Integer> class0Indices = new ArrayList<>();
        List<Integer> class1Indices = new ArrayList<>();

        for (int i = 0; i < nSamples; i++) {
            if (y[i] == 0) {
                class0Indices.add(i);
            } else {
                class1Indices.add(i);
            }
        }

        // Shuffle
        Collections.shuffle(class0Indices, new Random(42));
        Collections.shuffle(class1Indices, new Random(42));

        // Créer folds
        List<Fold> folds = new ArrayList<>();

        for (int k = 0; k < nFolds; k++) {
            folds.add(new Fold());
        }

        // Distribuer class 0 dans les folds
        for (int i = 0; i < class0Indices.size(); i++) {
            int foldIndex = i % nFolds;
            folds.get(foldIndex).getValidationIndices().add(class0Indices.get(i));
        }

        // Distribuer class 1 dans les folds
        for (int i = 0; i < class1Indices.size(); i++) {
            int foldIndex = i % nFolds;
            folds.get(foldIndex).getValidationIndices().add(class1Indices.get(i));
        }

        // Pour chaque fold, les autres échantillons sont le train set
        for (int k = 0; k < nFolds; k++) {
            Set<Integer> valSet = new HashSet<>(folds.get(k).getValidationIndices());

            for (int i = 0; i < nSamples; i++) {
                if (!valSet.contains(i)) {
                    folds.get(k).getTrainIndices().add(i);
                }
            }

            // Créer matrices train/val
            createFoldDatasets(folds.get(k), X, y);
        }

        return folds;
    }

    /**
     * Crée folds aléatoires (sans stratification)
     */
    private List<Fold> createRandomFolds(double[][] X, int[] y, int nFolds) {
        int nSamples = X.length;

        // Indices aléatoires
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < nSamples; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, new Random(42));

        // Créer folds
        List<Fold> folds = new ArrayList<>();
        int foldSize = nSamples / nFolds;

        for (int k = 0; k < nFolds; k++) {
            Fold fold = new Fold();

            int start = k * foldSize;
            int end = (k == nFolds - 1) ? nSamples : (k + 1) * foldSize;

            // Validation = fold k
            for (int i = start; i < end; i++) {
                fold.getValidationIndices().add(indices.get(i));
            }

            // Train = autres folds
            for (int i = 0; i < nSamples; i++) {
                if (i < start || i >= end) {
                    fold.getTrainIndices().add(indices.get(i));
                }
            }

            createFoldDatasets(fold, X, y);
            folds.add(fold);
        }

        return folds;
    }

    /**
     * Crée matrices X_train, y_train, X_val, y_val pour un fold
     */
    private void createFoldDatasets(Fold fold, double[][] X, int[] y) {
        List<Integer> trainIndices = fold.getTrainIndices();
        List<Integer> valIndices = fold.getValidationIndices();

        fold.setX_train(new double[trainIndices.size()][]);
        fold.setY_train(new int[trainIndices.size()]);
        fold.setX_val(new double[valIndices.size()][]);
        fold.setY_val(new int[valIndices.size()]);

        for (int i = 0; i < trainIndices.size(); i++) {
            int idx = trainIndices.get(i);
            fold.getX_train()[i] = X[idx];
            fold.getY_train()[i] = y[idx];
        }

        for (int i = 0; i < valIndices.size(); i++) {
            int idx = valIndices.get(i);
            fold.getX_val()[i] = X[idx];
            fold.getY_val()[i] = y[idx];
        }
    }

    /**
     * Entraîne et évalue modèle sur un fold
     */
    private ModelEvaluationService.EvaluationMetrics trainAndEvaluateFold(
            Fold fold,
            String modelType,
            String[] featureNames) throws XGBoostError {

        switch (modelType.toLowerCase()) {
            case "xgboost":
                return trainAndEvaluateXGBoost(fold, featureNames);

            case "gradientboost":
            case "gbm":
                return trainAndEvaluateGradientBoost(fold, featureNames);

            case "adaboost":
                return trainAndEvaluateAdaBoost(fold, featureNames);

            default:
                throw new IllegalArgumentException("Unknown model type: " + modelType);
        }
    }

    /**
     * Entraîne et évalue XGBoost
     */
    private ModelEvaluationService.EvaluationMetrics trainAndEvaluateXGBoost(
            Fold fold,
            String[] featureNames) throws XGBoostError {

        // Entraîner
        Booster model = xgboostService.trainXGBoostModel(fold.getX_train(), fold.getY_train());

        // Prédire
        double[] probabilities = xgboostService.predictBatch(model, fold.getX_val());
        int[] predictions = new int[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            predictions[i] = probabilities[i] >= 0.5 ? 1 : 0;
        }

        // Évaluer
        return evaluationService.evaluate(predictions, probabilities, fold.getY_val());
    }

    /**
     * Entraîne et évalue Gradient Boosting
     */
    private ModelEvaluationService.EvaluationMetrics trainAndEvaluateGradientBoost(
            Fold fold,
            String[] featureNames) {

        // Entraîner
        GradientTreeBoost model = gradientBoostingService.trainGradientBoostModel(
            fold.getX_train(),
            fold.getY_train(),
            featureNames
        );

        // Prédire
        double[] probabilities = gradientBoostingService.predictBatch(
            model,
            fold.getX_val(),
            featureNames
        );

        int[] predictions = new int[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            predictions[i] = probabilities[i] >= 0.5 ? 1 : 0;
        }

        // Évaluer
        return evaluationService.evaluate(predictions, probabilities, fold.getY_val());
    }

    /**
     * Entraîne et évalue AdaBoost
     */
    private ModelEvaluationService.EvaluationMetrics trainAndEvaluateAdaBoost(
            Fold fold,
            String[] featureNames) {

        // Entraîner
        AdaBoost model = adaBoostService.trainAdaBoostModel(
            fold.getX_train(),
            fold.getY_train(),
            featureNames
        );

        // Prédire
        double[] probabilities = adaBoostService.predictBatch(
            model,
            fold.getX_val(),
            featureNames
        );

        int[] predictions = new int[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            predictions[i] = probabilities[i] >= 0.5 ? 1 : 0;
        }

        // Évaluer
        return evaluationService.evaluate(predictions, probabilities, fold.getY_val());
    }

    /**
     * Calcule métriques moyennes sur tous les folds
     */
    private ModelEvaluationService.EvaluationMetrics calculateAverageMetrics(
            List<ModelEvaluationService.EvaluationMetrics> foldMetrics) {

        int n = foldMetrics.size();

        double avgAccuracy = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getAccuracy)
            .average()
            .orElse(0.0);

        double avgPrecision = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getPrecision)
            .average()
            .orElse(0.0);

        double avgRecall = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getRecall)
            .average()
            .orElse(0.0);

        double avgF1 = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getF1Score)
            .average()
            .orElse(0.0);

        double avgAuc = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getAucRoc)
            .average()
            .orElse(0.0);

        double avgSpecificity = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getSpecificity)
            .average()
            .orElse(0.0);

        double avgNpv = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getNpv)
            .average()
            .orElse(0.0);

        double avgMcc = foldMetrics.stream()
            .mapToDouble(ModelEvaluationService.EvaluationMetrics::getMcc)
            .average()
            .orElse(0.0);

        return ModelEvaluationService.EvaluationMetrics.builder()
            .accuracy(avgAccuracy)
            .precision(avgPrecision)
            .recall(avgRecall)
            .f1Score(avgF1)
            .aucRoc(avgAuc)
            .specificity(avgSpecificity)
            .npv(avgNpv)
            .mcc(avgMcc)
            .build();
    }

    /**
     * Calcule écart-type d'une métrique sur les folds
     */
    private double calculateStdDev(
            List<ModelEvaluationService.EvaluationMetrics> foldMetrics,
            java.util.function.ToDoubleFunction<ModelEvaluationService.EvaluationMetrics> metricExtractor) {

        double mean = foldMetrics.stream()
            .mapToDouble(metricExtractor)
            .average()
            .orElse(0.0);

        double variance = foldMetrics.stream()
            .mapToDouble(m -> {
                double val = metricExtractor.applyAsDouble(m);
                double diff = val - mean;
                return diff * diff;
            })
            .average()
            .orElse(0.0);

        return Math.sqrt(variance);
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    private static class Fold {
        private List<Integer> trainIndices = new ArrayList<>();
        private List<Integer> validationIndices = new ArrayList<>();
        private double[][] X_train;
        private int[] y_train;
        private double[][] X_val;
        private int[] y_val;
    }

    @Data
    public static class CVResult {
        private ModelEvaluationService.EvaluationMetrics meanMetrics;
        private List<ModelEvaluationService.EvaluationMetrics> foldMetrics;
        private double stdAccuracy;
        private double stdF1;
        private int nFolds;
        private String modelType;
        private long cvTimeMs;
    }
}

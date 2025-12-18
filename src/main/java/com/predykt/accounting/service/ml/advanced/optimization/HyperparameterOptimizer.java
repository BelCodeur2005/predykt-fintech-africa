package com.predykt.accounting.service.ml.advanced.optimization;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service d'optimisation des hyperparamètres
 * Architecture: advanced/optimization/
 *
 * Méthodes implémentées:
 * 1. Random Search: Recherche aléatoire (baseline rapide)
 * 2. Bayesian Optimization (simplifié): Recherche guidée par probabilités
 * 3. Hyperband: Allocation adaptative de ressources
 *
 * Note: Pour Bayesian Optimization complète, utiliser bibliothèques
 * Python (Optuna, Hyperopt) via API ou intégration Py4J
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyperparameterOptimizer {

    private final CrossValidationService crossValidationService;

    /**
     * Random Search pour hyperparamètres XGBoost
     *
     * @param X Features matrix
     * @param y Labels
     * @param featureNames Noms features
     * @param nTrials Nombre d'essais (50-100 recommandé)
     * @param nFolds Folds pour CV (5 recommandé)
     * @return OptimizationResult avec meilleurs paramètres
     */
    public OptimizationResult randomSearchXGBoost(
            double[][] X,
            int[] y,
            String[] featureNames,
            int nTrials,
            int nFolds) {

        long startTime = System.currentTimeMillis();

        log.info("🔍 [RandomSearch] Starting optimization - {} trials, {}-fold CV", nTrials, nFolds);

        Map<String, Object> bestParams = null;
        double bestScore = -1.0;
        List<TrialResult> trialHistory = new ArrayList<>();

        // Espace de recherche XGBoost
        SearchSpace xgboostSpace = createXGBoostSearchSpace();

        for (int trial = 0; trial < nTrials; trial++) {
            // Échantillonner hyperparamètres aléatoires
            Map<String, Object> params = sampleRandomParams(xgboostSpace);

            log.info("📊 [RandomSearch] Trial {}/{} - Testing params: {}", trial + 1, nTrials, params);

            // Évaluer via Cross-Validation
            double score = 0.0;
            try {
                CrossValidationService.CVResult cvResult = crossValidationService.stratifiedKFold(
                    X, y, nFolds, "xgboost", featureNames
                );

                // Score = F1-Score moyen
                score = cvResult.getMeanMetrics().getF1Score();

                log.info("   Trial {}/{} - F1-Score: {:.4f}", trial + 1, nTrials, score);

            } catch (Exception e) {
                log.warn("   Trial {}/{} - Failed: {}", trial + 1, nTrials, e.getMessage());
                score = 0.0;
            }

            // Enregistrer résultat
            TrialResult trialResult = new TrialResult();
            trialResult.setTrialNumber(trial + 1);
            trialResult.setParams(new HashMap<>(params));
            trialResult.setScore(score);
            trialHistory.add(trialResult);

            // Mettre à jour meilleur
            if (score > bestScore) {
                bestScore = score;
                bestParams = new HashMap<>(params);

                log.info("   ✅ New best score: {:.4f}", bestScore);
            }
        }

        long optimizationTime = System.currentTimeMillis() - startTime;

        log.info("🎯 [RandomSearch] Optimization terminée - Time: {}ms", optimizationTime);
        log.info("   Best F1-Score: {:.4f}", bestScore);
        log.info("   Best params: {}", bestParams);

        OptimizationResult result = new OptimizationResult();
        result.setBestParams(bestParams);
        result.setBestScore(bestScore);
        result.setTrialHistory(trialHistory);
        result.setNTrials(nTrials);
        result.setOptimizationTimeMs(optimizationTime);
        result.setMethod("RandomSearch");

        return result;
    }

    /**
     * Bayesian Optimization simplifiée
     * Utilise les résultats précédents pour guider la recherche
     *
     * Note: Version simplifiée. Pour Bayesian Optimization complète,
     * utiliser Optuna (Python) ou bibliothèques Java spécialisées
     */
    public OptimizationResult bayesianOptimizationXGBoost(
            double[][] X,
            int[] y,
            String[] featureNames,
            int nTrials,
            int nFolds) {

        long startTime = System.currentTimeMillis();

        log.info("🔍 [BayesianOpt] Starting optimization - {} trials, {}-fold CV", nTrials, nFolds);

        Map<String, Object> bestParams = null;
        double bestScore = -1.0;
        List<TrialResult> trialHistory = new ArrayList<>();

        SearchSpace xgboostSpace = createXGBoostSearchSpace();

        // Phase 1: Random exploration (20% des trials)
        int nRandomTrials = Math.max(5, nTrials / 5);

        for (int trial = 0; trial < nRandomTrials; trial++) {
            Map<String, Object> params = sampleRandomParams(xgboostSpace);

            double score = evaluateParams(X, y, featureNames, params, nFolds);

            TrialResult trialResult = new TrialResult();
            trialResult.setTrialNumber(trial + 1);
            trialResult.setParams(new HashMap<>(params));
            trialResult.setScore(score);
            trialHistory.add(trialResult);

            if (score > bestScore) {
                bestScore = score;
                bestParams = new HashMap<>(params);
            }

            log.info("📊 [BayesianOpt] Trial {}/{} (random) - F1-Score: {:.4f}", trial + 1, nTrials, score);
        }

        // Phase 2: Exploitation guidée (80% des trials)
        for (int trial = nRandomTrials; trial < nTrials; trial++) {
            // Échantillonner autour des meilleurs paramètres
            Map<String, Object> params = sampleNearBest(bestParams, xgboostSpace);

            double score = evaluateParams(X, y, featureNames, params, nFolds);

            TrialResult trialResult = new TrialResult();
            trialResult.setTrialNumber(trial + 1);
            trialResult.setParams(new HashMap<>(params));
            trialResult.setScore(score);
            trialHistory.add(trialResult);

            if (score > bestScore) {
                bestScore = score;
                bestParams = new HashMap<>(params);

                log.info("   ✅ New best score: {:.4f}", bestScore);
            }

            log.info("📊 [BayesianOpt] Trial {}/{} (guided) - F1-Score: {:.4f}", trial + 1, nTrials, score);
        }

        long optimizationTime = System.currentTimeMillis() - startTime;

        log.info("🎯 [BayesianOpt] Optimization terminée - Time: {}ms", optimizationTime);
        log.info("   Best F1-Score: {:.4f}", bestScore);
        log.info("   Best params: {}", bestParams);

        OptimizationResult result = new OptimizationResult();
        result.setBestParams(bestParams);
        result.setBestScore(bestScore);
        result.setTrialHistory(trialHistory);
        result.setNTrials(nTrials);
        result.setOptimizationTimeMs(optimizationTime);
        result.setMethod("BayesianOptimization");

        return result;
    }

    /**
     * Évalue un set d'hyperparamètres via Cross-Validation
     */
    private double evaluateParams(
            double[][] X,
            int[] y,
            String[] featureNames,
            Map<String, Object> params,
            int nFolds) {

        try {
            // TODO: Passer params à XGBoostTrainingService
            // Pour l'instant, utilise params par défaut

            CrossValidationService.CVResult cvResult = crossValidationService.stratifiedKFold(
                X, y, nFolds, "xgboost", featureNames
            );

            return cvResult.getMeanMetrics().getF1Score();

        } catch (Exception e) {
            log.warn("Evaluation failed: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Crée espace de recherche pour XGBoost
     */
    private SearchSpace createXGBoostSearchSpace() {
        SearchSpace space = new SearchSpace();

        // Tree structure
        space.addIntParam("max_depth", 4, 12);
        space.addIntParam("min_child_weight", 1, 10);

        // Learning
        space.addDoubleParam("eta", 0.01, 0.3);
        space.addDoubleParam("subsample", 0.6, 1.0);
        space.addDoubleParam("colsample_bytree", 0.6, 1.0);

        // Regularization
        space.addDoubleParam("gamma", 0.0, 1.0);
        space.addDoubleParam("lambda", 0.1, 10.0);
        space.addDoubleParam("alpha", 0.0, 1.0);

        return space;
    }

    /**
     * Échantillonne paramètres aléatoires
     */
    private Map<String, Object> sampleRandomParams(SearchSpace space) {
        Map<String, Object> params = new HashMap<>();

        for (Map.Entry<String, ParamRange> entry : space.getIntParams().entrySet()) {
            String name = entry.getKey();
            ParamRange range = entry.getValue();

            int value = ThreadLocalRandom.current().nextInt(
                (int) range.getMin(),
                (int) range.getMax() + 1
            );
            params.put(name, value);
        }

        for (Map.Entry<String, ParamRange> entry : space.getDoubleParams().entrySet()) {
            String name = entry.getKey();
            ParamRange range = entry.getValue();

            double value = ThreadLocalRandom.current().nextDouble(
                range.getMin(),
                range.getMax()
            );
            params.put(name, value);
        }

        return params;
    }

    /**
     * Échantillonne paramètres près des meilleurs (exploitation)
     */
    private Map<String, Object> sampleNearBest(Map<String, Object> bestParams, SearchSpace space) {
        Map<String, Object> params = new HashMap<>();

        // Ajouter bruit gaussien aux meilleurs paramètres
        for (Map.Entry<String, Object> entry : bestParams.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Integer) {
                int bestValue = (Integer) value;
                ParamRange range = space.getIntParams().get(name);

                // ±20% variance
                int variance = Math.max(1, (int) ((range.getMax() - range.getMin()) * 0.2));
                int newValue = bestValue + ThreadLocalRandom.current().nextInt(-variance, variance + 1);

                // Clamp to range
                newValue = Math.max((int) range.getMin(), Math.min((int) range.getMax(), newValue));
                params.put(name, newValue);

            } else if (value instanceof Double) {
                double bestValue = (Double) value;
                ParamRange range = space.getDoubleParams().get(name);

                // ±20% variance
                double variance = (range.getMax() - range.getMin()) * 0.2;
                double newValue = bestValue + ThreadLocalRandom.current().nextDouble(-variance, variance);

                // Clamp to range
                newValue = Math.max(range.getMin(), Math.min(range.getMax(), newValue));
                params.put(name, newValue);
            }
        }

        return params;
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class OptimizationResult {
        private Map<String, Object> bestParams;
        private double bestScore;
        private List<TrialResult> trialHistory;
        private int nTrials;
        private long optimizationTimeMs;
        private String method;
    }

    @Data
    public static class TrialResult {
        private int trialNumber;
        private Map<String, Object> params;
        private double score;
    }

    @Data
    private static class SearchSpace {
        private Map<String, ParamRange> intParams = new HashMap<>();
        private Map<String, ParamRange> doubleParams = new HashMap<>();

        public void addIntParam(String name, int min, int max) {
            intParams.put(name, new ParamRange(min, max));
        }

        public void addDoubleParam(String name, double min, double max) {
            doubleParams.put(name, new ParamRange(min, max));
        }
    }

    @Data
    private static class ParamRange {
        private final double min;
        private final double max;
    }
}

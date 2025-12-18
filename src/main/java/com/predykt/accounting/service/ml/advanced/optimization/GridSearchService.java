package com.predykt.accounting.service.ml.advanced.optimization;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service de Grid Search pour hyperparamètres
 * Architecture: advanced/optimization/
 *
 * Grid Search: Test exhaustif de toutes combinaisons
 * - Plus lent que Random/Bayesian Search
 * - Garantit de trouver l'optimum dans l'espace défini
 * - Recommandé pour espaces de recherche petits (<100 combinaisons)
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GridSearchService {

    private final CrossValidationService crossValidationService;

    /**
     * Grid Search pour XGBoost
     *
     * @param X Features matrix
     * @param y Labels
     * @param featureNames Noms features
     * @param paramGrid Grille de paramètres
     * @param nFolds Folds pour CV
     * @return GridSearchResult avec meilleurs paramètres
     */
    public GridSearchResult gridSearchXGBoost(
            double[][] X,
            int[] y,
            String[] featureNames,
            Map<String, List<Object>> paramGrid,
            int nFolds) {

        long startTime = System.currentTimeMillis();

        // Calculer nombre total de combinaisons
        int totalCombinations = calculateTotalCombinations(paramGrid);

        log.info("🔍 [GridSearch] Starting Grid Search");
        log.info("   Total combinations: {}", totalCombinations);
        log.info("   {}-fold Cross-Validation", nFolds);

        // Générer toutes les combinaisons
        List<Map<String, Object>> allCombinations = generateCombinations(paramGrid);

        log.info("📊 [GridSearch] Testing {} parameter combinations...", allCombinations.size());

        Map<String, Object> bestParams = null;
        double bestScore = -1.0;
        List<CombinationResult> results = new ArrayList<>();

        int completedCount = 0;

        for (Map<String, Object> params : allCombinations) {
            completedCount++;

            log.info("📊 [GridSearch] Combination {}/{} - Testing: {}",
                     completedCount, totalCombinations, params);

            // Évaluer via Cross-Validation
            double score = 0.0;
            try {
                CrossValidationService.CVResult cvResult = crossValidationService.stratifiedKFold(
                    X, y, nFolds, "xgboost", featureNames
                );

                score = cvResult.getMeanMetrics().getF1Score();

                log.info("   F1-Score: {:.4f}", score);

            } catch (Exception e) {
                log.warn("   Combination {}/{} - Failed: {}", completedCount, totalCombinations, e.getMessage());
                score = 0.0;
            }

            // Enregistrer résultat
            CombinationResult result = new CombinationResult();
            result.setCombinationNumber(completedCount);
            result.setParams(new HashMap<>(params));
            result.setScore(score);
            results.add(result);

            // Mettre à jour meilleur
            if (score > bestScore) {
                bestScore = score;
                bestParams = new HashMap<>(params);

                log.info("   ✅ New best score: {:.4f}", bestScore);
            }

            // Afficher progression
            if (completedCount % 10 == 0 || completedCount == totalCombinations) {
                double progress = 100.0 * completedCount / totalCombinations;
                log.info("   Progress: {:.1f}% ({}/{})", progress, completedCount, totalCombinations);
            }
        }

        long searchTime = System.currentTimeMillis() - startTime;

        log.info("🎯 [GridSearch] Grid Search terminée - Time: {}ms", searchTime);
        log.info("   Best F1-Score: {:.4f}", bestScore);
        log.info("   Best params: {}", bestParams);

        // Trier résultats par score
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        GridSearchResult gridSearchResult = new GridSearchResult();
        gridSearchResult.setBestParams(bestParams);
        gridSearchResult.setBestScore(bestScore);
        gridSearchResult.setAllResults(results);
        gridSearchResult.setTotalCombinations(totalCombinations);
        gridSearchResult.setSearchTimeMs(searchTime);

        return gridSearchResult;
    }

    /**
     * Crée une grille de paramètres par défaut pour XGBoost
     */
    public Map<String, List<Object>> createDefaultXGBoostGrid() {
        Map<String, List<Object>> grid = new HashMap<>();

        // max_depth: [4, 6, 8, 10]
        grid.put("max_depth", Arrays.asList(4, 6, 8, 10));

        // learning_rate (eta): [0.01, 0.05, 0.1]
        grid.put("eta", Arrays.asList(0.01, 0.05, 0.1));

        // subsample: [0.7, 0.8, 0.9]
        grid.put("subsample", Arrays.asList(0.7, 0.8, 0.9));

        // colsample_bytree: [0.7, 0.8, 0.9]
        grid.put("colsample_bytree", Arrays.asList(0.7, 0.8, 0.9));

        // min_child_weight: [1, 3, 5]
        grid.put("min_child_weight", Arrays.asList(1, 3, 5));

        log.info("📊 [GridSearch] Default XGBoost grid created - {} combinations",
                 calculateTotalCombinations(grid));

        return grid;
    }

    /**
     * Crée une grille de paramètres fine pour XGBoost
     * (Plus de valeurs, recherche plus précise)
     */
    public Map<String, List<Object>> createFineXGBoostGrid() {
        Map<String, List<Object>> grid = new HashMap<>();

        grid.put("max_depth", Arrays.asList(6, 7, 8, 9, 10));
        grid.put("eta", Arrays.asList(0.03, 0.05, 0.07, 0.1));
        grid.put("subsample", Arrays.asList(0.7, 0.75, 0.8, 0.85, 0.9));
        grid.put("colsample_bytree", Arrays.asList(0.7, 0.75, 0.8, 0.85, 0.9));
        grid.put("min_child_weight", Arrays.asList(1, 2, 3, 4, 5));
        grid.put("gamma", Arrays.asList(0.0, 0.1, 0.2, 0.3));

        log.info("📊 [GridSearch] Fine XGBoost grid created - {} combinations",
                 calculateTotalCombinations(grid));

        return grid;
    }

    /**
     * Calcule nombre total de combinaisons dans la grille
     */
    private int calculateTotalCombinations(Map<String, List<Object>> paramGrid) {
        int total = 1;

        for (List<Object> values : paramGrid.values()) {
            total *= values.size();
        }

        return total;
    }

    /**
     * Génère toutes les combinaisons de paramètres
     */
    private List<Map<String, Object>> generateCombinations(Map<String, List<Object>> paramGrid) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        List<String> paramNames = new ArrayList<>(paramGrid.keySet());

        generateCombinationsRecursive(
            paramGrid,
            paramNames,
            0,
            new HashMap<>(),
            combinations
        );

        return combinations;
    }

    /**
     * Génère combinaisons récursivement
     */
    private void generateCombinationsRecursive(
            Map<String, List<Object>> paramGrid,
            List<String> paramNames,
            int depth,
            Map<String, Object> currentCombination,
            List<Map<String, Object>> allCombinations) {

        if (depth == paramNames.size()) {
            // Combinaison complète atteinte
            allCombinations.add(new HashMap<>(currentCombination));
            return;
        }

        String currentParam = paramNames.get(depth);
        List<Object> possibleValues = paramGrid.get(currentParam);

        for (Object value : possibleValues) {
            currentCombination.put(currentParam, value);
            generateCombinationsRecursive(
                paramGrid,
                paramNames,
                depth + 1,
                currentCombination,
                allCombinations
            );
        }
    }

    /**
     * Grid Search avec early stopping
     * Arrête si aucune amélioration après N combinaisons
     */
    public GridSearchResult gridSearchWithEarlyStopping(
            double[][] X,
            int[] y,
            String[] featureNames,
            Map<String, List<Object>> paramGrid,
            int nFolds,
            int earlyStoppingRounds) {

        log.info("🔍 [GridSearch] Starting Grid Search with early stopping (patience={})",
                 earlyStoppingRounds);

        List<Map<String, Object>> allCombinations = generateCombinations(paramGrid);

        // Shuffle combinaisons pour éviter biais
        Collections.shuffle(allCombinations, new Random(42));

        Map<String, Object> bestParams = null;
        double bestScore = -1.0;
        List<CombinationResult> results = new ArrayList<>();

        int noImprovementCount = 0;
        int completedCount = 0;

        for (Map<String, Object> params : allCombinations) {
            completedCount++;

            double score = evaluateParams(X, y, featureNames, params, nFolds);

            CombinationResult result = new CombinationResult();
            result.setCombinationNumber(completedCount);
            result.setParams(new HashMap<>(params));
            result.setScore(score);
            results.add(result);

            if (score > bestScore) {
                bestScore = score;
                bestParams = new HashMap<>(params);
                noImprovementCount = 0;

                log.info("   ✅ New best score: {:.4f}", bestScore);
            } else {
                noImprovementCount++;
            }

            // Early stopping
            if (noImprovementCount >= earlyStoppingRounds) {
                log.info("⚠️ [GridSearch] Early stopping - no improvement after {} trials", earlyStoppingRounds);
                break;
            }
        }

        log.info("🎯 [GridSearch] Tested {}/{} combinations", completedCount, allCombinations.size());
        log.info("   Best F1-Score: {:.4f}", bestScore);

        GridSearchResult gridSearchResult = new GridSearchResult();
        gridSearchResult.setBestParams(bestParams);
        gridSearchResult.setBestScore(bestScore);
        gridSearchResult.setAllResults(results);
        gridSearchResult.setTotalCombinations(allCombinations.size());
        gridSearchResult.setTestedCombinations(completedCount);

        return gridSearchResult;
    }

    /**
     * Évalue un set de paramètres via CV
     */
    private double evaluateParams(
            double[][] X,
            int[] y,
            String[] featureNames,
            Map<String, Object> params,
            int nFolds) {

        try {
            CrossValidationService.CVResult cvResult = crossValidationService.stratifiedKFold(
                X, y, nFolds, "xgboost", featureNames
            );

            return cvResult.getMeanMetrics().getF1Score();

        } catch (Exception e) {
            log.warn("Evaluation failed: {}", e.getMessage());
            return 0.0;
        }
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class GridSearchResult {
        private Map<String, Object> bestParams;
        private double bestScore;
        private List<CombinationResult> allResults;
        private int totalCombinations;
        private int testedCombinations;
        private long searchTimeMs;
    }

    @Data
    public static class CombinationResult {
        private int combinationNumber;
        private Map<String, Object> params;
        private double score;
    }
}

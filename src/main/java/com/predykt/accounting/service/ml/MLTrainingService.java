package com.predykt.accounting.service.ml;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.ml.MLModel;
import com.predykt.accounting.domain.entity.ml.MLTrainingData;
import com.predykt.accounting.domain.enums.MLModelStatus;
import com.predykt.accounting.dto.ml.MatchFeatures;
import com.predykt.accounting.repository.ml.MLModelRepository;
import com.predykt.accounting.repository.ml.MLTrainingDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service d'entraînement ML pour matching bancaire
 *
 * MODE AVANCÉ (par défaut, predykt.ml.use-advanced-mode=true):
 * - 30 features avancées (4 tiers: amount, text, temporal, metadata)
 * - XGBoost ou Stacking Ensemble (4 algorithms + meta-learner)
 * - SMOTE, Scaling, Hyperparameter Optimization
 * - Cross-Validation stratifiée (5-fold)
 * - Model Versioning sémantique (v1.0.0)
 * - Monitoring & Drift detection
 * - Target accuracy: 95-97%
 *
 * MODE BASIQUE (legacy, predykt.ml.use-advanced-mode=false):
 * - 12 features basiques
 * - RandomForest Smile uniquement
 * - Pas de preprocessing
 * - Target accuracy: 70%
 *
 * @author PREDYKT ML Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLTrainingService {

    private final MLTrainingDataRepository trainingDataRepository;
    private final MLModelRepository modelRepository;
    private final MLModelStorageService modelStorageService;
    private final MLAdvancedOrchestrator advancedOrchestrator;

    @Value("${predykt.ml.use-advanced-mode:true}")
    private boolean useAdvancedMode;

    @Value("${predykt.ml.use-stacking:false}")
    private boolean useStacking;

    // Paramètres Random Forest (mode basique)
    private static final int NUM_TREES = 100;
    private static final int MAX_DEPTH = 20;
    private static final int MIN_SAMPLES_SPLIT = 5;
    private static final int MIN_SAMPLES_LEAF = 2;

    // Seuils (mode basique)
    private static final int MIN_TRAINING_DATA = 50;
    private static final double MIN_ACCURACY = 0.70;  // 70%

    /**
     * Entraîne un nouveau modèle pour une entreprise
     *
     * Dispatcher intelligent:
     * - Si useAdvancedMode=true: Utilise MLAdvancedOrchestrator (30 features, XGBoost/Stacking)
     * - Si useAdvancedMode=false: Utilise RandomForest basique (12 features)
     *
     * @param company Entreprise
     * @return Modèle entraîné, ou null si pas assez de données
     */
    @Transactional
    @CacheEvict(value = "mlModels", key = "#company.id")
    public MLModel trainNewModel(Company company) {
        long startTime = System.currentTimeMillis();

        // Dispatcher vers système avancé ou basique
        if (useAdvancedMode) {
            log.info("🚀 [ML] Mode AVANCÉ activé - Company: {}, Algorithm: {}",
                     company.getId(), useStacking ? "STACKING" : "XGBOOST");
            return advancedOrchestrator.trainAdvancedModel(company, useStacking);
        }

        // Mode legacy (basique) - RandomForest avec 12 features
        log.info("📊 [ML] Mode BASIQUE (legacy) - Company: {}, RandomForest", company.getId());
        return trainBasicModel(company, startTime);
    }

    /**
     * Entraîne un modèle basique (RandomForest avec 12 features)
     * Legacy mode pour compatibilité
     */
    private MLModel trainBasicModel(Company company, long startTime) {
        // 1. Récupérer les données d'entraînement
        List<MLTrainingData> trainingData = trainingDataRepository
            .findUsableTrainingData(company);

        if (trainingData.size() < MIN_TRAINING_DATA) {
            log.warn("Pas assez de données d'entraînement pour company {}: {} (minimum {})",
                company.getId(), trainingData.size(), MIN_TRAINING_DATA);
            return null;
        }

        log.info("Données d'entraînement: {} exemples", trainingData.size());

        // 2. Convertir en matrices
        TrainingDataset dataset = prepareDataset(trainingData);

        // 3. Entraîner Random Forest
        RandomForest rf = trainRandomForest(dataset);

        // 4. Évaluer le modèle
        TrainingMetrics metrics = evaluateModel(rf, dataset);

        log.info("Métriques d'entraînement: Accuracy={:.2f}%, Precision={:.2f}%, Recall={:.2f}%, F1={:.2f}%",
            metrics.accuracy * 100, metrics.precision * 100, metrics.recall * 100, metrics.f1Score * 100);

        // 5. Vérifier si le modèle est acceptable
        if (metrics.accuracy < MIN_ACCURACY) {
            log.warn("Modèle rejeté: accuracy trop faible ({:.2f}% < {:.2f}%)",
                metrics.accuracy * 100, MIN_ACCURACY * 100);
            return null;
        }

        // 6. Sauvegarder le modèle sur disque
        String version = generateVersion();
        String modelPath = modelStorageService.saveModel(rf, company.getId(), version);

        // 7. Créer l'entité MLModel
        MLModel model = MLModel.builder()
            .company(company)
            .modelName("RandomForest-BankMatching")
            .algorithm("RandomForest")
            .modelVersion(version)
            .modelPath(modelPath)
            .status(MLModelStatus.TRAINED)
            .isActive(false)  // Pas encore déployé
            .accuracy(BigDecimal.valueOf(metrics.accuracy).setScale(4, RoundingMode.HALF_UP))
            .precisionScore(BigDecimal.valueOf(metrics.precision).setScale(4, RoundingMode.HALF_UP))
            .recallScore(BigDecimal.valueOf(metrics.recall).setScale(4, RoundingMode.HALF_UP))
            .f1Score(BigDecimal.valueOf(metrics.f1Score).setScale(4, RoundingMode.HALF_UP))
            .trainingSamplesCount(trainingData.size())
            .trainingDurationSeconds((int)((System.currentTimeMillis() - startTime) / 1000))
            .build();

        MLModel saved = modelRepository.save(model);

        log.info("Modèle ML entraîné avec succès: {} (accuracy={:.2f}%, {}s)",
            saved.getModelVersion(), metrics.accuracy * 100, saved.getTrainingDurationSeconds());

        return saved;
    }

    /**
     * Déploie un modèle (marque comme actif, désactive les autres)
     */
    @Transactional
    @CacheEvict(value = "mlModels", key = "#model.company.id")
    public void deployModel(MLModel model) {
        // Désactiver tous les modèles actifs de cette entreprise
        List<MLModel> activeModels = modelRepository
            .findByCompanyAndStatus(model.getCompany(), MLModelStatus.DEPLOYED);

        for (MLModel active : activeModels) {
            active.setIsActive(false);
            active.setStatus(MLModelStatus.DEPRECATED);
            modelRepository.save(active);
            log.info("Modèle {} désactivé", active.getModelVersion());
        }

        // Activer le nouveau modèle
        model.setIsActive(true);
        model.setStatus(MLModelStatus.DEPLOYED);
        modelRepository.save(model);

        log.info("Modèle {} déployé pour company {}", model.getModelVersion(), model.getCompany().getId());
    }

    /**
     * Entraîne et déploie automatiquement si meilleur que l'actuel
     */
    @Transactional
    public boolean trainAndDeployIfBetter(Company company) {
        MLModel newModel = trainNewModel(company);
        if (newModel == null) {
            return false;
        }

        // Comparer avec le modèle actuel
        modelRepository.findByCompanyAndIsActiveTrue(company).ifPresentOrElse(
            currentModel -> {
                if (newModel.getAccuracy().compareTo(currentModel.getAccuracy()) > 0) {
                    log.info("Nouveau modèle meilleur: {:.2f}% > {:.2f}%",
                        newModel.getAccuracy().doubleValue() * 100,
                        currentModel.getAccuracy().doubleValue() * 100);
                    deployModel(newModel);
                } else {
                    log.info("Nouveau modèle moins bon, conservation de l'actuel");
                }
            },
            () -> {
                // Pas de modèle actuel, déployer le nouveau
                log.info("Aucun modèle actif, déploiement du nouveau");
                deployModel(newModel);
            }
        );

        // Nettoyer les anciens modèles
        modelStorageService.cleanupOldModels(company.getId());

        return true;
    }

    /**
     * Prépare le dataset pour Smile (mode basique - 12 features)
     */
    private TrainingDataset prepareDataset(List<MLTrainingData> trainingData) {
        int n = trainingData.size();
        int m = 12;  // 12 features basiques

        double[][] X = new double[n][m];
        int[] y = new int[n];

        for (int i = 0; i < n; i++) {
            MLTrainingData data = trainingData.get(i);

            // Features
            X[i] = convertFeaturesToArray(data.getFeatures());

            // Label (0 = rejeté, 1 = accepté)
            y[i] = data.getLabel();
        }

        return new TrainingDataset(X, y);
    }

    /**
     * Convertit Map features → double[] (mode basique)
     */
    private double[] convertFeaturesToArray(Map<String, Object> features) {
        String[] featureNames = MatchFeatures.getFeatureNames();
        double[] array = new double[featureNames.length];

        for (int i = 0; i < featureNames.length; i++) {
            Object value = features.get(featureNames[i]);
            array[i] = (value instanceof Number) ? ((Number) value).doubleValue() : 0.0;
        }

        return array;
    }

    /**
     * Entraîne un Random Forest (mode basique)
     */
    private RandomForest trainRandomForest(TrainingDataset dataset) {
        log.info("Entraînement Random Forest: {} arbres, profondeur max {}, min split {}",
            NUM_TREES, MAX_DEPTH, MIN_SAMPLES_SPLIT);

        // Smile 3.0.2 API: Créer DataFrame depuis double[][] et int[]
        DataFrame df = createDataFrame(dataset);

        // Créer formule (y est la variable cible)
        Formula formula = Formula.lhs("y");

        // Entraîner Random Forest (Smile 3.0.2 API)
        RandomForest rf = RandomForest.fit(
            formula,
            df,
            NUM_TREES,  // ntrees
            dataset.X[0].length,  // mtry (toutes les features)
            smile.base.cart.SplitRule.GINI,  // splitRule
            MAX_DEPTH,  // maxDepth
            500,  // maxNodes
            MIN_SAMPLES_LEAF,  // nodeSize
            1.0,  // subsample
            null  // classWeight
        );

        // Stocker le DataFrame pour l'évaluation
        dataset.dataFrame = df;

        return rf;
    }

    /**
     * Crée un DataFrame Smile depuis double[][] (features) et int[] (labels)
     */
    private DataFrame createDataFrame(TrainingDataset dataset) {
        String[] featureNames = MatchFeatures.getFeatureNames();
        List<smile.data.vector.BaseVector> vectors = new ArrayList<>();

        // Transposer X: double[N][F] → F colonnes de N valeurs
        for (int j = 0; j < dataset.X[0].length; j++) {
            final int colIndex = j;
            double[] columnValues = new double[dataset.X.length];
            for (int i = 0; i < dataset.X.length; i++) {
                columnValues[i] = dataset.X[i][colIndex];
            }
            vectors.add(DoubleVector.of(featureNames[j], columnValues));
        }

        // Ajouter colonne de labels
        vectors.add(IntVector.of("y", dataset.y));

        // Créer DataFrame (varargs, pas List)
        return DataFrame.of(vectors.toArray(new smile.data.vector.BaseVector[0]));
    }

    /**
     * Évalue le modèle (mode basique)
     */
    private TrainingMetrics evaluateModel(RandomForest rf, TrainingDataset dataset) {
        int[] predictions = new int[dataset.y.length];

        // Utiliser le DataFrame pour prédictions (Smile 3.0.2 API)
        for (int i = 0; i < dataset.dataFrame.size(); i++) {
            predictions[i] = rf.predict(dataset.dataFrame.get(i));
        }

        // Calculer les métriques
        int tp = 0, fp = 0, tn = 0, fn = 0;

        for (int i = 0; i < dataset.y.length; i++) {
            if (predictions[i] == 1 && dataset.y[i] == 1) tp++;
            else if (predictions[i] == 1 && dataset.y[i] == 0) fp++;
            else if (predictions[i] == 0 && dataset.y[i] == 0) tn++;
            else if (predictions[i] == 0 && dataset.y[i] == 1) fn++;
        }

        double accuracy = (double) (tp + tn) / dataset.y.length;
        double precision = tp > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall = tp > 0 ? (double) tp / (tp + fn) : 0.0;
        double f1 = (precision + recall > 0) ?
            2 * (precision * recall) / (precision + recall) : 0.0;

        return new TrainingMetrics(accuracy, precision, recall, f1);
    }

    /**
     * Génère une version de modèle
     */
    private String generateVersion() {
        return "v" + LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    /**
     * Classe interne pour dataset
     */
    private static class TrainingDataset {
        double[][] X;  // Features
        int[] y;       // Labels
        DataFrame dataFrame;  // DataFrame pour prédictions

        TrainingDataset(double[][] X, int[] y) {
            this.X = X;
            this.y = y;
        }
    }

    /**
     * Classe interne pour métriques
     */
    private static class TrainingMetrics {
        double accuracy;
        double precision;
        double recall;
        double f1Score;

        TrainingMetrics(double accuracy, double precision, double recall, double f1Score) {
            this.accuracy = accuracy;
            this.precision = precision;
            this.recall = recall;
            this.f1Score = f1Score;
        }
    }

    /**
     * Exporte les feature importances (explainability)
     */
    public Map<String, Double> getFeatureImportances(RandomForest rf) {
        Map<String, Double> importances = new HashMap<>();

        try {
            double[] importance = rf.importance();
            String[] featureNames = MatchFeatures.getFeatureNames();

            for (int i = 0; i < Math.min(importance.length, featureNames.length); i++) {
                importances.put(featureNames[i], importance[i]);
            }

            log.info("Feature importances: {}", importances);

        } catch (Exception e) {
            log.error("Erreur calcul feature importances: {}", e.getMessage());
        }

        return importances;
    }

    /**
     * Vérifie si une entreprise a assez de données pour entraîner
     */
    public boolean hasEnoughTrainingData(Company company) {
        long count = trainingDataRepository.countUsableTrainingData(company);
        return count >= MIN_TRAINING_DATA;
    }

    /**
     * Retourne les statistiques d'entraînement
     */
    public Map<String, Object> getTrainingStats(Company company) {
        Map<String, Object> stats = new HashMap<>();

        long totalData = trainingDataRepository.countUsableTrainingData(company);
        long acceptedCount = trainingDataRepository
            .findUsableTrainingData(company)
            .stream()
            .filter(MLTrainingData::getWasAccepted)
            .count();

        stats.put("totalTrainingData", totalData);
        stats.put("acceptedCount", acceptedCount);
        stats.put("rejectedCount", totalData - acceptedCount);
        stats.put("hasEnoughData", totalData >= MIN_TRAINING_DATA);
        stats.put("minRequired", MIN_TRAINING_DATA);
        stats.put("mode", useAdvancedMode ? "ADVANCED" : "BASIC");
        stats.put("algorithm", useAdvancedMode ? (useStacking ? "STACKING" : "XGBOOST") : "RandomForest");

        return stats;
    }
}

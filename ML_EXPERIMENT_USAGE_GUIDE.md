# 🔬 Guide d'Utilisation : ML Experiment Tracking

## Vue d'Ensemble

Le système **MLExperiment** permet de tracker toutes vos expériences de Machine Learning :
- Grid search d'hyperparamètres
- Comparaison d'algorithmes
- A/B testing de features
- Optimisation progressive

## Architecture

```
MLExperiment (Expérience)
  ├── Run #1 (hyperparams set A) → 89.2%
  ├── Run #2 (hyperparams set B) → 91.5%
  ├── Run #3 (hyperparams set C) → 93.8% ← BEST
  └── Run #4 (hyperparams set D) → 92.1%
```

---

## 📋 Cas d'Usage 1 : Grid Search Simple

### Objectif
Trouver le meilleur `max_depth` pour XGBoost.

### Code

```java
@Service
@RequiredArgsConstructor
public class MyMLService {

    private final MLExperimentService experimentService;
    private final XGBoostTrainingService xgboostService;

    public void optimizeMaxDepth(Company company, User user) {

        // 1. Créer l'expérience
        MLExperiment experiment = experimentService.createExperiment(
            company,
            "Optimisation max_depth XGBoost",
            "Trouver le meilleur max_depth pour le matching bancaire",
            "GRID_SEARCH",
            user
        );

        // 2. Tester différentes valeurs
        List<Integer> maxDepths = List.of(5, 10, 15, 20, 25);

        for (Integer depth : maxDepths) {
            // Hyperparamètres pour ce run
            Map<String, Object> hyperparams = Map.of(
                "max_depth", depth,
                "n_estimators", 200,
                "learning_rate", 0.05
            );

            // Entraîner le modèle avec ces hyperparams
            ModelEvaluationMetrics metrics = xgboostService.trainAndEvaluate(
                trainingData,
                hyperparams
            );

            // 3. Logger le run
            experimentService.logRun(
                experiment,
                hyperparams,
                metrics.getAccuracy(),
                metrics.getF1Score(),
                metrics.getPrecision(),
                metrics.getRecall()
            );

            log.info("Run avec max_depth={} → Accuracy: {:.2f}%",
                     depth, metrics.getAccuracy() * 100);
        }

        // 4. Terminer l'expérience
        experimentService.completeExperiment(experiment);

        // 5. Récupérer le meilleur run
        MLExperimentRun bestRun = experimentService.getBestRun(experiment)
            .orElseThrow();

        log.info("🏆 Meilleur run: max_depth={} avec {:.2f}% accuracy",
                 bestRun.getHyperparameter("max_depth"),
                 bestRun.getAccuracy() * 100);

        // 6. Optionnel: Déployer le meilleur modèle
        // deployBestModel(bestRun);
    }
}
```

### Résultat en Base de Données

**Table `ml_experiments`:**
```
| id | experiment_name                     | status    | best_accuracy | total_runs |
|----|-------------------------------------|-----------|---------------|------------|
| 1  | Optimisation max_depth XGBoost      | COMPLETED | 0.9380        | 5          |
```

**Table `ml_experiment_runs`:**
```
| id | run_number | hyperparameters                                    | accuracy | status    |
|----|------------|----------------------------------------------------|----------|-----------|
| 1  | 1          | {"max_depth": 5, "n_estimators": 200, "lr": 0.05} | 0.8920   | COMPLETED |
| 2  | 2          | {"max_depth": 10, "n_estimators": 200, "lr": 0.05}| 0.9150   | COMPLETED |
| 3  | 3          | {"max_depth": 15, "n_estimators": 200, "lr": 0.05}| 0.9380   | COMPLETED | ← BEST
| 4  | 4          | {"max_depth": 20, "n_estimators": 200, "lr": 0.05}| 0.9350   | COMPLETED |
| 5  | 5          | {"max_depth": 25, "n_estimators": 200, "lr": 0.05}| 0.9320   | COMPLETED |
```

---

## 📋 Cas d'Usage 2 : Grid Search Multi-Paramètres

### Objectif
Tester TOUTES les combinaisons de `n_estimators` et `learning_rate`.

### Code

```java
public void fullGridSearch(Company company, User user) {

    MLExperiment experiment = experimentService.createExperiment(
        company,
        "Grid Search Complet XGBoost",
        "Tester toutes les combinaisons de n_estimators et learning_rate",
        "GRID_SEARCH",
        user
    );

    // Grille de paramètres
    List<Integer> nEstimators = List.of(100, 200, 300, 500);
    List<Double> learningRates = List.of(0.01, 0.05, 0.1, 0.2);

    int totalCombinations = nEstimators.size() * learningRates.size();
    log.info("🔍 Grid Search: {} combinaisons à tester", totalCombinations);

    for (Integer n : nEstimators) {
        for (Double lr : learningRates) {

            Map<String, Object> hyperparams = Map.of(
                "n_estimators", n,
                "learning_rate", lr,
                "max_depth", 10  // Fixe
            );

            try {
                ModelEvaluationMetrics metrics = xgboostService.trainAndEvaluate(
                    trainingData,
                    hyperparams
                );

                experimentService.logRun(
                    experiment,
                    hyperparams,
                    metrics.getAccuracy(),
                    metrics.getF1Score(),
                    metrics.getPrecision(),
                    metrics.getRecall()
                );

            } catch (Exception e) {
                log.error("Erreur run n={}, lr={}: {}", n, lr, e.getMessage());
                // Le run est marqué comme failed automatiquement
            }
        }
    }

    experimentService.completeExperiment(experiment);

    // Générer rapport
    ExperimentReport report = experimentService.generateReport(experiment);
    log.info("📊 Rapport:");
    log.info("   Total runs: {}", report.getTotalRuns());
    log.info("   Successful: {}", report.getSuccessfulRuns());
    log.info("   Best accuracy: {:.2f}%", report.getMaxAccuracy() * 100);
    log.info("   Average accuracy: {:.2f}%", report.getAverageAccuracy() * 100);
    log.info("   Std dev: {:.4f}", report.getStdDevAccuracy());
}
```

---

## 📋 Cas d'Usage 3 : Comparaison d'Algorithmes

### Objectif
Comparer XGBoost, LightGBM et Random Forest.

### Code

```java
public void compareAlgorithms(Company company, User user) {

    MLExperiment experiment = experimentService.createExperiment(
        company,
        "Comparaison Algorithmes ML",
        "XGBoost vs LightGBM vs RandomForest",
        "MANUAL",
        user
    );

    // Run 1: XGBoost
    Map<String, Object> xgboostParams = Map.of(
        "algorithm", "XGBoost",
        "n_estimators", 200,
        "max_depth", 10
    );

    ModelEvaluationMetrics xgboostMetrics = xgboostService.trainAndEvaluate(
        trainingData, xgboostParams
    );

    experimentService.logRun(
        experiment,
        xgboostParams,
        xgboostMetrics.getAccuracy(),
        xgboostMetrics.getF1Score(),
        xgboostMetrics.getPrecision(),
        xgboostMetrics.getRecall()
    );

    // Run 2: LightGBM
    Map<String, Object> lgbmParams = Map.of(
        "algorithm", "LightGBM",
        "num_leaves", 31,
        "learning_rate", 0.05
    );

    ModelEvaluationMetrics lgbmMetrics = lightGBMService.trainAndEvaluate(
        trainingData, lgbmParams
    );

    experimentService.logRun(
        experiment,
        lgbmParams,
        lgbmMetrics.getAccuracy(),
        lgbmMetrics.getF1Score(),
        lgbmMetrics.getPrecision(),
        lgbmMetrics.getRecall()
    );

    // Run 3: Random Forest
    Map<String, Object> rfParams = Map.of(
        "algorithm", "RandomForest",
        "n_trees", 100,
        "max_depth", 15
    );

    ModelEvaluationMetrics rfMetrics = randomForestService.trainAndEvaluate(
        trainingData, rfParams
    );

    experimentService.logRun(
        experiment,
        rfParams,
        rfMetrics.getAccuracy(),
        rfMetrics.getF1Score(),
        rfMetrics.getPrecision(),
        rfMetrics.getRecall()
    );

    experimentService.completeExperiment(experiment);

    // Trouver le gagnant
    MLExperimentRun winner = experimentService.getBestRun(experiment).orElseThrow();

    log.info("🏆 Algorithme gagnant: {} avec {:.2f}% accuracy",
             winner.getHyperparameter("algorithm"),
             winner.getAccuracy() * 100);
}
```

---

## 📋 Cas d'Usage 4 : Tracking Temps Réel

### Objectif
Logger un run long avec tracking en temps réel.

### Code

```java
public void longRunningExperiment(Company company, User user) {

    MLExperiment experiment = experimentService.createExperiment(
        company,
        "Entraînement Deep Learning",
        "Test d'un réseau de neurones profond",
        "MANUAL",
        user
    );

    // Hyperparamètres
    Map<String, Object> hyperparams = Map.of(
        "layers", 5,
        "neurons_per_layer", 256,
        "dropout", 0.3,
        "batch_size", 64
    );

    // 1. Démarrer le run (status = RUNNING)
    MLExperimentRun run = experimentService.startRun(
        experiment,
        hyperparams,
        "Deep Neural Network - 5 layers"
    );

    try {
        // 2. Entraînement long
        log.info("🚀 Démarrage entraînement...");

        DeepLearningModel model = trainDeepLearningModel(trainingData, hyperparams);

        // 3. Évaluation
        ModelEvaluationMetrics metrics = evaluateModel(model, validationData);

        // 4. Terminer le run avec résultats
        experimentService.completeRun(
            run,
            metrics.getAccuracy(),
            metrics.getF1Score(),
            metrics.getPrecision(),
            metrics.getRecall()
        );

        log.info("✅ Run terminé avec succès: {:.2f}% accuracy",
                 metrics.getAccuracy() * 100);

    } catch (Exception e) {
        // Marquer le run comme échoué
        experimentService.failRun(run, e.getMessage());
        log.error("❌ Run échoué: {}", e.getMessage());
    }

    experimentService.completeExperiment(experiment);
}
```

---

## 📋 Cas d'Usage 5 : Configuration Avancée

### Objectif
Logger un run avec toutes les métadonnées possibles.

### Code

```java
public void advancedRunLogging(Company company, User user) {

    MLExperiment experiment = experimentService.createExperiment(
        company,
        "Expérience Complète",
        "Run avec toutes les métadonnées",
        "MANUAL",
        user
    );

    // Configuration complète du run
    MLExperimentService.RunConfiguration config = new MLExperimentService.RunConfiguration();

    // Hyperparamètres
    config.setHyperparameters(Map.of(
        "n_estimators", 200,
        "max_depth", 10,
        "learning_rate", 0.05,
        "subsample", 0.8,
        "colsample_bytree", 0.8
    ));

    // Métriques principales
    config.setAccuracy(0.9450);
    config.setF1Score(0.9380);
    config.setPrecision(0.9520);
    config.setRecall(0.9250);
    config.setRocAuc(0.9820);

    // Métriques additionnelles custom
    config.setMetrics(Map.of(
        "confusion_matrix", List.of(List.of(450, 50), List.of(30, 470)),
        "train_accuracy", 0.9650,
        "val_accuracy", 0.9450,
        "feature_importance", Map.of(
            "amount_difference", 0.35,
            "tfidf_similarity", 0.28,
            "date_diff_days", 0.15
        )
    ));

    // Données entraînement
    config.setTrainingSamples(8000);
    config.setValidationSamples(1000);
    config.setTestSamples(1000);

    // Tags et notes
    config.setTags(List.of("production_candidate", "best_config", "v2.0"));
    config.setNotes("Configuration optimale trouvée après 50 runs. " +
                    "Prêt pour déploiement en production.");

    // Chemin du modèle sauvegardé
    config.setModelPath("/models/company_1/xgboost/run_42/model.bin");

    config.setRunName("Configuration Optimale");

    // Logger le run complet
    MLExperimentRun run = experimentService.logRunWithConfig(experiment, config);

    // Marquer comme candidat pour déploiement
    experimentService.addTagToRun(run, "deploy_ready");

    experimentService.completeExperiment(experiment);
}
```

---

## 📋 Cas d'Usage 6 : Déploiement du Meilleur Run

### Objectif
Déployer automatiquement le meilleur run d'une expérience.

### Code

```java
@Service
@RequiredArgsConstructor
public class MLDeploymentService {

    private final MLExperimentService experimentService;
    private final ModelVersioningService versioningService;
    private final MLModelRepository modelRepository;

    public MLModel deployBestRun(MLExperiment experiment) {

        // 1. Récupérer le meilleur run
        MLExperimentRun bestRun = experimentService.getBestRun(experiment)
            .orElseThrow(() -> new IllegalStateException("Aucun run réussi"));

        log.info("🚀 Déploiement du meilleur run #{} - {:.2f}% accuracy",
                 bestRun.getRunNumber(),
                 bestRun.getAccuracy() * 100);

        // 2. Charger le modèle depuis le path
        Booster booster = XGBoost.loadModel(bestRun.getModelPath());

        // 3. Créer une nouvelle version de modèle
        ModelVersion modelVersion = versioningService.saveXGBoostModel(
            booster,
            experiment.getCompany(),
            "bank_matching_xgboost",
            createMetadataFromRun(bestRun)
        );

        // 4. Créer l'entité MLModel
        MLModel model = MLModel.builder()
            .company(experiment.getCompany())
            .modelName("bank_matching_xgboost")
            .modelVersion(modelVersion.getVersion())
            .algorithm("XGBoost")
            .accuracy(BigDecimal.valueOf(bestRun.getAccuracy()))
            .f1Score(BigDecimal.valueOf(bestRun.getF1Score()))
            .precision(BigDecimal.valueOf(bestRun.getPrecision()))
            .recall(BigDecimal.valueOf(bestRun.getRecall()))
            .modelPath(modelVersion.getModelPath())
            .hyperparameters(bestRun.getHyperparameters())
            .status(MLModelStatus.DEPLOYED)
            .isActive(true)
            .build();

        model.deploy();

        MLModel savedModel = modelRepository.save(model);

        // 5. Marquer le run comme déployé
        experimentService.markAsDeployed(bestRun, savedModel);

        log.info("✅ Modèle déployé: {} - {}",
                 savedModel.getModelVersion(),
                 savedModel.getSummary());

        return savedModel;
    }
}
```

---

## 📊 Requêtes Utiles

### Lister toutes les expériences d'une company
```java
List<MLExperiment> experiments = experimentService.listExperiments(company);

for (MLExperiment exp : experiments) {
    log.info("📊 {} - {} runs - Best: {:.2f}% - {}",
             exp.getExperimentName(),
             exp.getTotalRuns(),
             exp.getBestAccuracy() * 100,
             exp.getStatus());
}
```

### Comparer deux runs
```java
MLExperimentRun run1 = experiment.getRuns().get(0);
MLExperimentRun run2 = experiment.getRuns().get(1);

RunComparison comparison = experimentService.compareRuns(run1, run2);

log.info("Comparaison Run #{} vs Run #{}:", run1.getRunNumber(), run2.getRunNumber());
log.info("  Accuracy diff: {:.4f}", comparison.getAccuracyDiff());
log.info("  F1 Score diff: {:.4f}", comparison.getF1ScoreDiff());
```

### Générer un rapport complet
```java
ExperimentReport report = experimentService.generateReport(experiment);

log.info("📈 RAPPORT EXPÉRIENCE:");
log.info("  Total runs: {}", report.getTotalRuns());
log.info("  Successful: {} ({:.0f}%)",
         report.getSuccessfulRuns(),
         (report.getSuccessfulRuns() * 100.0 / report.getTotalRuns()));
log.info("  Failed: {}", report.getFailedRuns());
log.info("  Best accuracy: {:.2f}%", report.getMaxAccuracy() * 100);
log.info("  Average accuracy: {:.2f}%", report.getAverageAccuracy() * 100);
log.info("  Min accuracy: {:.2f}%", report.getMinAccuracy() * 100);
log.info("  Std dev: {:.4f}", report.getStdDevAccuracy());
```

---

## 🎯 Best Practices

### 1. Nommage des Expériences
```java
✅ GOOD:
- "Grid Search XGBoost max_depth"
- "Comparaison XGBoost vs LightGBM"
- "Optimisation SMOTE ratio"
- "A/B Test: TF-IDF vs Word2Vec"

❌ BAD:
- "Test 1"
- "Expérience ML"
- "abc"
```

### 2. Tags pour Organisation
```java
// Marquer les runs importants
experimentService.addTagToRun(run, "baseline");          // Run de référence
experimentService.addTagToRun(run, "best");              // Meilleur run
experimentService.addTagToRun(run, "production");        // Déployé en prod
experimentService.addTagToRun(run, "overfitting");       // Problème détecté
experimentService.addTagToRun(run, "needs_review");      // À vérifier
```

### 3. Logger Toutes les Informations
```java
// ✅ Logger métriques additionnelles
config.setMetrics(Map.of(
    "confusion_matrix", confusionMatrix,
    "feature_importance", featureImportance,
    "training_loss", trainingLoss,
    "validation_loss", validationLoss
));

// ✅ Ajouter des notes
config.setNotes("Augmentation des données avec SMOTE ratio=1.5. " +
                "Résultats prometteurs mais risque de surajustement.");
```

### 4. Gestion des Erreurs
```java
try {
    ModelEvaluationMetrics metrics = xgboostService.trainAndEvaluate(
        trainingData, hyperparams
    );

    experimentService.logRun(experiment, hyperparams,
                            metrics.getAccuracy(), ...);

} catch (Exception e) {
    // Créer un run échoué pour garder une trace
    MLExperimentRun failedRun = experimentService.startRun(
        experiment, hyperparams, "Failed Run"
    );
    experimentService.failRun(failedRun, e.getMessage());
}
```

---

## 🔍 Debugging & Monitoring

### Afficher l'état d'une expérience en cours
```java
MLExperiment experiment = // ...

log.info("📊 EXPÉRIENCE EN COURS:");
log.info("  Nom: {}", experiment.getExperimentName());
log.info("  Status: {}", experiment.getStatus());
log.info("  Runs: {}/{} completed",
         experiment.getSuccessfulRuns().size(),
         experiment.getTotalRuns());

if (experiment.getBestRun() != null) {
    log.info("  Meilleur run actuel: #{} - {:.2f}%",
             experiment.getBestRun().getRunNumber(),
             experiment.getBestAccuracy() * 100);
}
```

### Afficher les détails d'un run
```java
MLExperimentRun run = // ...

log.info("🔹 RUN #{}", run.getRunNumber());
log.info("  Hyperparams: {}", run.getHyperparameters());
log.info("  Accuracy: {:.2f}%", run.getAccuracy() * 100);
log.info("  F1 Score: {:.2f}%", run.getF1Score() * 100);
log.info("  Precision: {:.2f}%", run.getPrecision() * 100);
log.info("  Recall: {:.2f}%", run.getRecall() * 100);
log.info("  Duration: {}s", run.getDurationSeconds());
log.info("  Status: {}", run.getStatus());

if (run.getTags() != null) {
    log.info("  Tags: {}", String.join(", ", run.getTags()));
}
```

---

## 📈 Export des Résultats

### Export CSV pour analyse
```java
public void exportExperimentToCSV(MLExperiment experiment, String filePath) {
    List<MLExperimentRun> runs = experimentService.listRuns(experiment);

    try (PrintWriter writer = new PrintWriter(filePath)) {
        // Header
        writer.println("run_number,accuracy,f1_score,precision,recall,status,hyperparams");

        // Données
        for (MLExperimentRun run : runs) {
            writer.printf("%d,%.4f,%.4f,%.4f,%.4f,%s,%s%n",
                run.getRunNumber(),
                run.getAccuracy(),
                run.getF1Score(),
                run.getPrecision(),
                run.getRecall(),
                run.getStatus(),
                run.getHyperparameters().toString()
            );
        }
    }

    log.info("✅ Expérience exportée vers {}", filePath);
}
```

---

## ✅ Checklist de Mise en Production

Avant de déployer un run en production:

- [ ] L'expérience est terminée (`status = COMPLETED`)
- [ ] Le meilleur run a une accuracy > seuil minimum (ex: 90%)
- [ ] Le run a été validé manuellement
- [ ] Les hyperparamètres sont documentés
- [ ] Le modèle est sauvegardé avec versioning
- [ ] Les métriques sont satisfaisantes (precision, recall, F1)
- [ ] Pas de signe d'overfitting (train_accuracy ~= val_accuracy)
- [ ] Les tags sont bien ajoutés (`production_candidate`, `validated`)
- [ ] Le run est marqué comme déployé via `markAsDeployed()`

---

**Vous avez maintenant un système complet de tracking d'expériences ML !** 🎉
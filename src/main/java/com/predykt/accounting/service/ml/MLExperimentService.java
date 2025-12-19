package com.predykt.accounting.service.ml;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.User;
import com.predykt.accounting.domain.entity.ml.MLExperiment;
import com.predykt.accounting.domain.entity.ml.MLExperimentRun;
import com.predykt.accounting.domain.entity.ml.MLModel;
import com.predykt.accounting.repository.ml.MLExperimentRepository;
import com.predykt.accounting.repository.ml.MLExperimentRunRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de gestion des expériences ML
 *
 * Workflow typique:
 * 1. createExperiment() - Créer une nouvelle expérience
 * 2. logRun() - Logger plusieurs runs avec différents hyperparams
 * 3. getBestRun() - Trouver le meilleur run
 * 4. completeExperiment() - Terminer l'expérience
 * 5. Optional: deployBestRun() - Déployer le meilleur run
 *
 * Exemples d'utilisation:
 * - Grid search sur hyperparamètres
 * - Comparaison d'algorithmes
 * - A/B testing de features
 * - Tracking d'optimisation progressive
 *
 * @author PREDYKT ML Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MLExperimentService {

    private final MLExperimentRepository experimentRepository;
    private final MLExperimentRunRepository runRepository;

    /**
     * Crée une nouvelle expérience
     *
     * @param company Entreprise
     * @param experimentName Nom de l'expérience
     * @param description Description
     * @param experimentType Type (GRID_SEARCH, MANUAL, etc.)
     * @param user Utilisateur créateur
     * @return Expérience créée
     */
    @Transactional
    public MLExperiment createExperiment(
            Company company,
            String experimentName,
            String description,
            String experimentType,
            User user) {

        log.info("🔬 [MLExperiment] Creating experiment: {}", experimentName);

        MLExperiment experiment = MLExperiment.builder()
            .company(company)
            .experimentName(experimentName)
            .description(description)
            .experimentType(experimentType)
            .status("RUNNING")
            .startedAt(LocalDateTime.now())
            .createdByUser(user)
            .tags(new ArrayList<>())
            .runs(new ArrayList<>())
            .build();

        MLExperiment saved = experimentRepository.save(experiment);

        log.info("✅ [MLExperiment] Experiment created - ID: {}", saved.getId());

        return saved;
    }

    /**
     * Log un run avec hyperparamètres et résultats
     *
     * @param experiment Expérience
     * @param hyperparameters Hyperparamètres testés
     * @param accuracy Accuracy obtenue
     * @param f1Score F1 score
     * @param precision Precision
     * @param recall Recall
     * @return Run créé
     */
    @Transactional
    public MLExperimentRun logRun(
            MLExperiment experiment,
            Map<String, Object> hyperparameters,
            Double accuracy,
            Double f1Score,
            Double precision,
            Double recall) {

        int runNumber = experiment.getRuns().size() + 1;

        log.info("📊 [MLExperiment] Logging run #{} - Accuracy: {:.2f}%",
                runNumber, accuracy != null ? accuracy * 100 : 0.0);

        MLExperimentRun run = MLExperimentRun.builder()
            .experiment(experiment)
            .runNumber(runNumber)
            .hyperparameters(hyperparameters)
            .accuracy(accuracy)
            .f1Score(f1Score)
            .precision(precision)
            .recall(recall)
            .status("COMPLETED")
            .startedAt(LocalDateTime.now().minusSeconds(1))  // Simuler 1 seconde
            .completedAt(LocalDateTime.now())
            .durationSeconds(1)
            .build();

        experiment.addRun(run);

        MLExperimentRun saved = runRepository.save(run);
        experimentRepository.save(experiment);

        log.info("✅ [MLExperiment] Run #{} logged", runNumber);

        return saved;
    }

    /**
     * Log un run avec résultats complets (version étendue)
     *
     * @param experiment Expérience
     * @param runConfig Configuration du run
     * @return Run créé
     */
    @Transactional
    public MLExperimentRun logRunWithConfig(
            MLExperiment experiment,
            RunConfiguration runConfig) {

        int runNumber = experiment.getRuns().size() + 1;

        log.info("📊 [MLExperiment] Logging detailed run #{} - {}",
                runNumber, runConfig.getRunName() != null ? runConfig.getRunName() : "");

        LocalDateTime startTime = runConfig.getStartedAt() != null ? runConfig.getStartedAt() : LocalDateTime.now().minusSeconds(1);
        LocalDateTime endTime = LocalDateTime.now();
        int duration = (int) java.time.Duration.between(startTime, endTime).getSeconds();

        MLExperimentRun run = MLExperimentRun.builder()
            .experiment(experiment)
            .runNumber(runNumber)
            .runName(runConfig.getRunName())
            .hyperparameters(runConfig.getHyperparameters())
            .accuracy(runConfig.getAccuracy())
            .f1Score(runConfig.getF1Score())
            .precision(runConfig.getPrecision())
            .recall(runConfig.getRecall())
            .rocAuc(runConfig.getRocAuc())
            .metrics(runConfig.getMetrics())
            .trainingSamples(runConfig.getTrainingSamples())
            .validationSamples(runConfig.getValidationSamples())
            .testSamples(runConfig.getTestSamples())
            .tags(runConfig.getTags())
            .notes(runConfig.getNotes())
            .modelPath(runConfig.getModelPath())
            .status("COMPLETED")
            .startedAt(startTime)
            .completedAt(endTime)
            .durationSeconds(duration)
            .build();

        experiment.addRun(run);

        MLExperimentRun saved = runRepository.save(run);
        experimentRepository.save(experiment);

        log.info("✅ [MLExperiment] Detailed run #{} logged", runNumber);

        return saved;
    }

    /**
     * Démarre un run (pour tracking temps réel)
     *
     * @param experiment Expérience
     * @param hyperparameters Hyperparamètres
     * @param runName Nom du run
     * @return Run démarré
     */
    @Transactional
    public MLExperimentRun startRun(
            MLExperiment experiment,
            Map<String, Object> hyperparameters,
            String runName) {

        int runNumber = experiment.getRuns().size() + 1;

        log.info("🚀 [MLExperiment] Starting run #{} - {}", runNumber, runName);

        MLExperimentRun run = MLExperimentRun.builder()
            .experiment(experiment)
            .runNumber(runNumber)
            .runName(runName)
            .hyperparameters(hyperparameters)
            .status("RUNNING")
            .startedAt(LocalDateTime.now())
            .build();

        experiment.addRun(run);

        MLExperimentRun saved = runRepository.save(run);
        experimentRepository.save(experiment);

        return saved;
    }

    /**
     * Termine un run avec résultats
     *
     * @param run Run à terminer
     * @param accuracy Accuracy
     * @param f1Score F1 score
     * @param precision Precision
     * @param recall Recall
     */
    @Transactional
    public void completeRun(
            MLExperimentRun run,
            Double accuracy,
            Double f1Score,
            Double precision,
            Double recall) {

        log.info("✅ [MLExperiment] Completing run #{} - Accuracy: {:.2f}%",
                run.getRunNumber(), accuracy != null ? accuracy * 100 : 0.0);

        run.complete(accuracy, f1Score, precision, recall);
        runRepository.save(run);
    }

    /**
     * Marque un run comme échoué
     *
     * @param run Run
     * @param errorMessage Message d'erreur
     */
    @Transactional
    public void failRun(MLExperimentRun run, String errorMessage) {
        log.warn("❌ [MLExperiment] Run #{} failed - {}", run.getRunNumber(), errorMessage);

        run.fail(errorMessage);
        runRepository.save(run);
    }

    /**
     * Termine une expérience
     *
     * @param experiment Expérience
     */
    @Transactional
    public void completeExperiment(MLExperiment experiment) {
        log.info("🏁 [MLExperiment] Completing experiment: {}", experiment.getExperimentName());

        experiment.complete();
        experimentRepository.save(experiment);

        log.info("✅ [MLExperiment] Experiment completed - Best: {:.2f}%",
                experiment.getBestAccuracy() != null ? experiment.getBestAccuracy() * 100 : 0.0);
    }

    /**
     * Annule une expérience
     *
     * @param experiment Expérience
     */
    @Transactional
    public void cancelExperiment(MLExperiment experiment) {
        log.info("🚫 [MLExperiment] Cancelling experiment: {}", experiment.getExperimentName());

        experiment.cancel();
        experimentRepository.save(experiment);
    }

    /**
     * Récupère le meilleur run d'une expérience (par accuracy)
     *
     * @param experiment Expérience
     * @return Meilleur run
     */
    public Optional<MLExperimentRun> getBestRun(MLExperiment experiment) {
        return runRepository.findBestRunByAccuracy(experiment);
    }

    /**
     * Récupère le meilleur run (par F1 score)
     *
     * @param experiment Expérience
     * @return Meilleur run
     */
    public Optional<MLExperimentRun> getBestRunByF1(MLExperiment experiment) {
        return runRepository.findBestRunByF1Score(experiment);
    }

    /**
     * Liste tous les runs d'une expérience
     *
     * @param experiment Expérience
     * @return Liste des runs
     */
    public List<MLExperimentRun> listRuns(MLExperiment experiment) {
        return runRepository.findByExperimentOrderByRunNumberAsc(experiment);
    }

    /**
     * Liste les runs réussis triés par accuracy
     *
     * @param experiment Expérience
     * @return Liste des runs réussis
     */
    public List<MLExperimentRun> listSuccessfulRuns(MLExperiment experiment) {
        return runRepository.findSuccessfulRunsByAccuracy(experiment);
    }

    /**
     * Compare deux runs
     *
     * @param run1 Premier run
     * @param run2 Deuxième run
     * @return Comparaison
     */
    public RunComparison compareRuns(MLExperimentRun run1, MLExperimentRun run2) {
        RunComparison comparison = new RunComparison();
        comparison.setRun1(run1);
        comparison.setRun2(run2);
        comparison.setAccuracyDiff(
            (run2.getAccuracy() != null ? run2.getAccuracy() : 0.0) -
            (run1.getAccuracy() != null ? run1.getAccuracy() : 0.0)
        );
        comparison.setF1ScoreDiff(
            (run2.getF1Score() != null ? run2.getF1Score() : 0.0) -
            (run1.getF1Score() != null ? run1.getF1Score() : 0.0)
        );

        return comparison;
    }

    /**
     * Génère un rapport d'expérience
     *
     * @param experiment Expérience
     * @return Rapport
     */
    public ExperimentReport generateReport(MLExperiment experiment) {
        List<MLExperimentRun> runs = listRuns(experiment);
        List<MLExperimentRun> successful = runs.stream()
            .filter(MLExperimentRun::isSuccessful)
            .toList();

        ExperimentReport report = new ExperimentReport();
        report.setExperiment(experiment);
        report.setTotalRuns(runs.size());
        report.setSuccessfulRuns(successful.size());
        report.setFailedRuns((int) runs.stream().filter(MLExperimentRun::isFailed).count());

        if (!successful.isEmpty()) {
            report.setBestRun(getBestRun(experiment).orElse(null));
            report.setAverageAccuracy(runRepository.calculateAverageAccuracy(experiment));

            DoubleSummaryStatistics stats = successful.stream()
                .filter(r -> r.getAccuracy() != null)
                .mapToDouble(MLExperimentRun::getAccuracy)
                .summaryStatistics();

            report.setMinAccuracy(stats.getMin());
            report.setMaxAccuracy(stats.getMax());
            report.setStdDevAccuracy(calculateStdDev(successful));
        }

        return report;
    }

    /**
     * Marque un run comme déployé
     *
     * @param run Run
     * @param model Modèle déployé
     */
    @Transactional
    public void markAsDeployed(MLExperimentRun run, MLModel model) {
        log.info("🚀 [MLExperiment] Marking run #{} as deployed", run.getRunNumber());

        run.setDeployedAsModel(model);
        run.addTag("deployed");
        runRepository.save(run);

        MLExperiment experiment = run.getExperiment();
        experiment.setDeployedModel(model);
        experimentRepository.save(experiment);
    }

    /**
     * Ajoute un tag à un run
     *
     * @param run Run
     * @param tag Tag
     */
    @Transactional
    public void addTagToRun(MLExperimentRun run, String tag) {
        run.addTag(tag);
        runRepository.save(run);
    }

    /**
     * Liste toutes les expériences d'une company
     *
     * @param company Company
     * @return Liste des expériences
     */
    public List<MLExperiment> listExperiments(Company company) {
        return experimentRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    /**
     * Liste les expériences en cours
     *
     * @param company Company
     * @return Liste des expériences en cours
     */
    public List<MLExperiment> listRunningExperiments(Company company) {
        return experimentRepository.findRunningExperiments(company);
    }

    /**
     * Récupère une expérience par nom
     *
     * @param company Company
     * @param experimentName Nom de l'expérience
     * @return Expérience
     */
    public Optional<MLExperiment> findByName(Company company, String experimentName) {
        return experimentRepository.findByCompanyAndExperimentName(company, experimentName);
    }

    // ========== HELPER METHODS ==========

    private double calculateStdDev(List<MLExperimentRun> runs) {
        if (runs.size() < 2) return 0.0;

        double mean = runs.stream()
            .filter(r -> r.getAccuracy() != null)
            .mapToDouble(MLExperimentRun::getAccuracy)
            .average()
            .orElse(0.0);

        double variance = runs.stream()
            .filter(r -> r.getAccuracy() != null)
            .mapToDouble(r -> Math.pow(r.getAccuracy() - mean, 2))
            .average()
            .orElse(0.0);

        return Math.sqrt(variance);
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class RunConfiguration {
        private String runName;
        private Map<String, Object> hyperparameters;
        private Double accuracy;
        private Double f1Score;
        private Double precision;
        private Double recall;
        private Double rocAuc;
        private Map<String, Object> metrics;
        private Integer trainingSamples;
        private Integer validationSamples;
        private Integer testSamples;
        private List<String> tags;
        private String notes;
        private String modelPath;
        private LocalDateTime startedAt;
    }

    @Data
    public static class RunComparison {
        private MLExperimentRun run1;
        private MLExperimentRun run2;
        private Double accuracyDiff;
        private Double f1ScoreDiff;
    }

    @Data
    public static class ExperimentReport {
        private MLExperiment experiment;
        private Integer totalRuns;
        private Integer successfulRuns;
        private Integer failedRuns;
        private MLExperimentRun bestRun;
        private Double averageAccuracy;
        private Double minAccuracy;
        private Double maxAccuracy;
        private Double stdDevAccuracy;
    }
}
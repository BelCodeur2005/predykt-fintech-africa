package com.predykt.accounting.service.ml.advanced.deployment;

import com.predykt.accounting.domain.entity.Company;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de re-entraînement automatique des modèles
 * Architecture: advanced/deployment/
 *
 * Fonctionnalités:
 * 1. Re-entraînement périodique (hebdomadaire/mensuel)
 * 2. Re-entraînement déclenché par drift
 * 3. Re-entraînement déclenché par dégradation performance
 * 4. Validation avant déploiement
 * 5. Rollback automatique si nouveau modèle moins bon
 *
 * Stratégies de déclenchement:
 * - Time-based: Tous les X jours
 * - Data-based: Après N nouvelles prédictions
 * - Performance-based: Si accuracy drop > threshold
 * - Drift-based: Si data drift détecté
 *
 * @author PREDYKT ML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRetrainingService {

    private final ModelMonitoringService monitoringService;
    private final ModelVersioningService versioningService;

    // Tracking des re-entraînements
    private final Map<String, RetrainingStatus> retrainingHistory = new ConcurrentHashMap<>();

    // Seuils par défaut
    private static final double MIN_ACCURACY_IMPROVEMENT = 0.01;  // 1%
    private static final double MAX_ACCURACY_DROP = 0.02;         // 2%
    private static final int MIN_NEW_DATA_SAMPLES = 500;

    /**
     * Vérifie si re-entraînement nécessaire
     *
     * @param company Société
     * @param modelType Type de modèle
     * @return RetrainingDecision avec recommandation
     */
    public RetrainingDecision shouldRetrain(Company company, String modelType) {

        RetrainingDecision decision = new RetrainingDecision();
        decision.setCompanyId(company.getId());
        decision.setModelType(modelType);
        decision.setEvaluatedAt(LocalDateTime.now());

        // Récupérer statistiques du modèle
        ModelMonitoringService.ModelStatistics stats =
            monitoringService.getModelStatistics(company.getId(), modelType);

        // Critère 1: Data drift détecté
        ModelMonitoringService.DriftDetectionResult driftResult =
            monitoringService.detectDrift(stats);

        if (driftResult.isDriftDetected()) {
            decision.setShouldRetrain(true);
            decision.setReason("DATA_DRIFT");
            decision.setPriority("HIGH");
            decision.setDriftScore(driftResult.getDriftScore());

            log.info("🔄 [AutoRetraining] Retraining recommended - Reason: Data drift detected");

            return decision;
        }

        // Critère 2: Performance dégradée
        ModelMonitoringService.PerformanceMetrics recentMetrics =
            monitoringService.calculateRecentMetrics(stats, 100);

        double accuracyDrop = stats.getBaselineAccuracy() - recentMetrics.getAccuracy();

        if (accuracyDrop > MAX_ACCURACY_DROP) {
            decision.setShouldRetrain(true);
            decision.setReason("PERFORMANCE_DEGRADATION");
            decision.setPriority("HIGH");
            decision.setAccuracyDrop(accuracyDrop);

            log.info("🔄 [AutoRetraining] Retraining recommended - Reason: Performance drop {:.4f}",
                     accuracyDrop);

            return decision;
        }

        // Critère 3: Assez de nouvelles données
        RetrainingStatus lastRetraining = getLastRetrainingStatus(company.getId(), modelType);

        if (lastRetraining != null) {
            long newDataCount = stats.getPredictionCount() - lastRetraining.getTrainingSamplesCount();

            if (newDataCount >= MIN_NEW_DATA_SAMPLES) {
                decision.setShouldRetrain(true);
                decision.setReason("NEW_DATA_AVAILABLE");
                decision.setPriority("MEDIUM");
                decision.setNewDataCount(newDataCount);

                log.info("🔄 [AutoRetraining] Retraining recommended - Reason: {} new samples",
                         newDataCount);

                return decision;
            }
        }

        // Critère 4: Time-based (dernière entraînement > 30 jours)
        if (lastRetraining != null) {
            LocalDateTime lastTrainingDate = lastRetraining.getCompletedAt();
            long daysSinceLastTraining = java.time.temporal.ChronoUnit.DAYS.between(
                lastTrainingDate, LocalDateTime.now()
            );

            if (daysSinceLastTraining > 30) {
                decision.setShouldRetrain(true);
                decision.setReason("TIME_BASED");
                decision.setPriority("LOW");
                decision.setDaysSinceLastTraining(daysSinceLastTraining);

                log.info("🔄 [AutoRetraining] Retraining recommended - Reason: {} days since last training",
                         daysSinceLastTraining);

                return decision;
            }
        }

        // Pas de retraining nécessaire
        decision.setShouldRetrain(false);
        decision.setReason("NO_TRIGGER");

        return decision;
    }

    /**
     * Exécute re-entraînement automatique
     *
     * @param company Société
     * @param modelType Type de modèle
     * @param X_train Nouvelles données training
     * @param y_train Nouveaux labels
     * @param X_val Données validation
     * @param y_val Labels validation
     * @return RetrainingResult avec nouveau modèle
     */
    public RetrainingResult executeRetraining(
            Company company,
            String modelType,
            double[][] X_train,
            int[] y_train,
            double[][] X_val,
            int[] y_val) {

        long startTime = System.currentTimeMillis();

        log.info("🔄 [AutoRetraining] Starting automatic retraining - Company: {}, Model: {}",
                 company.getName(), modelType);

        RetrainingStatus status = new RetrainingStatus();
        status.setCompanyId(company.getId());
        status.setModelType(modelType);
        status.setStartedAt(LocalDateTime.now());
        status.setTrainingSamplesCount(X_train.length);
        status.setValidationSamplesCount(X_val.length);
        status.setStatus("IN_PROGRESS");

        try {
            // TODO: Appeler service d'entraînement approprié
            // Pour l'instant, simulation

            // Simuler entraînement
            Thread.sleep(1000);

            // Évaluer nouveau modèle sur validation
            double newAccuracy = 0.93 + Math.random() * 0.04;  // Simulé
            double newF1Score = 0.92 + Math.random() * 0.04;   // Simulé

            // Récupérer baseline actuelle
            ModelMonitoringService.ModelStatistics stats =
                monitoringService.getModelStatistics(company.getId(), modelType);

            double baselineAccuracy = stats.getBaselineAccuracy();

            // Décision: déployer ou rollback
            boolean shouldDeploy = newAccuracy >= baselineAccuracy - MAX_ACCURACY_DROP;

            if (shouldDeploy) {
                log.info("✅ [AutoRetraining] New model APPROVED - Accuracy: {:.4f} (baseline: {:.4f})",
                         newAccuracy, baselineAccuracy);

                // Déployer nouveau modèle (sauvegarder nouvelle version)
                // TODO: versioningService.saveXGBoostModel(...)

                // Mettre à jour baseline
                monitoringService.resetBaseline(company.getId(), modelType, newAccuracy, newF1Score);

                status.setStatus("DEPLOYED");
                status.setNewAccuracy(newAccuracy);
                status.setNewF1Score(newF1Score);

            } else {
                log.warn("⚠️ [AutoRetraining] New model REJECTED - Accuracy: {:.4f} < baseline: {:.4f}",
                         newAccuracy, baselineAccuracy);

                status.setStatus("REJECTED");
                status.setNewAccuracy(newAccuracy);
                status.setNewF1Score(newF1Score);
                status.setRejectionReason("Performance below baseline");
            }

            status.setCompletedAt(LocalDateTime.now());

            // Enregistrer dans historique
            String key = getRetrainingKey(company.getId(), modelType);
            retrainingHistory.put(key, status);

            long retrainingTime = System.currentTimeMillis() - startTime;

            log.info("🎯 [AutoRetraining] Retraining completed - Status: {}, Time: {}ms",
                     status.getStatus(), retrainingTime);

            RetrainingResult result = new RetrainingResult();
            result.setStatus(status);
            result.setDeployed(shouldDeploy);
            result.setRetrainingTimeMs(retrainingTime);

            return result;

        } catch (Exception e) {
            log.error("❌ [AutoRetraining] Retraining failed: {}", e.getMessage(), e);

            status.setStatus("FAILED");
            status.setCompletedAt(LocalDateTime.now());
            status.setErrorMessage(e.getMessage());

            RetrainingResult result = new RetrainingResult();
            result.setStatus(status);
            result.setDeployed(false);

            return result;
        }
    }

    /**
     * Scheduled task: vérifie périodiquement si retraining nécessaire
     * Exécuté tous les dimanches à 2h du matin
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void scheduledRetrainingCheck() {
        log.info("🔄 [AutoRetraining] Starting scheduled retraining check...");

        // TODO: Parcourir toutes les companies/modèles actifs
        // Pour chaque modèle:
        //   1. Vérifier si retraining nécessaire (shouldRetrain)
        //   2. Si oui, récupérer nouvelles données
        //   3. Exécuter retraining (executeRetraining)

        log.info("✅ [AutoRetraining] Scheduled check completed");
    }

    /**
     * Obtient statut du dernier re-entraînement
     */
    public RetrainingStatus getLastRetrainingStatus(Long companyId, String modelType) {
        String key = getRetrainingKey(companyId, modelType);
        return retrainingHistory.get(key);
    }

    /**
     * Liste historique des re-entraînements
     */
    public List<RetrainingStatus> getRetrainingHistory(Long companyId, String modelType) {
        // TODO: Implémenter stockage persistant dans database
        // Pour l'instant, retourne juste le dernier

        RetrainingStatus last = getLastRetrainingStatus(companyId, modelType);

        if (last != null) {
            return Collections.singletonList(last);
        }

        return Collections.emptyList();
    }

    private String getRetrainingKey(Long companyId, String modelType) {
        return companyId + "_" + modelType;
    }

    // ========== CLASSES DE DONNÉES ==========

    @Data
    public static class RetrainingDecision {
        private Long companyId;
        private String modelType;
        private boolean shouldRetrain;
        private String reason;
        private String priority;  // HIGH, MEDIUM, LOW
        private LocalDateTime evaluatedAt;
        private double driftScore;
        private double accuracyDrop;
        private long newDataCount;
        private long daysSinceLastTraining;
    }

    @Data
    public static class RetrainingStatus {
        private Long companyId;
        private String modelType;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String status;  // IN_PROGRESS, DEPLOYED, REJECTED, FAILED
        private long trainingSamplesCount;
        private long validationSamplesCount;
        private double newAccuracy;
        private double newF1Score;
        private String rejectionReason;
        private String errorMessage;
    }

    @Data
    public static class RetrainingResult {
        private RetrainingStatus status;
        private boolean deployed;
        private long retrainingTimeMs;
    }
}

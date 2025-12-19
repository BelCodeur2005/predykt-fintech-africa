package com.predykt.accounting.service.ml;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.ml.MLPredictionLog;
import com.predykt.accounting.repository.ml.MLPredictionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service de gestion des logs de predictions ML
 *
 * Responsabilites:
 * 1. Feedback loop - Mise a jour actualOutcome apres validation utilisateur
 * 2. Calcul de performance reelle des modeles (drift detection)
 * 3. Historique des predictions pour analyse
 * 4. Declenchement auto du re-training si performance degrade
 *
 * Ce service est CRITIQUE pour l'amelioration continue du systeme ML.
 * Sans feedback, le modele ne peut pas detecter de drift ni s'ameliorer.
 *
 * @author PREDYKT ML Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLPredictionLogService {

    private final MLPredictionLogRepository predictionLogRepository;
    private final MLTrainingService trainingService;

    // Seuils pour detection de degradation
    private static final double MIN_ACCEPTABLE_ACCURACY = 0.85;  // 85%
    private static final double MAX_ACCURACY_DROP = 0.10;        // 10%

    /**
     * Met a jour le resultat reel d'une prediction apres validation utilisateur
     *
     * @param predictionLogId ID du log de prediction
     * @param wasApplied Si la suggestion a ete appliquee par l'utilisateur
     * @param wasCorrect Si la prediction etait correcte (match valide)
     * @param userNotes Notes optionnelles de l'utilisateur
     */
    @Transactional
    public void updatePredictionOutcome(
            Long predictionLogId,
            boolean wasApplied,
            boolean wasCorrect,
            String userNotes) {

        log.info("📝 [PredictionLog] Mise a jour feedback - ID: {}, Applied: {}, Correct: {}",
                 predictionLogId, wasApplied, wasCorrect);

        MLPredictionLog predictionLog = predictionLogRepository.findById(predictionLogId)
            .orElseThrow(() -> new RuntimeException("Prediction log non trouve: " + predictionLogId));

        // Mise a jour des champs
        predictionLog.setActualOutcome(wasApplied);
        predictionLog.setWasCorrect(wasCorrect);
        predictionLog.setValidatedAt(LocalDateTime.now());

        if (userNotes != null && !userNotes.isEmpty()) {
            // Ajouter notes au Map features (JSON)
            Map<String, Object> features = predictionLog.getFeatures();
            if (features == null) {
                features = new HashMap<>();
            }
            features.put("user_feedback_notes", userNotes);
            predictionLog.setFeatures(features);
        }

        predictionLogRepository.save(predictionLog);

        // Verifier si re-training necessaire (drift detection)
        checkAndTriggerRetrainingIfNeeded(predictionLog.getCompany());
    }

    /**
     * Met a jour le feedback en batch (plusieurs predictions)
     */
    @Transactional
    public void updatePredictionOutcomeBatch(List<Long> predictionLogIds, boolean wasCorrect) {
        log.info("📝 [PredictionLog] Mise a jour feedback batch - {} predictions", predictionLogIds.size());

        for (Long id : predictionLogIds) {
            try {
                updatePredictionOutcome(id, true, wasCorrect, null);
            } catch (Exception e) {
                log.error("Erreur mise a jour prediction {}: {}", id, e.getMessage());
            }
        }
    }

    /**
     * Recupere l'historique des predictions pour une entreprise
     *
     * @param company Entreprise
     * @param since Date de debut (nullable)
     * @return Liste des predictions avec feedback
     */
    @Transactional(readOnly = true)
    public List<MLPredictionLog> getPredictionHistory(Company company, LocalDateTime since) {
        final LocalDateTime sinceDate = (since == null) ? LocalDateTime.now().minusMonths(1) : since;

        log.debug("📊 [PredictionLog] Recuperation historique company {} depuis {}",
                  company.getId(), sinceDate);

        // Note: Il faudrait ajouter une methode au repository pour filtrer par date
        // Pour l'instant, on recupere tout et on filtre
        return predictionLogRepository.findAll().stream()
            .filter(p -> p.getCompany().getId().equals(company.getId()))
            .filter(p -> p.getPredictedAt().isAfter(sinceDate))
            .sorted(Comparator.comparing(MLPredictionLog::getPredictedAt).reversed())
            .toList();
    }

    /**
     * Calcule les metriques de performance du modele
     *
     * @param company Entreprise
     * @return Map avec accuracy, precision, recall, f1, latency, drift
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calculateModelPerformance(Company company) {
        log.info("📊 [PredictionLog] Calcul performance modele - Company: {}", company.getId());

        LocalDateTime since = LocalDateTime.now().minusDays(30);  // 30 derniers jours

        Map<String, Object> metrics = new HashMap<>();

        // 1. Accuracy reelle (predictions validees)
        Double realAccuracy = predictionLogRepository.calculateRealWorldAccuracy(company, since);
        metrics.put("realWorldAccuracy", realAccuracy != null ? realAccuracy : 0.0);

        // 2. Nombre de predictions
        long totalPredictions = predictionLogRepository.countValidatedPredictions(company, since);
        long correctPredictions = predictionLogRepository.countCorrectPredictions(company, since);
        metrics.put("totalPredictions", totalPredictions);
        metrics.put("correctPredictions", correctPredictions);

        // 3. Latence moyenne
        Double avgLatency = predictionLogRepository.calculateAverageLatency(company, since);
        metrics.put("avgLatencyMs", avgLatency != null ? avgLatency : 0.0);

        // 4. Taux de validation (combien de predictions ont recu un feedback)
        List<MLPredictionLog> allPredictions = getPredictionHistory(company, since);
        long validatedCount = allPredictions.stream()
            .filter(p -> p.getActualOutcome() != null)
            .count();
        double validationRate = allPredictions.isEmpty() ? 0.0 :
            (double) validatedCount / allPredictions.size();
        metrics.put("validationRate", validationRate);

        // 5. Detection de drift (comparaison avec accuracy baseline)
        Map<String, Object> driftInfo = detectDrift(company, realAccuracy);
        metrics.put("drift", driftInfo);

        log.info("✅ [PredictionLog] Performance calculee - Accuracy: {:.2f}%, Validations: {}/{}",
                 (realAccuracy != null ? realAccuracy * 100 : 0.0),
                 validatedCount, allPredictions.size());

        return metrics;
    }

    /**
     * Detecte le drift de performance du modele
     *
     * @param company Entreprise
     * @param currentAccuracy Accuracy actuelle (nullable)
     * @return Map avec isDrift, dropPercentage, recommendation
     */
    private Map<String, Object> detectDrift(Company company, Double currentAccuracy) {
        Map<String, Object> drift = new HashMap<>();

        if (currentAccuracy == null) {
            drift.put("isDrift", false);
            drift.put("reason", "INSUFFICIENT_DATA");
            drift.put("message", "Pas assez de feedbacks pour detecter le drift");
            return drift;
        }

        // Recuperer l'accuracy baseline du modele actif
        Map<String, Object> trainingStats = trainingService.getTrainingStats(company);
        Object baselineObj = trainingStats.get("activeModelAccuracy");

        if (baselineObj == null) {
            drift.put("isDrift", false);
            drift.put("reason", "NO_BASELINE");
            drift.put("message", "Pas de modele actif pour comparaison");
            return drift;
        }

        double baselineAccuracy = ((Number) baselineObj).doubleValue();
        double accuracyDrop = baselineAccuracy - currentAccuracy;

        drift.put("baselineAccuracy", baselineAccuracy);
        drift.put("currentAccuracy", currentAccuracy);
        drift.put("accuracyDrop", accuracyDrop);

        // Critere 1: Accuracy actuelle trop faible
        if (currentAccuracy < MIN_ACCEPTABLE_ACCURACY) {
            drift.put("isDrift", true);
            drift.put("reason", "LOW_ACCURACY");
            drift.put("message", String.format(
                "Accuracy trop faible: %.2f%% < %.2f%%",
                currentAccuracy * 100, MIN_ACCEPTABLE_ACCURACY * 100));
            drift.put("severity", "HIGH");
            drift.put("recommendation", "RE_TRAINING_URGENT");
            return drift;
        }

        // Critere 2: Chute d'accuracy significative
        if (accuracyDrop > MAX_ACCURACY_DROP) {
            drift.put("isDrift", true);
            drift.put("reason", "ACCURACY_DROP");
            drift.put("message", String.format(
                "Chute d'accuracy significative: %.2f%% -> %.2f%% (-%2f%%)",
                baselineAccuracy * 100, currentAccuracy * 100, accuracyDrop * 100));
            drift.put("severity", "MEDIUM");
            drift.put("recommendation", "RE_TRAINING_RECOMMENDED");
            return drift;
        }

        // Pas de drift detecte
        drift.put("isDrift", false);
        drift.put("reason", "NORMAL");
        drift.put("message", "Performance stable");
        drift.put("severity", "NONE");

        return drift;
    }

    /**
     * Verifie la performance et declenche auto re-training si necessaire
     *
     * @param company Entreprise
     * @return true si re-training declenche, false sinon
     */
    @Transactional
    public boolean checkAndTriggerRetrainingIfNeeded(Company company) {
        log.debug("🔍 [PredictionLog] Verification besoin re-training - Company: {}", company.getId());

        // Calculer les metriques actuelles
        Map<String, Object> performance = calculateModelPerformance(company);

        @SuppressWarnings("unchecked")
        Map<String, Object> drift = (Map<String, Object>) performance.get("drift");

        if (drift == null) {
            return false;
        }

        Boolean isDrift = (Boolean) drift.get("isDrift");
        String severity = (String) drift.get("severity");

        if (Boolean.TRUE.equals(isDrift) && "HIGH".equals(severity)) {
            log.warn("⚠️  [PredictionLog] DRIFT CRITIQUE detecte - Company: {} - Declenchement re-training",
                     company.getId());

            // Declencher re-training asynchrone
            try {
                boolean success = trainingService.trainAndDeployIfBetter(company);
                if (success) {
                    log.info("✅ [PredictionLog] Re-training declenche avec succes");
                    return true;
                } else {
                    log.warn("⚠️  [PredictionLog] Re-training echoue ou pas de donnees suffisantes");
                }
            } catch (Exception e) {
                log.error("❌ [PredictionLog] Erreur declenchement re-training: {}", e.getMessage());
            }
        }

        return false;
    }

    /**
     * Obtient les statistiques de feedback pour une periode
     *
     * @param company Entreprise
     * @param startDate Date debut
     * @param endDate Date fin
     * @return Stats: total, validated, correct, incorrect, validation_rate
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFeedbackStats(Company company, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<MLPredictionLog> predictions = getPredictionHistory(company, start).stream()
            .filter(p -> p.getPredictedAt().isBefore(end))
            .toList();

        long total = predictions.size();
        long validated = predictions.stream()
            .filter(p -> p.getActualOutcome() != null)
            .count();
        long correct = predictions.stream()
            .filter(p -> Boolean.TRUE.equals(p.getWasCorrect()))
            .count();
        long incorrect = predictions.stream()
            .filter(p -> Boolean.FALSE.equals(p.getWasCorrect()))
            .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("period", Map.of("start", startDate, "end", endDate));
        stats.put("totalPredictions", total);
        stats.put("validatedPredictions", validated);
        stats.put("correctPredictions", correct);
        stats.put("incorrectPredictions", incorrect);
        stats.put("pendingFeedback", total - validated);
        stats.put("validationRate", total > 0 ? (double) validated / total : 0.0);
        stats.put("accuracyRate", validated > 0 ? (double) correct / validated : 0.0);

        return stats;
    }

    /**
     * Nettoie les anciens logs (retention: 6 mois)
     * A appeler periodiquement (cron job)
     */
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);

        log.info("🧹 [PredictionLog] Nettoyage logs avant {}", cutoffDate);

        List<MLPredictionLog> oldLogs = predictionLogRepository.findAll().stream()
            .filter(p -> p.getPredictedAt().isBefore(cutoffDate))
            .toList();

        if (!oldLogs.isEmpty()) {
            predictionLogRepository.deleteAll(oldLogs);
            log.info("✅ [PredictionLog] {} anciens logs supprimes", oldLogs.size());
        } else {
            log.info("✅ [PredictionLog] Aucun ancien log a supprimer");
        }
    }
}

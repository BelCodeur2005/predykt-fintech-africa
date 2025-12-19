package com.predykt.accounting.controller;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.ml.MLModel;
import com.predykt.accounting.domain.entity.ml.MLPredictionLog;
import com.predykt.accounting.dto.request.MLPredictionFeedbackRequest;
import com.predykt.accounting.dto.response.ApiResponse;
import com.predykt.accounting.dto.response.MLPerformanceResponse;
import com.predykt.accounting.dto.response.MLPredictionHistoryResponse;
import com.predykt.accounting.exception.ResourceNotFoundException;
import com.predykt.accounting.repository.CompanyRepository;
import com.predykt.accounting.service.ml.MLMatchingService;
import com.predykt.accounting.service.ml.MLPredictionLogService;
import com.predykt.accounting.service.ml.MLTrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controleur REST pour la gestion du systeme ML
 *
 * Endpoints:
 * - GET  /models              : Liste des modeles
 * - POST /models/train        : Entrainement manuel
 * - GET  /models/stats        : Statistiques entrainement
 * - GET  /predictions/history : Historique predictions
 * - POST /predictions/{id}/feedback : Feedback utilisateur
 * - GET  /predictions/performance   : Metriques performance reelle
 *
 * @author PREDYKT ML Team
 */
@RestController
@RequestMapping("/companies/{companyId}/ml")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Machine Learning", description = "Gestion du systeme ML de matching bancaire")
public class MLController {

    private final MLTrainingService trainingService;
    private final MLMatchingService matchingService;
    private final MLPredictionLogService predictionLogService;
    private final CompanyRepository companyRepository;

    /**
     * Liste tous les modeles ML d'une entreprise
     */
    @GetMapping("/models")
    @Operation(summary = "Liste des modeles ML",
               description = "Recupere tous les modeles ML (actifs et anciens)")
    public ResponseEntity<ApiResponse<List<MLModel>>> listModels(
            @PathVariable Long companyId) {

        Company company = getCompany(companyId);
        List<MLModel> models = trainingService.listCompanyModels(company);

        return ResponseEntity.ok(ApiResponse.success(models,
            String.format("%d modeles trouves", models.size())));
    }

    /**
     * Declenche un entrainement manuel
     */
    @PostMapping("/models/train")
    @Operation(summary = "Entrainer un nouveau modele",
               description = "Declenche l'entrainement d'un nouveau modele ML avec les donnees actuelles")
    public ResponseEntity<ApiResponse<MLModel>> trainNewModel(
            @PathVariable Long companyId) {

        log.info("🚀 [MLController] Declenchement entrainement manuel - Company: {}", companyId);

        Company company = getCompany(companyId);

        // Verifier si assez de donnees
        if (!trainingService.hasEnoughTrainingData(company)) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                    "Pas assez de donnees d'entrainement (minimum 50 exemples)",
                    "INSUFFICIENT_TRAINING_DATA"));
        }

        // Lancer entrainement
        MLModel newModel = trainingService.trainNewModel(company);

        if (newModel == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                    "Echec entrainement - Veuillez verifier les logs",
                    "TRAINING_FAILED"));
        }

        return ResponseEntity.ok(ApiResponse.success(newModel,
            String.format("Modele %s entraine avec succes (accuracy: %.2f%%)",
                newModel.getModelVersion(),
                newModel.getAccuracy().doubleValue() * 100)));
    }

    /**
     * Deploie un modele (le marque comme actif)
     */
    @PostMapping("/models/{modelId}/deploy")
    @Operation(summary = "Deployer un modele",
               description = "Active un modele et desactive les autres")
    public ResponseEntity<ApiResponse<Void>> deployModel(
            @PathVariable Long companyId,
            @PathVariable Long modelId) {

        log.info("🔄 [MLController] Deploiement modele {} - Company: {}", modelId, companyId);

        Company company = getCompany(companyId);
        List<MLModel> models = trainingService.listCompanyModels(company);

        MLModel modelToDeploy = models.stream()
            .filter(m -> m.getId().equals(modelId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Modele non trouve: " + modelId));

        trainingService.deployModel(modelToDeploy);

        return ResponseEntity.ok(ApiResponse.success(null,
            String.format("Modele %s deploye avec succes", modelToDeploy.getModelVersion())));
    }

    /**
     * Rollback vers un modele precedent
     */
    @PostMapping("/models/{modelId}/rollback")
    @Operation(summary = "Rollback vers un modele precedent",
               description = "Reactive un ancien modele")
    public ResponseEntity<ApiResponse<Void>> rollbackToModel(
            @PathVariable Long companyId,
            @PathVariable Long modelId) {

        log.info("🔄 [MLController] Rollback vers modele {} - Company: {}", modelId, companyId);

        Company company = getCompany(companyId);
        trainingService.rollbackToModel(company, modelId);

        return ResponseEntity.ok(ApiResponse.success(null,
            "Rollback effectue avec succes"));
    }

    /**
     * Supprime un modele (soft delete)
     */
    @DeleteMapping("/models/{modelId}")
    @Operation(summary = "Supprimer un modele",
               description = "Supprime un modele (impossible si actif)")
    public ResponseEntity<ApiResponse<Void>> deleteModel(
            @PathVariable Long companyId,
            @PathVariable Long modelId) {

        log.info("🗑️  [MLController] Suppression modele {} - Company: {}", modelId, companyId);

        Company company = getCompany(companyId);
        trainingService.deleteModel(company, modelId);

        return ResponseEntity.ok(ApiResponse.success(null,
            "Modele supprime avec succes"));
    }

    /**
     * Retourne les statistiques d'entrainement
     */
    @GetMapping("/models/stats")
    @Operation(summary = "Statistiques d'entrainement",
               description = "Retourne les stats: donnees disponibles, modele actif, etc.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTrainingStats(
            @PathVariable Long companyId) {

        Company company = getCompany(companyId);
        Map<String, Object> stats = trainingService.getTrainingStats(company);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Retourne les stats du modele actif
     */
    @GetMapping("/models/active/stats")
    @Operation(summary = "Stats du modele actif",
               description = "Retourne les stats du modele actuellement utilise")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveModelStats(
            @PathVariable Long companyId) {

        Company company = getCompany(companyId);
        Map<String, Object> stats = matchingService.getModelStats(company);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Soumet un feedback sur une prediction
     */
    @PostMapping("/predictions/{predictionId}/feedback")
    @Operation(summary = "Feedback sur une prediction",
               description = "Permet a l'utilisateur de valider ou rejeter une prediction ML")
    public ResponseEntity<ApiResponse<Void>> submitFeedback(
            @PathVariable Long companyId,
            @PathVariable Long predictionId,
            @RequestBody MLPredictionFeedbackRequest feedback) {

        log.info("📝 [MLController] Feedback prediction {} - Company: {}, Correct: {}",
                 predictionId, companyId, feedback.getWasCorrect());

        // Validation
        if (feedback.getWasApplied() == null || feedback.getWasCorrect() == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                    "wasApplied et wasCorrect sont requis",
                    "INVALID_FEEDBACK"));
        }

        // Mise a jour
        predictionLogService.updatePredictionOutcome(
            predictionId,
            feedback.getWasApplied(),
            feedback.getWasCorrect(),
            feedback.getNotes()
        );

        return ResponseEntity.ok(ApiResponse.success(null,
            "Feedback enregistre avec succes"));
    }

    /**
     * Historique des predictions avec feedback
     */
    @GetMapping("/predictions/history")
    @Operation(summary = "Historique des predictions",
               description = "Retourne l'historique des predictions ML avec feedbacks")
    public ResponseEntity<ApiResponse<List<MLPredictionHistoryResponse>>> getPredictionHistory(
            @PathVariable Long companyId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime since) {

        Company company = getCompany(companyId);
        List<MLPredictionLog> logs = predictionLogService.getPredictionHistory(company, since);

        // Convertir en DTO
        List<MLPredictionHistoryResponse> response = logs.stream()
            .map(this::convertToHistoryDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(response,
            String.format("%d predictions trouvees", response.size())));
    }

    /**
     * Metriques de performance reelle
     */
    @GetMapping("/predictions/performance")
    @Operation(summary = "Performance reelle du modele",
               description = "Retourne les metriques de performance basees sur les feedbacks utilisateurs")
    public ResponseEntity<ApiResponse<MLPerformanceResponse>> getPerformanceMetrics(
            @PathVariable Long companyId) {

        Company company = getCompany(companyId);
        Map<String, Object> metrics = predictionLogService.calculateModelPerformance(company);

        // Convertir en DTO
        MLPerformanceResponse response = convertToPerformanceDTO(metrics, company);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Stats de feedback pour une periode
     */
    @GetMapping("/predictions/feedback/stats")
    @Operation(summary = "Statistiques de feedback",
               description = "Retourne les stats de feedback pour une periode donnee")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeedbackStats(
            @PathVariable Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Company company = getCompany(companyId);
        Map<String, Object> stats = predictionLogService.getFeedbackStats(company, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Declenche manuellement la detection de drift
     */
    @PostMapping("/predictions/check-drift")
    @Operation(summary = "Detecter le drift",
               description = "Verifie si le modele a degrade et declenche re-training si necessaire")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkDrift(
            @PathVariable Long companyId) {

        log.info("🔍 [MLController] Detection drift manuelle - Company: {}", companyId);

        Company company = getCompany(companyId);
        boolean retrainingTriggered = predictionLogService.checkAndTriggerRetrainingIfNeeded(company);

        Map<String, Object> result = Map.of(
            "driftDetected", retrainingTriggered,
            "retrainingTriggered", retrainingTriggered,
            "message", retrainingTriggered ?
                "Drift detecte - Re-training declenche" :
                "Pas de drift detecte - Performance stable"
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== Helpers ====================

    private Company getCompany(Long companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvee: " + companyId));
    }

    private MLPredictionHistoryResponse convertToHistoryDTO(MLPredictionLog log) {
        return MLPredictionHistoryResponse.builder()
            .id(log.getId())
            .bankTransactionId(log.getBankTransaction() != null ? log.getBankTransaction().getId() : null)
            .bankTransactionDescription(log.getBankTransaction() != null ? log.getBankTransaction().getDescription() : null)
            .bankTransactionAmount(log.getBankTransaction() != null ? log.getBankTransaction().getAmount() : null)
            .glEntryId(log.getGlEntry() != null ? log.getGlEntry().getId() : null)
            .glEntryDescription(log.getGlEntry() != null ? log.getGlEntry().getDescription() : null)
            .glEntryAmount(log.getGlEntry() != null ?
                log.getGlEntry().getDebitAmount().subtract(log.getGlEntry().getCreditAmount()) : null)
            .predictedMatch(log.getPredictedMatch())
            .confidenceScore(log.getConfidenceScore())
            .modelVersion(log.getModel() != null ? log.getModel().getModelVersion() : null)
            .actualOutcome(log.getActualOutcome())
            .wasCorrect(log.getWasCorrect())
            .validatedAt(log.getValidatedAt())
            .features(log.getFeatures())
            .predictionTimeMs(log.getPredictionTimeMs())
            .predictedAt(log.getPredictedAt())
            .reconciliationId(log.getReconciliation() != null ? log.getReconciliation().getId() : null)
            .build();
    }

    @SuppressWarnings("unchecked")
    private MLPerformanceResponse convertToPerformanceDTO(Map<String, Object> metrics, Company company) {
        Map<String, Object> driftMap = (Map<String, Object>) metrics.get("drift");

        MLPerformanceResponse.DriftInfo driftInfo = null;
        if (driftMap != null) {
            driftInfo = MLPerformanceResponse.DriftInfo.builder()
                .isDrift((Boolean) driftMap.get("isDrift"))
                .reason((String) driftMap.get("reason"))
                .message((String) driftMap.get("message"))
                .severity((String) driftMap.get("severity"))
                .recommendation((String) driftMap.get("recommendation"))
                .accuracyDrop(driftMap.get("accuracyDrop") != null ?
                    ((Number) driftMap.get("accuracyDrop")).doubleValue() : null)
                .build();
        }

        Map<String, Object> trainingStats = trainingService.getTrainingStats(company);

        return MLPerformanceResponse.builder()
            .realWorldAccuracy((Double) metrics.get("realWorldAccuracy"))
            .baselineAccuracy(trainingStats.get("activeModelAccuracy") != null ?
                ((Number) trainingStats.get("activeModelAccuracy")).doubleValue() : null)
            .totalPredictions((Long) metrics.get("totalPredictions"))
            .correctPredictions((Long) metrics.get("correctPredictions"))
            .validatedPredictions((Long) metrics.get("totalPredictions"))
            .validationRate((Double) metrics.get("validationRate"))
            .avgLatencyMs((Double) metrics.get("avgLatencyMs"))
            .drift(driftInfo)
            .since(LocalDateTime.now().minusDays(30))
            .modelVersion((String) trainingStats.get("activeModel"))
            .algorithm((String) trainingStats.get("algorithm"))
            .modelCreatedAt(trainingStats.get("activeModelDate") != null ?
                (LocalDateTime) trainingStats.get("activeModelDate") : null)
            .build();
    }
}

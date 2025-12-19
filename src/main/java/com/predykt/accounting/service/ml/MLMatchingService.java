package com.predykt.accounting.service.ml;

import com.predykt.accounting.domain.entity.BankTransaction;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.GeneralLedger;
import com.predykt.accounting.domain.entity.ml.MLModel;
import com.predykt.accounting.domain.entity.ml.MLPredictionLog;
import com.predykt.accounting.dto.ml.MLPredictionResult;
import com.predykt.accounting.dto.ml.MatchFeatures;
import com.predykt.accounting.dto.ml.AdvancedMatchFeatures;
import com.predykt.accounting.repository.ml.MLModelRepository;
import com.predykt.accounting.repository.ml.MLPredictionLogRepository;
import com.predykt.accounting.service.ml.advanced.algorithms.XGBoostTrainingService;
import com.predykt.accounting.service.ml.advanced.feature.FeatureEngineeringPipeline;
import com.predykt.accounting.service.ml.advanced.explainability.SHAPExplainerService;
import com.predykt.accounting.dto.ml.SHAPExplanation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service ML pour matching bancaire (Systeme Avance Uniquement)
 *
 * Systeme ML de pointe avec:
 * - 30 features avancees (FeatureEngineeringPipeline)
 * - XGBoost ou Stacking Ensemble
 * - Predictions probabilistes precises
 * - Target accuracy: 95-97%
 *
 * Pipeline:
 * 1. Extract 30 advanced features
 * 2. Load active XGBoost/Stacking model
 * 3. Predict with ML model
 * 4. Log prediction
 * 5. Return MLPredictionResult
 *
 * @author PREDYKT ML Team
 * @version 3.0 - Advanced Only
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLMatchingService {

    private final FeatureEngineeringPipeline featurePipeline;
    private final XGBoostTrainingService xgboostService;
    private final SHAPExplainerService shapExplainerService;
    private final MLModelRepository modelRepository;
    private final MLPredictionLogRepository predictionLogRepository;

    /**
     * Predit le meilleur match GL pour une BankTransaction
     *
     * @param bt Transaction bancaire
     * @param glCandidates Candidats GL possibles
     * @param company Entreprise
     * @return Meilleur match predit avec score de confiance
     */
    @Transactional
    public Optional<MLPredictionResult> predictBestMatch(
        BankTransaction bt,
        List<GeneralLedger> glCandidates,
        Company company
    ) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Charger le modele actif
            Optional<MLModel> modelOpt = loadActiveModel(company);
            if (modelOpt.isEmpty()) {
                log.warn("Aucun modele ML actif pour company {}", company.getId());
                return Optional.empty();
            }

            MLModel model = modelOpt.get();
            log.debug("🚀 [ML] Mode AVANCE - XGBoost avec 30 features");

            // 2. Charger modele XGBoost
            Booster booster = loadXGBoostModel(model);
            if (booster == null) {
                log.error("Impossible de charger le modele XGBoost depuis {}", model.getModelPath());
                return Optional.empty();
            }

            // 3. Extraire features avancees + predire pour tous les candidats
            List<CandidateScore> scores = new ArrayList<>();

            for (GeneralLedger gl : glCandidates) {
                // Extraire 30 features avancees
                AdvancedMatchFeatures advFeatures = featurePipeline.extractAllFeatures(bt, gl, company);
                double[] featureArray = advFeatures.toArray();

                // Prediction XGBoost (probabilite reelle)
                double probability = xgboostService.predict(booster, featureArray);
                double confidence = probability * 100.0;  // Convertir en %
                boolean isPredictedMatch = probability >= 0.5;

                // Convertir AdvancedMatchFeatures -> MatchFeatures pour compatibilite logs
                MatchFeatures basicFeatures = convertToBasicFeatures(advFeatures);

                scores.add(new CandidateScore(gl, basicFeatures, confidence, isPredictedMatch));
            }

            // 4. Trouver le meilleur candidat
            Optional<CandidateScore> bestOpt = scores.stream()
                .filter(cs -> cs.isPredictedMatch)
                .max(Comparator.comparingDouble(cs -> cs.confidence));

            if (bestOpt.isEmpty()) {
                log.info("ML: Aucun match predit pour BT {} parmi {} candidats",
                    bt.getId(), glCandidates.size());
                return Optional.empty();
            }

            CandidateScore best = bestOpt.get();

            // 5. Construire le resultat avec features avancées pour explication enrichie
            long predictionTime = System.currentTimeMillis() - startTime;

            // Récupérer les features avancées du meilleur candidat
            AdvancedMatchFeatures bestAdvFeatures = featurePipeline.extractAllFeatures(bt, best.gl, company);
            double[] bestFeatureArray = bestAdvFeatures.toArray();
            double bestProbability = xgboostService.predict(booster, bestFeatureArray);

            // 6. Calculer SHAP explanation (explicabilité complète)
            SHAPExplanation shapExplanation = null;
            try {
                shapExplanation = shapExplainerService.explainPrediction(
                    booster,
                    bestFeatureArray,
                    bestAdvFeatures,
                    bestProbability,
                    model.getModelVersion()
                );
                log.debug("SHAP explanation generated - Top feature: {} (impact: {:.1f}%)",
                    shapExplanation.getTopFeatures().isEmpty() ? "N/A" :
                        shapExplanation.getTopFeatures().get(0).getFeatureName(),
                    shapExplanation.getTopFeatures().isEmpty() ? 0.0 :
                        shapExplanation.getTopFeatures().get(0).getImpactPercent());
            } catch (Exception e) {
                log.warn("Erreur calcul SHAP explanation: {}", e.getMessage());
            }

            MLPredictionResult result = MLPredictionResult.builder()
                .bankTransaction(bt)
                .glEntry(best.gl)
                .confidenceScore(best.confidence)
                .features(best.features)
                .advancedFeatures(bestAdvFeatures)  // Pour explication enrichie
                .shapExplanation(shapExplanation)   // Explication SHAP complète
                .modelVersion(model.getModelVersion())
                .predictionTimeMs(predictionTime)
                .build();

            result.generateExplanation();

            // 7. Logger la prediction
            logPrediction(result, model, company);

            log.info("ML: Prediction BT {} → GL {} avec confiance {:.1f}% ({}ms, XGBoost)",
                bt.getId(), best.gl.getId(), best.confidence, predictionTime);

            return Optional.of(result);

        } catch (Exception e) {
            log.error("Erreur prediction ML pour BT {}: {}", bt.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Charge un modele XGBoost depuis le path
     */
    private Booster loadXGBoostModel(MLModel model) {
        try {
            if (model.getModelPath() == null) {
                log.error("MLModel {} n'a pas de modelPath defini", model.getId());
                return null;
            }

            return ml.dmlc.xgboost4j.java.XGBoost.loadModel(model.getModelPath());

        } catch (Exception e) {
            log.error("Erreur chargement modele XGBoost {}: {}", model.getModelPath(), e.getMessage());
            return null;
        }
    }

    /**
     * Convertit AdvancedMatchFeatures (30) -> MatchFeatures (12) pour compatibilite logs
     */
    private MatchFeatures convertToBasicFeatures(AdvancedMatchFeatures advanced) {
        // Utiliser le Builder de MatchFeatures avec les noms de champs corrects
        return MatchFeatures.builder()
            .amountDifference(advanced.getAmountDifference())
            .dateDiffDays(advanced.getDateDiffCalendarDays() != null ?
                advanced.getDateDiffCalendarDays().longValue() : 0L)
            .textSimilarity(advanced.getTfidfCosineSimilarity())
            .amountRatio(advanced.getAmountRatio())
            .sameSense(advanced.getMonthDiff() != null && advanced.getMonthDiff() == 0 ? 1.0 : 0.0)
            .referenceMatch(0.0)
            .isRoundNumber(0.0)
            .isMonthEnd(advanced.getIsMonthEnd())
            .dayOfWeekBT(advanced.getDayOfWeek())
            .dayOfWeekGL(advanced.getDayOfWeek())
            .historicalMatchRate(advanced.getCustomerMatchRate())
            .avgDaysHistorical(advanced.getAvgMatchingDelay())
            .build();
    }

    /**
     * Predit pour plusieurs transactions en batch
     */
    @Transactional
    public List<MLPredictionResult> predictBatch(
        List<BankTransaction> btList,
        List<GeneralLedger> glPool,
        Company company
    ) {
        List<MLPredictionResult> results = new ArrayList<>();

        for (BankTransaction bt : btList) {
            Optional<MLPredictionResult> prediction = predictBestMatch(bt, glPool, company);
            prediction.ifPresent(results::add);
        }

        log.info("ML: Batch prediction terminee - {} matches trouves sur {} transactions",
            results.size(), btList.size());

        return results;
    }

    /**
     * Predit avec filtrage des candidats (optimisation)
     */
    @Transactional
    public Optional<MLPredictionResult> predictWithFiltering(
        BankTransaction bt,
        List<GeneralLedger> glCandidates,
        Company company
    ) {
        // Pre-filtre: montant ±50%, dates ±30 jours
        List<GeneralLedger> filtered = glCandidates.stream()
            .filter(gl -> isReasonableCandidate(bt, gl))
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            log.debug("ML: Pre-filtrage a elimine tous les candidats pour BT {}", bt.getId());
            return Optional.empty();
        }

        log.debug("ML: Pre-filtrage {} → {} candidats pour BT {}",
            glCandidates.size(), filtered.size(), bt.getId());

        return predictBestMatch(bt, filtered, company);
    }

    /**
     * Charge le modele actif (avec cache)
     */
    @Cacheable(value = "mlModels", key = "#company.id")
    public Optional<MLModel> loadActiveModel(Company company) {
        return modelRepository.findByCompanyAndIsActiveTrue(company);
    }

    /**
     * Invalide le cache du modele (apres entrainement)
     */
    public void invalidateModelCache(Company company) {
        // Cache eviction gere par @CacheEvict dans MLTrainingService
        log.info("Cache modele ML invalide pour company {}", company.getId());
    }

    /**
     * Verifie si un candidat est raisonnable (pre-filtrage)
     */
    private boolean isReasonableCandidate(BankTransaction bt, GeneralLedger gl) {
        // Montant: ±50%
        double btAmount = Math.abs(bt.getAmount().doubleValue());
        double glAmount = Math.abs(gl.getDebitAmount().subtract(gl.getCreditAmount()).doubleValue());

        if (glAmount == 0) return false;

        double ratio = btAmount / glAmount;
        if (ratio < 0.5 || ratio > 2.0) return false;

        // Date: ±30 jours
        long daysDiff = Math.abs(
            java.time.temporal.ChronoUnit.DAYS.between(
                bt.getTransactionDate(),
                gl.getEntryDate()
            )
        );

        return daysDiff <= 30;
    }

    /**
     * Log la prediction pour monitoring
     */
    private void logPrediction(MLPredictionResult result, MLModel model, Company company) {
        try {
            MLPredictionLog log = MLPredictionLog.builder()
                .company(company)
                .model(model)
                .bankTransaction(result.getBankTransaction())
                .glEntry(result.getGlEntry())
                .predictedMatch(true)
                .confidenceScore(BigDecimal.valueOf(result.getConfidenceScore()))
                .features(result.getFeatures().toMap())
                .predictionTimeMs(result.getPredictionTimeMs() != null ? result.getPredictionTimeMs().intValue() : null)
                .predictedAt(LocalDateTime.now())
                .build();

            MLPredictionLog saved = predictionLogRepository.save(log);
            result.setPredictionLogId(saved.getId());

        } catch (Exception e) {
            // Ne pas bloquer la prediction si log echoue
            this.log.error("Erreur log prediction: {}", e.getMessage());
        }
    }

    /**
     * Classe interne pour scorer les candidats
     */
    private static class CandidateScore {
        GeneralLedger gl;
        MatchFeatures features;
        double confidence;
        boolean isPredictedMatch;

        CandidateScore(GeneralLedger gl, MatchFeatures features,
                      double confidence, boolean isPredictedMatch) {
            this.gl = gl;
            this.features = features;
            this.confidence = confidence;
            this.isPredictedMatch = isPredictedMatch;
        }
    }

    /**
     * Verifie si le modele a besoin d'un re-entrainement
     */
    public boolean needsRetraining(Company company) {
        Optional<MLModel> modelOpt = loadActiveModel(company);
        if (modelOpt.isEmpty()) return true;

        MLModel model = modelOpt.get();

        // Critere 1: Modele trop ancien (>30 jours)
        if (model.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))) {
            return true;
        }

        // Critere 2: Accuracy trop faible (<85%)
        if (model.getAccuracy().doubleValue() < 0.85) {
            return true;
        }

        // Critere 3: Drift detecte (accuracy reelle vs entrainement)
        Double realAccuracy = predictionLogRepository.calculateRealWorldAccuracy(
            company,
            LocalDateTime.now().minusDays(7)
        );

        if (realAccuracy != null) {
            double drift = Math.abs(model.getAccuracy().doubleValue() - realAccuracy);
            if (drift > 0.10) {  // 10% de difference
                log.warn("Drift detecte pour company {}: accuracy model={:.2f}%, reel={:.2f}%",
                    company.getId(), model.getAccuracy().doubleValue() * 100, realAccuracy * 100);
                return true;
            }
        }

        return false;
    }

    /**
     * Retourne les statistiques du modele actif
     */
    public Map<String, Object> getModelStats(Company company) {
        Optional<MLModel> modelOpt = loadActiveModel(company);
        if (modelOpt.isEmpty()) {
            return Map.of("status", "NO_MODEL");
        }

        MLModel model = modelOpt.get();
        Map<String, Object> stats = new HashMap<>();

        stats.put("modelVersion", model.getModelVersion());
        stats.put("algorithm", model.getAlgorithm());
        stats.put("accuracy", model.getAccuracy());
        stats.put("f1Score", model.getF1Score());
        stats.put("createdAt", model.getCreatedAt());
        stats.put("mode", "ADVANCED");
        stats.put("features", 30);

        // Latence moyenne sur 7 derniers jours
        Double avgLatency = predictionLogRepository.calculateAverageLatency(
            company,
            LocalDateTime.now().minusDays(7)
        );
        stats.put("avgLatencyMs", avgLatency);

        // Accuracy reelle
        Double realAccuracy = predictionLogRepository.calculateRealWorldAccuracy(
            company,
            LocalDateTime.now().minusDays(7)
        );
        stats.put("realWorldAccuracy", realAccuracy);

        // Drift
        if (realAccuracy != null) {
            double drift = Math.abs(model.getAccuracy().doubleValue() - realAccuracy);
            stats.put("accuracyDrift", drift);
            stats.put("needsRetraining", drift > 0.10);
        }

        return stats;
    }
}

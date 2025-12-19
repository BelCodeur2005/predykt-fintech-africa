package com.predykt.accounting.service.ml;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.ml.MLModel;
import com.predykt.accounting.domain.entity.ml.MLTrainingData;
import com.predykt.accounting.domain.enums.MLModelStatus;
import com.predykt.accounting.repository.ml.MLModelRepository;
import com.predykt.accounting.repository.ml.MLTrainingDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service d'entrainement ML pour matching bancaire
 *
 * Systeme ML Avance Uniquement:
 * - 30 features avancees (4 tiers: amount, text, temporal, metadata)
 * - XGBoost ou Stacking Ensemble (4 algorithms + meta-learner)
 * - SMOTE, Scaling, Hyperparameter Optimization
 * - Cross-Validation stratifiee (5-fold)
 * - Model Versioning semantique (v1.0.0)
 * - Monitoring & Drift detection
 * - Target accuracy: 95-97%
 *
 * @author PREDYKT ML Team
 * @version 3.0 - Advanced Only
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLTrainingService {

    private final MLTrainingDataRepository trainingDataRepository;
    private final MLModelRepository modelRepository;
    private final MLAdvancedOrchestrator advancedOrchestrator;

    @Value("${predykt.ml.use-stacking:false}")
    private boolean useStacking;

    private static final int MIN_TRAINING_DATA = 50;

    /**
     * Entraine un nouveau modele pour une entreprise
     *
     * Utilise le systeme avance (XGBoost ou Stacking)
     *
     * @param company Entreprise
     * @return Modele entraine, ou null si pas assez de donnees
     */
    @Transactional
    @CacheEvict(value = "mlModels", key = "#company.id")
    public MLModel trainNewModel(Company company) {
        log.info("🚀 [ML] Entrainement modele AVANCE - Company: {}, Algorithm: {}",
                 company.getId(), useStacking ? "STACKING" : "XGBOOST");

        // Verifier les donnees d'entrainement disponibles
        if (!hasEnoughTrainingData(company)) {
            log.warn("Pas assez de donnees d'entrainement pour company {}", company.getId());
            return null;
        }

        // Deleguer a l'orchestrateur avance
        return advancedOrchestrator.trainAdvancedModel(company, useStacking);
    }

    /**
     * Deploie un modele (marque comme actif, desactive les autres)
     */
    @Transactional
    @CacheEvict(value = "mlModels", key = "#model.company.id")
    public void deployModel(MLModel model) {
        // Desactiver tous les modeles actifs de cette entreprise
        List<MLModel> activeModels = modelRepository
            .findByCompanyAndStatus(model.getCompany(), MLModelStatus.DEPLOYED);

        for (MLModel active : activeModels) {
            active.setIsActive(false);
            active.setStatus(MLModelStatus.DEPRECATED);
            modelRepository.save(active);
            log.info("Modele {} desactive", active.getModelVersion());
        }

        // Activer le nouveau modele
        model.setIsActive(true);
        model.setStatus(MLModelStatus.DEPLOYED);
        modelRepository.save(model);

        log.info("✅ Modele {} deploye pour company {}", model.getModelVersion(), model.getCompany().getId());
    }

    /**
     * Entraine et deploie automatiquement si meilleur que l'actuel
     */
    @Transactional
    public boolean trainAndDeployIfBetter(Company company) {
        MLModel newModel = trainNewModel(company);
        if (newModel == null) {
            return false;
        }

        // Comparer avec le modele actuel
        modelRepository.findByCompanyAndIsActiveTrue(company).ifPresentOrElse(
            currentModel -> {
                if (newModel.getAccuracy().compareTo(currentModel.getAccuracy()) > 0) {
                    log.info("✨ Nouveau modele meilleur: {:.2f}% > {:.2f}%",
                        newModel.getAccuracy().doubleValue() * 100,
                        currentModel.getAccuracy().doubleValue() * 100);
                    deployModel(newModel);
                } else {
                    log.info("Nouveau modele moins bon, conservation de l'actuel");
                }
            },
            () -> {
                // Pas de modele actuel, deployer le nouveau
                log.info("Aucun modele actif, deploiement du nouveau");
                deployModel(newModel);
            }
        );

        return true;
    }

    /**
     * Verifie si assez de donnees d'entrainement
     */
    public boolean hasEnoughTrainingData(Company company) {
        List<MLTrainingData> trainingData = trainingDataRepository.findUsableTrainingData(company);
        boolean hasEnough = trainingData.size() >= MIN_TRAINING_DATA;

        log.debug("Donnees d'entrainement disponibles: {} (minimum: {})",
                  trainingData.size(), MIN_TRAINING_DATA);

        return hasEnough;
    }

    /**
     * Retourne les statistiques d'entrainement
     */
    public Map<String, Object> getTrainingStats(Company company) {
        List<MLTrainingData> allData = trainingDataRepository.findUsableTrainingData(company);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSamples", allData.size());
        stats.put("minRequired", MIN_TRAINING_DATA);
        stats.put("canTrain", allData.size() >= MIN_TRAINING_DATA);
        stats.put("mode", "ADVANCED");
        stats.put("algorithm", useStacking ? "STACKING" : "XGBOOST");
        stats.put("features", 30);

        // Repartition positive/negative
        long positives = allData.stream().filter(d -> d.getLabel() == 1).count();
        long negatives = allData.stream().filter(d -> d.getLabel() == 0).count();
        stats.put("positiveSamples", positives);
        stats.put("negativeSamples", negatives);

        if (allData.size() > 0) {
            stats.put("classBalance", (double)positives / allData.size());
        }

        // Modele actif
        modelRepository.findByCompanyAndIsActiveTrue(company).ifPresent(model -> {
            stats.put("activeModel", model.getModelVersion());
            stats.put("activeModelAccuracy", model.getAccuracy());
            stats.put("activeModelDate", model.getCreatedAt());
        });

        return stats;
    }

    /**
     * Genere une version pour le modele
     */
    private String generateVersion() {
        return "v" + System.currentTimeMillis();
    }

    /**
     * Liste tous les modeles d'une entreprise
     */
    public List<MLModel> listCompanyModels(Company company) {
        return modelRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    /**
     * Supprime un modele (soft delete)
     */
    @Transactional
    @CacheEvict(value = "mlModels", key = "#company.id")
    public void deleteModel(Company company, Long modelId) {
        MLModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("Modele non trouve: " + modelId));

        if (!model.getCompany().getId().equals(company.getId())) {
            throw new RuntimeException("Modele n'appartient pas a cette entreprise");
        }

        if (model.getIsActive()) {
            throw new RuntimeException("Impossible de supprimer un modele actif");
        }

        // Utiliser DEPRECATED au lieu de DELETED (soft delete)
        model.setStatus(MLModelStatus.DEPRECATED);
        modelRepository.save(model);

        log.info("Modele {} supprime (DEPRECATED)", model.getModelVersion());
    }

    /**
     * Rollback vers un modele precedent
     */
    @Transactional
    @CacheEvict(value = "mlModels", key = "#company.id")
    public void rollbackToModel(Company company, Long modelId) {
        MLModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("Modele non trouve: " + modelId));

        if (!model.getCompany().getId().equals(company.getId())) {
            throw new RuntimeException("Modele n'appartient pas a cette entreprise");
        }

        // On peut deployer un modele DEPRECATED (rollback)
        if (model.getStatus() == MLModelStatus.TRAINING) {
            throw new RuntimeException("Impossible de deployer un modele en cours d'entrainement");
        }

        log.info("🔄 Rollback vers modele {} pour company {}", model.getModelVersion(), company.getId());
        deployModel(model);
    }
}

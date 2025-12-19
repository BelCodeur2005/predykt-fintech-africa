package com.predykt.accounting.domain.entity.ml;

import com.predykt.accounting.domain.entity.BaseEntity;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.User;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entité représentant une expérience ML (ensemble de runs avec variations d'hyperparamètres)
 *
 * Une expérience = série de runs pour tester différentes configurations
 * Exemples:
 * - "Grid Search XGBoost max_depth"
 * - "Optimisation learning rate"
 * - "Comparaison XGBoost vs LightGBM"
 * - "Impact de SMOTE sur accuracy"
 *
 * Workflow typique:
 * 1. Créer une expérience avec nom + objectif
 * 2. Lancer plusieurs runs avec différents hyperparams
 * 3. Comparer les résultats
 * 4. Déployer le meilleur run comme MLModel
 *
 * @author PREDYKT ML Team
 * @version 1.0
 */
@Entity
@Table(name = "ml_experiments", indexes = {
    @Index(name = "idx_ml_experiments_company", columnList = "company_id"),
    @Index(name = "idx_ml_experiments_status", columnList = "status"),
    @Index(name = "idx_ml_experiments_created", columnList = "created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLExperiment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    // ==================== Identification ====================

    @Column(name = "experiment_name", nullable = false, length = 200)
    private String experimentName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Type d'expérience
     * GRID_SEARCH, RANDOM_SEARCH, BAYESIAN_OPTIMIZATION, MANUAL, AB_TEST
     */
    @Column(name = "experiment_type", length = 50)
    private String experimentType;

    // ==================== Statut ====================

    /**
     * Statut de l'expérience
     * RUNNING, COMPLETED, FAILED, CANCELLED
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "RUNNING";

    // ==================== Méta-information ====================

    /**
     * Tags pour catégoriser les expériences
     * Exemples: ["baseline", "production", "research", "hyperopt"]
     */
    @Type(JsonBinaryType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    /**
     * Configuration de base de l'expérience
     * Paramètres fixes qui ne changent pas entre les runs
     * Exemple: {"algorithm": "XGBoost", "cv_folds": 5}
     */
    @Type(JsonBinaryType.class)
    @Column(name = "base_config", columnDefinition = "jsonb")
    private Map<String, Object> baseConfig;

    // ==================== Timing ====================

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // ==================== Runs ====================

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("runNumber ASC")
    @Builder.Default
    private List<MLExperimentRun> runs = new ArrayList<>();

    // ==================== Meilleur Run ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "best_run_id")
    private MLExperimentRun bestRun;

    @Column(name = "best_accuracy", precision = 5, scale = 4)
    private Double bestAccuracy;

    // ==================== Modèle déployé ====================

    /**
     * Si le meilleur run a été déployé, référence vers le MLModel
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployed_model_id")
    private MLModel deployedModel;

    // ==================== Utilisateur ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    // ==================== Méthodes métier ====================

    /**
     * Ajoute un run à l'expérience
     */
    public void addRun(MLExperimentRun run) {
        runs.add(run);
        run.setExperiment(this);
        run.setRunNumber(runs.size());
    }

    /**
     * Démarre l'expérience
     */
    public void start() {
        this.status = "RUNNING";
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Termine l'expérience avec succès
     */
    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = LocalDateTime.now();
        calculateDuration();
        updateBestRun();
    }

    /**
     * Marque l'expérience comme échouée
     */
    public void fail() {
        this.status = "FAILED";
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    /**
     * Annule l'expérience
     */
    public void cancel() {
        this.status = "CANCELLED";
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    /**
     * Calcule la durée totale de l'expérience
     */
    private void calculateDuration() {
        if (startedAt != null && completedAt != null) {
            this.durationSeconds = (int) java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
    }

    /**
     * Met à jour le meilleur run basé sur l'accuracy
     */
    public void updateBestRun() {
        if (runs.isEmpty()) {
            return;
        }

        MLExperimentRun best = runs.stream()
            .filter(run -> run.getAccuracy() != null)
            .max((r1, r2) -> Double.compare(r1.getAccuracy(), r2.getAccuracy()))
            .orElse(null);

        if (best != null) {
            this.bestRun = best;
            this.bestAccuracy = best.getAccuracy();
        }
    }

    /**
     * Retourne le nombre total de runs
     */
    public int getTotalRuns() {
        return runs.size();
    }

    /**
     * Retourne les runs réussis
     */
    public List<MLExperimentRun> getSuccessfulRuns() {
        return runs.stream()
            .filter(run -> "COMPLETED".equals(run.getStatus()))
            .toList();
    }

    /**
     * Retourne un résumé de l'expérience
     */
    public String getSummary() {
        return String.format("%s - %d runs - Best: %.2f%% - %s",
            experimentName,
            getTotalRuns(),
            bestAccuracy != null ? bestAccuracy * 100 : 0.0,
            status
        );
    }

    /**
     * Vérifie si l'expérience est en cours
     */
    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    /**
     * Vérifie si l'expérience est terminée
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
}
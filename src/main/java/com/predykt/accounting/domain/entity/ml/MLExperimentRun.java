package com.predykt.accounting.domain.entity.ml;

import com.predykt.accounting.domain.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entité représentant un run individuel d'une expérience ML
 *
 * Un run = un entraînement avec un set spécifique d'hyperparamètres
 *
 * Exemple:
 * Expérience "Optimisation XGBoost"
 *   ├── Run #1: max_depth=5, n_estimators=100  → 89.2%
 *   ├── Run #2: max_depth=10, n_estimators=100 → 91.5%
 *   └── Run #3: max_depth=10, n_estimators=200 → 93.8% ← BEST
 *
 * @author PREDYKT ML Team
 * @version 1.0
 */
@Entity
@Table(name = "ml_experiment_runs", indexes = {
    @Index(name = "idx_ml_experiment_runs_exp", columnList = "experiment_id"),
    @Index(name = "idx_ml_experiment_runs_status", columnList = "status"),
    @Index(name = "idx_ml_experiment_runs_accuracy", columnList = "accuracy")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_ml_experiment_run_number",
                     columnNames = {"experiment_id", "run_number"})
})
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"experiment"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLExperimentRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private MLExperiment experiment;

    // ==================== Identification ====================

    @Column(name = "run_number", nullable = false)
    private Integer runNumber;  // 1, 2, 3, ...

    @Column(name = "run_name", length = 200)
    private String runName;  // Optionnel: "baseline", "best_config", etc.

    // ==================== Hyperparamètres ====================

    /**
     * Hyperparamètres testés dans ce run
     * Exemple: {
     *   "n_estimators": 200,
     *   "max_depth": 10,
     *   "learning_rate": 0.05,
     *   "min_child_weight": 1,
     *   "subsample": 0.8
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(name = "hyperparameters", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> hyperparameters;

    // ==================== Métriques de Performance ====================

    @Column(name = "accuracy", precision = 5, scale = 4)
    private Double accuracy;

    @Column(name = "precision_score", precision = 5, scale = 4)
    private Double precision;

    @Column(name = "recall_score", precision = 5, scale = 4)
    private Double recall;

    @Column(name = "f1_score", precision = 5, scale = 4)
    private Double f1Score;

    @Column(name = "roc_auc", precision = 5, scale = 4)
    private Double rocAuc;

    // ==================== Métriques Additionnelles ====================

    /**
     * Autres métriques custom
     * Exemple: {
     *   "train_accuracy": 0.95,
     *   "val_accuracy": 0.92,
     *   "confusion_matrix": [[45, 5], [3, 47]],
     *   "feature_importance": {"amount": 0.35, "text": 0.28}
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private Map<String, Object> metrics;

    // ==================== Statut ====================

    /**
     * Statut du run
     * RUNNING, COMPLETED, FAILED, TIMEOUT
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ==================== Timing ====================

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // ==================== Données Entraînement ====================

    @Column(name = "training_samples")
    private Integer trainingSamples;

    @Column(name = "validation_samples")
    private Integer validationSamples;

    @Column(name = "test_samples")
    private Integer testSamples;

    // ==================== Tags et Notes ====================

    /**
     * Tags pour ce run spécifique
     * Exemples: "best", "baseline", "overfitting", "production_candidate"
     */
    @Type(JsonBinaryType.class)
    @Column(name = "tags", columnDefinition = "jsonb")
    private java.util.List<String> tags;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ==================== Artefacts ====================

    @Column(name = "model_path", columnDefinition = "TEXT")
    private String modelPath;  // Chemin vers le modèle sauvegardé pour ce run

    @Column(name = "log_file_path", columnDefinition = "TEXT")
    private String logFilePath;  // Chemin vers les logs détaillés

    // ==================== Modèle Déployé ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployed_as_model_id")
    private MLModel deployedAsModel;  // Si ce run a été déployé

    // ==================== Méthodes métier ====================

    /**
     * Démarre le run
     */
    public void start() {
        this.status = "RUNNING";
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Termine le run avec succès
     */
    public void complete(Double accuracy, Double f1Score, Double precision, Double recall) {
        this.status = "COMPLETED";
        this.completedAt = LocalDateTime.now();
        this.accuracy = accuracy;
        this.f1Score = f1Score;
        this.precision = precision;
        this.recall = recall;
        calculateDuration();
    }

    /**
     * Marque le run comme échoué
     */
    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
        calculateDuration();
    }

    /**
     * Marque le run comme timeout
     */
    public void timeout() {
        this.status = "TIMEOUT";
        this.completedAt = LocalDateTime.now();
        this.errorMessage = "Run exceeded maximum allowed duration";
        calculateDuration();
    }

    /**
     * Calcule la durée du run
     */
    private void calculateDuration() {
        if (startedAt != null && completedAt != null) {
            this.durationSeconds = (int) java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
    }

    /**
     * Ajoute un tag
     */
    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new java.util.ArrayList<>();
        }
        if (!this.tags.contains(tag)) {
            this.tags.add(tag);
        }
    }

    /**
     * Retourne un résumé du run
     */
    public String getSummary() {
        return String.format("Run #%d - Accuracy: %.2f%% - F1: %.2f%% - %s",
            runNumber,
            accuracy != null ? accuracy * 100 : 0.0,
            f1Score != null ? f1Score * 100 : 0.0,
            status
        );
    }

    /**
     * Vérifie si le run a réussi
     */
    public boolean isSuccessful() {
        return "COMPLETED".equals(status);
    }

    /**
     * Vérifie si le run a échoué
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || "TIMEOUT".equals(status);
    }

    /**
     * Vérifie si le run est en cours
     */
    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    /**
     * Retourne la valeur d'un hyperparamètre
     */
    public Object getHyperparameter(String key) {
        return hyperparameters != null ? hyperparameters.get(key) : null;
    }

    /**
     * Retourne une métrique
     */
    public Object getMetric(String key) {
        return metrics != null ? metrics.get(key) : null;
    }
}
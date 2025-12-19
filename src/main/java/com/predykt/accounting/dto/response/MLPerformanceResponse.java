package com.predykt.accounting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO pour les metriques de performance d'un modele ML
 *
 * Retourne les KPIs de performance reelle du modele base sur
 * les feedbacks utilisateurs (actualOutcome).
 *
 * @author PREDYKT ML Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPerformanceResponse {

    /**
     * Accuracy reelle (basee sur feedbacks utilisateurs)
     */
    private Double realWorldAccuracy;

    /**
     * Accuracy baseline (lors de l'entrainement)
     */
    private Double baselineAccuracy;

    /**
     * Nombre total de predictions
     */
    private Long totalPredictions;

    /**
     * Nombre de predictions correctes
     */
    private Long correctPredictions;

    /**
     * Nombre de predictions validees (avec feedback)
     */
    private Long validatedPredictions;

    /**
     * Taux de validation (% predictions avec feedback)
     */
    private Double validationRate;

    /**
     * Latence moyenne en milliseconde
     */
    private Double avgLatencyMs;

    /**
     * Information sur le drift
     */
    private DriftInfo drift;

    /**
     * Periode d'analyse
     */
    private LocalDateTime since;

    /**
     * Informations sur le modele actif
     */
    private String modelVersion;
    private String algorithm;
    private LocalDateTime modelCreatedAt;

    /**
     * Details du drift
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriftInfo {
        private Boolean isDrift;
        private String reason;
        private String message;
        private String severity;  // NONE, LOW, MEDIUM, HIGH
        private String recommendation;
        private Double accuracyDrop;
    }
}

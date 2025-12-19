package com.predykt.accounting.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO pour l'historique d'une prediction ML
 *
 * Retourne les details d'une prediction avec son feedback
 * pour analyse et debugging.
 *
 * @author PREDYKT ML Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionHistoryResponse {

    /**
     * ID du log de prediction
     */
    private Long id;

    /**
     * Transaction bancaire source
     */
    private Long bankTransactionId;
    private String bankTransactionDescription;
    private BigDecimal bankTransactionAmount;

    /**
     * Ecriture GL suggeree
     */
    private Long glEntryId;
    private String glEntryDescription;
    private BigDecimal glEntryAmount;

    /**
     * Prediction
     */
    private Boolean predictedMatch;
    private BigDecimal confidenceScore;
    private String modelVersion;

    /**
     * Feedback utilisateur
     */
    private Boolean actualOutcome;
    private Boolean wasCorrect;
    private LocalDateTime validatedAt;

    /**
     * Features utilisees (pour debugging)
     */
    private Map<String, Object> features;

    /**
     * Performance
     */
    private Integer predictionTimeMs;
    private LocalDateTime predictedAt;

    /**
     * Rapprochement associe
     */
    private Long reconciliationId;
}

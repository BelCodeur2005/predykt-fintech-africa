package com.predykt.accounting.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour le feedback utilisateur sur une prediction ML
 *
 * Permet a l'utilisateur de valider ou rejeter une suggestion ML
 * pour ameliorer le modele (feedback loop).
 *
 * @author PREDYKT ML Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionFeedbackRequest {

    /**
     * Si la suggestion a ete appliquee par l'utilisateur
     */
    private Boolean wasApplied;

    /**
     * Si la prediction etait correcte (match valide)
     * - true: Le match suggere etait correct
     * - false: Le match suggere etait incorrect
     */
    private Boolean wasCorrect;

    /**
     * Notes optionnelles de l'utilisateur
     * Ex: "Bon match", "Montant incorrect", "Date trop eloignee"
     */
    private String notes;

    /**
     * Raison du rejet (si wasCorrect=false)
     * Ex: "WRONG_AMOUNT", "WRONG_DATE", "WRONG_DESCRIPTION", "OTHER"
     */
    private String rejectionReason;
}

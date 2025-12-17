package com.predykt.accounting.repository.ml;

import com.predykt.accounting.domain.entity.BankReconciliation;
import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.ml.MLPredictionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository pour MLPredictionLog
 * Gère les logs de prédictions ML pour monitoring
 *
 * @author PREDYKT ML Team
 */
@Repository
public interface MLPredictionLogRepository extends JpaRepository<MLPredictionLog, Long> {

    List<MLPredictionLog> findByReconciliationOrderByPredictedAtDesc(
        BankReconciliation reconciliation
    );

    /**
     * Compte les prédictions correctes
     */
    @Query("SELECT COUNT(p) FROM MLPredictionLog p " +
           "WHERE p.company = :company " +
           "AND p.wasCorrect = true " +
           "AND p.predictedAt >= :since")
    long countCorrectPredictions(
        @Param("company") Company company,
        @Param("since") LocalDateTime since
    );

    /**
     * Compte les prédictions validées
     */
    @Query("SELECT COUNT(p) FROM MLPredictionLog p " +
           "WHERE p.company = :company " +
           "AND p.actualOutcome IS NOT NULL " +
           "AND p.predictedAt >= :since")
    long countValidatedPredictions(
        @Param("company") Company company,
        @Param("since") LocalDateTime since
    );

    /**
     * Calcule la latence moyenne
     */
    @Query("SELECT AVG(p.predictionTimeMs) FROM MLPredictionLog p " +
           "WHERE p.company = :company " +
           "AND p.predictedAt >= :since")
    Double calculateAverageLatency(
        @Param("company") Company company,
        @Param("since") LocalDateTime since
    );

    /**
     * Calcule l'accuracy réelle du modèle
     */
    @Query("SELECT CAST(SUM(CASE WHEN p.wasCorrect = true THEN 1 ELSE 0 END) AS double) / " +
           "CAST(COUNT(p) AS double) FROM MLPredictionLog p " +
           "WHERE p.company = :company " +
           "AND p.actualOutcome IS NOT NULL " +
           "AND p.predictedAt >= :since")
    Double calculateRealWorldAccuracy(
        @Param("company") Company company,
        @Param("since") LocalDateTime since
    );

    /**
     * Compte les matches récents (30 derniers jours)
     */
    @Query("SELECT COUNT(p) FROM MLPredictionLog p " +
           "WHERE p.company = :company " +
           "AND p.actualOutcome = true " +
           "AND p.predictedAt >= :since")
    long countRecentMatches(
        @Param("company") Company company,
        @Param("since") LocalDateTime since
    );

    /**
     * Calcule le taux de match pour un client/fournisseur spécifique
     * Basé sur le thirdPartyName de la transaction bancaire
     */
    @Query("SELECT CAST(SUM(CASE WHEN p.actualOutcome = true THEN 1 ELSE 0 END) AS double) / " +
           "CAST(COUNT(p) AS double) FROM MLPredictionLog p " +
           "WHERE p.company = :company " +
           "AND p.bankTransaction.thirdPartyName = :thirdPartyName " +
           "AND p.actualOutcome IS NOT NULL")
    Double calculateCustomerMatchRate(
        @Param("company") Company company,
        @Param("thirdPartyName") String thirdPartyName
    );

    /**
     * Calcule le délai moyen de matching (en jours)
     * Différence moyenne entre date GL et date BT pour les matches réussis
     */
    @Query("SELECT AVG(ABS(CAST((p.glEntry.entryDate - p.bankTransaction.transactionDate) AS long))) " +
           "FROM MLPredictionLog p " +
           "WHERE p.company = :company " +
           "AND p.actualOutcome = true")
    Double calculateAvgMatchingDelay(
        @Param("company") Company company
    );
}
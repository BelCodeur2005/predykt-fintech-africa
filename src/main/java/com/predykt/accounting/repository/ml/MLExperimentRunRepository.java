package com.predykt.accounting.repository.ml;

import com.predykt.accounting.domain.entity.ml.MLExperiment;
import com.predykt.accounting.domain.entity.ml.MLExperimentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour les runs d'expériences ML
 *
 * @author PREDYKT ML Team
 */
@Repository
public interface MLExperimentRunRepository extends JpaRepository<MLExperimentRun, Long> {

    /**
     * Trouve tous les runs d'une expérience
     */
    List<MLExperimentRun> findByExperimentOrderByRunNumberAsc(MLExperiment experiment);

    /**
     * Trouve un run spécifique par numéro
     */
    Optional<MLExperimentRun> findByExperimentAndRunNumber(MLExperiment experiment, Integer runNumber);

    /**
     * Trouve les runs par statut
     */
    List<MLExperimentRun> findByExperimentAndStatusOrderByRunNumberAsc(MLExperiment experiment, String status);

    /**
     * Trouve les runs réussis
     */
    @Query("SELECT r FROM MLExperimentRun r WHERE r.experiment = :experiment " +
           "AND r.status = 'COMPLETED' " +
           "ORDER BY r.accuracy DESC")
    List<MLExperimentRun> findSuccessfulRunsByAccuracy(@Param("experiment") MLExperiment experiment);

    /**
     * Trouve le meilleur run (par accuracy)
     */
    @Query("SELECT r FROM MLExperimentRun r WHERE r.experiment = :experiment " +
           "AND r.status = 'COMPLETED' " +
           "AND r.accuracy IS NOT NULL " +
           "ORDER BY r.accuracy DESC LIMIT 1")
    Optional<MLExperimentRun> findBestRunByAccuracy(@Param("experiment") MLExperiment experiment);

    /**
     * Trouve le meilleur run (par F1 score)
     */
    @Query("SELECT r FROM MLExperimentRun r WHERE r.experiment = :experiment " +
           "AND r.status = 'COMPLETED' " +
           "AND r.f1Score IS NOT NULL " +
           "ORDER BY r.f1Score DESC LIMIT 1")
    Optional<MLExperimentRun> findBestRunByF1Score(@Param("experiment") MLExperiment experiment);

    /**
     * Compte les runs par statut
     */
    @Query("SELECT COUNT(r) FROM MLExperimentRun r WHERE r.experiment = :experiment AND r.status = :status")
    Long countByStatus(@Param("experiment") MLExperiment experiment, @Param("status") String status);

    /**
     * Calcule l'accuracy moyenne des runs réussis
     */
    @Query("SELECT AVG(r.accuracy) FROM MLExperimentRun r " +
           "WHERE r.experiment = :experiment " +
           "AND r.status = 'COMPLETED' " +
           "AND r.accuracy IS NOT NULL")
    Double calculateAverageAccuracy(@Param("experiment") MLExperiment experiment);

    /**
     * Trouve les runs avec un tag spécifique
     */
    @Query("SELECT r FROM MLExperimentRun r WHERE r.experiment = :experiment " +
           "AND :tag MEMBER OF r.tags " +
           "ORDER BY r.runNumber ASC")
    List<MLExperimentRun> findByTag(@Param("experiment") MLExperiment experiment, @Param("tag") String tag);

    /**
     * Trouve les runs qui ont été déployés
     */
    @Query("SELECT r FROM MLExperimentRun r WHERE r.experiment = :experiment " +
           "AND r.deployedAsModel IS NOT NULL " +
           "ORDER BY r.runNumber ASC")
    List<MLExperimentRun> findDeployedRuns(@Param("experiment") MLExperiment experiment);
}
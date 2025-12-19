package com.predykt.accounting.repository.ml;

import com.predykt.accounting.domain.entity.Company;
import com.predykt.accounting.domain.entity.ml.MLExperiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les expériences ML
 *
 * @author PREDYKT ML Team
 */
@Repository
public interface MLExperimentRepository extends JpaRepository<MLExperiment, Long> {

    /**
     * Trouve toutes les expériences d'une company
     */
    List<MLExperiment> findByCompanyOrderByCreatedAtDesc(Company company);

    /**
     * Trouve les expériences par statut
     */
    List<MLExperiment> findByCompanyAndStatusOrderByCreatedAtDesc(Company company, String status);

    /**
     * Trouve une expérience par nom
     */
    Optional<MLExperiment> findByCompanyAndExperimentName(Company company, String experimentName);

    /**
     * Trouve les expériences en cours
     */
    @Query("SELECT e FROM MLExperiment e WHERE e.company = :company AND e.status = 'RUNNING' ORDER BY e.startedAt DESC")
    List<MLExperiment> findRunningExperiments(@Param("company") Company company);

    /**
     * Trouve les expériences par type
     */
    List<MLExperiment> findByCompanyAndExperimentTypeOrderByCreatedAtDesc(Company company, String experimentType);

    /**
     * Trouve les expériences terminées récemment
     */
    @Query("SELECT e FROM MLExperiment e WHERE e.company = :company " +
           "AND e.status = 'COMPLETED' " +
           "AND e.completedAt >= :since " +
           "ORDER BY e.completedAt DESC")
    List<MLExperiment> findRecentCompletedExperiments(
        @Param("company") Company company,
        @Param("since") LocalDateTime since
    );

    /**
     * Compte les expériences par statut
     */
    @Query("SELECT COUNT(e) FROM MLExperiment e WHERE e.company = :company AND e.status = :status")
    Long countByStatus(@Param("company") Company company, @Param("status") String status);

    /**
     * Trouve les meilleures expériences (par best_accuracy)
     */
    @Query("SELECT e FROM MLExperiment e WHERE e.company = :company " +
           "AND e.bestAccuracy IS NOT NULL " +
           "ORDER BY e.bestAccuracy DESC")
    List<MLExperiment> findTopExperiments(@Param("company") Company company);

    /**
     * Trouve les expériences qui ont produit un modèle déployé
     */
    @Query("SELECT e FROM MLExperiment e WHERE e.company = :company " +
           "AND e.deployedModel IS NOT NULL " +
           "ORDER BY e.completedAt DESC")
    List<MLExperiment> findExperimentsWithDeployedModel(@Param("company") Company company);
}
package com.predykt.accounting.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Configuration Spring pour ML Matching System (Systeme Avance)
 *
 * Configure:
 * - ThreadPools pour async (entrainement, predictions batch)
 * - Cache Redis pour modeles ML
 * - Scheduling automatique
 * - Parametres ML avances (XGBoost, Stacking, SMOTE, etc.)
 *
 * Les valeurs sont lues depuis application.yaml (prefix: predykt.ml)
 *
 * @author PREDYKT ML Team
 * @version 3.0 - Advanced System Only
 */
@Configuration
@EnableCaching
@EnableScheduling
@EnableAsync
@Slf4j
@ConditionalOnProperty(
    name = "predykt.ml.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class MLConfiguration {

    /**
     * ThreadPool pour entrainements ML asynchrones
     *
     * Dimensionnement:
     * - Core: 2 threads (1 entrainement a la fois generalement)
     * - Max: 4 threads (pic lors de re-training multiple companies)
     * - Queue: 100 (buffer important car entrainements longs)
     */
    @Bean(name = "mlTrainingExecutor")
    public Executor mlTrainingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ml-training-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("🚀 [ML Config] Training Executor initialise: core=2, max=4, queue=100");
        return executor;
    }

    /**
     * ThreadPool pour predictions ML en batch
     *
     * Dimensionnement:
     * - Core: 4 threads (predictions rapides, haute concurrence)
     * - Max: 8 threads (pic lors de rapprochements massifs)
     * - Queue: 500 (buffer predictions batch)
     */
    @Bean(name = "mlPredictionExecutor")
    public Executor mlPredictionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ml-predict-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("🚀 [ML Config] Prediction Executor initialise: core=4, max=8, queue=500");
        return executor;
    }

    /**
     * Configuration du cache Redis pour modeles ML
     *
     * Cache:
     * - TTL: 24h (modeles stables)
     * - Serialization: JSON (GenericJackson2)
     * - Null values: Desactives
     *
     * Utilise par @Cacheable("mlModels") dans MLMatchingService
     */
    @Bean
    public org.springframework.cache.CacheManager cacheManager(
        org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory
    ) {
        org.springframework.data.redis.cache.RedisCacheConfiguration config =
            org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(java.time.Duration.ofHours(24))  // Cache 24h
                .disableCachingNullValues()
                .serializeValuesWith(
                    org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer())
                );

        log.info("🚀 [ML Config] Redis Cache Manager initialise: TTL=24h");

        return org.springframework.data.redis.cache.RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }

    /**
     * Bean MLProperties injectable
     */
    @Bean
    public MLProperties mlProperties() {
        MLProperties props = new MLProperties();
        log.info("🚀 [ML Config] MLProperties bean cree - Mode avance: {}, Stacking: {}",
                 props.isUseAdvancedMode(), props.isUseStacking());
        return props;
    }

    /**
     * Proprietes ML configurables via application.yaml
     *
     * Prefix: predykt.ml
     *
     * Exemple application.yaml:
     * predykt:
     *   ml:
     *     enabled: true
     *     use-advanced-mode: true
     *     use-stacking: false
     *     auto-training:
     *       enabled: true
     *     min-training-data: 50
     *     training-cron: "0 0 3 * * ?"
     */
    @ConfigurationProperties(prefix = "predykt.ml")
    @Component
    @Data
    public static class MLProperties {

        // ==================== Activation ====================

        /**
         * Active/desactive le systeme ML complet
         * Si false, aucun service ML n'est charge
         */
        private boolean enabled = true;

        // ==================== Mode Systeme ====================

        /**
         * Mode avance (XGBoost/Stacking, 30 features, SMOTE, Scaling)
         * true = Systeme avance (95-97% accuracy)
         * false = Mode basique desactive (obsolete)
         */
        private boolean useAdvancedMode = true;

        /**
         * Utiliser Stacking Ensemble au lieu de XGBoost seul
         * true = 4 algorithms + meta-learner (plus lent, +1-2% accuracy)
         * false = XGBoost seul (recommande)
         */
        private boolean useStacking = false;

        // ==================== Entrainement Automatique ====================

        /**
         * Configuration du sous-objet auto-training
         */
        private AutoTraining autoTraining = new AutoTraining();

        @Data
        public static class AutoTraining {
            /**
             * Active l'entrainement automatique nocturne
             */
            private boolean enabled = true;
        }

        // ==================== Scheduling (Cron) ====================

        /**
         * Cron pour entrainement automatique
         * Defaut: "0 0 3 * * ?" = Tous les jours a 3h00
         */
        private String trainingCron = "0 0 3 * * ?";

        /**
         * Cron pour nettoyage modeles anciens
         * Defaut: "0 0 4 * * SUN" = Tous les dimanches a 4h00
         */
        private String cleanupCron = "0 0 4 * * SUN";

        /**
         * Cron pour monitoring & drift detection
         * Defaut: "0 0 9 * * MON" = Tous les lundis a 9h00
         */
        private String monitoringCron = "0 0 9 * * MON";

        // ==================== Donnees & Seuils ====================

        /**
         * Nombre minimum d'exemples pour entrainer un modele
         * Recommande: 50 minimum (100+ ideal)
         */
        private int minTrainingData = 50;

        /**
         * Accuracy minimum acceptable
         * Si inferieur, re-training automatique declenche
         */
        private double minAccuracy = 0.85;  // 85%

        /**
         * Seuil de drift pour re-training
         * Si (accuracy_baseline - accuracy_reelle) > drift, re-training
         */
        private double maxDrift = 0.10;  // 10%

        // ==================== Stockage ====================

        /**
         * Repertoire de base pour sauvegarder les modeles
         * Structure: {modelsBaseDir}/{companyId}/{modelVersion}/
         */
        private String modelsBaseDir = "./ml-models";

        /**
         * Nombre de versions de modeles a conserver par company
         * Les plus anciens sont archives/supprimes
         */
        private int maxModelVersions = 10;

        // ==================== Parametres Avances XGBoost ====================

        /**
         * Parametres XGBoost (si use-advanced-mode=true)
         */
        private XGBoostParams xgboost = new XGBoostParams();

        @Data
        public static class XGBoostParams {
            /**
             * Nombre d'arbres (iterations)
             * Recommande: 100-500
             */
            private int numRounds = 300;

            /**
             * Taux d'apprentissage (learning rate)
             * Recommande: 0.01-0.3
             */
            private double eta = 0.1;

            /**
             * Profondeur maximale des arbres
             * Recommande: 3-10
             */
            private int maxDepth = 6;

            /**
             * Objectif d'optimisation
             * "binary:logistic" pour classification binaire (match/no-match)
             */
            private String objective = "binary:logistic";

            /**
             * Metrique d'evaluation
             * "auc" = Area Under Curve (ROC)
             */
            private String evalMetric = "auc";
        }

        // ==================== Parametres SMOTE ====================

        /**
         * Parametres SMOTE (class balancing)
         */
        private SmoteParams smote = new SmoteParams();

        @Data
        public static class SmoteParams {
            /**
             * Activer SMOTE pour equilibrer les classes
             * Recommande: true si ratio positives/negatives < 0.3
             */
            private boolean enabled = true;

            /**
             * Nombre de voisins pour SMOTE
             * Recommande: 5
             */
            private int kNeighbors = 5;

            /**
             * Ratio cible apres SMOTE
             * 1.0 = equilibre parfait (50/50)
             * 0.5 = ratio 1:2
             */
            private double samplingRatio = 0.8;
        }

        // ==================== Cross-Validation ====================

        /**
         * Nombre de folds pour cross-validation
         * Recommande: 5 (compromis precision/temps)
         */
        private int cvFolds = 5;

        /**
         * Utiliser stratified K-Fold (maintient ratio classes)
         * Recommande: true
         */
        private boolean stratifiedCV = true;
    }
}

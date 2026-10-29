package com.predykt.accounting.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Router de DataSource multi-tenant
 * Route vers la bonne base de données selon le contexte tenant
 * 
 * MODE SHARED : 1 seule BDD partagée
 * MODE DEDICATED : N BDD (1 par tenant)
 * MODE CABINET : N BDD (1 par cabinet)
 */
@Slf4j
public class MultiTenantDataSourceRouter extends AbstractRoutingDataSource {
    
    private final Map<Object, Object> dataSources = new HashMap<>();
    private final String tenantMode;
    
    public MultiTenantDataSourceRouter(
            @Value("${predykt.tenant.mode:SHARED}") String tenantMode,
            DataSource defaultDataSource) {
        
        this.tenantMode = tenantMode;
        
        // DataSource par défaut (utilisée en mode SHARED ou si routing échoue)
        setDefaultTargetDataSource(defaultDataSource);
        dataSources.put("default", defaultDataSource);
        
        log.info("🔧 MultiTenantDataSourceRouter initialisé en mode: {}", tenantMode);
    }
    
    /**
     * Détermine quelle DataSource utiliser pour la requête actuelle
     */
    @Override
    protected Object determineCurrentLookupKey() {
        if (!TenantContextHolder.hasContext()) {
            log.debug("⚠️ Pas de contexte tenant - Utilisation DataSource par défaut");
            return "default";
        }
        
        TenantContextHolder.TenantContext context = TenantContextHolder.getContext();
        
        String dataSourceKey = switch (context.getMode()) {
            case SHARED -> "default"; // Toujours la même BDD en mode partagé
            case DEDICATED -> "tenant-" + context.getTenantId();
            case CABINET -> "cabinet-" + context.getCabinetId();
        };
        
        log.debug("🔍 Routing vers DataSource: {}", dataSourceKey);
        
        return dataSourceKey;
    }
    
    /**
     * Enregistre une nouvelle DataSource pour un tenant/cabinet
     * Utilisé en mode DEDICATED ou CABINET lors de l'ajout d'un nouveau client
     */
    public synchronized void addDataSource(String key, DataSourceConfig config) {
        if (dataSources.containsKey(key)) {
            log.warn("⚠️ DataSource {} existe déjà - Ignoré", key);
            return;
        }
        
        log.info("➕ Ajout DataSource: {} -> {}:{}/{}", 
                 key, config.getHost(), config.getPort(), config.getDatabase());
        
        HikariDataSource dataSource = createDataSource(config);
        dataSources.put(key, dataSource);
        
        // Mettre à jour le routing
        setTargetDataSources(dataSources);
        afterPropertiesSet();
        
        log.info("✅ DataSource {} ajoutée avec succès", key);
    }
    
    /**
     * Retire une DataSource (lors de la suppression d'un tenant)
     */
    public synchronized void removeDataSource(String key) {
        if (!dataSources.containsKey(key) || "default".equals(key)) {
            log.warn("⚠️ Impossible de retirer DataSource {}", key);
            return;
        }
        
        log.info("➖ Suppression DataSource: {}", key);
        
        Object dataSource = dataSources.remove(key);
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
        
        setTargetDataSources(dataSources);
        afterPropertiesSet();
        
        log.info("✅ DataSource {} supprimée", key);
    }
    
    /**
     * Crée une DataSource Hikari
     */
    private HikariDataSource createDataSource(DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        hikariConfig.setJdbcUrl(String.format(
            "jdbc:postgresql://%s:%d/%s",
            config.getHost(),
            config.getPort(),
            config.getDatabase()
        ));
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        
        // Pool configuration
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        
        // Performance
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        hikariConfig.setPoolName("HikariPool-" + config.getDatabase());
        
        return new HikariDataSource(hikariConfig);
    }
    
    /**
     * Configuration DataSource
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class DataSourceConfig {
        private String host;
        private Integer port;
        private String database;
        private String username;
        @lombok.ToString.Exclude
        private String password;
        
        @lombok.Builder.Default
        private Integer maxPoolSize = 10;
        
        @lombok.Builder.Default
        private Integer minIdle = 5;
    }
}
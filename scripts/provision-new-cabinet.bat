@echo off
SETLOCAL EnableDelayedExpansion

REM ============================================================================
REM Script de Provisioning Automatise d'un Nouveau Cabinet Comptable (Windows)
REM Usage: provision-new-cabinet.bat <cabinet_id> <cabinet_name> [cabinet_domain]
REM Exemple: provision-new-cabinet.bat cabinet-douala "Cabinet Expert Douala"
REM ============================================================================

REM Verification des arguments
IF "%~1"=="" (
    echo [ERROR] Usage: %~nx0 ^<cabinet_id^> ^<cabinet_name^> [cabinet_domain]
    echo.
    echo Exemples:
    echo   %~nx0 cabinet-douala "Cabinet Expert Douala"
    echo   %~nx0 cabinet-douala "Cabinet Expert Douala" cabinet-douala.predykt.com
    exit /b 1
)

SET CABINET_ID=%~1
SET CABINET_NAME=%~2
IF "%~3"=="" (
    SET CABINET_DOMAIN=%CABINET_ID%.local
) ELSE (
    SET CABINET_DOMAIN=%~3
)

REM Configuration
SET SCRIPT_DIR=%~dp0
SET PROJECT_ROOT=%SCRIPT_DIR%..
SET ENV_FILE=%PROJECT_ROOT%\.env.cabinet-%CABINET_ID%
SET DATA_DIR=%PROJECT_ROOT%\data\cabinets\%CABINET_ID%
SET LOGS_DIR=%PROJECT_ROOT%\logs\cabinets\%CABINET_ID%
SET BACKUPS_DIR=%PROJECT_ROOT%\backups\cabinets\%CABINET_ID%

REM Generation de ports uniques (simplifiee pour Windows)
SET /A APP_PORT=8100 + %RANDOM% %% 100
SET /A DB_PORT=5500 + %RANDOM% %% 50
SET /A REDIS_PORT=6500 + %RANDOM% %% 50
SET /A PGADMIN_PORT=5050 + %RANDOM% %% 20

echo.
echo ================================================================
echo   PREDYKT - Provisioning Nouveau Cabinet Comptable
echo   Mode: CABINET (Multi-Dossiers Clients)
echo ================================================================
echo.
echo Cabinet ID       : %CABINET_ID%
echo Nom Cabinet      : %CABINET_NAME%
echo Domaine          : %CABINET_DOMAIN%
echo App Port         : %APP_PORT%
echo DB Port          : %DB_PORT%
echo Redis Port       : %REDIS_PORT%
echo PgAdmin Port     : %PGADMIN_PORT%
echo.
echo Base de donnees  : predykt_cabinet_%CABINET_ID%
echo Fichier config   : %ENV_FILE%
echo.

SET /P CONFIRM="Continuer le provisioning ? (Y/N): "
IF /I NOT "%CONFIRM%"=="Y" (
    echo Provisioning annule
    exit /b 0
)

REM ============================================================================
REM ETAPE 1: Generation des secrets securises
REM ============================================================================

echo.
echo [1/10] Generation des secrets securises...

REM Generation de mots de passe aleatoires (32 caracteres)
SET DB_PASSWORD=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%
SET REDIS_PASSWORD=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%
SET JWT_SECRET=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%
SET PGADMIN_PASSWORD=%RANDOM%%RANDOM%%RANDOM%%RANDOM%

echo [OK] Secrets generes

REM ============================================================================
REM ETAPE 2: Creation de la structure de repertoires
REM ============================================================================

echo.
echo [2/10] Creation de la structure de repertoires...

IF NOT EXIST "%DATA_DIR%\uploads" mkdir "%DATA_DIR%\uploads"
IF NOT EXIST "%DATA_DIR%\exports" mkdir "%DATA_DIR%\exports"
IF NOT EXIST "%DATA_DIR%\imports" mkdir "%DATA_DIR%\imports"
IF NOT EXIST "%DATA_DIR%\reports" mkdir "%DATA_DIR%\reports"
IF NOT EXIST "%LOGS_DIR%\application" mkdir "%LOGS_DIR%\application"
IF NOT EXIST "%LOGS_DIR%\access" mkdir "%LOGS_DIR%\access"
IF NOT EXIST "%LOGS_DIR%\error" mkdir "%LOGS_DIR%\error"
IF NOT EXIST "%BACKUPS_DIR%\postgres" mkdir "%BACKUPS_DIR%\postgres"
IF NOT EXIST "%BACKUPS_DIR%\redis" mkdir "%BACKUPS_DIR%\redis"
IF NOT EXIST "%BACKUPS_DIR%\files" mkdir "%BACKUPS_DIR%\files"

echo [OK] Structure de repertoires creee

REM ============================================================================
REM ETAPE 3: Generation du fichier .env
REM ============================================================================

echo.
echo [3/10] Generation du fichier de configuration...

(
echo # ============================================================================
echo # Configuration Cabinet: %CABINET_ID%
echo # Genere automatiquement le %DATE% %TIME%
echo # ⚠️  NE PAS COMMITER DANS GIT - Contient des secrets sensibles
echo # ============================================================================
echo.
echo # Identifiant Cabinet
echo CABINET_ID=%CABINET_ID%
echo PREDYKT_TENANT_CABINET_ID=%CABINET_ID%
echo CABINET_NAME=%CABINET_NAME%
echo CABINET_DOMAIN=%CABINET_DOMAIN%
echo.
echo # Mode Tenant
echo PREDYKT_TENANT_MODE=CABINET
echo SPRING_PROFILES_ACTIVE=cabinet
echo.
echo # Ports
echo APP_PORT=%APP_PORT%
echo DB_PORT=%DB_PORT%
echo REDIS_PORT=%REDIS_PORT%
echo PGADMIN_PORT=%PGADMIN_PORT%
echo.
echo # PostgreSQL Configuration
echo DB_HOST=postgres-cabinet-%CABINET_ID%
echo DB_NAME=predykt_cabinet_%CABINET_ID%
echo DB_USER=predykt_%CABINET_ID%
echo DB_PASSWORD=%DB_PASSWORD%
echo POSTGRES_SHARED_BUFFERS=256MB
echo POSTGRES_EFFECTIVE_CACHE_SIZE=1GB
echo POSTGRES_MAX_CONNECTIONS=100
echo.
echo # Redis Configuration
echo REDIS_HOST=redis-cabinet-%CABINET_ID%
echo REDIS_PORT=6379
echo REDIS_PASSWORD=%REDIS_PASSWORD%
echo.
echo # JWT Security
echo JWT_SECRET=%JWT_SECRET%
echo JWT_EXPIRATION=86400000
echo JWT_REFRESH_EXPIRATION=604800000
echo.
echo # PgAdmin
echo PGADMIN_DEFAULT_EMAIL=admin@%CABINET_DOMAIN%
echo PGADMIN_DEFAULT_PASSWORD=%PGADMIN_PASSWORD%
echo.
echo # Application Configuration
echo VERSION=latest
echo JAVA_OPTS=-Xms512m -Xmx2048m
echo LOGGING_LEVEL_ROOT=INFO
echo LOGGING_LEVEL_PREDYKT=DEBUG
echo MAX_FILE_SIZE=50MB
echo MAX_REQUEST_SIZE=100MB
echo.
echo # Monitoring
echo MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
echo MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always
echo.
echo # Timezone
echo TZ=Africa/Douala
echo LANG=fr_FR.UTF-8
echo.
echo # Backup
echo BACKUP_RETENTION_DAYS=30
echo BACKUP_SCHEDULE=0 2 * * *
echo.
echo # Features
echo FEATURE_ML_RECONCILIATION=true
echo FEATURE_AUTO_BACKUP=true
echo FEATURE_EMAIL_NOTIFICATIONS=false
) > "%ENV_FILE%"

echo [OK] Configuration generee: %ENV_FILE%

REM ============================================================================
REM ETAPE 4: Verification de Docker
REM ============================================================================

echo.
echo [4/10] Verification de Docker...

docker --version >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker n'est pas installe!
    echo Installez Docker Desktop: https://docs.docker.com/desktop/install/windows-install/
    exit /b 1
)

docker info >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker n'est pas demarre!
    echo Demarrez Docker Desktop et relancez ce script
    exit /b 1
)

echo [OK] Docker est operationnel

REM ============================================================================
REM ETAPE 5: Generation du docker-compose.yml
REM ============================================================================

echo.
echo [5/10] Generation du docker-compose.yml...

SET COMPOSE_FILE=%PROJECT_ROOT%\docker-compose.cabinet-%CABINET_ID%.yml

(
echo version: '3.8'
echo.
echo services:
echo   # PostgreSQL Database
echo   postgres-cabinet-%CABINET_ID%:
echo     image: postgres:15-alpine
echo     container_name: postgres-cabinet-%CABINET_ID%
echo     environment:
echo       POSTGRES_DB: ${DB_NAME}
echo       POSTGRES_USER: ${DB_USER}
echo       POSTGRES_PASSWORD: ${DB_PASSWORD}
echo       POSTGRES_INITDB_ARGS: "-E UTF8 --locale=fr_FR.UTF-8"
echo     ports:
echo       - "${DB_PORT}:5432"
echo     volumes:
echo       - postgres-data-cabinet-%CABINET_ID%:/var/lib/postgresql/data
echo       - ./scripts/init-tenant-db.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
echo     networks:
echo       - predykt-cabinet-%CABINET_ID%
echo     healthcheck:
echo       test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
echo       interval: 10s
echo       timeout: 5s
echo       retries: 5
echo     restart: unless-stopped
echo.
echo   # Redis Cache
echo   redis-cabinet-%CABINET_ID%:
echo     image: redis:7-alpine
echo     container_name: redis-cabinet-%CABINET_ID%
echo     command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 512mb --maxmemory-policy allkeys-lru
echo     ports:
echo       - "${REDIS_PORT}:6379"
echo     volumes:
echo       - redis-data-cabinet-%CABINET_ID%:/data
echo     networks:
echo       - predykt-cabinet-%CABINET_ID%
echo     healthcheck:
echo       test: ["CMD", "redis-cli", "--raw", "incr", "ping"]
echo       interval: 10s
echo       timeout: 3s
echo       retries: 5
echo     restart: unless-stopped
echo.
echo   # PgAdmin
echo   pgadmin-cabinet-%CABINET_ID%:
echo     image: dpage/pgadmin4:latest
echo     container_name: pgadmin-cabinet-%CABINET_ID%
echo     environment:
echo       PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL}
echo       PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_DEFAULT_PASSWORD}
echo       PGADMIN_CONFIG_SERVER_MODE: 'False'
echo     ports:
echo       - "${PGADMIN_PORT}:80"
echo     volumes:
echo       - pgadmin-data-cabinet-%CABINET_ID%:/var/lib/pgadmin
echo     networks:
echo       - predykt-cabinet-%CABINET_ID%
echo     restart: unless-stopped
echo     depends_on:
echo       - postgres-cabinet-%CABINET_ID%
echo.
echo   # Application PREDYKT
echo   predykt-app-cabinet-%CABINET_ID%:
echo     build:
echo       context: .
echo       dockerfile: Dockerfile
echo     container_name: predykt-app-cabinet-%CABINET_ID%
echo     environment:
echo       PREDYKT_TENANT_MODE: ${PREDYKT_TENANT_MODE}
echo       PREDYKT_TENANT_CABINET_ID: ${PREDYKT_TENANT_CABINET_ID}
echo       SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
echo       DB_HOST: ${DB_HOST}
echo       DB_PORT: 5432
echo       DB_NAME: ${DB_NAME}
echo       DB_USER: ${DB_USER}
echo       DB_PASSWORD: ${DB_PASSWORD}
echo       REDIS_HOST: ${REDIS_HOST}
echo       REDIS_PORT: ${REDIS_PORT}
echo       REDIS_PASSWORD: ${REDIS_PASSWORD}
echo       JWT_SECRET: ${JWT_SECRET}
echo       JWT_EXPIRATION: ${JWT_EXPIRATION}
echo       JWT_REFRESH_EXPIRATION: ${JWT_REFRESH_EXPIRATION}
echo       SERVER_PORT: 8080
echo       JAVA_OPTS: ${JAVA_OPTS}
echo       LOGGING_LEVEL_ROOT: ${LOGGING_LEVEL_ROOT}
echo       LOGGING_LEVEL_PREDYKT: ${LOGGING_LEVEL_PREDYKT}
echo     ports:
echo       - "${APP_PORT}:8080"
echo     volumes:
echo       - %DATA_DIR%/uploads:/app/uploads
echo       - %DATA_DIR%/exports:/app/exports
echo       - %DATA_DIR%/imports:/app/imports
echo       - %DATA_DIR%/reports:/app/reports
echo       - %LOGS_DIR%/application:/app/logs
echo     networks:
echo       - predykt-cabinet-%CABINET_ID%
echo     depends_on:
echo       postgres-cabinet-%CABINET_ID%:
echo         condition: service_healthy
echo       redis-cabinet-%CABINET_ID%:
echo         condition: service_healthy
echo     healthcheck:
echo       test: ["CMD", "curl", "-f", "http://localhost:8080/api/v1/health"]
echo       interval: 30s
echo       timeout: 10s
echo       retries: 3
echo       start_period: 60s
echo     restart: unless-stopped
echo.
echo networks:
echo   predykt-cabinet-%CABINET_ID%:
echo     driver: bridge
echo     name: predykt-cabinet-%CABINET_ID%
echo.
echo volumes:
echo   postgres-data-cabinet-%CABINET_ID%:
echo     name: postgres-data-cabinet-%CABINET_ID%
echo   redis-data-cabinet-%CABINET_ID%:
echo     name: redis-data-cabinet-%CABINET_ID%
echo   pgadmin-data-cabinet-%CABINET_ID%:
echo     name: pgadmin-data-cabinet-%CABINET_ID%
) > "%COMPOSE_FILE%"

echo [OK] docker-compose.yml genere

REM ============================================================================
REM ETAPE 6: Demarrage des services Docker
REM ============================================================================

echo.
echo [6/10] Demarrage des services Docker...

cd /d "%PROJECT_ROOT%"

echo Telechargement des images Docker...
docker-compose -f "docker-compose.cabinet-%CABINET_ID%.yml" --env-file "%ENV_FILE%" pull

echo Demarrage PostgreSQL et Redis...
docker-compose -f "docker-compose.cabinet-%CABINET_ID%.yml" --env-file "%ENV_FILE%" up -d postgres-cabinet-%CABINET_ID% redis-cabinet-%CABINET_ID% pgadmin-cabinet-%CABINET_ID%

echo [OK] Services infrastructure demarres

REM ============================================================================
REM ETAPE 7: Attente de la base de donnees
REM ============================================================================

echo.
echo [7/10] Attente de l'initialisation de PostgreSQL...

SET MAX_RETRIES=60
SET RETRY_COUNT=0

:WAIT_DB
docker exec postgres-cabinet-%CABINET_ID% pg_isready -U predykt_%CABINET_ID% -d predykt_cabinet_%CABINET_ID% >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    echo [OK] Base de donnees prete: predykt_cabinet_%CABINET_ID%
    GOTO DB_READY
)

SET /A RETRY_COUNT+=1
IF %RETRY_COUNT% GEQ %MAX_RETRIES% (
    echo [ERROR] Timeout: Base de donnees non disponible apres %MAX_RETRIES% tentatives
    echo Verifiez les logs: docker logs postgres-cabinet-%CABINET_ID%
    exit /b 1
)

timeout /t 2 /nobreak >nul
GOTO WAIT_DB

:DB_READY

REM ============================================================================
REM ETAPE 8: Construction et demarrage de l'application
REM ============================================================================

echo.
echo [8/10] Construction et demarrage de l'application PREDYKT...

echo Build de l'application Java (peut prendre quelques minutes^)...
IF EXIST "%PROJECT_ROOT%\mvnw.cmd" (
    cd /d "%PROJECT_ROOT%"
    call mvnw.cmd clean package -DskipTests
) ELSE (
    echo [WARN] Maven wrapper introuvable - Assurez-vous que l'application est deja buildee
)

echo Demarrage du conteneur application...
docker-compose -f "docker-compose.cabinet-%CABINET_ID%.yml" --env-file "%ENV_FILE%" up -d predykt-app-cabinet-%CABINET_ID%

echo [OK] Application demarree

REM ============================================================================
REM ETAPE 9: Attente que l'application soit prete
REM ============================================================================

echo.
echo [9/10] Attente du demarrage complet de l'application...

SET MAX_RETRIES=90
SET RETRY_COUNT=0

:WAIT_APP
curl -f -s "http://localhost:%APP_PORT%/api/v1/health" >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    echo [OK] Application prete et repond aux requetes
    GOTO APP_READY
)

SET /A RETRY_COUNT+=1
IF %RETRY_COUNT% GEQ %MAX_RETRIES% (
    echo [ERROR] Timeout: Application non disponible apres %MAX_RETRIES% secondes
    echo.
    echo Actions de depannage:
    echo   1. Verifier les logs: docker logs predykt-app-cabinet-%CABINET_ID%
    echo   2. Verifier la DB: docker exec postgres-cabinet-%CABINET_ID% psql -U predykt_%CABINET_ID% -d predykt_cabinet_%CABINET_ID% -c "\dt"
    exit /b 1
)

SET /A MOD=%RETRY_COUNT% %% 10
IF %MOD% EQU 0 (
    echo Attente... (%RETRY_COUNT%s / %MAX_RETRIES%s max^)
)

timeout /t 1 /nobreak >nul
GOTO WAIT_APP

:APP_READY

REM ============================================================================
REM ETAPE 10: Initialisation des donnees de test (OPTIONNEL)
REM ============================================================================

echo.
echo [10/10] Initialisation des donnees du cabinet...
echo.
SET /P CREATE_TEST="Creer des donnees de test (cabinet + 3 entreprises + utilisateurs^) ? (Y/N): "

IF /I "%CREATE_TEST%"=="Y" (
    echo Creation des donnees de test...

    REM Fichier SQL temporaire
    SET SQL_FILE=%TEMP%\init-cabinet-%CABINET_ID%.sql

    (
        echo -- Creation du cabinet
        echo INSERT INTO cabinets (id, name, domain, created_at, updated_at^)
        echo VALUES (1, '%CABINET_NAME%', '%CABINET_DOMAIN%', NOW(^), NOW(^)^)
        echo ON CONFLICT (id^) DO NOTHING;
        echo.
        echo -- Creation de 3 entreprises clientes
        echo INSERT INTO companies (id, name, email, country, currency, accounting_standard, cabinet_id, created_at, updated_at^)
        echo VALUES
        echo   (10, 'Entreprise A SARL', 'contact@entreprise-a.cm', 'CM', 'XAF', 'OHADA', 1, NOW(^), NOW(^)^),
        echo   (11, 'Entreprise B SA', 'contact@entreprise-b.cm', 'CM', 'XAF', 'OHADA', 1, NOW(^), NOW(^)^),
        echo   (12, 'Entreprise C SAS', 'contact@entreprise-c.cm', 'CM', 'XAF', 'OHADA', 1, NOW(^), NOW(^)^)
        echo ON CONFLICT (id^) DO NOTHING;
        echo.
        echo -- Creation des roles
        echo INSERT INTO roles (id, name, description, is_system^)
        echo VALUES (1, 'ROLE_ADMIN', 'Administrateur cabinet', true^)
        echo ON CONFLICT (name^) DO NOTHING;
        echo.
        echo INSERT INTO roles (id, name, description, is_system^)
        echo VALUES (2, 'ROLE_ACCOUNTANT', 'Collaborateur comptable', true^)
        echo ON CONFLICT (name^) DO NOTHING;
        echo.
        echo INSERT INTO roles (id, name, description, is_system^)
        echo VALUES (3, 'ROLE_CLIENT', 'Client (acces lecture seule^)', true^)
        echo ON CONFLICT (name^) DO NOTHING;
        echo.
        echo -- Creation utilisateur admin (mot de passe: Admin@123^)
        echo INSERT INTO users (id, email, password_hash, first_name, last_name, is_active, is_email_verified, company_id, cabinet_id, created_at, updated_at^)
        echo VALUES (100, 'admin@%CABINET_DOMAIN%', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVqNkMS7gC', 'Admin', 'Cabinet', true, true, 10, 1, NOW(^), NOW(^)^)
        echo ON CONFLICT (email, company_id^) DO NOTHING;
        echo.
        echo -- Association utilisateur → role
        echo INSERT INTO user_roles (user_id, role_id^)
        echo VALUES (100, 1^)
        echo ON CONFLICT DO NOTHING;
        echo.
        echo -- Permissions d'acces
        echo INSERT INTO user_company_access (user_id, company_id, access_level, can_view_confidential, granted_at, granted_by^)
        echo VALUES
        echo   (100, 10, 'ADMIN', true, NOW(^), 'SYSTEM'^),
        echo   (100, 11, 'ADMIN', true, NOW(^), 'SYSTEM'^),
        echo   (100, 12, 'ADMIN', true, NOW(^), 'SYSTEM'^)
        echo ON CONFLICT DO NOTHING;
    ) > "%SQL_FILE%"

    docker exec -i postgres-cabinet-%CABINET_ID% psql -U predykt_%CABINET_ID% -d predykt_cabinet_%CABINET_ID% < "%SQL_FILE%" >nul 2>&1

    IF %ERRORLEVEL% EQU 0 (
        echo [OK] Donnees de test creees avec succes
    ) ELSE (
        echo [WARN] Erreur lors de la creation des donnees de test (peut-etre deja existantes^)
    )

    del "%SQL_FILE%"
)

REM ============================================================================
REM RESUME FINAL
REM ============================================================================

echo.
echo ================================================================
echo          PROVISIONING CABINET TERMINE AVEC SUCCES
echo ================================================================
echo.
echo Cabinet Information:
echo    ID:          %CABINET_ID%
echo    Nom:         %CABINET_NAME%
echo    Domaine:     %CABINET_DOMAIN%
echo    Database:    predykt_cabinet_%CABINET_ID%
echo.
echo Services Accessibles:
echo    API:         http://localhost:%APP_PORT%
echo    Health:      http://localhost:%APP_PORT%/api/v1/health
echo    Swagger:     http://localhost:%APP_PORT%/api/v1/swagger-ui.html
echo    PgAdmin:     http://localhost:%PGADMIN_PORT%
echo.
echo Acces PgAdmin:
echo    Email:       admin@%CABINET_DOMAIN%
echo    Password:    %PGADMIN_PASSWORD%
echo.
echo Utilisateur Test (si cree^):
echo    Email:       admin@%CABINET_DOMAIN%
echo    Password:    Admin@123
echo    ⚠️  CHANGEZ le mot de passe immediatement!
echo.
echo Fichiers Importants:
echo    Config:      %ENV_FILE%
echo    Compose:     %COMPOSE_FILE%
echo    Data:        %DATA_DIR%
echo    Logs:        %LOGS_DIR%
echo    Backups:     %BACKUPS_DIR%
echo.
echo Commandes Utiles:
echo    Status:      docker-compose -f docker-compose.cabinet-%CABINET_ID%.yml ps
echo    Logs:        docker logs -f predykt-app-cabinet-%CABINET_ID%
echo    Stop:        docker-compose -f docker-compose.cabinet-%CABINET_ID%.yml stop
echo    Restart:     docker-compose -f docker-compose.cabinet-%CABINET_ID%.yml restart
echo.
echo Test de Connexion:
echo    curl http://localhost:%APP_PORT%/api/v1/health
echo.
echo Documentation:
echo    Testing Guide: TESTING_MODE_CABINET.md
echo    Auth Guide:    AUTHENTICATION.md
echo.
echo [OK] Cabinet %CABINET_ID% pret a l'emploi!
echo.
echo Pour gerer ce cabinet, utilisez:
echo    scripts\deploy-cabinet.sh %CABINET_ID% [start^|stop^|logs^|backup^|status]
echo.

REM Enregistrement dans le registre
SET REGISTRY_FILE=%PROJECT_ROOT%\cabinets-registry.log
echo %DATE% %TIME%^|%CABINET_ID%^|%CABINET_NAME%^|%CABINET_DOMAIN%^|%APP_PORT%^|%DB_PORT%^|%REDIS_PORT%^|%PGADMIN_PORT% >> "%REGISTRY_FILE%"

ENDLOCAL
pause

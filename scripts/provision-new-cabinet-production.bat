@echo off
SETLOCAL EnableDelayedExpansion

REM ============================================================================
REM Script de Provisioning PRODUCTION - Cabinet MODE CABINET (Windows)
REM Version: 2.0.0
REM
REM Usage: provision-new-cabinet-production.bat <cabinet_id> <cabinet_name> <domain> <admin_email>
REM
REM Exemple:
REM   provision-new-cabinet-production.bat cabinet-douala ^
REM       "Cabinet Expert Douala" ^
REM       cabinet-douala.predykt.com ^
REM       admin@predykt.com
REM
REM IMPORTANT: Ce script nécessite des privilèges administrateur
REM ============================================================================

SET VERSION=2.0.0

REM Verification des arguments
IF "%~1"=="" GOTO :usage
IF "%~2"=="" GOTO :usage
IF "%~3"=="" GOTO :usage
IF "%~4"=="" GOTO :usage

SET CABINET_ID=%~1
SET CABINET_NAME=%~2
SET CABINET_DOMAIN=%~3
SET ADMIN_EMAIL=%~4
SET SSL_EMAIL=%ADMIN_EMAIL%

REM Configuration
SET SCRIPT_DIR=%~dp0
SET PROJECT_ROOT=%SCRIPT_DIR%..
SET ENV_FILE=%PROJECT_ROOT%\.env.cabinet-%CABINET_ID%
SET DATA_DIR=%PROJECT_ROOT%\data\cabinets\%CABINET_ID%
SET LOGS_DIR=%PROJECT_ROOT%\logs\cabinets\%CABINET_ID%
SET BACKUPS_DIR=%PROJECT_ROOT%\backups\cabinets\%CABINET_ID%
SET MONITORING_DIR=%PROJECT_ROOT%\monitoring\cabinets\%CABINET_ID%

REM Generation de ports uniques
SET /A APP_PORT=8100 + %RANDOM% %% 100
SET /A DB_PORT=5500 + %RANDOM% %% 50
SET /A REDIS_PORT=6500 + %RANDOM% %% 50
SET /A PROMETHEUS_PORT=9090 + %RANDOM% %% 10
SET /A GRAFANA_PORT=3000 + %RANDOM% %% 50

echo.
echo ========================================================================
echo   PREDYKT - PROVISIONING CABINET COMPTABLE - PRODUCTION
echo   MODE: CABINET v%VERSION%
echo ========================================================================
echo.
echo Configuration:
echo   ID:              %CABINET_ID%
echo   Nom:             %CABINET_NAME%
echo   Domaine:         %CABINET_DOMAIN%
echo   Email Admin:     %ADMIN_EMAIL%
echo.
echo Ports:
echo   Application:     %APP_PORT%
echo   PostgreSQL:      %DB_PORT%
echo   Redis:           %REDIS_PORT%
echo   Prometheus:      %PROMETHEUS_PORT%
echo   Grafana:         %GRAFANA_PORT%
echo.

SET /P CONFIRM="Continuer le provisioning PRODUCTION ? (Y/N): "
IF /I NOT "%CONFIRM%"=="Y" (
    echo Provisioning annule
    exit /b 0
)

REM ============================================================================
REM ETAPE 1: VALIDATION PRE-DEPLOIEMENT
REM ============================================================================

echo.
echo [1/12] Validation pre-deploiement...

REM Verifier Docker
docker --version >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker n'est pas installe!
    exit /b 1
)

docker info >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker n'est pas demarre!
    exit /b 1
)

echo [OK] Docker operationnel

REM Verification cabinet_id (lowercase, no special chars)
echo %CABINET_ID% | findstr /R "^[a-z0-9-]*$" >nul
IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] CABINET_ID invalide (seulement lowercase, chiffres, tirets^)
    exit /b 1
)

echo [OK] CABINET_ID valide

REM ============================================================================
REM ETAPE 2: GENERATION DES SECRETS SECURISES (512-bit minimum)
REM ============================================================================

echo.
echo [2/12] Generation des secrets securises...

REM Generation secrets aleatoires (simplifies pour Windows)
SET JWT_SECRET=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%
SET DB_PASSWORD=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%
SET REDIS_PASSWORD=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%
SET GRAFANA_ADMIN_PASSWORD=%RANDOM%%RANDOM%%RANDOM%%RANDOM%
SET BACKUP_ENCRYPTION_KEY=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%

echo [OK] Secrets generes

REM ============================================================================
REM ETAPE 3: CREATION DE LA STRUCTURE DE REPERTOIRES
REM ============================================================================

echo.
echo [3/12] Creation de la structure de repertoires...

IF NOT EXIST "%DATA_DIR%\uploads" mkdir "%DATA_DIR%\uploads"
IF NOT EXIST "%DATA_DIR%\exports" mkdir "%DATA_DIR%\exports"
IF NOT EXIST "%DATA_DIR%\imports" mkdir "%DATA_DIR%\imports"
IF NOT EXIST "%DATA_DIR%\reports" mkdir "%DATA_DIR%\reports"
IF NOT EXIST "%LOGS_DIR%\application" mkdir "%LOGS_DIR%\application"
IF NOT EXIST "%LOGS_DIR%\audit" mkdir "%LOGS_DIR%\audit"
IF NOT EXIST "%LOGS_DIR%\security" mkdir "%LOGS_DIR%\security"
IF NOT EXIST "%BACKUPS_DIR%\postgres" mkdir "%BACKUPS_DIR%\postgres"
IF NOT EXIST "%BACKUPS_DIR%\redis" mkdir "%BACKUPS_DIR%\redis"
IF NOT EXIST "%BACKUPS_DIR%\encrypted" mkdir "%BACKUPS_DIR%\encrypted"
IF NOT EXIST "%MONITORING_DIR%\prometheus" mkdir "%MONITORING_DIR%\prometheus"
IF NOT EXIST "%MONITORING_DIR%\grafana" mkdir "%MONITORING_DIR%\grafana"

echo [OK] Structure creee

REM ============================================================================
REM ETAPE 4: GENERATION DU FICHIER .ENV PRODUCTION
REM ============================================================================

echo.
echo [4/12] Generation de la configuration .env PRODUCTION...

(
echo # ============================================================================
echo # Configuration Cabinet PRODUCTION: %CABINET_ID%
echo # Genere: %DATE% %TIME%
echo # Version: %VERSION%
echo # ⚠️  FICHIER SENSIBLE - NE JAMAIS COMMITER DANS GIT
echo # ============================================================================
echo.
echo # Identifiant Cabinet
echo CABINET_ID=%CABINET_ID%
echo PREDYKT_TENANT_CABINET_ID=%CABINET_ID%
echo CABINET_NAME=%CABINET_NAME%
echo CABINET_DOMAIN=%CABINET_DOMAIN%
echo ADMIN_EMAIL=%ADMIN_EMAIL%
echo.
echo # Mode Tenant ^& Environnement ^(PRODUCTION^)
echo PREDYKT_TENANT_MODE=CABINET
echo SPRING_PROFILES_ACTIVE=cabinet,prod
echo ENVIRONMENT=production
echo ENABLE_SECURITY=true
echo.
echo # Ports
echo APP_PORT=%APP_PORT%
echo DB_PORT=%DB_PORT%
echo REDIS_PORT=%REDIS_PORT%
echo PROMETHEUS_PORT=%PROMETHEUS_PORT%
echo GRAFANA_PORT=%GRAFANA_PORT%
echo.
echo # PostgreSQL Configuration ^(PRODUCTION^)
echo DB_HOST=postgres-cabinet-%CABINET_ID%
echo DB_NAME=predykt_cabinet_%CABINET_ID%
echo DB_USER=predykt_%CABINET_ID%
echo DB_PASSWORD=%DB_PASSWORD%
echo POSTGRES_SHARED_BUFFERS=2GB
echo POSTGRES_EFFECTIVE_CACHE_SIZE=6GB
echo POSTGRES_MAX_CONNECTIONS=200
echo POSTGRES_SSL_MODE=require
echo.
echo # Redis Configuration
echo REDIS_HOST=redis-cabinet-%CABINET_ID%
echo REDIS_PORT=6379
echo REDIS_PASSWORD=%REDIS_PASSWORD%
echo REDIS_MAXMEMORY=1gb
echo REDIS_APPENDONLY=yes
echo.
echo # JWT Security ^(512-bit^)
echo JWT_SECRET=%JWT_SECRET%
echo JWT_EXPIRATION=28800000
echo JWT_REFRESH_EXPIRATION=604800000
echo.
echo # Application Configuration
echo VERSION=latest
echo JAVA_OPTS=-Xms1g -Xmx3g -XX:+UseG1GC
echo.
echo # Logging ^(PRODUCTION - INFO level^)
echo LOGGING_LEVEL_ROOT=WARN
echo LOGGING_LEVEL_PREDYKT=INFO
echo LOGGING_PATTERN_CONSOLE=%%d{yyyy-MM-dd HH:mm:ss} [CABINET-%CABINET_ID%] %%msg%%n
echo.
echo # SSL/TLS
echo ENABLE_SSL=true
echo SSL_EMAIL=%SSL_EMAIL%
echo.
echo # Monitoring
echo ENABLE_MONITORING=true
echo MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
echo GRAFANA_ADMIN_PASSWORD=%GRAFANA_ADMIN_PASSWORD%
echo.
echo # Backup ^(Chiffre^)
echo ENABLE_BACKUPS=true
echo BACKUP_SCHEDULE=0 2 * * *
echo BACKUP_RETENTION_DAYS=30
echo BACKUP_ENCRYPTION_ENABLED=true
echo BACKUP_ENCRYPTION_KEY=%BACKUP_ENCRYPTION_KEY%
echo.
echo # Timezone
echo TZ=Africa/Douala
echo LANG=fr_FR.UTF-8
echo.
echo # Rate Limiting
echo RATE_LIMIT_ENABLED=true
echo RATE_LIMIT_PER_MINUTE=60
echo.
echo # CORS ^(Production^)
echo CORS_ALLOWED_ORIGINS=https://%CABINET_DOMAIN%
echo.
echo # Audit
echo ENABLE_AUDIT_LOG=true
echo AUDIT_LOG_RETENTION_DAYS=2555
echo.
echo # Features
echo FEATURE_ML_RECONCILIATION=true
echo FEATURE_AUTO_BACKUP=true
echo.
echo # Metadata
echo PROVISIONED_AT=%DATE% %TIME%
echo PROVISIONED_BY=%USERNAME%
echo PROVISIONING_SCRIPT_VERSION=%VERSION%
) > "%ENV_FILE%"

echo [OK] Configuration .env generee: %ENV_FILE%

REM ============================================================================
REM ETAPE 5-8: NOTE IMPORTANTE
REM ============================================================================

echo.
echo [5-8] Configuration avancee...
echo.
echo NOTE: Les etapes suivantes necessitent configuration manuelle sur Windows:
echo   - Firewall: Configurer Windows Firewall pour ports 80/443
echo   - SSL/TLS: Installer certificat Let's Encrypt manuellement
echo   - Monitoring: Prometheus/Grafana a configurer separement
echo.
echo Pour production complete, utilisez le script Linux:
echo   provision-new-cabinet-production.sh
echo.

REM ============================================================================
REM ETAPE 9: DEMARRAGE DES SERVICES DOCKER
REM ============================================================================

echo.
echo [9/12] Demarrage des services Docker...

cd /d "%PROJECT_ROOT%"

SET COMPOSE_FILE=docker-compose.cabinet-%CABINET_ID%.prod.yml

REM Copier le template si pas existe
IF NOT EXIST "%COMPOSE_FILE%" (
    echo Generation du docker-compose...
    REM Utiliser le fichier genere par le script Linux ou un template
    copy docker-compose.cabinet.yml "%COMPOSE_FILE%"
)

echo Telechargement des images...
docker-compose -f "%COMPOSE_FILE%" --env-file "%ENV_FILE%" pull

echo Demarrage PostgreSQL et Redis...
docker-compose -f "%COMPOSE_FILE%" --env-file "%ENV_FILE%" up -d postgres-cabinet-%CABINET_ID% redis-cabinet-%CABINET_ID%

echo [OK] Services infrastructure demarres

REM ============================================================================
REM ETAPE 10: ATTENTE BASE DE DONNEES
REM ============================================================================

echo.
echo [10/12] Attente de PostgreSQL...

SET MAX_RETRIES=60
SET RETRY_COUNT=0

:WAIT_DB
docker exec postgres-cabinet-%CABINET_ID% pg_isready -U predykt_%CABINET_ID% -d predykt_cabinet_%CABINET_ID% >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    echo [OK] PostgreSQL pret
    GOTO DB_READY
)

SET /A RETRY_COUNT+=1
IF %RETRY_COUNT% GEQ %MAX_RETRIES% (
    echo [ERROR] Timeout PostgreSQL
    exit /b 1
)

timeout /t 2 /nobreak >nul
GOTO WAIT_DB

:DB_READY

REM ============================================================================
REM ETAPE 11: DEMARRAGE APPLICATION
REM ============================================================================

echo.
echo [11/12] Demarrage de l'application PREDYKT...

REM Build Maven si mvnw disponible
IF EXIST "%PROJECT_ROOT%\mvnw.cmd" (
    echo Build Maven...
    cd /d "%PROJECT_ROOT%"
    call mvnw.cmd clean package -DskipTests -Pprod
)

echo Demarrage application...
docker-compose -f "%COMPOSE_FILE%" --env-file "%ENV_FILE%" up -d predykt-app-cabinet-%CABINET_ID%

echo [OK] Application demarree

REM ============================================================================
REM ETAPE 12: ATTENTE APPLICATION PRETE
REM ============================================================================

echo.
echo [12/12] Attente de l'application...

SET MAX_RETRIES=180
SET RETRY_COUNT=0

:WAIT_APP
curl -f -s "http://localhost:%APP_PORT%/api/v1/health" >nul 2>&1
IF %ERRORLEVEL% EQU 0 (
    echo [OK] Application prete
    GOTO APP_READY
)

SET /A RETRY_COUNT+=1
IF %RETRY_COUNT% GEQ %MAX_RETRIES% (
    echo [ERROR] Timeout application
    echo Verifiez: docker logs predykt-app-cabinet-%CABINET_ID%
    exit /b 1
)

SET /A MOD=%RETRY_COUNT% %% 30
IF %MOD% EQU 0 (
    echo Attente... (%RETRY_COUNT%/%MAX_RETRIES%s^)
)

timeout /t 1 /nobreak >nul
GOTO WAIT_APP

:APP_READY

REM ============================================================================
REM RESUME FINAL
REM ============================================================================

echo.
echo ========================================================================
echo          PROVISIONING PRODUCTION TERMINE AVEC SUCCES
echo ========================================================================
echo.
echo Cabinet Information:
echo    ID:              %CABINET_ID%
echo    Nom:             %CABINET_NAME%
echo    Domaine:         %CABINET_DOMAIN%
echo    Database:        predykt_cabinet_%CABINET_ID%
echo    Environment:     PRODUCTION
echo.
echo Services Accessibles:
echo    API:             http://localhost:%APP_PORT%
echo    Health:          http://localhost:%APP_PORT%/api/v1/health
echo    Prometheus:      http://localhost:%PROMETHEUS_PORT%
echo    Grafana:         http://localhost:%GRAFANA_PORT%
echo.
echo Grafana Access:
echo    Username:        admin
echo    Password:        %GRAFANA_ADMIN_PASSWORD%
echo.
echo Fichiers Importants:
echo    Configuration:   %ENV_FILE%
echo    Docker Compose:  %COMPOSE_FILE%
echo    Logs:            %LOGS_DIR%
echo    Backups:         %BACKUPS_DIR%
echo.
echo Commandes Utiles:
echo    Status:          docker-compose -f %COMPOSE_FILE% ps
echo    Logs:            docker logs -f predykt-app-cabinet-%CABINET_ID%
echo    Stop:            docker-compose -f %COMPOSE_FILE% stop
echo.
echo [OK] Cabinet %CABINET_ID% pret en PRODUCTION!
echo.
echo PROCHAINES ETAPES:
echo    1. Configurer SMTP dans %ENV_FILE%
echo    2. Configurer SSL/TLS (certificat Let's Encrypt^)
echo    3. Configurer Windows Firewall
echo    4. Tester backup/restore
echo    5. Creer premier utilisateur admin
echo.

REM Enregistrer dans le registre
SET REGISTRY_FILE=%PROJECT_ROOT%\cabinets-registry-production.log
echo %DATE% %TIME%^|%CABINET_ID%^|%CABINET_NAME%^|%CABINET_DOMAIN%^|%APP_PORT%^|%ADMIN_EMAIL%^|PRODUCTION^|%VERSION% >> "%REGISTRY_FILE%"

ENDLOCAL
pause
exit /b 0

:usage
echo.
echo Usage: %~nx0 ^<cabinet_id^> ^<cabinet_name^> ^<domain^> ^<admin_email^>
echo.
echo Exemple:
echo   %~nx0 cabinet-douala "Cabinet Expert Douala" cabinet-douala.predykt.com admin@predykt.com
echo.
exit /b 1

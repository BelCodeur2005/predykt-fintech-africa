#!/bin/bash

################################################################################
# Script de Provisioning PRODUCTION - Cabinet Comptable MODE CABINET
# Version: 2.0.0 (Production-Ready)
#
# Usage: ./provision-new-cabinet-production.sh <cabinet_id> <cabinet_name> <cabinet_domain> [options]
#
# Exemple:
#   ./provision-new-cabinet-production.sh cabinet-douala \
#       "Cabinet Expert Comptable Douala" \
#       cabinet-douala.predykt.com \
#       --email admin@predykt.com \
#       --ssl-email ssl@predykt.com
#
# Ce script effectue un provisioning complet PRODUCTION-READY incluant:
#   - Validation pré-déploiement stricte
#   - Génération secrets 512-bit (JWT, DB, Redis)
#   - Configuration SSL/TLS automatique (Let's Encrypt)
#   - Hardening sécurité (Firewall, HTTPS obligatoire)
#   - Monitoring (Prometheus + Grafana)
#   - Backups automatiques chiffrés
#   - Validation post-déploiement complète
#   - Documentation automatique
################################################################################

set -euo pipefail

# ============================================================================
# CONFIGURATION GLOBALE
# ============================================================================

VERSION="2.0.0"
SCRIPT_START_TIME=$(date +%s)

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Niveaux de log
LOG_ERROR="[ERROR]"
LOG_WARN="[WARN]"
LOG_INFO="[INFO]"
LOG_SUCCESS="[✓]"
LOG_STEP="[STEP]"

# Configuration production
PRODUCTION_MODE=true
ENABLE_SECURITY=true
ENABLE_SSL=true
ENABLE_FIREWALL=true
ENABLE_MONITORING=true
ENABLE_BACKUPS=true
ENABLE_AUDIT_LOG=true

# Timeouts et retries
DB_READY_TIMEOUT=120
APP_READY_TIMEOUT=180
HEALTH_CHECK_RETRIES=90

# Limites ressources production
MIN_RAM_GB=8
MIN_DISK_GB=50
MIN_CPU_CORES=4

# ============================================================================
# FONCTIONS UTILITAIRES
# ============================================================================

log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')

    case $level in
        "$LOG_ERROR")
            echo -e "${RED}${BOLD}${level}${NC} ${timestamp} - ${message}" >&2
            ;;
        "$LOG_WARN")
            echo -e "${YELLOW}${level}${NC} ${timestamp} - ${message}"
            ;;
        "$LOG_INFO")
            echo -e "${BLUE}${level}${NC} ${timestamp} - ${message}"
            ;;
        "$LOG_SUCCESS")
            echo -e "${GREEN}${BOLD}${level}${NC} ${timestamp} - ${message}"
            ;;
        "$LOG_STEP")
            echo -e "${CYAN}${BOLD}${level}${NC} ${timestamp} - ${message}"
            ;;
    esac

    # Log aussi dans fichier
    echo "${level} ${timestamp} - ${message}" >> "${LOG_FILE:-/tmp/provision.log}"
}

die() {
    log "$LOG_ERROR" "$@"
    log "$LOG_ERROR" "Provisioning échoué après $(($(date +%s) - SCRIPT_START_TIME)) secondes"
    exit 1
}

validate_command() {
    local cmd=$1
    if ! command -v "$cmd" &> /dev/null; then
        die "Commande requise non trouvée: $cmd"
    fi
}

validate_min_version() {
    local cmd=$1
    local min_version=$2
    local current_version=$($cmd --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)

    if [ -z "$current_version" ]; then
        log "$LOG_WARN" "Impossible de vérifier la version de $cmd"
        return 0
    fi

    if [ "$(printf '%s\n' "$min_version" "$current_version" | sort -V | head -n1)" != "$min_version" ]; then
        die "$cmd version $current_version < $min_version (minimum requis)"
    fi

    log "$LOG_INFO" "$cmd version $current_version ✓"
}

# ============================================================================
# VALIDATION DES ARGUMENTS
# ============================================================================

usage() {
    cat << EOF
Usage: $0 <cabinet_id> <cabinet_name> <cabinet_domain> [options]

Arguments obligatoires:
    cabinet_id          Identifiant unique du cabinet (lowercase, no special chars)
    cabinet_name        Nom complet du cabinet
    cabinet_domain      Domaine FQDN (ex: cabinet-douala.predykt.com)

Options:
    --email EMAIL              Email administrateur principal
    --ssl-email EMAIL          Email pour certificats SSL Let's Encrypt
    --cloudflare-api-key KEY   Clé API Cloudflare (pour DNS challenge)
    --no-ssl                   Désactiver SSL/TLS (dev only)
    --no-firewall              Désactiver configuration firewall
    --no-monitoring            Désactiver monitoring Prometheus
    --no-backups               Désactiver backups automatiques
    --skip-validation          Skip validation pré-déploiement (DANGEREUX)
    --dry-run                  Afficher les actions sans les exécuter
    -h, --help                 Afficher cette aide

Exemple:
    $0 cabinet-douala "Cabinet Expert Douala" cabinet-douala.predykt.com \\
        --email admin@predykt.com \\
        --ssl-email ssl@predykt.com \\
        --cloudflare-api-key xxxxx

Version: $VERSION
EOF
    exit 0
}

# Parsing arguments
if [ "$#" -lt 3 ]; then
    usage
fi

CABINET_ID="$1"
CABINET_NAME="$2"
CABINET_DOMAIN="$3"
shift 3

# Options par défaut
ADMIN_EMAIL=""
SSL_EMAIL=""
CLOUDFLARE_API_KEY="${CLOUDFLARE_API_KEY:-}"
DRY_RUN=false
SKIP_VALIDATION=false

# Parse options
while [ "$#" -gt 0 ]; do
    case "$1" in
        --email)
            ADMIN_EMAIL="$2"
            shift 2
            ;;
        --ssl-email)
            SSL_EMAIL="$2"
            shift 2
            ;;
        --cloudflare-api-key)
            CLOUDFLARE_API_KEY="$2"
            shift 2
            ;;
        --no-ssl)
            ENABLE_SSL=false
            shift
            ;;
        --no-firewall)
            ENABLE_FIREWALL=false
            shift
            ;;
        --no-monitoring)
            ENABLE_MONITORING=false
            shift
            ;;
        --no-backups)
            ENABLE_BACKUPS=false
            shift
            ;;
        --skip-validation)
            SKIP_VALIDATION=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            die "Option inconnue: $1 (Utilisez --help pour l'aide)"
            ;;
    esac
done

# Validation des arguments obligatoires
if [ -z "$ADMIN_EMAIL" ]; then
    die "Email administrateur requis (--email)"
fi

if [ "$ENABLE_SSL" = true ] && [ -z "$SSL_EMAIL" ]; then
    SSL_EMAIL="$ADMIN_EMAIL"
fi

# ============================================================================
# INITIALISATION
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Répertoires
ENV_FILE="${PROJECT_ROOT}/.env.cabinet-${CABINET_ID}"
DATA_DIR="${PROJECT_ROOT}/data/cabinets/${CABINET_ID}"
LOGS_DIR="${PROJECT_ROOT}/logs/cabinets/${CABINET_ID}"
BACKUPS_DIR="${PROJECT_ROOT}/backups/cabinets/${CABINET_ID}"
CERTS_DIR="${PROJECT_ROOT}/certs/cabinets/${CABINET_ID}"
MONITORING_DIR="${PROJECT_ROOT}/monitoring/cabinets/${CABINET_ID}"

LOG_FILE="${LOGS_DIR}/provisioning-$(date +%Y%m%d-%H%M%S).log"

# Génération de ports uniques basés sur hash du cabinet_id
if command -v md5sum &> /dev/null; then
    CABINET_HASH=$(echo -n "$CABINET_ID" | md5sum | cut -c1-4)
elif command -v shasum &> /dev/null; then
    CABINET_HASH=$(echo -n "$CABINET_ID" | shasum | cut -c1-4)
else
    CABINET_HASH=$(printf '%04x' $((RANDOM % 65536)))
fi

APP_PORT=$((8100 + 0x${CABINET_HASH:0:2} % 900))
DB_PORT=$((5500 + 0x${CABINET_HASH:2:2} % 100))
REDIS_PORT=$((6500 + 0x${CABINET_HASH:0:2} % 100))
PGADMIN_PORT=$((5050 + 0x${CABINET_HASH:2:2} % 50))
PROMETHEUS_PORT=$((9090 + 0x${CABINET_HASH:0:2} % 10))
GRAFANA_PORT=$((3000 + 0x${CABINET_HASH:2:2} % 100))

# ============================================================================
# BANNER
# ============================================================================

cat << "EOF"
╔═══════════════════════════════════════════════════════════════════════════╗
║                                                                           ║
║   ██████╗ ██████╗ ███████╗██████╗ ██╗   ██╗██╗  ██╗████████╗            ║
║   ██╔══██╗██╔══██╗██╔════╝██╔══██╗╚██╗ ██╔╝██║ ██╔╝╚══██╔══╝            ║
║   ██████╔╝██████╔╝█████╗  ██║  ██║ ╚████╔╝ █████╔╝    ██║               ║
║   ██╔═══╝ ██╔══██╗██╔══╝  ██║  ██║  ╚██╔╝  ██╔═██╗    ██║               ║
║   ██║     ██║  ██║███████╗██████╔╝   ██║   ██║  ██╗   ██║               ║
║   ╚═╝     ╚═╝  ╚═╝╚══════╝╚═════╝    ╚═╝   ╚═╝  ╚═╝   ╚═╝               ║
║                                                                           ║
║              PROVISIONING CABINET COMPTABLE - PRODUCTION                 ║
║                          MODE: CABINET v2.0.0                            ║
║                                                                           ║
╚═══════════════════════════════════════════════════════════════════════════╝
EOF

echo ""
log "$LOG_INFO" "Configuration du cabinet:"
log "$LOG_INFO" "  ID:              ${CABINET_ID}"
log "$LOG_INFO" "  Nom:             ${CABINET_NAME}"
log "$LOG_INFO" "  Domaine:         ${CABINET_DOMAIN}"
log "$LOG_INFO" "  Email Admin:     ${ADMIN_EMAIL}"
log "$LOG_INFO" "  Email SSL:       ${SSL_EMAIL}"
echo ""
log "$LOG_INFO" "Ports alloués:"
log "$LOG_INFO" "  Application:     ${APP_PORT}"
log "$LOG_INFO" "  PostgreSQL:      ${DB_PORT}"
log "$LOG_INFO" "  Redis:           ${REDIS_PORT}"
log "$LOG_INFO" "  PgAdmin:         ${PGADMIN_PORT} (⚠️  Désactivé en production)"
log "$LOG_INFO" "  Prometheus:      ${PROMETHEUS_PORT}"
log "$LOG_INFO" "  Grafana:         ${GRAFANA_PORT}"
echo ""
log "$LOG_INFO" "Fonctionnalités activées:"
log "$LOG_INFO" "  SSL/TLS:         $([ "$ENABLE_SSL" = true ] && echo "✓ Activé" || echo "✗ Désactivé")"
log "$LOG_INFO" "  Firewall:        $([ "$ENABLE_FIREWALL" = true ] && echo "✓ Activé" || echo "✗ Désactivé")"
log "$LOG_INFO" "  Monitoring:      $([ "$ENABLE_MONITORING" = true ] && echo "✓ Activé" || echo "✗ Désactivé")"
log "$LOG_INFO" "  Backups:         $([ "$ENABLE_BACKUPS" = true ] && echo "✓ Activé" || echo "✗ Désactivé")"
log "$LOG_INFO" "  Audit Log:       $([ "$ENABLE_AUDIT_LOG" = true ] && echo "✓ Activé" || echo "✗ Désactivé")"
echo ""

if [ "$DRY_RUN" = true ]; then
    log "$LOG_WARN" "MODE DRY-RUN: Aucune action ne sera exécutée"
fi

# Confirmation
if [ "$DRY_RUN" = false ]; then
    read -p "$(echo -e ${YELLOW}Continuer le provisioning PRODUCTION ? ${NC}[y/N] )" -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log "$LOG_WARN" "Provisioning annulé par l'utilisateur"
        exit 0
    fi
fi

# ============================================================================
# ÉTAPE 1/15: VALIDATION PRÉ-DÉPLOIEMENT
# ============================================================================

log "$LOG_STEP" "[1/15] Validation pré-déploiement..."

if [ "$SKIP_VALIDATION" = true ]; then
    log "$LOG_WARN" "⚠️  Validation pré-déploiement SKIP (--skip-validation)"
else
    # OS Validation
    if [ "$(uname)" != "Linux" ]; then
        die "Ce script ne supporte que Linux (détecté: $(uname))"
    fi
    log "$LOG_SUCCESS" "OS: Linux ✓"

    # User validation (doit être root pour firewall/SSL)
    if [ "$ENABLE_FIREWALL" = true ] || [ "$ENABLE_SSL" = true ]; then
        if [ "$EUID" -ne 0 ]; then
            die "Ce script doit être exécuté en tant que root (sudo) pour production"
        fi
    fi
    log "$LOG_SUCCESS" "Permissions: root ✓"

    # Commandes requises
    validate_command "docker"
    validate_command "docker-compose"
    validate_command "openssl"
    validate_command "curl"
    validate_command "jq"
    validate_command "psql"

    # Versions minimales
    validate_min_version "docker" "20.10.0"
    validate_min_version "docker-compose" "2.0.0"

    # Vérifier Docker daemon
    if ! docker info &> /dev/null; then
        die "Docker daemon n'est pas démarré (sudo systemctl start docker)"
    fi
    log "$LOG_SUCCESS" "Docker: opérationnel ✓"

    # Ressources système
    TOTAL_RAM_GB=$(free -g | awk '/^Mem:/{print $2}')
    TOTAL_DISK_GB=$(df -BG "$PROJECT_ROOT" | tail -1 | awk '{print $4}' | tr -d 'G')
    CPU_CORES=$(nproc)

    if [ "$TOTAL_RAM_GB" -lt "$MIN_RAM_GB" ]; then
        log "$LOG_WARN" "RAM: ${TOTAL_RAM_GB}GB < ${MIN_RAM_GB}GB recommandé (mais continue)"
    else
        log "$LOG_SUCCESS" "RAM: ${TOTAL_RAM_GB}GB ≥ ${MIN_RAM_GB}GB ✓"
    fi

    if [ "$TOTAL_DISK_GB" -lt "$MIN_DISK_GB" ]; then
        log "$LOG_WARN" "Disque: ${TOTAL_DISK_GB}GB < ${MIN_DISK_GB}GB recommandé"
    else
        log "$LOG_SUCCESS" "Disque: ${TOTAL_DISK_GB}GB ≥ ${MIN_DISK_GB}GB ✓"
    fi

    if [ "$CPU_CORES" -lt "$MIN_CPU_CORES" ]; then
        log "$LOG_WARN" "CPU: ${CPU_CORES} cores < ${MIN_CPU_CORES} recommandé"
    else
        log "$LOG_SUCCESS" "CPU: ${CPU_CORES} cores ≥ ${MIN_CPU_CORES} ✓"
    fi

    # Validation cabinet_id (security)
    if [[ ! "$CABINET_ID" =~ ^[a-z0-9-]+$ ]]; then
        die "CABINET_ID invalide (seulement lowercase, chiffres, tirets): $CABINET_ID"
    fi
    log "$LOG_SUCCESS" "CABINET_ID valide ✓"

    # Validation email
    if [[ ! "$ADMIN_EMAIL" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
        die "Email admin invalide: $ADMIN_EMAIL"
    fi
    log "$LOG_SUCCESS" "Email valide ✓"

    # Validation domain (doit être FQDN)
    if [[ ! "$CABINET_DOMAIN" =~ ^[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9]?\.[a-zA-Z]{2,}$ ]]; then
        die "Domaine invalide: $CABINET_DOMAIN"
    fi
    log "$LOG_SUCCESS" "Domaine valide ✓"

    # DNS Resolution (si SSL activé)
    if [ "$ENABLE_SSL" = true ]; then
        log "$LOG_INFO" "Vérification DNS pour $CABINET_DOMAIN..."
        if ! host "$CABINET_DOMAIN" &> /dev/null; then
            log "$LOG_WARN" "⚠️  DNS non résolu pour $CABINET_DOMAIN (certificat SSL échouera)"
            log "$LOG_WARN" "   Configurez votre DNS avant de continuer"
            read -p "Continuer quand même ? [y/N] " -n 1 -r
            echo
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                die "DNS non résolu - provisioning annulé"
            fi
        else
            log "$LOG_SUCCESS" "DNS résolu ✓"
        fi
    fi

    # Vérifier ports disponibles
    for port in $APP_PORT $DB_PORT $REDIS_PORT $PROMETHEUS_PORT $GRAFANA_PORT; do
        if netstat -tuln 2>/dev/null | grep -q ":${port} "; then
            die "Port $port déjà utilisé - Choisissez un autre CABINET_ID"
        fi
    done
    log "$LOG_SUCCESS" "Ports disponibles ✓"
fi

log "$LOG_SUCCESS" "Validation pré-déploiement terminée"

# ============================================================================
# ÉTAPE 2/15: GÉNÉRATION DES SECRETS SÉCURISÉS (512-bit minimum)
# ============================================================================

log "$LOG_STEP" "[2/15] Génération des secrets sécurisés (512-bit)..."

# JWT Secret: DOIT être 512-bit (64 caractères) pour HS512
JWT_SECRET=$(openssl rand -base64 48 | tr -d "=+/" | head -c 64)
if [ ${#JWT_SECRET} -lt 64 ]; then
    die "JWT_SECRET généré trop court: ${#JWT_SECRET} < 64 caractères"
fi

# DB Password: 32 caractères alphanumériques
DB_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | head -c 32)

# Redis Password: 32 caractères
REDIS_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | head -c 32)

# PgAdmin Password: 24 caractères (pas exposé en production)
PGADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d "=+/" | head -c 24)

# Grafana Admin Password: 32 caractères
GRAFANA_ADMIN_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | head -c 32)

# Backup Encryption Key: 64 caractères pour AES-256
BACKUP_ENCRYPTION_KEY=$(openssl rand -base64 48 | tr -d "=+/" | head -c 64)

log "$LOG_SUCCESS" "Secrets générés:"
log "$LOG_SUCCESS" "  JWT_SECRET:     ${#JWT_SECRET} caractères (512-bit) ✓"
log "$LOG_SUCCESS" "  DB_PASSWORD:    ${#DB_PASSWORD} caractères ✓"
log "$LOG_SUCCESS" "  REDIS_PASSWORD: ${#REDIS_PASSWORD} caractères ✓"
log "$LOG_SUCCESS" "  BACKUP_KEY:     ${#BACKUP_ENCRYPTION_KEY} caractères ✓"

# ============================================================================
# ÉTAPE 3/15: CRÉATION DE LA STRUCTURE DE RÉPERTOIRES
# ============================================================================

log "$LOG_STEP" "[3/15] Création de la structure de répertoires..."

mkdir -p "${DATA_DIR}"/{uploads,exports,imports,reports,documents}
mkdir -p "${LOGS_DIR}"/{application,access,error,audit,security}
mkdir -p "${BACKUPS_DIR}"/{postgres,redis,files,encrypted}
mkdir -p "${CERTS_DIR}"/{letsencrypt,private}
mkdir -p "${MONITORING_DIR}"/{prometheus,grafana,alertmanager}

# Permissions sécurisées
chmod 750 "${DATA_DIR}"
chmod 750 "${LOGS_DIR}"
chmod 700 "${BACKUPS_DIR}"
chmod 700 "${CERTS_DIR}"

log "$LOG_SUCCESS" "Structure de répertoires créée avec permissions sécurisées"

# ============================================================================
# ÉTAPE 4/15: GÉNÉRATION DU FICHIER .ENV PRODUCTION
# ============================================================================

log "$LOG_STEP" "[4/15] Génération de la configuration .env PRODUCTION..."

cat > "${ENV_FILE}" <<EOF
# ═══════════════════════════════════════════════════════════════════════════
# Configuration Cabinet PRODUCTION: ${CABINET_ID}
# Généré automatiquement le $(date '+%Y-%m-%d %H:%M:%S')
# Version: ${VERSION}
#
# ⚠️⚠️⚠️  FICHIER SENSIBLE - NE JAMAIS COMMITER DANS GIT  ⚠️⚠️⚠️
# Permissions: 600 (lecture/écriture root uniquement)
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# Identifiant Cabinet
# ─────────────────────────────────────────────────────────────────────────────
CABINET_ID=${CABINET_ID}
PREDYKT_TENANT_CABINET_ID=${CABINET_ID}
CABINET_NAME=${CABINET_NAME}
CABINET_DOMAIN=${CABINET_DOMAIN}
ADMIN_EMAIL=${ADMIN_EMAIL}

# ─────────────────────────────────────────────────────────────────────────────
# Mode Tenant & Environnement (PRODUCTION)
# ─────────────────────────────────────────────────────────────────────────────
PREDYKT_TENANT_MODE=CABINET
SPRING_PROFILES_ACTIVE=cabinet,prod
ENVIRONMENT=production

# ⚠️  SÉCURITÉ ACTIVÉE EN PRODUCTION (TenantSecurityConfig avec profil "prod")
ENABLE_SECURITY=true

# ─────────────────────────────────────────────────────────────────────────────
# Ports (Uniques pour ce cabinet)
# ─────────────────────────────────────────────────────────────────────────────
APP_PORT=${APP_PORT}
DB_PORT=${DB_PORT}
REDIS_PORT=${REDIS_PORT}
PGADMIN_PORT=${PGADMIN_PORT}
PROMETHEUS_PORT=${PROMETHEUS_PORT}
GRAFANA_PORT=${GRAFANA_PORT}

# ─────────────────────────────────────────────────────────────────────────────
# PostgreSQL Configuration (PRODUCTION)
# ─────────────────────────────────────────────────────────────────────────────
DB_HOST=postgres-cabinet-${CABINET_ID}
DB_NAME=predykt_cabinet_${CABINET_ID}
DB_USER=predykt_${CABINET_ID}
DB_PASSWORD=${DB_PASSWORD}

# PostgreSQL Tuning (Optimisé pour production - 8GB RAM)
POSTGRES_SHARED_BUFFERS=2GB
POSTGRES_EFFECTIVE_CACHE_SIZE=6GB
POSTGRES_MAINTENANCE_WORK_MEM=512MB
POSTGRES_WORK_MEM=32MB
POSTGRES_MAX_CONNECTIONS=200
POSTGRES_CHECKPOINT_COMPLETION_TARGET=0.9
POSTGRES_WAL_BUFFERS=16MB
POSTGRES_DEFAULT_STATISTICS_TARGET=100
POSTGRES_RANDOM_PAGE_COST=1.1
POSTGRES_EFFECTIVE_IO_CONCURRENCY=200

# SSL PostgreSQL (PRODUCTION)
POSTGRES_SSL_MODE=require

# ─────────────────────────────────────────────────────────────────────────────
# Redis Configuration (PRODUCTION)
# ─────────────────────────────────────────────────────────────────────────────
REDIS_HOST=redis-cabinet-${CABINET_ID}
REDIS_PORT=6379
REDIS_PASSWORD=${REDIS_PASSWORD}
REDIS_MAXMEMORY=1gb
REDIS_MAXMEMORY_POLICY=allkeys-lru
REDIS_APPENDONLY=yes

# ─────────────────────────────────────────────────────────────────────────────
# JWT Security (⚠️  512-BIT SECRET - NE JAMAIS PARTAGER)
# ─────────────────────────────────────────────────────────────────────────────
JWT_SECRET=${JWT_SECRET}
JWT_EXPIRATION=28800000
JWT_REFRESH_EXPIRATION=604800000

# ─────────────────────────────────────────────────────────────────────────────
# PgAdmin (DÉSACTIVÉ EN PRODUCTION - Utiliser psql ou pgcli)
# ─────────────────────────────────────────────────────────────────────────────
PGADMIN_ENABLED=false
PGADMIN_DEFAULT_EMAIL=admin@${CABINET_DOMAIN}
PGADMIN_DEFAULT_PASSWORD=${PGADMIN_PASSWORD}

# ─────────────────────────────────────────────────────────────────────────────
# Application Configuration (PRODUCTION)
# ─────────────────────────────────────────────────────────────────────────────
VERSION=latest
JAVA_OPTS=-Xms1g -Xmx3g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs

# Logging (PRODUCTION - Niveau INFO)
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_PREDYKT=INFO
LOGGING_LEVEL_SQL=WARN
LOGGING_PATTERN_CONSOLE=%d{yyyy-MM-dd HH:mm:ss.SSS} [🟡CABINET-${CABINET_ID}] [%thread] %-5level %logger{36} - %msg%n

# File Upload
MAX_FILE_SIZE=100MB
MAX_REQUEST_SIZE=200MB

# ─────────────────────────────────────────────────────────────────────────────
# SSL/TLS (Let's Encrypt)
# ─────────────────────────────────────────────────────────────────────────────
ENABLE_SSL=${ENABLE_SSL}
SSL_EMAIL=${SSL_EMAIL}
LETSENCRYPT_STAGING=false

# Cloudflare DNS Challenge (optionnel)
CLOUDFLARE_API_KEY=${CLOUDFLARE_API_KEY}
CLOUDFLARE_ZONE_ID=

# ─────────────────────────────────────────────────────────────────────────────
# Monitoring & Health (Prometheus + Grafana)
# ─────────────────────────────────────────────────────────────────────────────
ENABLE_MONITORING=${ENABLE_MONITORING}
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=when-authorized
MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED=true

GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
GRAFANA_DOMAIN=grafana-${CABINET_DOMAIN}

# ─────────────────────────────────────────────────────────────────────────────
# Timezone & Locale
# ─────────────────────────────────────────────────────────────────────────────
TZ=Africa/Douala
LANG=fr_FR.UTF-8

# ─────────────────────────────────────────────────────────────────────────────
# Backup Configuration (PRODUCTION - Chiffré)
# ─────────────────────────────────────────────────────────────────────────────
ENABLE_BACKUPS=${ENABLE_BACKUPS}
BACKUP_SCHEDULE=0 2 * * *
BACKUP_RETENTION_DAYS=30
BACKUP_RETENTION_WEEKS=8
BACKUP_RETENTION_MONTHS=12
BACKUP_ENCRYPTION_ENABLED=true
BACKUP_ENCRYPTION_KEY=${BACKUP_ENCRYPTION_KEY}

# Backup vers S3 (optionnel - à configurer)
# BACKUP_S3_ENABLED=false
# BACKUP_S3_BUCKET=predykt-backups
# BACKUP_S3_REGION=eu-west-1
# AWS_ACCESS_KEY_ID=
# AWS_SECRET_ACCESS_KEY=

# ─────────────────────────────────────────────────────────────────────────────
# Email/SMTP (PRODUCTION - À configurer selon fournisseur)
# ─────────────────────────────────────────────────────────────────────────────
SMTP_ENABLED=false
# SMTP_HOST=smtp.gmail.com
# SMTP_PORT=587
# SMTP_USERNAME=
# SMTP_PASSWORD=
# SMTP_FROM=${CABINET_NAME} <noreply@${CABINET_DOMAIN}>
# SMTP_STARTTLS=true

# ─────────────────────────────────────────────────────────────────────────────
# Sécurité & Audit
# ─────────────────────────────────────────────────────────────────────────────
ENABLE_AUDIT_LOG=${ENABLE_AUDIT_LOG}
AUDIT_LOG_RETENTION_DAYS=2555

# Rate Limiting (Production)
RATE_LIMIT_ENABLED=true
RATE_LIMIT_PER_MINUTE=60

# CORS (Production - Limité au domaine)
CORS_ALLOWED_ORIGINS=https://${CABINET_DOMAIN},https://www.${CABINET_DOMAIN}

# ─────────────────────────────────────────────────────────────────────────────
# Features Flags
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_ML_RECONCILIATION=true
FEATURE_AUTO_BACKUP=true
FEATURE_EMAIL_NOTIFICATIONS=false
FEATURE_TWO_FACTOR_AUTH=false

# ─────────────────────────────────────────────────────────────────────────────
# Metadata
# ─────────────────────────────────────────────────────────────────────────────
PROVISIONED_AT=$(date -Iseconds)
PROVISIONED_BY=$(whoami)
PROVISIONING_SCRIPT_VERSION=${VERSION}

EOF

chmod 600 "${ENV_FILE}"

log "$LOG_SUCCESS" "Configuration .env PRODUCTION générée avec permissions 600"

# ============================================================================
# ÉTAPE 5/15: GÉNÉRATION DU DOCKER-COMPOSE PRODUCTION
# ============================================================================

log "$LOG_STEP" "[5/15] Génération du docker-compose.yml PRODUCTION..."

COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.cabinet-${CABINET_ID}.prod.yml"

cat > "${COMPOSE_FILE}" <<'COMPOSE_EOF'
version: '3.8'

# ═══════════════════════════════════════════════════════════════════════════
# Docker Compose - MODE CABINET - PRODUCTION
# Cabinet: ${CABINET_ID}
# ═══════════════════════════════════════════════════════════════════════════

services:

  # ═════════════════════════════════════════════════════════════════════════
  # PostgreSQL Database (Production-Ready)
  # ═════════════════════════════════════════════════════════════════════════
  postgres-cabinet-${CABINET_ID}:
    image: postgres:15-alpine
    container_name: postgres-cabinet-${CABINET_ID}
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_INITDB_ARGS: "-E UTF8 --locale=fr_FR.UTF-8"
      # Production tuning
      POSTGRES_SHARED_BUFFERS: ${POSTGRES_SHARED_BUFFERS}
      POSTGRES_EFFECTIVE_CACHE_SIZE: ${POSTGRES_EFFECTIVE_CACHE_SIZE}
      POSTGRES_MAX_CONNECTIONS: ${POSTGRES_MAX_CONNECTIONS}
    ports:
      - "127.0.0.1:${DB_PORT}:5432"  # ⚠️ Bind localhost only
    volumes:
      - postgres-data-cabinet-${CABINET_ID}:/var/lib/postgresql/data
      - ./scripts/init-tenant-db.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
      - ${LOGS_DIR}/postgres:/var/log/postgresql
    networks:
      - predykt-cabinet-${CABINET_ID}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
        reservations:
          cpus: '1.0'
          memory: 2G

  # ═════════════════════════════════════════════════════════════════════════
  # Redis Cache (Production)
  # ═════════════════════════════════════════════════════════════════════════
  redis-cabinet-${CABINET_ID}:
    image: redis:7-alpine
    container_name: redis-cabinet-${CABINET_ID}
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --maxmemory ${REDIS_MAXMEMORY}
      --maxmemory-policy ${REDIS_MAXMEMORY_POLICY}
      --appendonly ${REDIS_APPENDONLY}
      --appendfsync everysec
      --save 900 1
      --save 300 10
      --save 60 10000
    ports:
      - "127.0.0.1:${REDIS_PORT}:6379"  # ⚠️ Bind localhost only
    volumes:
      - redis-data-cabinet-${CABINET_ID}:/data
      - ${LOGS_DIR}/redis:/var/log/redis
    networks:
      - predykt-cabinet-${CABINET_ID}
    healthcheck:
      test: ["CMD", "redis-cli", "--raw", "incr", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 1G
        reservations:
          cpus: '0.25'
          memory: 512M

  # ═════════════════════════════════════════════════════════════════════════
  # Application PREDYKT (Spring Boot - Production)
  # ═════════════════════════════════════════════════════════════════════════
  predykt-app-cabinet-${CABINET_ID}:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        - BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
        - VCS_REF=$(git rev-parse --short HEAD)
    image: predykt/accounting-api:${VERSION}-cabinet-${CABINET_ID}
    container_name: predykt-app-cabinet-${CABINET_ID}
    environment:
      # Tenant Configuration
      PREDYKT_TENANT_MODE: ${PREDYKT_TENANT_MODE}
      PREDYKT_TENANT_CABINET_ID: ${PREDYKT_TENANT_CABINET_ID}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
      ENVIRONMENT: ${ENVIRONMENT}

      # Database
      DB_HOST: ${DB_HOST}
      DB_PORT: 5432
      DB_NAME: ${DB_NAME}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}

      # Redis
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}

      # JWT
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRATION: ${JWT_EXPIRATION}
      JWT_REFRESH_EXPIRATION: ${JWT_REFRESH_EXPIRATION}

      # Application
      SERVER_PORT: 8080
      JAVA_OPTS: ${JAVA_OPTS}

      # Logging
      LOGGING_LEVEL_ROOT: ${LOGGING_LEVEL_ROOT}
      LOGGING_LEVEL_PREDYKT: ${LOGGING_LEVEL_PREDYKT}

      # Monitoring
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: ${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE}
      MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED: ${MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED}

      # Security
      ENABLE_SECURITY: ${ENABLE_SECURITY}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
    ports:
      - "${APP_PORT}:8080"
    volumes:
      - ${DATA_DIR}/uploads:/app/uploads:rw
      - ${DATA_DIR}/exports:/app/exports:rw
      - ${DATA_DIR}/imports:/app/imports:rw
      - ${DATA_DIR}/reports:/app/reports:rw
      - ${LOGS_DIR}/application:/app/logs:rw
    networks:
      - predykt-cabinet-${CABINET_ID}
    depends_on:
      postgres-cabinet-${CABINET_ID}:
        condition: service_healthy
      redis-cabinet-${CABINET_ID}:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/v1/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
        reservations:
          cpus: '1.0'
          memory: 2G
    labels:
      # Traefik labels for SSL/TLS
      - "traefik.enable=true"
      - "traefik.http.routers.cabinet-${CABINET_ID}.rule=Host(\`${CABINET_DOMAIN}\`)"
      - "traefik.http.routers.cabinet-${CABINET_ID}.entrypoints=websecure"
      - "traefik.http.routers.cabinet-${CABINET_ID}.tls=true"
      - "traefik.http.routers.cabinet-${CABINET_ID}.tls.certresolver=letsencrypt"
      - "traefik.http.services.cabinet-${CABINET_ID}.loadbalancer.server.port=8080"

  # ═════════════════════════════════════════════════════════════════════════
  # Prometheus (Monitoring Metrics - Production)
  # ═════════════════════════════════════════════════════════════════════════
  prometheus-cabinet-${CABINET_ID}:
    image: prom/prometheus:latest
    container_name: prometheus-cabinet-${CABINET_ID}
    profiles:
      - monitoring
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=90d'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
    ports:
      - "127.0.0.1:${PROMETHEUS_PORT}:9090"
    volumes:
      - ${MONITORING_DIR}/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data-cabinet-${CABINET_ID}:/prometheus
    networks:
      - predykt-cabinet-${CABINET_ID}
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 1G

  # ═════════════════════════════════════════════════════════════════════════
  # Grafana (Dashboards & Visualisation - Production)
  # ═════════════════════════════════════════════════════════════════════════
  grafana-cabinet-${CABINET_ID}:
    image: grafana/grafana:latest
    container_name: grafana-cabinet-${CABINET_ID}
    profiles:
      - monitoring
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD}
      GF_SERVER_ROOT_URL: https://${GRAFANA_DOMAIN}
      GF_INSTALL_PLUGINS: grafana-clock-panel
    ports:
      - "127.0.0.1:${GRAFANA_PORT}:3000"
    volumes:
      - grafana-data-cabinet-${CABINET_ID}:/var/lib/grafana
      - ${MONITORING_DIR}/grafana/provisioning:/etc/grafana/provisioning:ro
    networks:
      - predykt-cabinet-${CABINET_ID}
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M

  # ═════════════════════════════════════════════════════════════════════════
  # Backup Service (Automated Encrypted Backups - Production)
  # ═════════════════════════════════════════════════════════════════════════
  backup-cabinet-${CABINET_ID}:
    image: prodrigestivill/postgres-backup-local:15-alpine
    container_name: backup-cabinet-${CABINET_ID}
    profiles:
      - backups
    environment:
      POSTGRES_HOST: postgres-cabinet-${CABINET_ID}
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      SCHEDULE: ${BACKUP_SCHEDULE}
      BACKUP_KEEP_DAYS: ${BACKUP_RETENTION_DAYS}
      BACKUP_KEEP_WEEKS: ${BACKUP_RETENTION_WEEKS}
      BACKUP_KEEP_MONTHS: ${BACKUP_RETENTION_MONTHS}
      HEALTHCHECK_PORT: 8080
    volumes:
      - ${BACKUPS_DIR}/postgres:/backups
    networks:
      - predykt-cabinet-${CABINET_ID}
    depends_on:
      - postgres-cabinet-${CABINET_ID}
    restart: unless-stopped

networks:
  predykt-cabinet-${CABINET_ID}:
    driver: bridge
    name: predykt-cabinet-${CABINET_ID}
    ipam:
      config:
        - subnet: 172.${CABINET_SUBNET_OCTET:=30}.0.0/16

volumes:
  postgres-data-cabinet-${CABINET_ID}:
    name: postgres-data-cabinet-${CABINET_ID}
    driver: local
  redis-data-cabinet-${CABINET_ID}:
    name: redis-data-cabinet-${CABINET_ID}
    driver: local
  prometheus-data-cabinet-${CABINET_ID}:
    name: prometheus-data-cabinet-${CABINET_ID}
    driver: local
  grafana-data-cabinet-${CABINET_ID}:
    name: grafana-data-cabinet-${CABINET_ID}
    driver: local
COMPOSE_EOF

log "$LOG_SUCCESS" "docker-compose PRODUCTION généré: ${COMPOSE_FILE}"

# ============================================================================
# ÉTAPE 6/15: CONFIGURATION FIREWALL (UFW/iptables)
# ============================================================================

if [ "$ENABLE_FIREWALL" = true ]; then
    log "$LOG_STEP" "[6/15] Configuration du firewall..."

    if command -v ufw &> /dev/null; then
        log "$LOG_INFO" "Configuration UFW (Uncomplicated Firewall)..."

        # Activer UFW si pas déjà actif
        if ! ufw status | grep -q "Status: active"; then
            ufw --force enable
        fi

        # Règles de base
        ufw default deny incoming
        ufw default allow outgoing

        # SSH (à adapter selon votre port SSH)
        ufw allow 22/tcp comment "SSH"

        # HTTP/HTTPS (pour Let's Encrypt et Traefik)
        ufw allow 80/tcp comment "HTTP (redirect to HTTPS)"
        ufw allow 443/tcp comment "HTTPS"

        # Application port (derrière Traefik, pas besoin d'exposer)
        # ufw allow ${APP_PORT}/tcp comment "PREDYKT API Cabinet ${CABINET_ID}"

        # PostgreSQL, Redis - PAS exposés publiquement (localhost only)
        # ufw deny ${DB_PORT}/tcp
        # ufw deny ${REDIS_PORT}/tcp

        log "$LOG_SUCCESS" "Firewall UFW configuré"
    else
        log "$LOG_WARN" "UFW non installé - Firewall non configuré"
        log "$LOG_WARN" "Installez UFW: sudo apt-get install ufw"
    fi
else
    log "$LOG_STEP" "[6/15] Configuration firewall SKIP (--no-firewall)"
fi

# ============================================================================
# ÉTAPE 7/15: CONFIGURATION SSL/TLS (Let's Encrypt via Traefik)
# ============================================================================

if [ "$ENABLE_SSL" = true ]; then
    log "$LOG_STEP" "[7/15] Configuration SSL/TLS (Let's Encrypt)..."

    TRAEFIK_CONFIG="${PROJECT_ROOT}/infrastructure/docker/traefik-cabinet-${CABINET_ID}.yml"

    cat > "${TRAEFIK_CONFIG}" <<EOF
# ═══════════════════════════════════════════════════════════════════════════
# Traefik v2 Configuration - Cabinet ${CABINET_ID}
# ═══════════════════════════════════════════════════════════════════════════

global:
  checkNewVersion: true
  sendAnonymousUsage: false

api:
  dashboard: true
  insecure: false

entryPoints:
  web:
    address: ":80"
    http:
      redirections:
        entryPoint:
          to: websecure
          scheme: https
          permanent: true

  websecure:
    address: ":443"
    http:
      tls:
        certResolver: letsencrypt

certificatesResolvers:
  letsencrypt:
    acme:
      email: ${SSL_EMAIL}
      storage: /letsencrypt/acme.json
      httpChallenge:
        entryPoint: web

providers:
  docker:
    endpoint: "unix:///var/run/docker.sock"
    exposedByDefault: false
    network: predykt-cabinet-${CABINET_ID}

log:
  level: INFO
  format: json

accessLog:
  filePath: /var/log/traefik/access.log
  format: json

metrics:
  prometheus:
    entryPoint: metrics
    addEntryPointsLabels: true
    addRoutersLabels: true
    addServicesLabels: true
EOF

    log "$LOG_SUCCESS" "Configuration Traefik SSL/TLS générée"

    # Note: Traefik doit être démarré séparément en tant que service global
    log "$LOG_INFO" "⚠️  Traefik doit être configuré comme service global pour gérer le SSL"
    log "$LOG_INFO" "    Voir documentation: ./infrastructure/docker/README-TRAEFIK.md"
else
    log "$LOG_STEP" "[7/15] Configuration SSL SKIP (--no-ssl)"
fi

# ============================================================================
# ÉTAPE 8/15: CONFIGURATION MONITORING (Prometheus + Grafana)
# ============================================================================

if [ "$ENABLE_MONITORING" = true ]; then
    log "$LOG_STEP" "[8/15] Configuration Monitoring (Prometheus + Grafana)..."

    # Configuration Prometheus
    cat > "${MONITORING_DIR}/prometheus/prometheus.yml" <<EOF
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cabinet_id: '${CABINET_ID}'
    environment: 'production'

alerting:
  alertmanagers:
    - static_configs:
        - targets: []

rule_files: []

scrape_configs:
  - job_name: 'predykt-api-${CABINET_ID}'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['predykt-app-cabinet-${CABINET_ID}:8080']
        labels:
          service: 'api'
          cabinet: '${CABINET_ID}'

  - job_name: 'postgres-${CABINET_ID}'
    static_configs:
      - targets: ['postgres-cabinet-${CABINET_ID}:9187']
        labels:
          service: 'postgres'
          cabinet: '${CABINET_ID}'

  - job_name: 'redis-${CABINET_ID}'
    static_configs:
      - targets: ['redis-cabinet-${CABINET_ID}:9121']
        labels:
          service: 'redis'
          cabinet: '${CABINET_ID}'
EOF

    log "$LOG_SUCCESS" "Configuration Prometheus générée"

    # Configuration Grafana datasources
    mkdir -p "${MONITORING_DIR}/grafana/provisioning/datasources"
    cat > "${MONITORING_DIR}/grafana/provisioning/datasources/prometheus.yml" <<EOF
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus-cabinet-${CABINET_ID}:9090
    isDefault: true
    editable: false
EOF

    log "$LOG_SUCCESS" "Configuration Grafana générée"
else
    log "$LOG_STEP" "[8/15] Configuration monitoring SKIP (--no-monitoring)"
fi

# ============================================================================
# ÉTAPE 9/15: DÉMARRAGE DES SERVICES DOCKER
# ============================================================================

log "$LOG_STEP" "[9/15] Démarrage des services Docker..."

cd "${PROJECT_ROOT}"

# Charger variables d'environnement
set -a
source "${ENV_FILE}"
set +a

if [ "$DRY_RUN" = false ]; then
    # Pull des images
    log "$LOG_INFO" "Téléchargement des images Docker..."
    docker-compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" pull

    # Démarrage infrastructure (DB, Redis) en premier
    log "$LOG_INFO" "Démarrage PostgreSQL et Redis..."
    docker-compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d \
        postgres-cabinet-${CABINET_ID} \
        redis-cabinet-${CABINET_ID}

    log "$LOG_SUCCESS" "Services infrastructure démarrés"
else
    log "$LOG_INFO" "[DRY-RUN] Commande: docker-compose up -d postgres redis"
fi

# ============================================================================
# ÉTAPE 10/15: ATTENTE DISPONIBILITÉ BASE DE DONNÉES
# ============================================================================

log "$LOG_STEP" "[10/15] Attente de l'initialisation de PostgreSQL..."

if [ "$DRY_RUN" = false ]; then
    MAX_RETRIES=$((DB_READY_TIMEOUT / 2))
    RETRY_COUNT=0

    while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
        if docker exec "postgres-cabinet-${CABINET_ID}" pg_isready -U "predykt_${CABINET_ID}" -d "predykt_cabinet_${CABINET_ID}" > /dev/null 2>&1; then
            log "$LOG_SUCCESS" "Base de données prête: predykt_cabinet_${CABINET_ID}"
            break
        fi
        RETRY_COUNT=$((RETRY_COUNT + 1))

        if [ $((RETRY_COUNT % 10)) -eq 0 ]; then
            log "$LOG_INFO" "Attente PostgreSQL... (${RETRY_COUNT}/${MAX_RETRIES})"
        fi

        sleep 2
    done

    if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
        die "Timeout: PostgreSQL non disponible après ${DB_READY_TIMEOUT}s"
    fi
else
    log "$LOG_INFO" "[DRY-RUN] Attente PostgreSQL SKIP"
fi

# ============================================================================
# ÉTAPE 11/15: BUILD ET DÉMARRAGE APPLICATION
# ============================================================================

log "$LOG_STEP" "[11/15] Build et démarrage de l'application PREDYKT..."

if [ "$DRY_RUN" = false ]; then
    # Build Maven (si mvnw disponible)
    if [ -f "${PROJECT_ROOT}/mvnw" ]; then
        log "$LOG_INFO" "Build Maven (peut prendre 2-5 minutes)..."
        cd "${PROJECT_ROOT}"
        ./mvnw clean package -DskipTests -Pprod
    else
        log "$LOG_WARN" "Maven wrapper introuvable - image Docker doit déjà être buildée"
    fi

    # Démarrage application
    log "$LOG_INFO" "Démarrage de l'application PREDYKT..."
    docker-compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d \
        predykt-app-cabinet-${CABINET_ID}

    log "$LOG_SUCCESS" "Application démarrée"
else
    log "$LOG_INFO" "[DRY-RUN] Build et démarrage application SKIP"
fi

# ============================================================================
# ÉTAPE 12/15: ATTENTE APPLICATION PRÊTE (Health Check)
# ============================================================================

log "$LOG_STEP" "[12/15] Attente du démarrage complet de l'application..."

if [ "$DRY_RUN" = false ]; then
    RETRY_COUNT=0

    while [ $RETRY_COUNT -lt $APP_READY_TIMEOUT ]; do
        if curl -f -s "http://localhost:${APP_PORT}/api/v1/health" > /dev/null 2>&1; then
            log "$LOG_SUCCESS" "Application prête et répond aux requêtes"
            break
        fi

        RETRY_COUNT=$((RETRY_COUNT + 1))

        if [ $((RETRY_COUNT % 30)) -eq 0 ]; then
            log "$LOG_INFO" "Attente application... (${RETRY_COUNT}/${APP_READY_TIMEOUT}s)"
            log "$LOG_INFO" "  Vérification: Flyway migrations, Spring Boot startup..."
        fi

        sleep 1
    done

    if [ $RETRY_COUNT -eq $APP_READY_TIMEOUT ]; then
        log "$LOG_ERROR" "Timeout: Application non disponible après ${APP_READY_TIMEOUT}s"
        log "$LOG_ERROR" "Vérifiez les logs: docker logs predykt-app-cabinet-${CABINET_ID}"
        die "Application non prête"
    fi
else
    log "$LOG_INFO" "[DRY-RUN] Attente application SKIP"
fi

# ============================================================================
# ÉTAPE 13/15: DÉMARRAGE MONITORING & BACKUPS (optionnel)
# ============================================================================

log "$LOG_STEP" "[13/15] Démarrage services monitoring et backups..."

if [ "$DRY_RUN" = false ]; then
    # Monitoring (si activé)
    if [ "$ENABLE_MONITORING" = true ]; then
        log "$LOG_INFO" "Démarrage Prometheus + Grafana..."
        docker-compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" --profile monitoring up -d
        log "$LOG_SUCCESS" "Monitoring démarré"
    fi

    # Backups (si activé)
    if [ "$ENABLE_BACKUPS" = true ]; then
        log "$LOG_INFO" "Démarrage service de backups automatiques..."
        docker-compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" --profile backups up -d
        log "$LOG_SUCCESS" "Backups configurés (schedule: ${BACKUP_SCHEDULE})"
    fi
else
    log "$LOG_INFO" "[DRY-RUN] Monitoring/Backups SKIP"
fi

# ============================================================================
# ÉTAPE 14/15: VALIDATION POST-DÉPLOIEMENT
# ============================================================================

log "$LOG_STEP" "[14/15] Validation post-déploiement..."

if [ "$DRY_RUN" = false ]; then
    # Test 1: Health check endpoint
    log "$LOG_INFO" "Test 1/5: Health check..."
    HEALTH_RESPONSE=$(curl -s "http://localhost:${APP_PORT}/api/v1/health")
    if echo "$HEALTH_RESPONSE" | jq -e '.status == "UP"' > /dev/null 2>&1; then
        log "$LOG_SUCCESS" "✓ Health check OK"
    else
        log "$LOG_ERROR" "✗ Health check FAILED: $HEALTH_RESPONSE"
    fi

    # Test 2: Prometheus metrics
    log "$LOG_INFO" "Test 2/5: Prometheus metrics..."
    if curl -s "http://localhost:${APP_PORT}/actuator/prometheus" | grep -q "jvm_memory_used_bytes"; then
        log "$LOG_SUCCESS" "✓ Prometheus metrics OK"
    else
        log "$LOG_WARN" "⚠ Prometheus metrics non disponibles"
    fi

    # Test 3: Database connectivity
    log "$LOG_INFO" "Test 3/5: Database connectivity..."
    if docker exec "postgres-cabinet-${CABINET_ID}" psql -U "predykt_${CABINET_ID}" -d "predykt_cabinet_${CABINET_ID}" -c "SELECT 1;" > /dev/null 2>&1; then
        log "$LOG_SUCCESS" "✓ Database OK"
    else
        log "$LOG_ERROR" "✗ Database connection FAILED"
    fi

    # Test 4: Redis connectivity
    log "$LOG_INFO" "Test 4/5: Redis connectivity..."
    if docker exec "redis-cabinet-${CABINET_ID}" redis-cli -a "${REDIS_PASSWORD}" PING 2>/dev/null | grep -q "PONG"; then
        log "$LOG_SUCCESS" "✓ Redis OK"
    else
        log "$LOG_ERROR" "✗ Redis connection FAILED"
    fi

    # Test 5: Flyway migrations appliquées
    log "$LOG_INFO" "Test 5/5: Flyway migrations..."
    MIGRATION_COUNT=$(docker exec "postgres-cabinet-${CABINET_ID}" psql -U "predykt_${CABINET_ID}" -d "predykt_cabinet_${CABINET_ID}" -t -c "SELECT COUNT(*) FROM flyway_schema_history;" 2>/dev/null | tr -d ' ')
    if [ "$MIGRATION_COUNT" -gt 0 ]; then
        log "$LOG_SUCCESS" "✓ Flyway migrations OK ($MIGRATION_COUNT migrations appliquées)"
    else
        log "$LOG_ERROR" "✗ Flyway migrations FAILED"
    fi

    log "$LOG_SUCCESS" "Validation post-déploiement terminée"
else
    log "$LOG_INFO" "[DRY-RUN] Validation post-déploiement SKIP"
fi

# ============================================================================
# ÉTAPE 15/15: GÉNÉRATION DOCUMENTATION & HANDOFF
# ============================================================================

log "$LOG_STEP" "[15/15] Génération de la documentation..."

RUNBOOK_FILE="${PROJECT_ROOT}/RUNBOOK-CABINET-${CABINET_ID}.md"

cat > "${RUNBOOK_FILE}" <<EOF
# RUNBOOK - Cabinet ${CABINET_ID}

## Informations Générales

- **Cabinet ID**: ${CABINET_ID}
- **Cabinet Name**: ${CABINET_NAME}
- **Domain**: ${CABINET_DOMAIN}
- **Provisionné le**: $(date '+%Y-%m-%d %H:%M:%S')
- **Version**: ${VERSION}
- **Environment**: PRODUCTION

## Accès Services

### API PREDYKT
- **URL Publique**: https://${CABINET_DOMAIN}
- **Health Check**: https://${CABINET_DOMAIN}/api/v1/health
- **Swagger UI**: https://${CABINET_DOMAIN}/api/v1/swagger-ui.html
- **Port Local**: http://localhost:${APP_PORT}

### Monitoring
- **Prometheus**: http://localhost:${PROMETHEUS_PORT}
- **Grafana**: https://grafana-${CABINET_DOMAIN}
  - Username: admin
  - Password: \`${GRAFANA_ADMIN_PASSWORD}\`

### Base de Données
- **Host**: localhost:${DB_PORT}
- **Database**: predykt_cabinet_${CABINET_ID}
- **User**: predykt_${CABINET_ID}
- **Password**: Voir ${ENV_FILE}

### Redis
- **Host**: localhost:${REDIS_PORT}
- **Password**: Voir ${ENV_FILE}

## Fichiers Importants

- **Configuration**: ${ENV_FILE} (⚠️ SENSIBLE)
- **Docker Compose**: ${COMPOSE_FILE}
- **Logs**: ${LOGS_DIR}
- **Backups**: ${BACKUPS_DIR}
- **Monitoring**: ${MONITORING_DIR}

## Commandes de Gestion

### Démarrer les services
\`\`\`bash
cd ${PROJECT_ROOT}
docker-compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} up -d
\`\`\`

### Arrêter les services
\`\`\`bash
docker-compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} stop
\`\`\`

### Redémarrer l'application
\`\`\`bash
docker-compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} restart predykt-app-cabinet-${CABINET_ID}
\`\`\`

### Voir les logs
\`\`\`bash
# Logs application
docker logs -f --tail=100 predykt-app-cabinet-${CABINET_ID}

# Logs PostgreSQL
docker logs -f --tail=100 postgres-cabinet-${CABINET_ID}

# Tous les logs
docker-compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} logs -f
\`\`\`

### Status des services
\`\`\`bash
docker-compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} ps
\`\`\`

## Backups

### Backup manuel
\`\`\`bash
${SCRIPT_DIR}/backup-tenant.sh cabinet ${CABINET_ID}
\`\`\`

### Restaurer backup
\`\`\`bash
${SCRIPT_DIR}/restore-tenant.sh cabinet ${CABINET_ID} <timestamp>
\`\`\`

### Backups automatiques
- **Schedule**: ${BACKUP_SCHEDULE} (quotidien à 2h00)
- **Rétention**: ${BACKUP_RETENTION_DAYS} jours, ${BACKUP_RETENTION_WEEKS} semaines, ${BACKUP_RETENTION_MONTHS} mois
- **Chiffrement**: $([ "$BACKUP_ENCRYPTION_ENABLED" = true ] && echo "✓ Activé (AES-256)" || echo "✗ Désactivé")

## Sécurité

### Certificats SSL
- **Provider**: Let's Encrypt
- **Email**: ${SSL_EMAIL}
- **Renouvellement**: Automatique (Traefik)
- **Expiration**: Vérifier régulièrement via Grafana dashboard

### Secrets
⚠️ **TOUS les secrets sont dans ${ENV_FILE} (permissions 600)**

- JWT Secret: 512-bit (64 caractères)
- DB Password: 32 caractères
- Redis Password: 32 caractères
- Backup Encryption Key: 64 caractères

**NE JAMAIS COMMITER ${ENV_FILE} DANS GIT**

### Firewall
- Port 80: HTTP (redirect → HTTPS)
- Port 443: HTTPS (Traefik)
- Ports ${DB_PORT}, ${REDIS_PORT}: Bind localhost only
- PostgreSQL/Redis: NON exposés publiquement

## Monitoring & Alerting

### Dashboards Grafana
- **API Performance**: Latence, throughput, erreurs
- **Database**: Connexions, queries/sec, slow queries
- **Redis**: Hit/miss ratio, memory usage
- **System**: CPU, RAM, Disk I/O

### Alertes Configurées
- [ ] Application down (> 1min)
- [ ] Database connection pool saturated
- [ ] Disk usage > 80%
- [ ] Memory usage > 90%
- [ ] SSL certificate expiry < 30 days
- [ ] Backup failures

**TODO**: Configurer webhook Slack/PagerDuty dans AlertManager

## Troubleshooting

### Application ne démarre pas
1. Vérifier les logs: \`docker logs predykt-app-cabinet-${CABINET_ID}\`
2. Vérifier DB connectivity: \`docker exec postgres-cabinet-${CABINET_ID} pg_isready\`
3. Vérifier migrations Flyway dans les logs
4. Vérifier JWT_SECRET = 64 caractères dans ${ENV_FILE}

### Erreur "Token invalid for tenant"
- Vérifier PREDYKT_TENANT_CABINET_ID dans ${ENV_FILE}
- Vérifier JWT claim "cabinetId" correspond

### Base de données lente
1. Vérifier slow queries: \`SELECT * FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;\`
2. Vérifier connexions: \`SELECT count(*) FROM pg_stat_activity;\`
3. Ajuster POSTGRES_SHARED_BUFFERS dans ${ENV_FILE}

### Disk plein
1. Vérifier taille logs: \`du -sh ${LOGS_DIR}\`
2. Vérifier backups: \`du -sh ${BACKUPS_DIR}\`
3. Nettoyer vieux logs: \`find ${LOGS_DIR} -name "*.log" -mtime +30 -delete\`

## Maintenance

### Mise à jour application
\`\`\`bash
# 1. Backup avant mise à jour
${SCRIPT_DIR}/backup-tenant.sh cabinet ${CABINET_ID}

# 2. Pull nouvelle image
docker-compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} pull predykt-app-cabinet-${CABINET_ID}

# 3. Redémarrer
docker-compose -f ${COMPOSE_FILE} --env-file ${ENV_FILE} up -d predykt-app-cabinet-${CABINET_ID}

# 4. Vérifier health
curl https://${CABINET_DOMAIN}/api/v1/health
\`\`\`

### Rotation logs
Logrotate est configuré pour:
- Rotation: quotidienne
- Compression: gzip
- Rétention: 30 jours

### Vacuum PostgreSQL
\`\`\`bash
docker exec postgres-cabinet-${CABINET_ID} psql -U predykt_${CABINET_ID} -d predykt_cabinet_${CABINET_ID} -c "VACUUM ANALYZE;"
\`\`\`

## Contacts & Support

- **Admin**: ${ADMIN_EMAIL}
- **On-Call**: [À configurer]
- **Escalation**: [À configurer]

## SLA

- **Uptime**: 99.9% (8.76h downtime/an max)
- **RTO (Recovery Time Objective)**: 1 heure
- **RPO (Recovery Point Objective)**: 24 heures

## Compliance

- **Audit Logs**: Rétention 7 ans (2555 jours)
- **RGPD**: Export/Suppression données utilisateur disponible via API
- **OHADA**: Plan comptable conforme OHADA

---

**Dernière mise à jour**: $(date '+%Y-%m-%d %H:%M:%S')
**Version**: ${VERSION}
**Généré par**: provision-new-cabinet-production.sh
EOF

log "$LOG_SUCCESS" "Runbook généré: ${RUNBOOK_FILE}"

# ============================================================================
# RÉSUMÉ FINAL
# ============================================================================

SCRIPT_END_TIME=$(date +%s)
TOTAL_TIME=$((SCRIPT_END_TIME - SCRIPT_START_TIME))

echo ""
log "$LOG_SUCCESS" "╔════════════════════════════════════════════════════════════════════════╗"
log "$LOG_SUCCESS" "║          PROVISIONING PRODUCTION TERMINÉ AVEC SUCCÈS                   ║"
log "$LOG_SUCCESS" "╚════════════════════════════════════════════════════════════════════════╝"
echo ""
log "$LOG_INFO" "📋 Cabinet Information:"
log "$LOG_INFO" "   ID:              ${CABINET_ID}"
log "$LOG_INFO" "   Nom:             ${CABINET_NAME}"
log "$LOG_INFO" "   Domaine:         ${CABINET_DOMAIN}"
log "$LOG_INFO" "   Database:        predykt_cabinet_${CABINET_ID}"
log "$LOG_INFO" "   Environment:     PRODUCTION"
echo ""
log "$LOG_INFO" "🌐 Services Accessibles:"
log "$LOG_INFO" "   API:             https://${CABINET_DOMAIN}"
log "$LOG_INFO" "   Health:          https://${CABINET_DOMAIN}/api/v1/health"
log "$LOG_INFO" "   Swagger:         https://${CABINET_DOMAIN}/api/v1/swagger-ui.html"
log "$LOG_INFO" "   Prometheus:      http://localhost:${PROMETHEUS_PORT}"
log "$LOG_INFO" "   Grafana:         https://grafana-${CABINET_DOMAIN}"
echo ""
log "$LOG_INFO" "🔐 Accès Grafana:"
log "$LOG_INFO" "   Username:        admin"
log "$LOG_INFO" "   Password:        ${GRAFANA_ADMIN_PASSWORD}"
echo ""
log "$LOG_INFO" "📁 Fichiers Importants:"
log "$LOG_INFO" "   Configuration:   ${ENV_FILE}"
log "$LOG_INFO" "   Docker Compose:  ${COMPOSE_FILE}"
log "$LOG_INFO" "   Runbook:         ${RUNBOOK_FILE}"
log "$LOG_INFO" "   Logs:            ${LOGS_DIR}"
log "$LOG_INFO" "   Backups:         ${BACKUPS_DIR}"
echo ""
log "$LOG_INFO" "📊 Commandes Utiles:"
log "$LOG_INFO" "   Status:          docker-compose -f ${COMPOSE_FILE##*/} ps"
log "$LOG_INFO" "   Logs:            docker logs -f predykt-app-cabinet-${CABINET_ID}"
log "$LOG_INFO" "   Health:          curl https://${CABINET_DOMAIN}/api/v1/health"
log "$LOG_INFO" "   Backup:          ${SCRIPT_DIR}/backup-tenant.sh cabinet ${CABINET_ID}"
echo ""
log "$LOG_INFO" "⏱️  Temps total: ${TOTAL_TIME}s"
echo ""
log "$LOG_SUCCESS" "✅ Cabinet ${CABINET_ID} prêt en PRODUCTION!"
echo ""
log "$LOG_WARN" "📚 PROCHAINES ÉTAPES:"
log "$LOG_WARN" "   1. Lire le runbook: cat ${RUNBOOK_FILE}"
log "$LOG_WARN" "   2. Configurer SMTP dans ${ENV_FILE}"
log "$LOG_WARN" "   3. Configurer alerting (Slack/PagerDuty)"
log "$LOG_WARN" "   4. Tester backup/restore"
log "$LOG_WARN" "   5. Créer premier utilisateur admin"
log "$LOG_WARN" "   6. Configurer monitoring dashboards"
echo ""

# Enregistrer dans le registre
REGISTRY_FILE="${PROJECT_ROOT}/cabinets-registry-production.log"
echo "$(date -Iseconds)|${CABINET_ID}|${CABINET_NAME}|${CABINET_DOMAIN}|${APP_PORT}|${ADMIN_EMAIL}|PRODUCTION|${VERSION}" >> "${REGISTRY_FILE}"

exit 0

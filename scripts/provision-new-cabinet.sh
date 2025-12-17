#!/bin/bash

################################################################################
# Script de Provisioning Automatisé d'un Nouveau Cabinet Comptable
# Usage: ./provision-new-cabinet.sh <cabinet_id> <cabinet_name> <cabinet_domain>
# Exemple: ./provision-new-cabinet.sh cabinet-douala "Cabinet Expert Douala" cabinet-douala.predykt.com
################################################################################

set -euo pipefail

# Couleurs pour output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Fonction d'affichage
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

log_success() {
    echo -e "${MAGENTA}[✓]${NC} $1"
}

# Vérification des arguments
if [ "$#" -lt 2 ]; then
    log_error "Usage: $0 <cabinet_id> <cabinet_name> [cabinet_domain]"
    log_error ""
    log_error "Exemples:"
    log_error "  $0 cabinet-douala \"Cabinet Expert Douala\""
    log_error "  $0 cabinet-douala \"Cabinet Expert Douala\" cabinet-douala.predykt.com"
    exit 1
fi

CABINET_ID="$1"
CABINET_NAME="$2"
CABINET_DOMAIN="${3:-${CABINET_ID}.local}"

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="${PROJECT_ROOT}/.env.cabinet-${CABINET_ID}"
DATA_DIR="${PROJECT_ROOT}/data/cabinets/${CABINET_ID}"
LOGS_DIR="${PROJECT_ROOT}/logs/cabinets/${CABINET_ID}"
BACKUPS_DIR="${PROJECT_ROOT}/backups/cabinets/${CABINET_ID}"

# Génération de ports uniques basés sur hash du cabinet_id
CABINET_HASH=$(echo -n "$CABINET_ID" | md5sum 2>/dev/null | cut -c1-4 || echo -n "$CABINET_ID" | shasum | cut -c1-4)
APP_PORT=$((8100 + 0x${CABINET_HASH:0:2} % 900))
DB_PORT=$((5500 + 0x${CABINET_HASH:2:2} % 100))
REDIS_PORT=$((6500 + 0x${CABINET_HASH:0:2} % 100))
PGADMIN_PORT=$((5050 + 0x${CABINET_HASH:2:2} % 50))

log_info "╔════════════════════════════════════════════════════════════════╗"
log_info "║  PREDYKT - Provisioning Nouveau Cabinet Comptable             ║"
log_info "║  Mode: CABINET (Multi-Dossiers Clients)                       ║"
log_info "╚════════════════════════════════════════════════════════════════╝"
log_info ""
log_info "Cabinet ID       : ${CABINET_ID}"
log_info "Nom Cabinet      : ${CABINET_NAME}"
log_info "Domaine          : ${CABINET_DOMAIN}"
log_info "App Port         : ${APP_PORT}"
log_info "DB Port          : ${DB_PORT}"
log_info "Redis Port       : ${REDIS_PORT}"
log_info "PgAdmin Port     : ${PGADMIN_PORT}"
log_info ""
log_info "Base de données  : predykt_cabinet_${CABINET_ID}"
log_info "Fichier config   : ${ENV_FILE}"
log_info ""

# Confirmation
read -p "Continuer le provisioning ? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_warn "Provisioning annulé"
    exit 0
fi

################################################################################
# ÉTAPE 1: Génération des secrets sécurisés
################################################################################

log_step "[1/10] Génération des secrets sécurisés..."

# Génération de secrets robustes
if command -v openssl &> /dev/null; then
    DB_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-32)
    REDIS_PASSWORD=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-32)
    JWT_SECRET=$(openssl rand -base64 64 | tr -d "=+/" | cut -c1-64)
    PGADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d "=+/" | cut -c1-24)
else
    # Fallback si openssl n'est pas disponible
    DB_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
    REDIS_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
    JWT_SECRET=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -n 1)
    PGADMIN_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 24 | head -n 1)
fi

log_success "Secrets générés (DB, Redis, JWT, PgAdmin)"

################################################################################
# ÉTAPE 2: Création de la structure de répertoires
################################################################################

log_step "[2/10] Création de la structure de répertoires..."

mkdir -p "${DATA_DIR}"/{uploads,exports,imports,reports}
mkdir -p "${LOGS_DIR}"/{application,access,error}
mkdir -p "${BACKUPS_DIR}"/{postgres,redis,files}

log_success "Structure de répertoires créée"

################################################################################
# ÉTAPE 3: Génération du fichier .env
################################################################################

log_step "[3/10] Génération du fichier de configuration..."

cat > "${ENV_FILE}" <<EOF
# ═══════════════════════════════════════════════════════════════════════════
# Configuration Cabinet: ${CABINET_ID}
# Généré automatiquement le $(date '+%Y-%m-%d %H:%M:%S')
# ⚠️  NE PAS COMMITER DANS GIT - Contient des secrets sensibles
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# Identifiant Cabinet
# ─────────────────────────────────────────────────────────────────────────────
CABINET_ID=${CABINET_ID}
PREDYKT_TENANT_CABINET_ID=${CABINET_ID}
CABINET_NAME=${CABINET_NAME}
CABINET_DOMAIN=${CABINET_DOMAIN}

# ─────────────────────────────────────────────────────────────────────────────
# Mode Tenant (CABINET = Un cabinet avec plusieurs dossiers clients)
# ─────────────────────────────────────────────────────────────────────────────
PREDYKT_TENANT_MODE=CABINET
SPRING_PROFILES_ACTIVE=cabinet

# 🔐 SÉCURITÉ ACTIVÉE par défaut (TenantSecurityConfig avec JWT)
# L'authentification est TOUJOURS requise, même en développement
# Pour désactiver temporairement (tests uniquement):
#   SPRING_PROFILES_ACTIVE=cabinet,dev-no-auth
ENVIRONMENT=development

# ─────────────────────────────────────────────────────────────────────────────
# Ports (Uniques pour ce cabinet)
# ─────────────────────────────────────────────────────────────────────────────
APP_PORT=${APP_PORT}
DB_PORT=${DB_PORT}
REDIS_PORT=${REDIS_PORT}
PGADMIN_PORT=${PGADMIN_PORT}

# ─────────────────────────────────────────────────────────────────────────────
# PostgreSQL Configuration
# ─────────────────────────────────────────────────────────────────────────────
DB_HOST=postgres-cabinet-${CABINET_ID}
DB_NAME=predykt_cabinet_${CABINET_ID}
DB_USER=predykt_${CABINET_ID}
DB_PASSWORD=${DB_PASSWORD}

# PostgreSQL Tuning (ajuster selon RAM serveur)
POSTGRES_SHARED_BUFFERS=256MB
POSTGRES_EFFECTIVE_CACHE_SIZE=1GB
POSTGRES_MAX_CONNECTIONS=100

# ─────────────────────────────────────────────────────────────────────────────
# Redis Configuration
# ─────────────────────────────────────────────────────────────────────────────
REDIS_HOST=redis-cabinet-${CABINET_ID}
REDIS_PORT=6379
REDIS_PASSWORD=${REDIS_PASSWORD}

# ─────────────────────────────────────────────────────────────────────────────
# JWT Security (⚠️  NE JAMAIS PARTAGER)
# ─────────────────────────────────────────────────────────────────────────────
JWT_SECRET=${JWT_SECRET}
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# ─────────────────────────────────────────────────────────────────────────────
# PgAdmin (Interface Web PostgreSQL)
# ─────────────────────────────────────────────────────────────────────────────
PGADMIN_DEFAULT_EMAIL=admin@${CABINET_DOMAIN}
PGADMIN_DEFAULT_PASSWORD=${PGADMIN_PASSWORD}

# ─────────────────────────────────────────────────────────────────────────────
# Application Configuration
# ─────────────────────────────────────────────────────────────────────────────
VERSION=latest
JAVA_OPTS=-Xms512m -Xmx2048m

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_PREDYKT=DEBUG
LOGGING_PATTERN_CONSOLE=%d{HH:mm:ss.SSS} [🟡CABINET-${CABINET_ID}] [%thread] %-5level %logger{36} - %msg%n

# File Upload
MAX_FILE_SIZE=50MB
MAX_REQUEST_SIZE=100MB

# ─────────────────────────────────────────────────────────────────────────────
# Monitoring & Health
# ─────────────────────────────────────────────────────────────────────────────
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always

# ─────────────────────────────────────────────────────────────────────────────
# Timezone & Locale
# ─────────────────────────────────────────────────────────────────────────────
TZ=Africa/Douala
LANG=fr_FR.UTF-8

# ─────────────────════════════════════════════════════════════════════════════
# Backup Configuration
# ─────────────────────────────────────────────────────────────────────────────
BACKUP_RETENTION_DAYS=30
BACKUP_SCHEDULE=0 2 * * *

# ─────────────────────────────────────────────────────────────────────────────
# Email (À configurer selon votre provider SMTP)
# ─────────────────────────────────────────────────────────────────────────────
# SMTP_HOST=smtp.gmail.com
# SMTP_PORT=587
# SMTP_USERNAME=
# SMTP_PASSWORD=
# SMTP_FROM=${CABINET_NAME} <noreply@${CABINET_DOMAIN}>

# ─────────────────────────────────────────────────────────────────────────────
# Features Flags
# ─────────────────────────────────────────────────────────────────────────────
FEATURE_ML_RECONCILIATION=true
FEATURE_AUTO_BACKUP=true
FEATURE_EMAIL_NOTIFICATIONS=false

EOF

chmod 600 "${ENV_FILE}"

log_success "Configuration générée: ${ENV_FILE}"

################################################################################
# ÉTAPE 4: Vérification de Docker
################################################################################

log_step "[4/10] Vérification de Docker..."

if ! command -v docker &> /dev/null; then
    log_error "Docker n'est pas installé!"
    log_error "Installez Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

if ! docker info &> /dev/null; then
    log_error "Docker n'est pas démarré!"
    log_error "Démarrez Docker et relancez ce script"
    exit 1
fi

log_success "Docker est opérationnel"

################################################################################
# ÉTAPE 5: Génération du docker-compose.yml
################################################################################

log_step "[5/10] Génération du docker-compose.yml..."

cat > "${PROJECT_ROOT}/docker-compose.cabinet-${CABINET_ID}.yml" <<'COMPOSE_EOF'
version: '3.8'

services:
  # PostgreSQL Database (Un cabinet = Une base de données)
  postgres-cabinet-${CABINET_ID}:
    image: postgres:15-alpine
    container_name: postgres-cabinet-${CABINET_ID}
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_INITDB_ARGS: "-E UTF8 --locale=fr_FR.UTF-8"
    ports:
      - "${DB_PORT}:5432"
    volumes:
      - postgres-data-cabinet-${CABINET_ID}:/var/lib/postgresql/data
      - ./scripts/init-tenant-db.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    networks:
      - predykt-cabinet-${CABINET_ID}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # Redis Cache
  redis-cabinet-${CABINET_ID}:
    image: redis:7-alpine
    container_name: redis-cabinet-${CABINET_ID}
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 512mb --maxmemory-policy allkeys-lru
    ports:
      - "${REDIS_PORT}:6379"
    volumes:
      - redis-data-cabinet-${CABINET_ID}:/data
    networks:
      - predykt-cabinet-${CABINET_ID}
    healthcheck:
      test: ["CMD", "redis-cli", "--raw", "incr", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    restart: unless-stopped

  # PgAdmin (Interface Web PostgreSQL)
  pgadmin-cabinet-${CABINET_ID}:
    image: dpage/pgadmin4:latest
    container_name: pgadmin-cabinet-${CABINET_ID}
    environment:
      PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL}
      PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_DEFAULT_PASSWORD}
      PGADMIN_CONFIG_SERVER_MODE: 'False'
    ports:
      - "${PGADMIN_PORT}:80"
    volumes:
      - pgadmin-data-cabinet-${CABINET_ID}:/var/lib/pgadmin
    networks:
      - predykt-cabinet-${CABINET_ID}
    restart: unless-stopped
    depends_on:
      - postgres-cabinet-${CABINET_ID}

  # Application PREDYKT (Mode CABINET)
  predykt-app-cabinet-${CABINET_ID}:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: predykt-app-cabinet-${CABINET_ID}
    environment:
      # Tenant Configuration
      PREDYKT_TENANT_MODE: ${PREDYKT_TENANT_MODE}
      PREDYKT_TENANT_CABINET_ID: ${PREDYKT_TENANT_CABINET_ID}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}

      # Database
      DB_HOST: ${DB_HOST}
      DB_PORT: 5432
      DB_NAME: ${DB_NAME}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}

      # Redis
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT}
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
      LOGGING_PATTERN_CONSOLE: ${LOGGING_PATTERN_CONSOLE}
    ports:
      - "${APP_PORT}:8080"
    volumes:
      - ${DATA_DIR}/uploads:/app/uploads
      - ${DATA_DIR}/exports:/app/exports
      - ${DATA_DIR}/imports:/app/imports
      - ${DATA_DIR}/reports:/app/reports
      - ${LOGS_DIR}/application:/app/logs
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
      start_period: 60s
    restart: unless-stopped

networks:
  predykt-cabinet-${CABINET_ID}:
    driver: bridge
    name: predykt-cabinet-${CABINET_ID}

volumes:
  postgres-data-cabinet-${CABINET_ID}:
    name: postgres-data-cabinet-${CABINET_ID}
  redis-data-cabinet-${CABINET_ID}:
    name: redis-data-cabinet-${CABINET_ID}
  pgadmin-data-cabinet-${CABINET_ID}:
    name: pgadmin-data-cabinet-${CABINET_ID}
COMPOSE_EOF

log_success "docker-compose.yml généré"

################################################################################
# ÉTAPE 6: Démarrage des services Docker
################################################################################

log_step "[6/10] Démarrage des services Docker..."

cd "${PROJECT_ROOT}"

# Charger les variables d'environnement
set -a
source "${ENV_FILE}"
set +a

# Pull des images
log_info "Téléchargement des images Docker..."
docker-compose -f "docker-compose.cabinet-${CABINET_ID}.yml" --env-file "${ENV_FILE}" pull

# Démarrage des services (d'abord infra, puis app)
log_info "Démarrage PostgreSQL et Redis..."
docker-compose -f "docker-compose.cabinet-${CABINET_ID}.yml" --env-file "${ENV_FILE}" up -d postgres-cabinet-${CABINET_ID} redis-cabinet-${CABINET_ID} pgadmin-cabinet-${CABINET_ID}

log_success "Services infrastructure démarrés"

################################################################################
# ÉTAPE 7: Attente de la disponibilité de la base de données
################################################################################

log_step "[7/10] Attente de l'initialisation de PostgreSQL..."

MAX_RETRIES=60
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if docker exec "postgres-cabinet-${CABINET_ID}" pg_isready -U "predykt_${CABINET_ID}" -d "predykt_cabinet_${CABINET_ID}" > /dev/null 2>&1; then
        log_success "Base de données prête: predykt_cabinet_${CABINET_ID}"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo -n "."
    sleep 2
done
echo ""

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    log_error "Timeout: Base de données non disponible après ${MAX_RETRIES} tentatives"
    log_error "Vérifiez les logs: docker logs postgres-cabinet-${CABINET_ID}"
    exit 1
fi

################################################################################
# ÉTAPE 8: Construction et démarrage de l'application
################################################################################

log_step "[8/10] Construction et démarrage de l'application PREDYKT..."

# Build de l'application Java
log_info "Build de l'application Java (peut prendre quelques minutes)..."
if [ -f "${PROJECT_ROOT}/mvnw" ]; then
    cd "${PROJECT_ROOT}"
    ./mvnw clean package -DskipTests
else
    log_warn "Maven wrapper introuvable - Assurez-vous que l'application est déjà buildée"
fi

# Démarrage de l'application
log_info "Démarrage du conteneur application..."
docker-compose -f "docker-compose.cabinet-${CABINET_ID}.yml" --env-file "${ENV_FILE}" up -d predykt-app-cabinet-${CABINET_ID}

log_success "Application démarrée"

################################################################################
# ÉTAPE 9: Attente que l'application soit prête (Flyway migrations + boot)
################################################################################

log_step "[9/10] Attente du démarrage complet de l'application..."

MAX_RETRIES=90
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -f -s "http://localhost:${APP_PORT}/api/v1/health" > /dev/null 2>&1; then
        log_success "Application prête et répond aux requêtes"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))

    # Afficher un message tous les 10 essais
    if [ $((RETRY_COUNT % 10)) -eq 0 ]; then
        log_info "Attente... (${RETRY_COUNT}s / ${MAX_RETRIES}s max)"
    fi

    echo -n "."
    sleep 1
done
echo ""

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    log_error "Timeout: Application non disponible après ${MAX_RETRIES} secondes"
    log_error ""
    log_error "Actions de dépannage:"
    log_error "  1. Vérifier les logs: docker logs predykt-app-cabinet-${CABINET_ID}"
    log_error "  2. Vérifier la DB: docker exec postgres-cabinet-${CABINET_ID} psql -U predykt_${CABINET_ID} -d predykt_cabinet_${CABINET_ID} -c '\\dt'"
    log_error "  3. Vérifier les migrations Flyway dans les logs"
    exit 1
fi

################################################################################
# ÉTAPE 10: Initialisation du cabinet avec données de test (OPTIONNEL)
################################################################################

log_step "[10/10] Initialisation des données du cabinet..."

log_info "Voulez-vous créer des données de test (cabinet + 3 entreprises + utilisateurs) ?"
read -p "Créer des données de test ? (y/n) " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    log_info "Création des données de test..."

    # Script SQL d'initialisation
    SQL_INIT=$(cat <<SQL
-- Création du cabinet
INSERT INTO cabinets (id, name, domain, email, phone, address, plan, max_companies, max_users, is_active, created_at, updated_at)
VALUES (1, '${CABINET_NAME}', '${CABINET_DOMAIN}', 'contact@${CABINET_DOMAIN}', '+237 XXX XXX XXX', 'Douala, Cameroun', 'STARTER', 50, 100, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Création de 3 entreprises clientes
INSERT INTO companies (id, name, email, country, currency, accounting_standard, cabinet_id, created_at, updated_at)
VALUES
  (10, 'Entreprise A SARL', 'contact@entreprise-a.cm', 'CM', 'XAF', 'OHADA', 1, NOW(), NOW()),
  (11, 'Entreprise B SA', 'contact@entreprise-b.cm', 'CM', 'XAF', 'OHADA', 1, NOW(), NOW()),
  (12, 'Entreprise C SAS', 'contact@entreprise-c.cm', 'CM', 'XAF', 'OHADA', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Création des rôles MODE CABINET (si pas déjà existants)
INSERT INTO roles (id, name, description, is_system)
VALUES (1, 'ROLE_ADMIN_CABINET', 'Administrateur cabinet - Accès complet tous dossiers', true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (id, name, description, is_system)
VALUES (2, 'ROLE_COLLABORATOR', 'Collaborateur cabinet - Accès dossiers assignés', true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (id, name, description, is_system)
VALUES (3, 'ROLE_CLIENT', 'Client - Lecture seule son propre dossier', true)
ON CONFLICT (name) DO NOTHING;

-- Création d'un utilisateur admin
-- Mot de passe: Admin@123 (BCrypt avec strength 12)
INSERT INTO users (id, email, password_hash, first_name, last_name, is_active, is_email_verified, company_id, cabinet_id, created_at, updated_at)
VALUES (100, 'admin@${CABINET_DOMAIN}', '\$2a\$12\$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVqNkMS7gC', 'Admin', 'Cabinet', true, true, 10, 1, NOW(), NOW())
ON CONFLICT (email, company_id) DO NOTHING;

-- Association utilisateur → rôle
INSERT INTO user_roles (user_id, role_id)
VALUES (100, 1)
ON CONFLICT DO NOTHING;

-- Permissions d'accès (admin a accès à tous les dossiers)
INSERT INTO user_company_access (user_id, company_id, access_level, can_view_confidential, granted_at, granted_by)
VALUES
  (100, 10, 'ADMIN', true, NOW(), 'SYSTEM'),
  (100, 11, 'ADMIN', true, NOW(), 'SYSTEM'),
  (100, 12, 'ADMIN', true, NOW(), 'SYSTEM')
ON CONFLICT DO NOTHING;

-- Message de confirmation
DO \$\$
BEGIN
    RAISE NOTICE '✓ Cabinet créé: ${CABINET_NAME}';
    RAISE NOTICE '✓ 3 entreprises créées (IDs: 10, 11, 12)';
    RAISE NOTICE '✓ Utilisateur admin créé: admin@${CABINET_DOMAIN}';
    RAISE NOTICE '✓ Mot de passe par défaut: Admin@123';
    RAISE NOTICE '⚠️  CHANGEZ le mot de passe après la première connexion!';
END \$\$;
SQL
)

    # Exécution du script SQL
    echo "$SQL_INIT" | docker exec -i "postgres-cabinet-${CABINET_ID}" psql -U "predykt_${CABINET_ID}" -d "predykt_cabinet_${CABINET_ID}" > /dev/null 2>&1

    if [ $? -eq 0 ]; then
        log_success "Données de test créées avec succès"
    else
        log_warn "Erreur lors de la création des données de test (peut-être déjà existantes)"
    fi
fi

################################################################################
# RÉSUMÉ FINAL
################################################################################

log_info ""
log_info "╔════════════════════════════════════════════════════════════════╗"
log_info "║          PROVISIONING CABINET TERMINÉ AVEC SUCCÈS              ║"
log_info "╚════════════════════════════════════════════════════════════════╝"
log_info ""
log_info "📋 Cabinet Information:"
log_info "   ID:          ${CABINET_ID}"
log_info "   Nom:         ${CABINET_NAME}"
log_info "   Domaine:     ${CABINET_DOMAIN}"
log_info "   Database:    predykt_cabinet_${CABINET_ID}"
log_info ""
log_info "🌐 Services Accessibles:"
log_info "   API:         http://localhost:${APP_PORT}"
log_info "   Health:      http://localhost:${APP_PORT}/api/v1/health"
log_info "   Swagger:     http://localhost:${APP_PORT}/api/v1/swagger-ui.html"
log_info "   PgAdmin:     http://localhost:${PGADMIN_PORT}"
log_info ""
log_info "🔐 Accès PgAdmin:"
log_info "   Email:       admin@${CABINET_DOMAIN}"
log_info "   Password:    ${PGADMIN_PASSWORD}"
log_info ""
log_info "👤 Utilisateur Test (si créé):"
log_info "   Email:       admin@${CABINET_DOMAIN}"
log_info "   Password:    Admin@123"
log_info "   ⚠️  CHANGEZ le mot de passe immédiatement!"
log_info ""
log_info "📁 Fichiers Importants:"
log_info "   Config:      ${ENV_FILE}"
log_info "   Compose:     ${PROJECT_ROOT}/docker-compose.cabinet-${CABINET_ID}.yml"
log_info "   Data:        ${DATA_DIR}"
log_info "   Logs:        ${LOGS_DIR}"
log_info "   Backups:     ${BACKUPS_DIR}"
log_info ""
log_info "📊 Commandes Utiles:"
log_info "   Status:      docker-compose -f docker-compose.cabinet-${CABINET_ID}.yml ps"
log_info "   Logs:        docker logs -f predykt-app-cabinet-${CABINET_ID}"
log_info "   Stop:        docker-compose -f docker-compose.cabinet-${CABINET_ID}.yml stop"
log_info "   Restart:     docker-compose -f docker-compose.cabinet-${CABINET_ID}.yml restart"
log_info "   Backup:      ./scripts/backup-tenant.sh cabinet ${CABINET_ID}"
log_info ""
log_info "🔐 Authentification:"
log_info "   ⚠️  JWT REQUIS pour tous les endpoints (sauf /health, /auth/**, /swagger)"
log_info "   1. Login: curl -X POST http://localhost:${APP_PORT}/api/v1/auth/login \\"
log_info "             -H 'Content-Type: application/json' \\"
log_info "             -d '{\"email\":\"admin@${CABINET_DOMAIN}\",\"password\":\"Admin@123\"}'"
log_info "   2. Utiliser le token: -H 'Authorization: Bearer {accessToken}'"
log_info ""
log_info "🧪 Test de Connexion:"
log_info "   Health (sans auth): curl http://localhost:${APP_PORT}/api/v1/health"
log_info "   Companies (avec auth requis): curl -H 'Authorization: Bearer TOKEN' \\"
log_info "                                  http://localhost:${APP_PORT}/api/v1/companies"
log_info ""
log_info "📚 Documentation:"
log_info "   Testing Guide: ./TESTING_MODE_CABINET.md"
log_info "   Auth Guide:    ./AUTHENTICATION.md"
log_info ""

# Enregistrement dans le registre des cabinets
REGISTRY_FILE="${PROJECT_ROOT}/cabinets-registry.log"
echo "$(date)|${CABINET_ID}|${CABINET_NAME}|${CABINET_DOMAIN}|${APP_PORT}|${DB_PORT}|${REDIS_PORT}|${PGADMIN_PORT}" >> "${REGISTRY_FILE}"

log_success "✅ Cabinet ${CABINET_ID} prêt à l'emploi!"
log_info ""
log_info "Pour gérer ce cabinet, utilisez:"
log_info "  ./scripts/deploy-cabinet.sh ${CABINET_ID} [start|stop|logs|backup|status]"

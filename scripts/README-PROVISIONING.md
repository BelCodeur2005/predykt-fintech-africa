# Scripts de Provisioning PREDYKT

Ce répertoire contient les scripts d'automatisation pour le provisioning et la gestion des tenants PREDYKT.

## Vue d'Ensemble

PREDYKT supporte 3 modes de déploiement multi-tenant :

| Mode | Usage | Script de Provisioning | Environnement |
|------|-------|------------------------|---------------|
| **SHARED** | PME partageant une DB | Pas de script (déploiement standard) | Tous |
| **DEDICATED** | Grande entreprise avec DB dédiée | `provision-new-tenant.sh` | Production |
| **CABINET** | Cabinet comptable multi-dossiers | `provision-new-cabinet.sh` (dev)<br>`provision-new-cabinet-production.sh` (prod) ⭐ | Tous |

## 🏭 Provisioning PRODUCTION - Cabinet (MODE CABINET)

**NOUVEAU** : Script de provisioning production-ready avec sécurité hardening complet.

### 🔒 Prérequis PRODUCTION

- **OS**: Linux (Ubuntu 20.04+, Debian 11+, CentOS 8+)
- **Privilèges**: root (sudo) requis
- **Docker**: 20.10+ et Docker Compose 2.0+
- **Ressources minimales**: 8GB RAM, 50GB disque, 4 CPU cores
- **Domaine**: DNS configuré et propagé
- **Email**: Compte email valide pour Let's Encrypt

### 🚀 Utilisation PRODUCTION

#### Sur Linux (RECOMMANDÉ pour production) :

```bash
cd scripts
chmod +x provision-new-cabinet-production.sh

# Syntaxe complète
sudo ./provision-new-cabinet-production.sh \
    <cabinet_id> \
    <cabinet_name> \
    <cabinet_domain> \
    --email <admin_email> \
    --ssl-email <ssl_email> \
    [--cloudflare-api-key <key>]

# Exemple complet
sudo ./provision-new-cabinet-production.sh \
    cabinet-douala \
    "Cabinet Expert Comptable Douala" \
    cabinet-douala.predykt.com \
    --email admin@predykt.com \
    --ssl-email ssl@predykt.com \
    --cloudflare-api-key xxxxxxxxxxxxx

# Options avancées
sudo ./provision-new-cabinet-production.sh cabinet-douala ... \
    --no-ssl              # Désactiver SSL (dev only)
    --no-firewall         # Désactiver firewall
    --no-monitoring       # Désactiver Prometheus/Grafana
    --no-backups          # Désactiver backups automatiques
    --skip-validation     # Skip validation pré-déploiement (DANGEREUX)
    --dry-run             # Afficher actions sans exécuter
```

### ⚡ Ce que fait le script PRODUCTION

Le script effectue **15 étapes automatisées** pour un déploiement production-ready :

1. **Validation pré-déploiement stricte**
   - Vérification OS (Linux seulement)
   - Vérification ressources (8GB RAM, 50GB disk, 4 CPUs)
   - Vérification Docker versions
   - Validation cabinet_id, email, domain
   - Vérification DNS resolution
   - Vérification ports disponibles

2. **Génération secrets sécurisés (512-bit)**
   - JWT_SECRET: **64 caractères** (512-bit pour HS512)
   - DB_PASSWORD: 32 caractères
   - REDIS_PASSWORD: 32 caractères
   - GRAFANA_ADMIN_PASSWORD: 32 caractères
   - BACKUP_ENCRYPTION_KEY: 64 caractères (AES-256)

3. **Structure de répertoires avec permissions sécurisées**
   - data/ (750), logs/ (750), backups/ (700), certs/ (700)
   - Répertoires: uploads, exports, imports, reports, audit, security

4. **Configuration .env PRODUCTION**
   - **SPRING_PROFILES_ACTIVE=cabinet,prod-tenant** (sécurité activée)
   - **ENABLE_SECURITY=true** (SecurityConfig activé)
   - Logging niveau INFO (pas DEBUG)
   - PostgreSQL tuning production (2GB shared_buffers, 6GB cache)
   - Rate limiting activé (60 req/min)
   - CORS restreint au domaine
   - Audit logs 7 ans (2555 jours)

5. **Docker Compose PRODUCTION**
   - Resource limits (CPU, RAM)
   - Bind localhost only pour PostgreSQL/Redis
   - Health checks robustes
   - Traefik labels pour SSL/TLS
   - Prometheus + Grafana (profile monitoring)
   - Backup automatique chiffré (profile backups)

6. **Configuration Firewall (UFW)**
   - Port 22 (SSH)
   - Port 80 (HTTP → HTTPS redirect)
   - Port 443 (HTTPS)
   - PostgreSQL/Redis NON exposés publiquement

7. **Configuration SSL/TLS (Let's Encrypt)**
   - Traefik v2 reverse proxy
   - Certificat automatique Let's Encrypt
   - HTTP → HTTPS redirect forcé
   - Renouvellement automatique
   - Support DNS Challenge (Cloudflare/Route53)

8. **Configuration Monitoring (Prometheus + Grafana)**
   - Prometheus scrape API metrics (/actuator/prometheus)
   - Grafana dashboards provisionnés
   - Métriques: API, PostgreSQL, Redis
   - Alerting (à configurer)

9. **Démarrage services infrastructure**
   - PostgreSQL 15 avec tuning production
   - Redis 7 avec persistence (AOF + RDB)

10. **Attente disponibilité DB** (120s timeout)

11. **Build Maven production** (-Pprod profile)

12. **Démarrage application PREDYKT**
    - Sécurité activée
    - Profil prod-tenant
    - JWT 512-bit validé

13. **Attente application prête** (180s timeout)
    - Health check endpoint
    - Flyway migrations vérifiées

14. **Démarrage Monitoring & Backups**
    - Prometheus + Grafana (--profile monitoring)
    - Backup quotidien 2h00 chiffré (--profile backups)

15. **Validation post-déploiement & Documentation**
    - Tests: Health, Prometheus, DB, Redis, Flyway
    - Génération RUNBOOK complet
    - Enregistrement registre cabinets-registry-production.log

### 📊 Résultat PRODUCTION

Après exécution (environ 5-10 minutes), vous aurez :

#### Fichiers générés :
- ✅ `.env.cabinet-{CABINET_ID}` (permissions 600)
- ✅ `docker-compose.cabinet-{CABINET_ID}.prod.yml`
- ✅ `RUNBOOK-CABINET-{CABINET_ID}.md` (documentation opérationnelle)
- ✅ Configuration Traefik SSL/TLS
- ✅ Configuration Prometheus + Grafana

#### Services accessibles :
- 🌐 **API**: `https://{CABINET_DOMAIN}` (SSL/TLS activé)
- ❤️ **Health**: `https://{CABINET_DOMAIN}/api/v1/health`
- 📚 **Swagger**: `https://{CABINET_DOMAIN}/api/v1/swagger-ui.html`
- 📊 **Prometheus**: `http://localhost:{PROMETHEUS_PORT}`
- 📈 **Grafana**: `https://grafana-{CABINET_DOMAIN}` (admin / mot_de_passe_généré)

#### Sécurité activée :
- ✅ JWT 512-bit (HS512 compliant)
- ✅ SecurityConfig activé (authentification requise)
- ✅ Firewall configuré (UFW)
- ✅ SSL/TLS Let's Encrypt automatique
- ✅ PostgreSQL/Redis bind localhost only
- ✅ Rate limiting 60 req/min
- ✅ CORS restreint
- ✅ Audit logs 7 ans

#### Backups automatiques :
- 🔐 Chiffrement AES-256
- ⏰ Quotidien 2h00 AM
- 📅 Rétention: 30 jours, 8 semaines, 12 mois
- 💾 PostgreSQL + Redis + Fichiers

#### Monitoring :
- 📊 Prometheus metrics
- 📈 Grafana dashboards
- 🚨 Alerting (à configurer)
- 📝 Logs centralisés

### 📋 Checklist Pré-Production

Avant d'exécuter le script production :

- [ ] Domaine acheté et DNS configuré (A record pointant vers serveur)
- [ ] Serveur Linux avec 8GB+ RAM, 50GB+ disk, 4+ CPU cores
- [ ] Docker 20.10+ et Docker Compose 2.0+ installés
- [ ] Ports 80, 443 accessibles depuis Internet
- [ ] Email valide pour certificats SSL Let's Encrypt
- [ ] Backup off-site configuré (S3/GCS - optionnel)
- [ ] Plan de monitoring/alerting (Slack/PagerDuty)
- [ ] Runbook opérationnel validé par l'équipe
- [ ] On-call rotation définie

### 🔥 Différences DEV vs PRODUCTION

| Aspect | DEV (`provision-new-cabinet.sh`) | PRODUCTION (`provision-new-cabinet-production.sh`) |
|--------|----------------------------------|---------------------------------------------------|
| **Sécurité** | Désactivée (permitAll) | ✅ Activée (authenticated) |
| **JWT Secret** | 320-bit | ✅ 512-bit (validé) |
| **SSL/TLS** | ❌ HTTP | ✅ HTTPS Let's Encrypt |
| **Firewall** | ❌ Aucun | ✅ UFW configuré |
| **Monitoring** | ❌ Aucun | ✅ Prometheus + Grafana |
| **Backups** | ❌ Manuels | ✅ Automatiques chiffrés |
| **Logging** | DEBUG | ✅ INFO/WARN |
| **PgAdmin** | ✅ Exposé | ❌ Désactivé |
| **DB/Redis** | Exposé sur ports externes | ✅ Bind localhost only |
| **Rate Limiting** | ❌ Aucun | ✅ 60 req/min |
| **Audit Logs** | ❌ Aucun | ✅ 7 ans rétention |
| **CORS** | Wildcard | ✅ Restreint au domaine |
| **Données test** | ✅ Créées | ❌ Non créées |
| **Runbook** | ❌ Aucun | ✅ Généré automatiquement |
| **Validation** | Basique | ✅ Stricte (15 checks) |
| **Ports** | Publics | ✅ Localhost + Traefik |

### ⚠️ IMPORTANT - Production

1. **NE JAMAIS utiliser le script dev en production** - Sécurité désactivée
2. **Changer tous les mots de passe** après premier déploiement
3. **Configurer SMTP** pour notifications email
4. **Configurer alerting** (Slack/PagerDuty dans AlertManager)
5. **Tester backup/restore** avant mise en prod
6. **Créer runbook incident** pour équipe on-call
7. **Valider SLA** avec équipe ops (99.9% uptime)
8. **Configurer backup off-site** (S3/GCS)

---

## 🧪 Provisioning DEVELOPPEMENT - Cabinet (MODE CABINET)

### Prérequis

- Docker et Docker Compose installés
- Java 17+ et Maven (ou Maven Wrapper inclus)
- PostgreSQL 15+ (via Docker)
- Redis 7+ (via Docker)

### Utilisation

#### Sur Linux/Mac :

```bash
cd scripts
chmod +x provision-new-cabinet.sh

# Syntaxe
./provision-new-cabinet.sh <cabinet_id> <cabinet_name> [cabinet_domain]

# Exemple
./provision-new-cabinet.sh cabinet-douala "Cabinet Expert Douala"
./provision-new-cabinet.sh cabinet-douala "Cabinet Expert Douala" cabinet-douala.predykt.com
```

#### Sur Windows :

```cmd
cd scripts

REM Syntaxe
provision-new-cabinet.bat <cabinet_id> <cabinet_name> [cabinet_domain]

REM Exemple
provision-new-cabinet.bat cabinet-douala "Cabinet Expert Douala"
provision-new-cabinet.bat cabinet-douala "Cabinet Expert Douala" cabinet-douala.predykt.com
```

### Ce que fait le script

Le script effectue automatiquement les 10 étapes suivantes :

1. **Génération de secrets sécurisés** (DB password, Redis password, JWT secret, PgAdmin password)
2. **Création de la structure de répertoires** (data, logs, backups)
3. **Génération du fichier `.env.cabinet-{CABINET_ID}`** avec toute la configuration
4. **Vérification de Docker** (installation et état)
5. **Génération du `docker-compose.cabinet-{CABINET_ID}.yml`**
6. **Démarrage des services Docker** (PostgreSQL, Redis, PgAdmin)
7. **Attente de la disponibilité de la base de données** (avec retry automatique)
8. **Construction et démarrage de l'application PREDYKT**
9. **Attente du démarrage complet** (migrations Flyway, boot Spring)
10. **Initialisation des données de test** (optionnel) :
    - 1 cabinet
    - 3 entreprises clientes (Entreprise A, B, C)
    - 1 utilisateur admin (`admin@{CABINET_DOMAIN}` / `Admin@123`)
    - Permissions d'accès configurées

### Résultat

Après exécution réussie, vous aurez :

#### Fichiers générés :
- `.env.cabinet-{CABINET_ID}` : Configuration complète avec secrets
- `docker-compose.cabinet-{CABINET_ID}.yml` : Compose file dédié
- `data/cabinets/{CABINET_ID}/` : Répertoires de données
- `logs/cabinets/{CABINET_ID}/` : Répertoires de logs
- `backups/cabinets/{CABINET_ID}/` : Répertoires de backups

#### Services accessibles :
- **API** : `http://localhost:{APP_PORT}`
- **Health Check** : `http://localhost:{APP_PORT}/api/v1/health`
- **Swagger UI** : `http://localhost:{APP_PORT}/api/v1/swagger-ui.html`
- **PgAdmin** : `http://localhost:{PGADMIN_PORT}`

#### Base de données :
- Nom : `predykt_cabinet_{CABINET_ID}`
- User : `predykt_{CABINET_ID}`
- Migrations Flyway appliquées automatiquement

#### Données de test (si créées) :
```sql
Cabinet: Cabinet Expert Douala (ID: 1)
├── Entreprise A SARL (ID: 10)
├── Entreprise B SA (ID: 11)
└── Entreprise C SAS (ID: 12)

Utilisateur admin:
  Email: admin@{CABINET_DOMAIN}
  Password: Admin@123
  Accès: ADMIN sur les 3 entreprises
```

## 📊 Gestion d'un Cabinet (après provisioning)

Une fois le cabinet provisionné, utilisez le script de gestion :

```bash
# Démarrer
./deploy-cabinet.sh cabinet-douala start

# Arrêter
./deploy-cabinet.sh cabinet-douala stop

# Redémarrer
./deploy-cabinet.sh cabinet-douala restart

# Voir les logs
./deploy-cabinet.sh cabinet-douala logs

# Voir le statut
./deploy-cabinet.sh cabinet-douala status

# Lister les dossiers clients
./deploy-cabinet.sh cabinet-douala companies

# Créer un backup
./deploy-cabinet.sh cabinet-douala backup

# Restaurer un backup
./deploy-cabinet.sh cabinet-douala restore <timestamp>

# Mettre à jour l'application
./deploy-cabinet.sh cabinet-douala update
```

## 🏢 Provisioning d'un Tenant Dédié (MODE DEDICATED)

Pour une grande entreprise avec base de données dédiée :

```bash
# Syntaxe
./provision-new-tenant.sh <tenant_id> <tenant_domain> <company_name>

# Exemple
./provision-new-tenant.sh companyA erp.companyA.com "Company A Ltd"
```

Ce script effectue le même niveau d'automatisation que pour les cabinets, mais pour une seule entreprise.

## 🔒 Sécurité

### Secrets générés automatiquement
Tous les mots de passe et secrets sont générés aléatoirement avec `openssl` (ou fallback sécurisé) :
- **DB Password** : 32 caractères alphanumériques
- **Redis Password** : 32 caractères alphanumériques
- **JWT Secret** : 64 caractères alphanumériques (requis pour HS512)
- **PgAdmin Password** : 24 caractères alphanumériques

### Fichiers sensibles
Les fichiers `.env.cabinet-*` contiennent des secrets et sont automatiquement :
- Créés avec permissions restreintes (`chmod 600` sur Linux)
- Exclus du contrôle de version (`.gitignore`)

⚠️ **NE JAMAIS commiter les fichiers `.env.cabinet-*` dans Git**

### Mot de passe par défaut
L'utilisateur admin créé par le script a le mot de passe par défaut `Admin@123`.

**⚠️ CHANGEZ CE MOT DE PASSE IMMÉDIATEMENT après le premier login !**

## 🧪 Test après provisioning

### 1. Vérifier la santé de l'application

```bash
curl http://localhost:{APP_PORT}/api/v1/health
```

Réponse attendue :
```json
{
  "status": "UP",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 2. Se connecter avec l'utilisateur test

```bash
# Login
curl -X POST http://localhost:{APP_PORT}/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@{CABINET_DOMAIN}",
    "password": "Admin@123"
  }'
```

Réponse attendue :
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "...",
    "expiresIn": 86400
  }
}
```

### 3. Lister les dossiers clients

```bash
curl http://localhost:{APP_PORT}/api/v1/companies \
  -H "Authorization: Bearer {ACCESS_TOKEN}"
```

### 4. Utiliser Postman

Référez-vous au guide complet : **[TESTING_MODE_CABINET.md](../TESTING_MODE_CABINET.md)**

Ce guide contient :
- Collection Postman complète avec 8 scénarios de test
- Variables d'environnement Postman
- Tests de permissions et d'isolation
- Troubleshooting complet

## 📂 Structure générée

```
D:\Bel\projets\predykt-backend-java\
├── .env.cabinet-{CABINET_ID}                    # Configuration (SENSIBLE)
├── docker-compose.cabinet-{CABINET_ID}.yml      # Docker Compose dédié
├── cabinets-registry.log                        # Registre de tous les cabinets
│
├── data/cabinets/{CABINET_ID}/
│   ├── uploads/                                 # Fichiers uploadés
│   ├── exports/                                 # Exports générés
│   ├── imports/                                 # Imports CSV
│   └── reports/                                 # Rapports PDF
│
├── logs/cabinets/{CABINET_ID}/
│   ├── application/                             # Logs applicatifs
│   ├── access/                                  # Logs d'accès
│   └── error/                                   # Logs d'erreurs
│
└── backups/cabinets/{CABINET_ID}/
    ├── postgres/                                # Backups PostgreSQL
    ├── redis/                                   # Backups Redis
    └── files/                                   # Backups fichiers
```

## 🐳 Conteneurs Docker créés

Pour chaque cabinet, 4 conteneurs sont créés :

| Conteneur | Nom | Port | Rôle |
|-----------|-----|------|------|
| PostgreSQL | `postgres-cabinet-{CABINET_ID}` | `{DB_PORT}:5432` | Base de données |
| Redis | `redis-cabinet-{CABINET_ID}` | `{REDIS_PORT}:6379` | Cache |
| PgAdmin | `pgadmin-cabinet-{CABINET_ID}` | `{PGADMIN_PORT}:80` | Interface DB |
| Application | `predykt-app-cabinet-{CABINET_ID}` | `{APP_PORT}:8080` | API PREDYKT |

Réseau Docker dédié : `predykt-cabinet-{CABINET_ID}`

## 🔧 Troubleshooting

### Erreur : Docker n'est pas démarré
```bash
# Démarrer Docker Desktop (Windows/Mac)
# Ou sur Linux :
sudo systemctl start docker
```

### Erreur : Port déjà utilisé
Le script génère des ports aléatoires basés sur le hash du `CABINET_ID`. Si un port est déjà utilisé, essayez :
1. Changer le `CABINET_ID`
2. Modifier manuellement les ports dans `.env.cabinet-{CABINET_ID}`
3. Redémarrer : `./deploy-cabinet.sh {CABINET_ID} restart`

### Erreur : Base de données non disponible
```bash
# Vérifier les logs PostgreSQL
docker logs postgres-cabinet-{CABINET_ID}

# Vérifier la connectivité
docker exec postgres-cabinet-{CABINET_ID} pg_isready -U predykt_{CABINET_ID}

# Redémarrer PostgreSQL
docker restart postgres-cabinet-{CABINET_ID}
```

### Erreur : Application ne démarre pas
```bash
# Vérifier les logs de l'application
docker logs -f predykt-app-cabinet-{CABINET_ID}

# Vérifier les migrations Flyway (dans les logs)
# Rechercher : "Flyway migration"

# Vérifier la connexion à la DB (dans les logs)
# Rechercher : "HikariPool" ou "database connection"
```

### Erreur : Échec de création des données de test
Normal si vous relancez le script - les données existent déjà (contraintes `ON CONFLICT DO NOTHING`).

Pour recréer les données :
1. Supprimer les données existantes dans PgAdmin
2. Ou supprimer la base de données :
   ```bash
   docker exec postgres-cabinet-{CABINET_ID} psql -U predykt_{CABINET_ID} -c "DROP DATABASE predykt_cabinet_{CABINET_ID}; CREATE DATABASE predykt_cabinet_{CABINET_ID};"
   ```
3. Redémarrer l'application pour réappliquer les migrations

## 📚 Documentation complémentaire

- **[TESTING_MODE_CABINET.md](../TESTING_MODE_CABINET.md)** : Guide complet de test avec Postman
- **[AUTHENTICATION.md](../AUTHENTICATION.md)** : Architecture d'authentification JWT
- **[CLAUDE.md](../CLAUDE.md)** : Documentation complète du projet
- **[docker-compose.yml](../docker-compose.yml)** : Compose file pour MODE SHARED

## 🎯 Prochaines Étapes

Après le provisioning réussi :

1. ✅ **Tester la connexion** : Utiliser le guide [TESTING_MODE_CABINET.md](../TESTING_MODE_CABINET.md)
2. ✅ **Changer le mot de passe admin** : `Admin@123` → mot de passe sécurisé
3. ✅ **Créer des utilisateurs réels** : Via API `/api/v1/auth/register`
4. ✅ **Configurer SMTP** : Pour les notifications email (dans `.env.cabinet-{CABINET_ID}`)
5. ✅ **Planifier les backups** : Via cron job + `./deploy-cabinet.sh {CABINET_ID} backup`
6. ✅ **Configurer le monitoring** : Prometheus + Grafana (endpoints exposés)
7. ✅ **Configurer SSL/TLS** : Via reverse proxy (Nginx, Traefik, Caddy)
8. ✅ **Production hardening** : Activer l'authentification dans `SecurityConfig.java`

## ❓ Support

Pour toute question ou problème :

1. Vérifier les logs : `docker logs -f predykt-app-cabinet-{CABINET_ID}`
2. Consulter le troubleshooting : [TESTING_MODE_CABINET.md](../TESTING_MODE_CABINET.md#troubleshooting)
3. Ouvrir une issue sur GitHub

---

**Version** : 1.0.0
**Dernière mise à jour** : 2024-01-15
**Auteur** : PREDYKT Team

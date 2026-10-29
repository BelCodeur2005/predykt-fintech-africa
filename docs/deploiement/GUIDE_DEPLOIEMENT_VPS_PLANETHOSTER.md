# GUIDE DÉPLOIEMENT VPS PLANETHOSTER - MODE CABINET TEST

**Date**: 2025-12-19
**Version**: 1.0
**Cible**: VPS KVM PlanetHoster (Test/Staging)
**Mode**: CABINET (Multi-tenant comptable)

---

## Table des Matières

1. [Prérequis VPS PlanetHoster](#1-prérequis-vps-planethoster)
2. [Connexion SSH Initiale](#2-connexion-ssh-initiale)
3. [Configuration Sécurité de Base](#3-configuration-sécurité-de-base)
4. [Installation Docker + Docker Compose](#4-installation-docker--docker-compose)
5. [Configuration Firewall (UFW)](#5-configuration-firewall-ufw)
6. [Configuration Nom de Domaine](#6-configuration-nom-de-domaine)
7. [Clonage du Projet](#7-clonage-du-projet)
8. [Provisioning MODE CABINET](#8-provisioning-mode-cabinet)
9. [Configuration SSL/TLS (Let's Encrypt)](#9-configuration-ssltls-lets-encrypt)
10. [Tests Complets Post-Déploiement](#10-tests-complets-post-déploiement)
11. [Monitoring et Logs](#11-monitoring-et-logs)
12. [Maintenance et Backups](#12-maintenance-et-backups)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Prérequis VPS PlanetHoster

### 1.1 Choix de l'Offre VPS

Pour un environnement **TEST/STAGING** MODE CABINET, recommandations:

| Caractéristique | Minimum | Recommandé | Offre PlanetHoster |
|-----------------|---------|------------|-------------------|
| **RAM** | 4 GB | 8 GB | VPS-2 (8GB RAM) |
| **CPU** | 2 cores | 4 cores | VPS-2 (4 vCPU) |
| **Disque** | 50 GB SSD | 100 GB SSD | VPS-2 (100GB SSD) |
| **Bande passante** | 1 TB/mois | Illimité | VPS-2 (Illimité) |
| **OS** | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS | ✓ Disponible |
| **IP** | 1 IP publique | 1 IP publique | ✓ Incluse |

**Recommandation**: **VPS-2** ou **VPS-3** de PlanetHoster

**Prix estimé**: ~15-25 CAD/mois pour VPS-2

### 1.2 Commande du VPS

1. Aller sur https://www.planethoster.com/fr/serveurs-vps
2. Choisir **VPS-2** (8GB RAM, 4 vCPU, 100GB SSD)
3. Sélectionner OS: **Ubuntu 22.04 LTS 64-bit**
4. Options:
   - ✓ Activer backup automatique (+5 CAD/mois)
   - ✓ Snapshot (pour restauration rapide)
   - ✗ Panel de gestion (pas nécessaire, on gère via SSH)
5. Finaliser la commande

### 1.3 Informations Reçues par Email

Après commande, PlanetHoster envoie un email contenant:

```
VPS Information:
----------------
Hostname: vpsXXXXX.planethoster.net
IP Address: 192.0.2.100
SSH Port: 22
Root Password: [mot de passe temporaire]

OS: Ubuntu 22.04 LTS
```

**⚠️ IMPORTANT**: Changez immédiatement le mot de passe root!

### 1.4 Nom de Domaine

Vous avez besoin d'un **nom de domaine** pointant vers le VPS pour le SSL.

**Option A**: Sous-domaine d'un domaine existant
```
cabinet-test.monentreprise.com → 192.0.2.100
```

**Option B**: Nouveau domaine
```
cabinet-test-douala.cm → 192.0.2.100
```

---

## 2. Connexion SSH Initiale

### 2.1 Depuis Windows (Git Bash ou PowerShell)

```bash
# Ouvrir Git Bash ou PowerShell
ssh root@192.0.2.100

# Entrer le mot de passe temporaire reçu par email
Password: [mot_de_passe_temporaire]

# Vous devriez voir:
# Welcome to Ubuntu 22.04.3 LTS (GNU/Linux 5.15.0-91-generic x86_64)
```

### 2.2 Changer le Mot de Passe Root (OBLIGATOIRE)

```bash
# Immédiatement après la première connexion
passwd root

# Entrer nouveau mot de passe FORT:
# - Minimum 16 caractères
# - Lettres majuscules + minuscules + chiffres + symboles
# Exemple: Pr3dyKt!VPS@2025#Secure
```

### 2.3 Mettre à Jour le Système

```bash
# Mise à jour de la liste des paquets
apt update

# Mise à jour des paquets installés (peut prendre 5-10 minutes)
apt upgrade -y

# Installer utilitaires de base
apt install -y curl wget git vim htop net-tools ufw

# Redémarrer si kernel mis à jour
reboot

# Attendre 30 secondes, puis se reconnecter
ssh root@192.0.2.100
```

### 2.4 Vérifier les Ressources du VPS

```bash
# CPU
nproc
# Résultat attendu: 4

# RAM totale
free -h
# Résultat attendu: Total ~8GB

# Disque
df -h
# Résultat attendu: /dev/vda1 ~100GB

# OS Version
lsb_release -a
# Résultat attendu: Ubuntu 22.04.3 LTS
```

---

## 3. Configuration Sécurité de Base

### 3.1 Créer un Utilisateur Non-Root (Recommandé)

```bash
# Créer utilisateur 'predykt'
adduser predykt

# Entrer mot de passe fort
# Confirmer les informations (Entrée pour laisser vide)

# Donner privilèges sudo
usermod -aG sudo predykt

# Vérifier
groups predykt
# Résultat: predykt : predykt sudo
```

### 3.2 Configurer SSH avec Clé Publique (Optionnel mais Recommandé)

**Sur votre machine locale Windows:**

```bash
# Générer paire de clés SSH (si pas déjà fait)
ssh-keygen -t ed25519 -C "admin@cabinet-test"

# Fichier: C:\Users\VotreNom\.ssh\id_ed25519
# Appuyer Entrée pour accepter

# Copier la clé publique vers le VPS
type C:\Users\VotreNom\.ssh\id_ed25519.pub | ssh root@192.0.2.100 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"

# OU avec Git Bash:
cat ~/.ssh/id_ed25519.pub | ssh root@192.0.2.100 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

**Sur le VPS:**

```bash
# Définir permissions correctes
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys

# Désactiver authentification par mot de passe (après avoir testé la clé!)
# ATTENTION: Testez d'abord la connexion par clé dans un nouveau terminal!
vim /etc/ssh/sshd_config

# Modifier:
# PasswordAuthentication no
# PubkeyAuthentication yes

# Redémarrer SSH
systemctl restart sshd
```

### 3.3 Configurer le Timezone

```bash
# Définir timezone Afrique/Douala
timedatectl set-timezone Africa/Douala

# Vérifier
timedatectl
# Résultat: Time zone: Africa/Douala (WAT, +0100)

# Activer NTP pour synchronisation temps
timedatectl set-ntp true
```

---

## 4. Installation Docker + Docker Compose

### 4.1 Désinstaller Anciennes Versions Docker (si présentes)

```bash
apt remove -y docker docker-engine docker.io containerd runc || true
```

### 4.2 Installer Docker (Méthode Officielle)

```bash
# Installer dépendances
apt install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Ajouter clé GPG officielle Docker
mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Ajouter repository Docker
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

# Mise à jour et installation Docker
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Vérifier installation
docker --version
# Résultat attendu: Docker version 24.x.x

docker compose version
# Résultat attendu: Docker Compose version v2.x.x
```

### 4.3 Démarrer et Activer Docker

```bash
# Démarrer Docker
systemctl start docker

# Activer au démarrage
systemctl enable docker

# Vérifier status
systemctl status docker
# Doit afficher: active (running)

# Tester Docker
docker run hello-world
# Doit télécharger et exécuter l'image hello-world
```

### 4.4 Configurer Docker (Optionnel - Limites Ressources)

```bash
# Créer fichier de configuration Docker daemon
cat > /etc/docker/daemon.json <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "default-address-pools": [
    {
      "base": "172.17.0.0/16",
      "size": 24
    }
  ]
}
EOF

# Redémarrer Docker
systemctl restart docker
```

---

## 5. Configuration Firewall (UFW)

### 5.1 Installer et Configurer UFW

```bash
# Installer UFW (normalement déjà installé)
apt install -y ufw

# Politique par défaut
ufw default deny incoming
ufw default allow outgoing

# Autoriser SSH (TRÈS IMPORTANT - sinon vous serez bloqué!)
ufw allow 22/tcp comment "SSH"

# Autoriser HTTP (pour Let's Encrypt challenge)
ufw allow 80/tcp comment "HTTP"

# Autoriser HTTPS (pour l'application)
ufw allow 443/tcp comment "HTTPS"

# Autoriser ping (ICMP)
ufw allow from any to any proto icmp

# Activer UFW (ATTENTION: Assurez-vous que SSH est autorisé!)
ufw --force enable

# Vérifier status
ufw status verbose
```

**Résultat attendu:**
```
Status: active

To                         Action      From
--                         ------      ----
22/tcp                     ALLOW IN    Anywhere                  # SSH
80/tcp                     ALLOW IN    Anywhere                  # HTTP
443/tcp                    ALLOW IN    Anywhere                  # HTTPS
```

### 5.2 Vérifier Ports Disponibles

```bash
# Vérifier qu'aucun service n'utilise déjà les ports
netstat -tuln | grep -E ':(80|443|8080|5432|6379)'

# Si vide, c'est bon. Sinon, identifier et arrêter les services conflictuels.
```

---

## 6. Configuration Nom de Domaine

### 6.1 Configurer le DNS

Vous devez créer un enregistrement DNS **A** pointant vers l'IP de votre VPS.

**Chez votre registrar de domaine (ex: OVH, Namecheap, GoDaddy):**

1. Aller dans la gestion DNS de votre domaine
2. Ajouter un enregistrement **A**:
   ```
   Type: A
   Nom: cabinet-test (ou @ pour domaine racine)
   Valeur: 192.0.2.100
   TTL: 3600 (1 heure)
   ```

3. Optionnel - Ajouter un enregistrement pour Grafana:
   ```
   Type: A
   Nom: grafana-cabinet-test
   Valeur: 192.0.2.100
   TTL: 3600
   ```

### 6.2 Vérifier la Propagation DNS

```bash
# Depuis votre machine locale
nslookup cabinet-test.monentreprise.com

# Résultat attendu:
# Name:    cabinet-test.monentreprise.com
# Address: 192.0.2.100

# Ou avec dig:
dig cabinet-test.monentreprise.com +short
# Résultat: 192.0.2.100
```

**⚠️ IMPORTANT**: La propagation DNS peut prendre de 5 minutes à 24 heures. Attendez que le domaine pointe bien vers votre IP avant de continuer.

### 6.3 Vérifier depuis le VPS

```bash
# Sur le VPS
ping -c 3 cabinet-test.monentreprise.com

# Doit afficher l'IP du VPS (192.0.2.100)
```

---

## 7. Clonage du Projet

### 7.1 Installer Git (si pas déjà fait)

```bash
git --version
# Si "command not found":
apt install -y git
```

### 7.2 Créer Répertoire pour le Projet

```bash
# Créer répertoire /opt/predykt
mkdir -p /opt/predykt
cd /opt/predykt
```

### 7.3 Cloner le Repository

**Option A: Repository Git Public**

```bash
git clone https://github.com/votre-org/predykt-backend-java.git
cd predykt-backend-java
```

**Option B: Repository Git Privé (avec clé de déploiement)**

```bash
# Générer clé SSH pour le déploiement
ssh-keygen -t ed25519 -C "deploy@cabinet-test" -f ~/.ssh/id_deploy
# Appuyer Entrée (pas de passphrase pour déploiement automatisé)

# Copier la clé publique
cat ~/.ssh/id_deploy.pub
# Ajouter cette clé dans GitHub/GitLab → Settings → Deploy Keys

# Cloner avec SSH
git clone git@github.com:votre-org/predykt-backend-java.git
cd predykt-backend-java
```

**Option C: Upload Manuel (via SCP depuis votre machine)**

```bash
# Depuis votre machine Windows (Git Bash)
cd D:\Bel\projets\predykt-backend-java
scp -r . root@192.0.2.100:/opt/predykt/predykt-backend-java/

# Exclure node_modules, target, etc. avec rsync:
rsync -avz --exclude 'target' --exclude 'node_modules' --exclude '.git' \
  . root@192.0.2.100:/opt/predykt/predykt-backend-java/
```

### 7.4 Vérifier le Contenu

```bash
cd /opt/predykt/predykt-backend-java

# Lister fichiers importants
ls -la

# Devrait afficher:
# - mvnw (Maven wrapper)
# - pom.xml
# - src/
# - docker-compose.yml
# - scripts/
# - activités.csv
# - PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json
```

---

## 8. Provisioning MODE CABINET

### 8.1 Rendre le Script Exécutable

```bash
cd /opt/predykt/predykt-backend-java/scripts

# Donner permissions d'exécution
chmod +x provision-new-cabinet-production.sh

# Vérifier
ls -l provision-new-cabinet-production.sh
# Devrait afficher: -rwxr-xr-x
```

### 8.2 Préparer les Variables

**REMPLACEZ** les valeurs suivantes selon votre configuration:

```bash
# Variables à personnaliser
CABINET_ID="cabinet-test"
CABINET_NAME="Cabinet Test Douala"
CABINET_DOMAIN="cabinet-test.monentreprise.com"
ADMIN_EMAIL="admin@monentreprise.com"
SSL_EMAIL="ssl@monentreprise.com"
```

### 8.3 Lancer le Provisioning

```bash
cd /opt/predykt/predykt-backend-java/scripts

# Lancer le script de provisioning PRODUCTION
./provision-new-cabinet-production.sh \
    cabinet-test \
    "Cabinet Test Douala" \
    cabinet-test.monentreprise.com \
    --email admin@monentreprise.com \
    --ssl-email ssl@monentreprise.com

# Le script va:
# [1/15] Valider environnement (OS, Docker, RAM, CPU, DNS)
# [2/15] Générer secrets (JWT 512-bit, DB, Redis)
# [3/15] Créer structure répertoires
# [4/15] Générer .env PRODUCTION
# [5/15] Générer docker-compose PRODUCTION
# [6/15] Configurer firewall UFW
# [7/15] Configurer SSL/TLS (Let's Encrypt)
# [8/15] Configurer Prometheus + Grafana
# [9/15] Démarrer PostgreSQL + Redis
# [10/15] Attendre PostgreSQL ready
# [11/15] Builder et démarrer application
# [12/15] Attendre application ready (health check)
# [13/15] Démarrer monitoring + backups
# [14/15] Validation post-déploiement (5 tests)
# [15/15] Générer documentation (RUNBOOK)

# Durée totale: 5-10 minutes
```

### 8.4 Répondre aux Questions

Le script pose **2 questions**:

**Question 1**: Continuer le provisioning PRODUCTION ? [y/N]
```
Répondre: y
```

**Question 2**: Voulez-vous vérifier le DNS avant de continuer ? (si DNS non résolu)
```
# Si le DNS n'est pas encore propagé:
Répondre: n (continuer quand même)

# Note: Le SSL ne fonctionnera pas tant que DNS n'est pas résolu
# Vous pourrez le configurer après
```

### 8.5 Surveiller l'Exécution

```bash
# Dans un autre terminal SSH, surveiller les logs Docker
docker logs -f postgres-cabinet-cabinet-test

# Ou surveiller logs de l'application
docker logs -f predykt-app-cabinet-cabinet-test
```

### 8.6 Résultat Attendu

**Si tout se passe bien**, vous verrez à la fin:

```
╔════════════════════════════════════════════════════════════════════════╗
║          PROVISIONING PRODUCTION TERMINÉ AVEC SUCCÈS                   ║
╚════════════════════════════════════════════════════════════════════════╝

📋 Cabinet Information:
   ID:              cabinet-test
   Nom:             Cabinet Test Douala
   Domaine:         cabinet-test.monentreprise.com
   Database:        predykt_cabinet_cabinet_test
   Environment:     PRODUCTION

🌐 Services Accessibles:
   API:             https://cabinet-test.monentreprise.com
   Health:          https://cabinet-test.monentreprise.com/api/v1/health
   Swagger:         https://cabinet-test.monentreprise.com/api/v1/swagger-ui.html
   Prometheus:      http://localhost:9090
   Grafana:         https://grafana-cabinet-test.monentreprise.com

⏱️  Temps total: 342s

✅ Cabinet cabinet-test prêt en PRODUCTION!
```

---

## 9. Configuration SSL/TLS (Let's Encrypt)

### 9.1 Installer Traefik (Reverse Proxy pour SSL)

Le script de provisioning a généré la config Traefik, mais Traefik doit être installé séparément.

```bash
cd /opt/predykt/predykt-backend-java

# Créer répertoire pour Traefik
mkdir -p infrastructure/docker/traefik

# Créer docker-compose pour Traefik
cat > infrastructure/docker/docker-compose.traefik.yml <<'EOF'
version: '3.8'

services:
  traefik:
    image: traefik:v2.10
    container_name: traefik-reverse-proxy
    restart: unless-stopped
    security_opt:
      - no-new-privileges:true
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./traefik.yml:/etc/traefik/traefik.yml:ro
      - ./acme.json:/acme.json
      - ./logs:/var/log/traefik
    networks:
      - traefik-public
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.dashboard.rule=Host(`traefik.cabinet-test.monentreprise.com`)"
      - "traefik.http.routers.dashboard.service=api@internal"
      - "traefik.http.routers.dashboard.entrypoints=websecure"
      - "traefik.http.routers.dashboard.tls=true"
      - "traefik.http.routers.dashboard.tls.certresolver=letsencrypt"

networks:
  traefik-public:
    external: true
EOF

# Créer fichier de configuration Traefik
cat > infrastructure/docker/traefik.yml <<'EOF'
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
      email: ssl@monentreprise.com
      storage: /acme.json
      httpChallenge:
        entryPoint: web

providers:
  docker:
    endpoint: "unix:///var/run/docker.sock"
    exposedByDefault: false
    network: traefik-public

log:
  level: INFO
  format: json
  filePath: /var/log/traefik/traefik.log

accessLog:
  filePath: /var/log/traefik/access.log
  format: json
EOF

# Créer fichier acme.json pour certificats
touch infrastructure/docker/acme.json
chmod 600 infrastructure/docker/acme.json

# Créer network Docker pour Traefik
docker network create traefik-public

# Démarrer Traefik
cd infrastructure/docker
docker compose -f docker-compose.traefik.yml up -d

# Vérifier logs
docker logs -f traefik-reverse-proxy
```

### 9.2 Connecter l'Application à Traefik

```bash
cd /opt/predykt/predykt-backend-java

# Connecter le réseau de l'application au réseau Traefik
docker network connect traefik-public predykt-app-cabinet-cabinet-test
```

### 9.3 Vérifier le Certificat SSL

```bash
# Depuis votre machine locale
curl -I https://cabinet-test.monentreprise.com/api/v1/health

# Devrait afficher:
# HTTP/2 200
# Server: traefik
# ...

# Ou vérifier avec un navigateur:
# https://cabinet-test.monentreprise.com/api/v1/swagger-ui.html
```

**⚠️ Si erreur SSL**:

```bash
# Vérifier logs Traefik
docker logs traefik-reverse-proxy | grep -i "certificate\|acme"

# Vérifier que DNS pointe bien
dig cabinet-test.monentreprise.com +short
# Doit retourner l'IP du VPS

# Attendre 1-2 minutes pour que Let's Encrypt génère le certificat
```

---

## 10. Tests Complets Post-Déploiement

### 10.1 Test 1: Health Check (sans authentification)

```bash
# Depuis le VPS ou votre machine locale
curl https://cabinet-test.monentreprise.com/api/v1/health

# Résultat attendu:
# {
#   "status": "UP",
#   "timestamp": "2025-12-19T10:30:00Z",
#   "version": "1.0.0",
#   "tenant": {
#     "mode": "CABINET",
#     "cabinetId": "cabinet-test"
#   }
# }
```

### 10.2 Test 2: Créer Premier Utilisateur Admin

```bash
# Depuis le VPS, se connecter à PostgreSQL
docker exec -it postgres-cabinet-cabinet-test psql -U predykt_cabinet_test -d predykt_cabinet_cabinet_test

# Exécuter SQL:
```

```sql
-- Créer le cabinet (si pas déjà fait par le script)
INSERT INTO cabinets (id, name, domain, email, plan, max_companies, max_users, is_active, created_at, updated_at)
VALUES (1, 'Cabinet Test Douala', 'cabinet-test.monentreprise.com', 'admin@monentreprise.com', 'STARTER', 50, 100, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Créer 1 entreprise de test
INSERT INTO companies (id, name, email, country, currency, accounting_standard, cabinet_id, created_at, updated_at)
VALUES (10, 'Entreprise Test SARL', 'contact@entreprise-test.cm', 'CM', 'XAF', 'OHADA', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Créer rôle ADMIN
INSERT INTO roles (id, name, description, is_system)
VALUES (1, 'ROLE_ADMIN_CABINET', 'Administrateur cabinet - Accès complet', true)
ON CONFLICT (name) DO NOTHING;

-- Créer utilisateur admin (password: Admin@123)
-- Hash BCrypt pour "Admin@123": $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVqNkMS7gC
INSERT INTO users (id, email, password_hash, first_name, last_name, is_active, is_email_verified, company_id, cabinet_id, created_at, updated_at)
VALUES (100, 'admin@cabinet-test.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVqNkMS7gC', 'Admin', 'Cabinet', true, true, 10, 1, NOW(), NOW())
ON CONFLICT (email, company_id) DO NOTHING;

-- Associer utilisateur au rôle
INSERT INTO user_roles (user_id, role_id)
VALUES (100, 1)
ON CONFLICT DO NOTHING;

-- Donner accès à l'entreprise
INSERT INTO user_company_access (user_id, company_id, access_level, can_view_confidential, granted_at, granted_by)
VALUES (100, 10, 'ADMIN', true, NOW(), 'SYSTEM')
ON CONFLICT DO NOTHING;

-- Vérifier
SELECT u.email, c.name as company, r.name as role
FROM users u
JOIN companies c ON u.company_id = c.id
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id;

-- Sortir
\q
```

### 10.3 Test 3: Login API (obtenir JWT)

```bash
# Test Login depuis votre machine locale
curl -X POST https://cabinet-test.monentreprise.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@cabinet-test.com",
    "password": "Admin@123"
  }' | jq '.'

# Résultat attendu:
# {
#   "success": true,
#   "message": "Connexion réussie",
#   "data": {
#     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
#     "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
#     "tokenType": "Bearer",
#     "expiresIn": 28800,
#     "user": {
#       "id": 100,
#       "email": "admin@cabinet-test.com",
#       "cabinetId": 1,
#       "companyId": 10
#     }
#   }
# }
```

**Sauvegarder le token** pour les tests suivants:

```bash
# Stocker le token dans une variable
TOKEN="eyJhbGciOiJIUzUxMiJ9..."
```

### 10.4 Test 4: Lister les Entreprises (avec JWT)

```bash
curl -X GET https://cabinet-test.monentreprise.com/api/v1/companies \
  -H "Authorization: Bearer $TOKEN" | jq '.'

# Résultat attendu:
# {
#   "success": true,
#   "data": [
#     {
#       "id": 10,
#       "name": "Entreprise Test SARL",
#       "cabinetId": 1,
#       "currency": "XAF",
#       "accountingStandard": "OHADA"
#     }
#   ]
# }
```

### 10.5 Test 5: Initialiser Plan Comptable OHADA

```bash
curl -X POST https://cabinet-test.monentreprise.com/api/v1/companies/10/charts/initialize \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq '.'

# Résultat attendu:
# {
#   "success": true,
#   "message": "Plan comptable OHADA initialisé avec 500+ comptes"
# }
```

### 10.6 Test 6: Vérifier les Comptes OHADA

```bash
curl -X GET "https://cabinet-test.monentreprise.com/api/v1/companies/10/charts?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.content[] | {number: .accountNumber, name: .accountName}'

# Devrait afficher:
# {
#   "number": "101000",
#   "name": "Capital social"
# }
# {
#   "number": "121000",
#   "name": "Report à nouveau"
# }
# ...
```

### 10.7 Test 7: Swagger UI (Interface Graphique)

Ouvrir dans un navigateur:

```
https://cabinet-test.monentreprise.com/api/v1/swagger-ui.html
```

1. Cliquer sur **Authorize** (🔓)
2. Entrer: `Bearer <votre_token>`
3. Cliquer **Authorize**
4. Tester les endpoints directement depuis Swagger

---

## 11. Monitoring et Logs

### 11.1 Accès à Grafana

```
URL: https://grafana-cabinet-test.monentreprise.com
Username: admin
Password: (voir fichier .env.cabinet-test ou RUNBOOK)
```

**Dashboards Pré-configurés**:
- API Performance (latence, throughput, erreurs)
- PostgreSQL (connexions, queries/sec)
- Redis (hit/miss ratio, memory)
- Système (CPU, RAM, Disk I/O)

### 11.2 Consulter les Logs

```bash
# Logs application Spring Boot
docker logs -f --tail=100 predykt-app-cabinet-cabinet-test

# Logs PostgreSQL
docker logs -f --tail=100 postgres-cabinet-cabinet-test

# Logs Redis
docker logs -f --tail=100 redis-cabinet-cabinet-test

# Logs Traefik (SSL/TLS)
docker logs -f --tail=100 traefik-reverse-proxy

# Logs dans fichiers
tail -f /opt/predykt/predykt-backend-java/logs/cabinets/cabinet-test/application/spring.log
```

### 11.3 Monitoring en Temps Réel

```bash
# CPU/RAM des conteneurs
docker stats

# Espace disque
df -h

# Connexions PostgreSQL actives
docker exec postgres-cabinet-cabinet-test psql -U predykt_cabinet_test -d predykt_cabinet_cabinet_test -c \
  "SELECT count(*) as active_connections FROM pg_stat_activity WHERE state = 'active';"
```

---

## 12. Maintenance et Backups

### 12.1 Backups Automatiques

Le script a configuré des backups **automatiques quotidiens** à 2h00 du matin.

**Vérifier la config backup**:

```bash
# Voir le container backup
docker ps | grep backup

# Vérifier répertoire backups
ls -lh /opt/predykt/predykt-backend-java/backups/cabinets/cabinet-test/postgres/

# Devrait afficher des fichiers .sql.gz quotidiens
```

### 12.2 Backup Manuel

```bash
cd /opt/predykt/predykt-backend-java/scripts

# Lancer backup manuel
./backup-tenant.sh cabinet cabinet-test

# Résultat:
# Backup créé: /opt/predykt/.../backups/.../predykt_cabinet_cabinet_test_20251219_143000.sql.gz
```

### 12.3 Restaurer un Backup

```bash
cd /opt/predykt/predykt-backend-java/scripts

# Lister backups disponibles
ls -lht ../backups/cabinets/cabinet-test/postgres/

# Restaurer (⚠️ ÉCRASE LA BASE ACTUELLE!)
./restore-tenant.sh cabinet cabinet-test 20251219_143000

# Confirmer: y
```

### 12.4 Mise à Jour de l'Application

```bash
cd /opt/predykt/predykt-backend-java

# 1. Backup avant mise à jour
./scripts/backup-tenant.sh cabinet cabinet-test

# 2. Pull dernier code
git pull origin main

# 3. Rebuild application
./mvnw clean package -DskipTests -Pprod

# 4. Redémarrer conteneur
docker-compose -f docker-compose.cabinet-cabinet-test.prod.yml restart predykt-app-cabinet-cabinet-test

# 5. Vérifier health
curl https://cabinet-test.monentreprise.com/api/v1/health
```

### 12.5 Rotation des Logs

```bash
# Installer logrotate (normalement déjà installé)
apt install -y logrotate

# Créer config logrotate pour PREDYKT
cat > /etc/logrotate.d/predykt <<'EOF'
/opt/predykt/predykt-backend-java/logs/cabinets/*/application/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    missingok
    create 0644 root root
}
EOF

# Tester rotation
logrotate -d /etc/logrotate.d/predykt
```

---

## 13. Troubleshooting

### 13.1 Problème: Application ne démarre pas

**Symptômes**:
```bash
docker ps | grep predykt
# Container "Restarting" ou absent
```

**Diagnostic**:
```bash
# Voir les logs
docker logs --tail=50 predykt-app-cabinet-cabinet-test

# Erreurs fréquentes:
# 1. "Connection refused" → PostgreSQL pas prêt
# 2. "JWT secret too short" → JWT_SECRET < 64 caractères
# 3. "Flyway migration failed" → Problème migration DB
```

**Solutions**:

**1. PostgreSQL pas prêt**:
```bash
# Vérifier PostgreSQL
docker logs postgres-cabinet-cabinet-test

# Redémarrer PostgreSQL
docker restart postgres-cabinet-cabinet-test

# Attendre 30s, puis redémarrer app
docker restart predykt-app-cabinet-cabinet-test
```

**2. JWT Secret trop court**:
```bash
# Vérifier .env
grep JWT_SECRET /opt/predykt/predykt-backend-java/.env.cabinet-cabinet-test

# Si < 64 caractères, régénérer:
NEW_JWT=$(openssl rand -base64 48 | tr -d "=+/" | head -c 64)
sed -i "s/^JWT_SECRET=.*/JWT_SECRET=$NEW_JWT/" .env.cabinet-cabinet-test

# Redémarrer
docker restart predykt-app-cabinet-cabinet-test
```

**3. Migration Flyway échouée**:
```bash
# Se connecter à PostgreSQL
docker exec -it postgres-cabinet-cabinet-test psql -U predykt_cabinet_test -d predykt_cabinet_cabinet_test

# Voir migrations
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;

# Si migration échouée (success = false):
# Option A: Réparer manuellement
# Option B: Supprimer base et recréer (⚠️ PERTE DE DONNÉES)
```

### 13.2 Problème: Erreur 502 Bad Gateway (Traefik)

**Symptômes**:
```bash
curl https://cabinet-test.monentreprise.com/api/v1/health
# 502 Bad Gateway
```

**Diagnostic**:
```bash
# Vérifier que l'application tourne
docker ps | grep predykt

# Vérifier logs Traefik
docker logs traefik-reverse-proxy | tail -50

# Vérifier réseau Docker
docker network inspect traefik-public | grep predykt
```

**Solution**:
```bash
# Reconnecter l'application au réseau Traefik
docker network connect traefik-public predykt-app-cabinet-cabinet-test

# Redémarrer Traefik
docker restart traefik-reverse-proxy

# Attendre 10s
sleep 10

# Tester
curl https://cabinet-test.monentreprise.com/api/v1/health
```

### 13.3 Problème: Certificat SSL non généré

**Symptômes**:
```bash
curl https://cabinet-test.monentreprise.com
# SSL certificate problem: unable to get local issuer certificate
```

**Diagnostic**:
```bash
# Vérifier DNS
dig cabinet-test.monentreprise.com +short
# Doit retourner l'IP du VPS

# Vérifier logs Traefik pour ACME
docker logs traefik-reverse-proxy | grep -i "acme\|certificate"
```

**Solutions**:

**1. DNS pas résolu**:
```bash
# Attendre propagation DNS (jusqu'à 24h)
# Vérifier régulièrement:
dig cabinet-test.monentreprise.com +short
```

**2. Port 80 bloqué** (Let's Encrypt ne peut pas faire le challenge):
```bash
# Vérifier UFW
ufw status | grep 80

# Si absent, autoriser:
ufw allow 80/tcp
```

**3. Forcer renouvellement certificat**:
```bash
# Supprimer acme.json
rm /opt/predykt/predykt-backend-java/infrastructure/docker/acme.json
touch /opt/predykt/predykt-backend-java/infrastructure/docker/acme.json
chmod 600 /opt/predykt/predykt-backend-java/infrastructure/docker/acme.json

# Redémarrer Traefik
docker restart traefik-reverse-proxy

# Surveiller logs
docker logs -f traefik-reverse-proxy | grep -i "certificate"
```

### 13.4 Problème: Authentification échoue (401 Unauthorized)

**Symptômes**:
```bash
curl -X POST https://cabinet-test.monentreprise.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cabinet-test.com","password":"Admin@123"}'

# {"success":false,"message":"Identifiants incorrects"}
```

**Diagnostic**:
```bash
# Vérifier que l'utilisateur existe
docker exec postgres-cabinet-cabinet-test psql -U predykt_cabinet_test -d predykt_cabinet_cabinet_test -c \
  "SELECT email, is_active FROM users WHERE email = 'admin@cabinet-test.com';"
```

**Solutions**:

**1. Utilisateur n'existe pas**:
```bash
# Recréer l'utilisateur (voir Test 10.2)
```

**2. Mauvais hash de password**:
```sql
-- Se connecter à PostgreSQL
docker exec -it postgres-cabinet-cabinet-test psql -U predykt_cabinet_test -d predykt_cabinet_cabinet_test

-- Mettre à jour password (Admin@123)
UPDATE users
SET password_hash = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVqNkMS7gC'
WHERE email = 'admin@cabinet-test.com';
```

### 13.5 Problème: Disque plein

**Symptômes**:
```bash
df -h
# /dev/vda1   100G   98G   0 100% /
```

**Solutions**:

**1. Nettoyer logs Docker**:
```bash
# Voir taille logs Docker
du -sh /var/lib/docker/containers/*/*-json.log

# Nettoyer logs > 100MB
find /var/lib/docker/containers/ -name "*-json.log" -size +100M -exec truncate -s 0 {} \;
```

**2. Nettoyer images Docker inutilisées**:
```bash
docker system prune -a --volumes
# Libère ~5-10GB
```

**3. Nettoyer vieux backups**:
```bash
# Supprimer backups > 30 jours
find /opt/predykt/predykt-backend-java/backups/ -name "*.sql.gz" -mtime +30 -delete
```

**4. Nettoyer logs applicatifs**:
```bash
find /opt/predykt/predykt-backend-java/logs/ -name "*.log" -mtime +30 -delete
```

### 13.6 Problème: Mémoire saturée (OOM)

**Symptômes**:
```bash
docker logs predykt-app-cabinet-cabinet-test | grep -i "OutOfMemoryError"
# java.lang.OutOfMemoryError: Java heap space
```

**Solutions**:

**1. Augmenter JAVA_OPTS**:
```bash
# Éditer .env
vim /opt/predykt/predykt-backend-java/.env.cabinet-cabinet-test

# Modifier:
JAVA_OPTS=-Xms1g -Xmx4g -XX:+UseG1GC

# Redémarrer
docker restart predykt-app-cabinet-cabinet-test
```

**2. Limiter ressources PostgreSQL**:
```bash
# Éditer .env
vim .env.cabinet-cabinet-test

# Réduire:
POSTGRES_SHARED_BUFFERS=1GB
POSTGRES_EFFECTIVE_CACHE_SIZE=3GB

# Redémarrer
docker restart postgres-cabinet-cabinet-test
```

---

## 14. Tests Postman sur le VPS

### 14.1 Configurer Postman pour le VPS

1. Ouvrir Postman
2. Importer la collection `PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json`
3. Importer l'environnement `PREDYKT_COMPLETE.postman_environment.json`
4. **Modifier les variables d'environnement**:

```javascript
base_url: https://cabinet-test.monentreprise.com/api/v1
cabinet_id: 1
company_id_A: 10
admin_email: admin@cabinet-test.com
admin_password: Admin@123
```

### 14.2 Exécuter les Tests

Suivre le **GUIDE_TEST_POSTMAN_MODE_CABINET.md** en utilisant l'URL publique HTTPS du VPS.

**Phases de test à exécuter**:

1. **Phase Setup**: Login, Health Check, Companies, Plan OHADA
2. **Phase Tiers**: Clients, Fournisseurs
3. **Phase Facturation**: Invoices, Bills, Payments
4. **Phase Import CSV**: Importer `activités.csv` depuis votre machine
5. **Phase Rapports**: Bilan, Compte résultat, Ratios
6. **Phase ML**: Training XGBoost, Predictions

### 14.3 Upload Fichier CSV depuis Postman

Pour tester l'import CSV sur le VPS:

1. Endpoint: `POST https://cabinet-test.monentreprise.com/api/v1/companies/10/import/activities`
2. Authorization: `Bearer {{access_token}}`
3. Body → form-data:
   - Key: `file`
   - Type: File
   - Value: Sélectionner `D:\Bel\projets\predykt-backend-java\activités.csv`
4. Send

**Résultat attendu**: ~1500 activités importées

---

## 15. Checklist Déploiement Complet

### ✅ Pré-Déploiement
- [ ] VPS commandé (VPS-2 ou VPS-3 PlanetHoster)
- [ ] IP publique reçue
- [ ] Domaine configuré (DNS A record)
- [ ] DNS propagé (dig/nslookup)

### ✅ Configuration VPS
- [ ] Connexion SSH réussie
- [ ] Mot de passe root changé
- [ ] Utilisateur non-root créé (optionnel)
- [ ] Système mis à jour (apt upgrade)
- [ ] Timezone configuré (Africa/Douala)
- [ ] Docker installé et fonctionnel
- [ ] Docker Compose v2 installé
- [ ] UFW configuré (ports 22, 80, 443)

### ✅ Déploiement Application
- [ ] Projet cloné dans `/opt/predykt/`
- [ ] Script provisioning exécuté avec succès
- [ ] PostgreSQL démarré et ready
- [ ] Redis démarré
- [ ] Application démarrée (health check OK)
- [ ] Migrations Flyway appliquées
- [ ] Monitoring Prometheus + Grafana actifs
- [ ] Backups automatiques configurés

### ✅ SSL/TLS
- [ ] Traefik installé
- [ ] Réseau Docker traefik-public créé
- [ ] Application connectée à Traefik
- [ ] Certificat Let's Encrypt généré
- [ ] HTTPS fonctionnel (pas d'erreur SSL)

### ✅ Tests Fonctionnels
- [ ] Health check HTTPS OK
- [ ] Utilisateur admin créé
- [ ] Login API réussi (JWT obtenu)
- [ ] Entreprise listée
- [ ] Plan OHADA initialisé (500+ comptes)
- [ ] Swagger UI accessible
- [ ] Grafana accessible

### ✅ Tests Postman
- [ ] Collection importée
- [ ] Variables d'environnement configurées (URL HTTPS)
- [ ] Login fonctionnel
- [ ] Import CSV réussi (1500+ activités)
- [ ] Rapports générés (Bilan, Résultat)
- [ ] Exports PDF/Excel fonctionnels

### ✅ Production Ready
- [ ] Runbook généré et lu
- [ ] Backup manuel testé
- [ ] Restore testé (sur backup de test)
- [ ] Logs accessibles et monitorés
- [ ] Alertes configurées (optionnel)
- [ ] Documentation à jour

---

## 16. Coûts Estimés

### Coûts Mensuels VPS PlanetHoster

| Élément | Coût Mensuel (CAD) |
|---------|-------------------|
| VPS-2 (8GB RAM, 4 vCPU, 100GB SSD) | ~20-25$ |
| Backups automatiques | +5$ |
| Domaine .com/.cm | ~1-2$ (annuel / 12) |
| **TOTAL** | **~26-32 CAD/mois** |

**Équivalent EUR**: ~18-22€/mois
**Équivalent USD**: ~19-23$/mois
**Équivalent XAF**: ~11,000-13,000 FCFA/mois

### Coûts Annuels

| Élément | Coût Annuel (CAD) |
|---------|-------------------|
| VPS-2 | ~240-300$ |
| Backups | ~60$ |
| Domaine | ~15-20$ |
| **TOTAL** | **~315-380 CAD/an** |

---

## 17. Support et Ressources

### Documentation Officielle

- **PlanetHoster**: https://www.planethoster.com/fr/support
- **Docker**: https://docs.docker.com/
- **Traefik**: https://doc.traefik.io/traefik/
- **Let's Encrypt**: https://letsencrypt.org/docs/
- **PostgreSQL**: https://www.postgresql.org/docs/15/
- **Spring Boot**: https://docs.spring.io/spring-boot/docs/current/reference/html/

### Fichiers Importants du Projet

- `GUIDE_TEST_POSTMAN_MODE_CABINET.md` - Tests Postman complets
- `RUNBOOK-CABINET-{ID}.md` - Généré automatiquement après provisioning
- `.env.cabinet-{ID}` - Configuration sensible (⚠️ NE PAS COMMITER)
- `docker-compose.cabinet-{ID}.prod.yml` - Docker Compose production

### Commandes Rapides de Référence

```bash
# Status tous conteneurs
docker ps

# Logs application
docker logs -f predykt-app-cabinet-cabinet-test

# Redémarrer application
docker restart predykt-app-cabinet-cabinet-test

# Health check
curl https://cabinet-test.monentreprise.com/api/v1/health

# Backup manuel
/opt/predykt/predykt-backend-java/scripts/backup-tenant.sh cabinet cabinet-test

# Monitoring ressources
docker stats

# Espace disque
df -h
```

---

**Version**: 1.0
**Date**: 2025-12-19
**Auteur**: Guide Déploiement PREDYKT MODE CABINET

**Prochaines étapes**:
1. Suivre ce guide de A à Z
2. Tester tous les endpoints avec Postman
3. Configurer monitoring dashboards Grafana
4. Planifier stratégie de backup
5. Préparer documentation utilisateurs finaux

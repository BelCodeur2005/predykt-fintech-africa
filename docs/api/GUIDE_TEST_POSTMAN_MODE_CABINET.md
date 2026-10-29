# GUIDE COMPLET - Tests Postman MODE CABINET

**Date de création**: 2025-12-19
**Version**: 1.0
**Mode**: CABINET (Multi-tenant, multi-dossiers clients)

## Table des Matières

1. [Prérequis](#1-prérequis)
2. [Provisioning du Cabinet (Première Installation)](#2-provisioning-du-cabinet)
3. [Démarrage de l'Infrastructure](#3-démarrage-de-linfrastructure)
4. [Configuration de Postman](#4-configuration-de-postman)
5. [Authentification et Premier Test](#5-authentification-et-premier-test)
6. [Ordre de Test Recommandé](#6-ordre-de-test-recommandé)
7. [Variables d'Environnement Expliquées](#7-variables-denvironnement-expliquées)
8. [Tests Complets par Module](#8-tests-complets-par-module)
9. [Gestion des Erreurs](#9-gestion-des-erreurs)
10. [Dépannage](#10-dépannage)

---

## 1. Prérequis

### 1.1 Logiciels Requis

```bash
✓ Docker Desktop (Windows/Mac) ou Docker Engine (Linux)
✓ Docker Compose v2.x
✓ Postman Desktop (ou version web)
✓ Git Bash (Windows) ou Terminal (Mac/Linux)
✓ Java 17+ (pour build local, optionnel)
✓ Maven 3.8+ (pour build local, optionnel)
```

### 1.2 Vérification de l'Environnement

```bash
# Vérifier Docker
docker --version
docker-compose --version

# Vérifier que Docker tourne
docker ps

# Vérifier les ports disponibles (MODE CABINET utilise des ports dynamiques)
# Par défaut pour le cabinet de test:
# - App: 8080-8999
# - PostgreSQL: 5500-5599
# - Redis: 6500-6599
# - PgAdmin: 5050-5099
```

### 1.3 Cloner le Projet

```bash
git clone <repo-url> predykt-backend-java
cd predykt-backend-java
```

---

## 2. Provisioning du Cabinet (Première Installation)

### 2.1 Script de Provisioning Automatique

Le script `provision-new-cabinet.sh` va automatiquement:

1. ✓ Générer des secrets sécurisés (DB, Redis, JWT)
2. ✓ Créer la structure de répertoires
3. ✓ Générer le fichier `.env.cabinet-{ID}`
4. ✓ Générer le `docker-compose.cabinet-{ID}.yml`
5. ✓ Démarrer PostgreSQL + Redis + PgAdmin
6. ✓ Builder et démarrer l'application Java
7. ✓ Attendre que les migrations Flyway s'exécutent
8. ✓ (Optionnel) Créer des données de test

### 2.2 Exécution du Provisioning

```bash
# Aller dans le répertoire scripts
cd scripts

# Rendre le script exécutable (Linux/Mac)
chmod +x provision-new-cabinet.sh

# Lancer le provisioning
./provision-new-cabinet.sh cabinet-test "Cabinet Test Douala"

# Répondre aux questions:
# 1. "Continuer le provisioning? (y/n)" -> y
# 2. "Créer des données de test? (y/n)" -> y
```

### 2.3 Ce qui est Créé Automatiquement

**Fichiers générés**:
```
.env.cabinet-test                          # Configuration environnement
docker-compose.cabinet-test.yml            # Docker compose pour ce cabinet
data/cabinets/cabinet-test/                # Dossiers de données
logs/cabinets/cabinet-test/                # Logs applicatifs
backups/cabinets/cabinet-test/             # Backups
cabinets-registry.log                      # Registre des cabinets créés
```

**Données de test créées (si accepté)**:
```
Cabinet:     ID=1, Nom="Cabinet Test Douala"
Entreprises:
  - ID=10: Entreprise A SARL
  - ID=11: Entreprise B SA
  - ID=12: Entreprise C SAS
Utilisateur Admin:
  - Email: admin@localhost (ou admin@{CABINET_DOMAIN})
  - Password: Admin@123
  - Accès: Tous les dossiers (10, 11, 12)
```

### 2.4 Vérification Post-Provisioning

```bash
# Vérifier que tous les conteneurs tournent
docker ps | grep cabinet-test

# Devrait afficher 4 conteneurs:
# - postgres-cabinet-test
# - redis-cabinet-test
# - pgadmin-cabinet-test
# - predykt-app-cabinet-test

# Tester l'endpoint health (sans authentification)
curl http://localhost:8080/api/v1/health

# Réponse attendue:
# {"status":"UP","timestamp":"2025-12-19T...","version":"1.0.0"}
```

---

## 3. Démarrage de l'Infrastructure

### 3.1 Avec Docker Compose (MODE SIMPLE - DEV LOCAL)

Si vous ne voulez pas utiliser le script de provisioning complet:

```bash
# Démarrer seulement l'infrastructure (PostgreSQL + Redis + PgAdmin)
docker-compose up -d

# Attendre que PostgreSQL soit prêt (environ 10-15 secondes)
docker logs -f predykt-postgres

# Vérifier que la DB est prête
docker exec predykt-postgres pg_isready -U predykt
```

### 3.2 Démarrer l'Application Java

```bash
# Option 1: Avec Maven (développement)
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=cabinet

# Option 2: Build JAR puis exécuter
./mvnw clean package -DskipTests
java -jar target/predykt-accounting-api-1.0.0.jar

# L'application démarre sur http://localhost:8080
# Les migrations Flyway s'exécutent automatiquement
```

### 3.3 Vérifier les Logs

```bash
# Logs de l'application
docker logs -f predykt-app-cabinet-test

# Logs PostgreSQL
docker logs predykt-postgres

# Vérifier les migrations Flyway dans les logs
# Rechercher: "Flyway" ou "Successfully applied"
```

### 3.4 Accès aux Services

| Service | URL | Credentials |
|---------|-----|-------------|
| **API PREDYKT** | http://localhost:8080 | JWT requis (voir section 5) |
| **Health Check** | http://localhost:8080/api/v1/health | Aucun (public) |
| **Swagger UI** | http://localhost:8080/api/v1/swagger-ui.html | Aucun (public) |
| **PgAdmin** | http://localhost:5050 | admin@predykt.com / admin123 |
| **PostgreSQL** | localhost:5432 | predykt / predykt_secure_2024 |
| **Redis** | localhost:6379 | Password: redis_secure_2024 |

---

## 4. Configuration de Postman

### 4.1 Importer la Collection Postman

1. Ouvrir Postman Desktop
2. Cliquer sur **Import** (en haut à gauche)
3. Sélectionner le fichier:
   ```
   PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json
   ```
4. Confirmer l'import
5. Vérifier que la collection apparaît avec **354 endpoints** dans **41 dossiers**

### 4.2 Importer l'Environnement Postman

1. Dans Postman, cliquer sur **Import**
2. Sélectionner le fichier:
   ```
   PREDYKT_COMPLETE.postman_environment.json
   ```
3. Confirmer l'import
4. Sélectionner l'environnement **"PREDYKT - COMPLET - DEV"** dans le dropdown en haut à droite

### 4.3 Vérifier les Variables d'Environnement

Cliquer sur l'icône **œil** (👁️) à côté de l'environnement pour voir les variables:

**Variables Critiques à Vérifier**:
```javascript
base_url: http://localhost:8080/api/v1  ✓ Correct
cabinet_id: 1                            ✓ Match avec données test
company_id_A: 10                         ✓ Entreprise A
company_id_B: 11                         ✓ Entreprise B
company_id_C: 12                         ✓ Entreprise C
admin_email: admin@localhost             ✓ Utilisateur admin test
admin_password: Admin@123                ✓ Mot de passe par défaut
```

### 4.4 Configurer les Auto-Scripts (Optionnel mais Recommandé)

La collection a déjà des **scripts de tests automatiques** sur chaque endpoint qui:
- Valident le code HTTP (200, 201, etc.)
- Valident la structure `ApiResponse` (success, message, timestamp)
- Extraient automatiquement les IDs des ressources créées

**Exemple**: Après avoir créé un client, le script extrait automatiquement `customer_id` et le stocke dans l'environnement.

---

## 5. Authentification et Premier Test

### 5.1 Architecture d'Authentification MODE CABINET

**Important**: En MODE CABINET, l'authentification JWT est **TOUJOURS activée** (sauf pour `/health`, `/auth/**`, `/swagger-ui/**`).

**Claims JWT en MODE CABINET**:
```json
{
  "userId": 100,
  "email": "admin@localhost",
  "companyId": 10,           // Dossier actuel
  "cabinetId": 1,            // Cabinet propriétaire
  "roles": ["ROLE_ADMIN_CABINET"],
  "iat": 1734604800,
  "exp": 1734691200
}
```

### 5.2 Étape 1: Login (Obtenir le Token JWT)

**Collection**: `01 - Authentication` → `POST Login Admin Cabinet`

**Request**:
```http
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "{{admin_email}}",
  "password": "{{admin_password}}"
}
```

**Response attendue** (200 OK):
```json
{
  "success": true,
  "message": "Connexion réussie",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOjEwMCwiZW1haWwiOiJhZG1pbkB...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOjEwMCwidHlwZSI6InJlZnJl...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 100,
      "email": "admin@localhost",
      "firstName": "Admin",
      "lastName": "Cabinet",
      "roles": ["ROLE_ADMIN_CABINET"],
      "cabinetId": 1,
      "companyId": 10
    }
  },
  "timestamp": "2025-12-19T10:30:00"
}
```

**Script de Test Auto** (déjà dans la collection):
```javascript
// Extraire et stocker le token automatiquement
if (pm.response.code === 200) {
    const response = pm.response.json();
    if (response.data && response.data.accessToken) {
        pm.environment.set("access_token", response.data.accessToken);
        pm.environment.set("refresh_token", response.data.refreshToken);
        pm.environment.set("user_id", response.data.user.id);
        console.log("✓ Token JWT stocké dans l'environnement");
    }
}
```

### 5.3 Étape 2: Vérifier l'Authentification

**Collection**: `00 - Health Check` → `GET Health Check`

```http
GET http://localhost:8080/api/v1/health
```

✓ **Pas d'authentification requise** - Devrait fonctionner

**Collection**: `03 - Companies Management` → `GET List All Companies`

```http
GET http://localhost:8080/api/v1/companies
Authorization: Bearer {{access_token}}
```

✓ **Authentification requise** - Doit retourner les 3 entreprises (IDs: 10, 11, 12)

### 5.4 Tester le Refresh Token (Optionnel)

Quand le `accessToken` expire (24h par défaut), utilisez:

**Collection**: `01 - Authentication` → `POST Refresh Token`

```http
POST http://localhost:8080/api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "{{refresh_token}}"
}
```

---

## 6. Ordre de Test Recommandé

### 6.1 Phase 1: Setup Initial (🔐 Requis)

| # | Dossier | Endpoint | Objectif | Variables Générées |
|---|---------|----------|----------|-------------------|
| 1 | `01 - Authentication` | `POST Login Admin Cabinet` | Obtenir JWT | `access_token`, `user_id` |
| 2 | `00 - Health Check` | `GET Health Check` | Vérifier l'API | - |
| 3 | `03 - Companies` | `GET List All Companies` | Lister dossiers | - |
| 4 | `04 - Chart of Accounts` | `POST Initialize OHADA Chart` | Créer plan comptable | - |

**Résultat attendu Phase 1**: ✓ JWT actif, ✓ API opérationnelle, ✓ 3 entreprises listées, ✓ Plan OHADA initialisé

### 6.2 Phase 2: Tiers (Clients & Fournisseurs)

| # | Dossier | Endpoint | Variables Générées |
|---|---------|----------|-------------------|
| 5 | `12 - Customers` | `POST Create Customer` | `customer_id` |
| 6 | `12 - Customers` | `GET Customer By ID` | - |
| 7 | `13 - Suppliers` | `POST Create Supplier` | `supplier_id` |
| 8 | `13 - Suppliers` | `GET Supplier By ID` | - |

### 6.3 Phase 3: Facturation

| # | Dossier | Endpoint | Variables Générées |
|---|---------|----------|-------------------|
| 9 | `10 - Invoices` | `POST Create Invoice` | `invoice_id` |
| 10 | `10 - Invoices` | `GET Invoice By ID` | - |
| 11 | `11 - Bills` | `POST Create Bill` | `bill_id` |
| 12 | `11 - Bills` | `GET Bill By ID` | - |

### 6.4 Phase 4: Import de Données CSV

| # | Dossier | Endpoint | Fichier CSV | Résultat |
|---|---------|----------|-------------|----------|
| 13 | `05 - Data Import` | `POST Import Activities CSV` | `activités.csv` | ~1500 écritures créées |
| 14 | `07 - General Ledger` | `GET Trial Balance` | - | Vérifier équilibre |

**Fichier CSV à utiliser**: `activités.csv` (déjà dans le projet root)

**Comment l'utiliser dans Postman**:
1. Ouvrir l'endpoint `POST Import Activities CSV`
2. Onglet **Body** → **form-data**
3. Ajouter une clé `file` de type **File**
4. Sélectionner `D:\Bel\projets\predykt-backend-java\activités.csv`
5. Send

### 6.5 Phase 5: Rapports Financiers

| # | Dossier | Endpoint | Paramètres |
|---|---------|----------|------------|
| 15 | `08 - Financial Reports` | `GET Balance Sheet` | `asOfDate=2025-01-31` |
| 16 | `08 - Financial Reports` | `GET Income Statement` | `startDate=2025-01-01&endDate=2025-01-31` |
| 17 | `09 - Financial Ratios` | `POST Calculate Ratios` | `startDate=2025-01-01&endDate=2025-01-31` |

### 6.6 Phase 6: Banque & Rapprochement ML

| # | Dossier | Endpoint | Variables |
|---|---------|----------|-----------|
| 18 | `06 - Bank Transactions` | `POST Import Bank Transactions` | Fichier CSV banque |
| 19 | `32 - Bank Reconciliation Advanced` | `POST Smart Match` | `transaction_id` |
| 20 | `32 - Bank Reconciliation Advanced` | `GET Match Suggestions` | - |
| 21 | `32 - Bank Reconciliation Advanced` | `POST Auto Reconcile` | - |

### 6.7 Phase 7: Machine Learning

| # | Dossier | Endpoint | Objectif |
|---|---------|----------|----------|
| 22 | `33 - ML Models & Training` | `POST Train New Model` | Entraîner XGBoost |
| 23 | `33 - ML Models & Training` | `GET List Models` | Vérifier modèles |
| 24 | `33 - ML Models & Training` | `POST Deploy Model` | Activer modèle |
| 25 | `34 - ML Predictions` | `GET Prediction History` | Voir prédictions |
| 26 | `34 - ML Predictions` | `POST Submit Feedback` | Corriger ML |

### 6.8 Phase 8: TVA & Fiscalité

| # | Dossier | Endpoint | Objectif |
|---|---------|----------|----------|
| 27 | `16 - Tax Management` | `GET VAT Collected` | TVA collectée |
| 28 | `16 - Tax Management` | `GET VAT Deductible` | TVA déductible |
| 29 | `17 - VAT Declarations` | `POST Generate Declaration` | Déclaration auto |
| 30 | `19 - VAT Rules` | `POST Create Rule` | Règle récupérabilité |

### 6.9 Phase 9: Exports (PDF/Excel/CSV)

| # | Dossier | Endpoint | Format |
|---|---------|----------|--------|
| 31 | `36 - Exports` | `GET Export Balance Sheet PDF` | PDF |
| 32 | `36 - Exports` | `GET Export Income Statement Excel` | Excel |
| 33 | `36 - Exports` | `GET Export General Ledger CSV` | CSV |

### 6.10 Phase 10: Cabinet Management

| # | Dossier | Endpoint | Objectif |
|---|---------|----------|----------|
| 34 | `37 - Cabinet Management` | `GET Cabinet Profile` | Info cabinet |
| 35 | `37 - Cabinet Management` | `GET Cabinet Companies` | Tous les dossiers |
| 36 | `38 - Time Tracking` | `POST Start Time Entry` | Suivi temps |
| 37 | `39 - Cabinet Invoices` | `POST Create Cabinet Invoice` | Facture honoraires |

---

## 7. Variables d'Environnement Expliquées

### 7.1 Variables de Configuration

| Variable | Valeur Initiale | Description | Modifiable |
|----------|----------------|-------------|-----------|
| `base_url` | `http://localhost:8080/api/v1` | URL de base de l'API | ✓ En production |
| `base_url_no_api` | `http://localhost:8080` | URL sans `/api/v1` | ✓ |
| `cabinet_id` | `1` | ID du cabinet actif | ✓ Si multi-cabinets |
| `cabinet_domain` | `localhost` | Domaine du cabinet | ✓ En production |

### 7.2 Variables d'Authentification

| Variable | Type | Description | Auto-remplie |
|----------|------|-------------|--------------|
| `access_token` | Secret | JWT access token | ✓ Après login |
| `refresh_token` | Secret | JWT refresh token | ✓ Après login |
| `token_expiry` | Default | Timestamp expiration | ✓ Après login |
| `admin_email` | Default | Email admin test | Manuelle |
| `admin_password` | Secret | Password admin | Manuelle |
| `user_id` | Default | ID utilisateur connecté | ✓ Après login |

### 7.3 Variables Dossiers Clients (Companies)

| Variable | Valeur | Description | Utilisation |
|----------|--------|-------------|-------------|
| `company_id` | `1` | Dossier par défaut (legacy) | Endpoints anciens |
| `company_id_A` | `10` | Entreprise A SARL | Tests principaux |
| `company_id_B` | `11` | Entreprise B SA | Tests multi-dossiers |
| `company_id_C` | `12` | Entreprise C SAS | Tests multi-dossiers |

**Quand utiliser quoi?**
- `company_id_A` (10): Pour tous les tests de base
- `company_id_B` (11): Pour tester l'isolation des données
- `company_id_C` (12): Pour tester les permissions d'accès

### 7.4 Variables de Ressources (Auto-générées)

Ces variables sont **automatiquement extraites** par les scripts de test après création:

| Variable | Générée par | Endpoint suivant qui l'utilise |
|----------|-------------|-------------------------------|
| `customer_id` | POST Create Customer | GET Customer By ID |
| `supplier_id` | POST Create Supplier | GET Supplier By ID |
| `invoice_id` | POST Create Invoice | GET Invoice By ID |
| `bill_id` | POST Create Bill | GET Bill By ID |
| `payment_id` | POST Record Payment | GET Payment By ID |
| `deposit_id` | POST Create Deposit | GET Deposit By ID |
| `ml_model_id` | POST Train New Model | POST Deploy Model |
| `prediction_id` | (Auto ML) | POST Submit Feedback |
| `experiment_id` | POST Create Experiment | POST Start Experiment Run |

### 7.5 Variables Comptables OHADA

| Variable | Valeur | Description |
|----------|--------|-------------|
| `account_client` | `411100` | Compte Clients (Classe 4) |
| `account_fournisseur` | `401100` | Compte Fournisseurs |
| `account_vente` | `701100` | Ventes de marchandises |
| `account_achat` | `601100` | Achats de marchandises |
| `account_tva_collectee` | `443100` | TVA collectée |
| `account_tva_deductible` | `445100` | TVA déductible |
| `account_banque` | `521100` | Banque |
| `account_caisse` | `571100` | Caisse |

### 7.6 Variables de Dates

| Variable | Valeur | Description |
|----------|--------|-------------|
| `fiscal_year` | `2025` | Année fiscale |
| `start_date` | `2025-01-01` | Début période |
| `end_date` | `2025-12-31` | Fin période |
| `as_of_date` | `2025-01-31` | Date arrêté |

### 7.7 Variables de Pagination

| Variable | Valeur | Description |
|----------|--------|-------------|
| `page` | `0` | Page courante (0-indexed) |
| `size` | `20` | Taille de page |
| `sort` | `createdAt,desc` | Tri par date desc |

---

## 8. Tests Complets par Module

### 8.1 Module: Authentification (Dossier 01)

#### Test 1.1: Login Succès
```http
POST /auth/login
{
  "email": "admin@localhost",
  "password": "Admin@123"
}

Résultat attendu: 200 OK
✓ accessToken présent
✓ refreshToken présent
✓ user.cabinetId = 1
✓ user.companyId = 10
✓ Variable access_token auto-remplie
```

#### Test 1.2: Login Échec (Mauvais password)
```http
POST /auth/login
{
  "email": "admin@localhost",
  "password": "WrongPassword"
}

Résultat attendu: 401 Unauthorized
✓ success = false
✓ message = "Identifiants incorrects"
```

#### Test 1.3: Refresh Token
```http
POST /auth/refresh
{
  "refreshToken": "{{refresh_token}}"
}

Résultat attendu: 200 OK
✓ Nouveau accessToken généré
✓ Variable access_token mise à jour
```

#### Test 1.4: Logout
```http
POST /auth/logout
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ Token invalidé (côté serveur si implémenté)
```

### 8.2 Module: Companies (Dossier 03)

#### Test 2.1: Lister tous les dossiers du cabinet
```http
GET /companies
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ 3 entreprises retournées
✓ IDs: 10, 11, 12
✓ Toutes ont cabinetId = 1
✓ accountingStandard = "OHADA"
```

#### Test 2.2: Détails d'un dossier
```http
GET /companies/{{company_id_A}}
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.id = 10
✓ data.name = "Entreprise A SARL"
✓ data.currency = "XAF"
```

#### Test 2.3: Créer un nouveau dossier client
```http
POST /companies
{
  "name": "Nouveau Client SARL",
  "legalForm": "SARL",
  "currency": "XAF",
  "accountingStandard": "OHADA",
  "country": "CM"
}

Résultat attendu: 201 Created
✓ data.id généré (ex: 13)
✓ data.cabinetId = 1 (auto-assigné)
✓ data.isActive = true
```

### 8.3 Module: Plan Comptable OHADA (Dossier 04)

#### Test 3.1: Initialiser plan OHADA
```http
POST /companies/{{company_id_A}}/charts/initialize
Authorization: Bearer {{access_token}}

Résultat attendu: 201 Created
✓ ~500 comptes créés
✓ Classes 1-8 présentes
✓ message = "Plan comptable OHADA initialisé"
```

#### Test 3.2: Lister tous les comptes
```http
GET /companies/{{company_id_A}}/charts
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ Environ 500 comptes
✓ Compte 101 (Capital) présent
✓ Compte 701 (Ventes) présent
```

#### Test 3.3: Rechercher un compte
```http
GET /companies/{{company_id_A}}/charts/411100
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.accountNumber = "411100"
✓ data.accountName = "Clients"
✓ data.accountType = "TIERS"
✓ data.isActive = true
```

#### Test 3.4: Créer un compte personnalisé
```http
POST /companies/{{company_id_A}}/charts
{
  "accountNumber": "701999",
  "accountName": "Ventes Export Spéciales",
  "accountType": "PRODUITS",
  "description": "Compte personnalisé pour export"
}

Résultat attendu: 201 Created
✓ Compte créé avec le numéro exact
✓ isCustom = true
```

### 8.4 Module: Import CSV (Dossier 05)

#### Test 4.1: Preview Import (avant import réel)
```http
POST /companies/{{company_id_A}}/import/activities/preview
Body: form-data
  file: activités.csv

Résultat attendu: 200 OK
✓ data.totalRows = ~1500
✓ data.validRows = ~1500
✓ data.invalidRows = 0
✓ Aperçu des 10 premières lignes
✓ Mapping automatique affiché
```

#### Test 4.2: Import Réel
```http
POST /companies/{{company_id_A}}/import/activities
Body: form-data
  file: activités.csv

Résultat attendu: 201 Created
⏱ Peut prendre 30-60 secondes
✓ data.successCount = ~1500
✓ data.errorCount = 0
✓ data.importId généré
✓ Écritures créées dans general_ledger

Vérifier dans logs:
- "Processing CSV import..."
- "Successfully imported 1500 activities"
```

#### Test 4.3: Vérifier les écritures créées
```http
GET /companies/{{company_id_A}}/general-ledger/trial-balance?asOfDate=2025-12-31
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ Total débits = Total crédits (équilibre)
✓ Comptes 701, 601, 661, etc. avec soldes
```

### 8.5 Module: Clients (Dossier 12)

#### Test 5.1: Créer un client
```http
POST /companies/{{company_id_A}}/customers
{
  "name": "CLIENT TEST SARL",
  "email": "contact@client-test.cm",
  "phone": "+237670123456",
  "address": "Douala, Cameroun",
  "taxId": "P237000123456"
}

Résultat attendu: 201 Created
✓ data.id généré
✓ Variable customer_id auto-remplie
✓ data.companyId = 10
```

#### Test 5.2: Lister les clients
```http
GET /companies/{{company_id_A}}/customers
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ Au moins 1 client (celui créé)
✓ Pagination fonctionnelle
```

#### Test 5.3: Solde client
```http
GET /companies/{{company_id_A}}/customers/{{customer_id}}/balance
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.balance = 0.00 (nouveau client)
✓ data.currency = "XAF"
```

### 8.6 Module: Factures Clients (Dossier 10)

#### Test 6.1: Créer une facture
```http
POST /api/v1/companies/{{company_id_A}}/invoices
{
  "customerId": "{{customer_id}}",
  "invoiceDate": "2025-01-15",
  "dueDate": "2025-02-15",
  "currency": "XAF",
  "lines": [
    {
      "description": "Prestation comptable Janvier",
      "quantity": 1,
      "unitPrice": 500000,
      "vatRate": 18.0,
      "accountNumber": "706100"
    }
  ]
}

Résultat attendu: 201 Created
✓ data.id généré
✓ data.totalHT = 500000
✓ data.totalTVA = 90000 (18%)
✓ data.totalTTC = 590000
✓ data.status = "DRAFT"
✓ Variable invoice_id auto-remplie
```

#### Test 6.2: Valider la facture
```http
POST /api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}/validate
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.status = "VALIDATED"
✓ data.invoiceNumber généré (ex: "INV-2025-001")
✓ Écriture comptable créée automatiquement:
  - Débit 411xxx (Client) 590000
  - Crédit 706100 (Prestation) 500000
  - Crédit 443100 (TVA collectée) 90000
```

#### Test 6.3: Historique paiements facture
```http
GET /api/v1/companies/{{company_id_A}}/invoices/{{invoice_id}}/payments
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data = [] (vide si pas encore de paiement)
```

### 8.7 Module: Paiements (Dossier 14)

#### Test 7.1: Enregistrer un paiement
```http
POST /companies/{{company_id_A}}/payments
{
  "invoiceId": "{{invoice_id}}",
  "amount": 590000,
  "paymentDate": "2025-01-20",
  "paymentMethod": "BANK_TRANSFER",
  "bankAccountNumber": "521100",
  "reference": "VIR-2025-001"
}

Résultat attendu: 201 Created
✓ data.id généré
✓ Variable payment_id auto-remplie
✓ Écriture comptable:
  - Débit 521100 (Banque) 590000
  - Crédit 411xxx (Client) 590000
✓ Invoice.amountPaid = 590000
✓ Invoice.balance = 0
✓ Invoice.status = "PAID"
```

### 8.8 Module: Rapports Financiers (Dossier 08)

#### Test 8.1: Bilan Comptable
```http
GET /companies/{{company_id_A}}/reports/balance-sheet?asOfDate=2025-01-31
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.assets présent (Actif)
  - Classe 2 (Immobilisations)
  - Classe 3 (Stocks)
  - Classe 4 (Créances)
  - Classe 5 (Trésorerie)
✓ data.liabilities présent (Passif)
  - Classe 1 (Capitaux propres)
  - Classe 4 (Dettes)
✓ Total Actif = Total Passif
```

#### Test 8.2: Compte de Résultat
```http
GET /companies/{{company_id_A}}/reports/income-statement?startDate=2025-01-01&endDate=2025-01-31
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.revenue (Classe 7 - Produits)
✓ data.expenses (Classe 6 - Charges)
✓ data.netIncome = revenue - expenses
✓ data.grossMargin
✓ data.operatingIncome (EBIT)
```

#### Test 8.3: Tableau des Flux de Trésorerie
```http
GET /companies/{{company_id_A}}/reports/cash-flow?startDate=2025-01-01&endDate=2025-01-31
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.operatingCashFlow
✓ data.investingCashFlow
✓ data.financingCashFlow
✓ data.netCashFlow = somme des 3
```

#### Test 8.4: TAFIRE (OHADA)
```http
GET /companies/{{company_id_A}}/reports/tafire?asOfDate=2025-01-31
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ Tableau OHADA conforme
✓ Variation du FR (Fonds de Roulement)
✓ Variation du BFR (Besoin en Fonds de Roulement)
✓ Variation de Trésorerie
```

### 8.9 Module: Ratios Financiers (Dossier 09)

#### Test 9.1: Calculer les ratios
```http
POST /companies/{{company_id_A}}/ratios/calculate?startDate=2025-01-01&endDate=2025-01-31
Authorization: Bearer {{access_token}}

Résultat attendu: 201 Created
✓ ~20 ratios calculés:

PROFITABILITÉ:
- ROA (Return on Assets)
- ROE (Return on Equity)
- Gross Margin %
- Net Margin %
- Operating Margin %

LIQUIDITÉ:
- Current Ratio
- Quick Ratio
- Cash Ratio

ACTIVITÉ:
- DSO (Days Sales Outstanding)
- DIO (Days Inventory Outstanding)
- DPO (Days Payables Outstanding)
- Cash Conversion Cycle

SOLVABILITÉ:
- Debt-to-Equity
- Debt-to-Assets
- Interest Coverage Ratio
```

#### Test 9.2: Historique des ratios
```http
GET /companies/{{company_id_A}}/ratios/history
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ Liste des calculs précédents
✓ Évolution dans le temps
```

### 8.10 Module: Machine Learning (Dossiers 33-35)

#### Test 10.1: Entraîner un modèle XGBoost
```http
POST /companies/{{company_id_A}}/ml/models/train
{
  "modelName": "XGBoost Bank Reconciliation v1",
  "algorithm": "XGBOOST",
  "hyperparameters": {
    "max_depth": 6,
    "learning_rate": 0.1,
    "n_estimators": 100,
    "objective": "binary:logistic"
  },
  "useAdvancedFeatures": true
}

Résultat attendu: 201 Created
⏱ Peut prendre 2-5 minutes
✓ data.modelId généré
✓ data.status = "TRAINING"
✓ data.accuracy > 0.90 (objectif 95-97%)
✓ Variable ml_model_id auto-remplie

Logs attendus:
- "Starting XGBoost training..."
- "Feature extraction complete: 30 features"
- "Training accuracy: 96.3%"
- "Model saved successfully"
```

#### Test 10.2: Déployer le modèle
```http
POST /companies/{{company_id_A}}/ml/models/{{ml_model_id}}/deploy
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.status = "DEPLOYED"
✓ data.isActive = true
✓ Modèle utilisé pour prédictions
```

#### Test 10.3: Obtenir des suggestions de matching
```http
GET /companies/{{company_id_A}}/bank-reconciliation/suggestions?transactionId={{transaction_id}}
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data = array de suggestions
✓ Chaque suggestion contient:
  - glEntryId (écriture suggérée)
  - confidence (0-100%)
  - matchScore
  - explanation (SHAP values)
✓ Trié par confidence DESC
✓ Top suggestion > 80% confidence
```

#### Test 10.4: Créer une expérience ML
```http
POST /companies/{{company_id_A}}/ml/experiments
{
  "name": "Hyperparameter Tuning - max_depth",
  "description": "Test variations de max_depth: 4, 6, 8, 10"
}

Résultat attendu: 201 Created
✓ data.experimentId généré
✓ Variable ml_experiment_id auto-remplie
```

#### Test 10.5: Lancer plusieurs runs d'expérience
```http
POST /companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/runs
{
  "parameters": {
    "max_depth": 4,
    "learning_rate": 0.1,
    "n_estimators": 100
  }
}

Répéter avec max_depth: 6, 8, 10

Résultat attendu: 201 Created (pour chaque run)
✓ Chaque run a un runId unique
✓ Métriques enregistrées (accuracy, precision, recall, F1)
```

#### Test 10.6: Comparer les runs
```http
GET /companies/{{company_id_A}}/ml/experiments/{{experiment_id}}/compare
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ Tableau comparatif:
  | Run ID | max_depth | Accuracy | Precision | Recall | F1 Score |
  |--------|-----------|----------|-----------|--------|----------|
  | 1      | 4         | 94.2%    | 93.1%     | 95.3%  | 94.2%    |
  | 2      | 6         | 96.5%    | 95.8%     | 97.2%  | 96.5%    | ← Best
  | 3      | 8         | 95.8%    | 94.9%     | 96.7%  | 95.8%    |
  | 4      | 10        | 95.1%    | 94.2%     | 96.0%  | 95.1%    |
```

### 8.11 Module: TVA (Dossiers 16-20)

#### Test 11.1: Créer une règle de récupérabilité TVA
```http
POST /companies/{{company_id_A}}/vat-rules
{
  "category": "VEHICULES",
  "recoverabilityRate": 50.0,
  "description": "Véhicules de tourisme - 50% déductible",
  "accountPattern": "624[1-3]%"
}

Résultat attendu: 201 Created
✓ data.id généré
✓ data.recoverabilityRate = 50.0
✓ Variable vat_rule_id auto-remplie
```

#### Test 11.2: Calculer la TVA du mois
```http
GET /companies/{{company_id_A}}/taxes/vat/to-pay?period=2025-01
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.tva_collectee (compte 443100)
✓ data.tva_deductible (compte 445100)
✓ data.tva_a_payer = collectée - déductible
✓ data.adjustments (récupérabilité partielle)
```

#### Test 11.3: Générer déclaration TVA
```http
POST /companies/{{company_id_A}}/vat-declarations/generate?period=2025-01
Authorization: Bearer {{access_token}}

Résultat attendu: 201 Created
✓ Déclaration créée automatiquement
✓ data.period = "2025-01"
✓ data.status = "DRAFT"
✓ data.totalDue calculé
```

### 8.12 Module: Exports (Dossier 36)

#### Test 12.1: Export Bilan en PDF
```http
GET /companies/{{company_id_A}}/exports/balance-sheet/pdf?asOfDate=2025-01-31
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
Content-Type: application/pdf
✓ Fichier PDF téléchargé
✓ Contient le bilan OHADA
✓ Header avec logo et infos entreprise
✓ Format SYSCOHADA respecté
```

#### Test 12.2: Export Grand-Livre en Excel
```http
GET /companies/{{company_id_A}}/exports/general-ledger/excel?accountNumber=411100
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
Content-Type: application/vnd.ms-excel
✓ Fichier Excel téléchargé
✓ Toutes les écritures du compte 411100
✓ Colonnes: Date, Journal, Libellé, Débit, Crédit, Solde
```

#### Test 12.3: Export Clients en CSV
```http
GET /companies/{{company_id_A}}/exports/customers/csv
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
Content-Type: text/csv
✓ Fichier CSV téléchargé
✓ Header: id,name,email,phone,balance,currency
✓ Encodage UTF-8
✓ Séparateur: virgule
```

### 8.13 Module: Cabinet Management (Dossiers 37-40)

#### Test 13.1: Profil du cabinet
```http
GET /cabinet/profile
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.id = 1
✓ data.name = "Cabinet Test Douala"
✓ data.domain = "localhost"
✓ data.plan = "STARTER"
✓ data.maxCompanies = 50
✓ data.currentCompanies = 3
```

#### Test 13.2: Ajouter un collaborateur au cabinet
```http
POST /cabinet/users
{
  "email": "collaborateur@localhost",
  "firstName": "Jean",
  "lastName": "Dupont",
  "role": "ROLE_COLLABORATOR"
}

Résultat attendu: 201 Created
✓ data.id généré
✓ Email de bienvenue envoyé (si SMTP configuré)
✓ Variable collaborator_id auto-remplie
```

#### Test 13.3: Démarrer un suivi de temps
```http
POST /cabinet/time-tracking/start
{
  "companyId": "{{company_id_A}}",
  "taskType": "ACCOUNTING",
  "description": "Saisie écritures comptables client A"
}

Résultat attendu: 201 Created
✓ data.id généré
✓ data.startTime = now
✓ data.status = "RUNNING"
✓ Variable time_entry_id auto-remplie
```

#### Test 13.4: Arrêter le compteur de temps
```http
POST /cabinet/time-tracking/stop/{{time_entry_id}}
Authorization: Bearer {{access_token}}

Résultat attendu: 200 OK
✓ data.endTime = now
✓ data.duration calculé (en minutes)
✓ data.status = "COMPLETED"
```

#### Test 13.5: Créer une facture honoraires
```http
POST /cabinet/invoices
{
  "companyId": "{{company_id_A}}",
  "period": "2025-01",
  "amount": 500000,
  "description": "Honoraires comptables Janvier 2025",
  "dueDate": "2025-02-15"
}

Résultat attendu: 201 Created
✓ data.id généré
✓ data.status = "DRAFT"
✓ Variable cabinet_invoice_id auto-remplie
```

---

## 9. Gestion des Erreurs

### 9.1 Erreurs Courantes

#### Erreur 401: Unauthorized
```json
{
  "success": false,
  "message": "Token JWT invalide ou expiré",
  "timestamp": "2025-12-19T12:30:00"
}
```

**Solution**:
1. Refaire un login: `POST /auth/login`
2. Vérifier que `{{access_token}}` est bien rempli dans l'environnement
3. Vérifier l'expiration du token (24h par défaut)

#### Erreur 403: Forbidden
```json
{
  "success": false,
  "message": "Accès refusé à ce dossier",
  "errorCode": "ACCESS_DENIED"
}
```

**Solution**:
1. Vérifier que l'utilisateur a accès au `company_id` demandé
2. Vérifier la table `user_company_access` dans PgAdmin
3. L'admin devrait avoir accès aux 3 dossiers (10, 11, 12)

#### Erreur 404: Not Found
```json
{
  "success": false,
  "message": "Entreprise non trouvée avec ID: 99",
  "errorCode": "COMPANY_NOT_FOUND"
}
```

**Solution**:
1. Vérifier que l'ID existe: `GET /companies`
2. Utiliser les IDs corrects: 10, 11, ou 12
3. Vérifier la variable `{{company_id_A}}` = 10

#### Erreur 400: Bad Request (Validation)
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Format email invalide"
    },
    {
      "field": "amount",
      "message": "Le montant doit être positif"
    }
  ]
}
```

**Solution**:
1. Vérifier le format des champs
2. Consulter la doc Swagger pour voir les contraintes
3. Exemples:
   - Email: `user@example.com` (pas `user@`)
   - Phone: `+237670123456` (format international)
   - Amount: `> 0`
   - Date: `YYYY-MM-DD` (pas `DD/MM/YYYY`)

#### Erreur 409: Conflict (Accounting)
```json
{
  "success": false,
  "message": "Écriture comptable déséquilibrée: Débit=1000000, Crédit=950000",
  "errorCode": "UNBALANCED_ENTRY"
}
```

**Solution**:
1. Vérifier que Total Débit = Total Crédit
2. Arrondir les montants à 2 décimales
3. Vérifier le calcul de la TVA

### 9.2 Erreurs de Configuration

#### Base de données non accessible
```
Error connecting to database: Connection refused
```

**Solution**:
```bash
# Vérifier que PostgreSQL tourne
docker ps | grep postgres

# Redémarrer PostgreSQL
docker restart predykt-postgres

# Vérifier les logs
docker logs predykt-postgres
```

#### Redis non accessible
```
Error connecting to Redis: Connection refused
```

**Solution**:
```bash
# Vérifier que Redis tourne
docker ps | grep redis

# Tester la connexion
docker exec predykt-redis redis-cli -a redis_secure_2024 ping

# Devrait retourner: PONG
```

#### Flyway migration échouée
```
Flyway migration failed: Checksum mismatch
```

**Solution**:
```bash
# Supprimer la base et recréer (⚠️ PERTE DE DONNÉES)
docker exec predykt-postgres psql -U predykt -c "DROP DATABASE predykt_db;"
docker exec predykt-postgres psql -U predykt -c "CREATE DATABASE predykt_db;"

# Redémarrer l'application (migrations re-exécutées)
docker restart predykt-app-cabinet-test
```

---

## 10. Dépannage

### 10.1 L'application ne démarre pas

#### Symptômes
- `curl http://localhost:8080/api/v1/health` → Connection refused
- Container `predykt-app-cabinet-test` status = `Restarting`

#### Diagnostic
```bash
# Voir les logs de l'application
docker logs predykt-app-cabinet-test

# Chercher les erreurs:
grep -i "error\|exception\|failed" logs.txt
```

#### Erreurs Communes

**1. Base de données non prête**
```
Caused by: org.postgresql.util.PSQLException: Connection refused
```
Solution: Attendre 30s que PostgreSQL soit prêt

**2. Mauvais mot de passe DB**
```
FATAL: password authentication failed for user "predykt"
```
Solution: Vérifier `.env.cabinet-test` → DB_PASSWORD

**3. Port déjà utilisé**
```
Error: Port 8080 is already in use
```
Solution:
```bash
# Trouver le processus sur le port 8080
netstat -ano | findstr :8080  # Windows
lsof -i :8080                  # Linux/Mac

# Arrêter le processus ou changer le port dans .env
```

**4. Flyway migration bloquée**
```
FlywayException: Validate failed: Checksum mismatch
```
Solution: Voir section 9.2

### 10.2 Authentification ne fonctionne pas

#### Symptôme
- Login retourne 401 alors que credentials corrects

#### Diagnostic
```bash
# Vérifier que l'utilisateur existe dans la DB
docker exec predykt-postgres psql -U predykt -d predykt_db -c \
  "SELECT id, email, is_active FROM users WHERE email = 'admin@localhost';"

# Vérifier le hash du password
docker exec predykt-postgres psql -U predykt -d predykt_db -c \
  "SELECT password_hash FROM users WHERE email = 'admin@localhost';"
```

#### Solution
Si l'utilisateur n'existe pas, re-exécuter le script d'initialisation:
```bash
# Voir provision-new-cabinet.sh lignes 542-597
# Ou créer manuellement:
docker exec predykt-postgres psql -U predykt -d predykt_db <<SQL
INSERT INTO users (email, password_hash, first_name, last_name, is_active, company_id, cabinet_id)
VALUES ('admin@localhost', '\$2a\$12\$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYVqNkMS7gC', 'Admin', 'Cabinet', true, 10, 1);
SQL
```

### 10.3 Variables Postman non mises à jour

#### Symptôme
- `{{customer_id}}` reste vide après création d'un client

#### Solution
1. Vérifier les **Tests** dans Postman (onglet Tests de l'endpoint)
2. Le script doit contenir:
   ```javascript
   if (pm.response.code === 201) {
       const response = pm.response.json();
       pm.environment.set("customer_id", response.data.id);
   }
   ```
3. Si le script manque, l'ajouter manuellement
4. Re-exécuter la requête

### 10.4 Import CSV échoue

#### Symptôme
- `POST /import/activities` retourne 400 Bad Request

#### Erreurs Possibles

**1. Mauvais format de fichier**
```json
{
  "message": "CSV header mismatch: expected 'date de saisie', got 'date'"
}
```
Solution: Utiliser le fichier exact `activités.csv` du projet

**2. Encodage incorrect**
```json
{
  "message": "Invalid character at line 23"
}
```
Solution: Le CSV doit être en UTF-8 avec BOM

**3. Timeout (fichier trop gros)**
```json
{
  "message": "Request timeout"
}
```
Solution: Augmenter le timeout dans Postman (Settings → Request timeout: 120000ms)

### 10.5 ML Training échoue

#### Symptôme
- `POST /ml/models/train` retourne 500 Internal Server Error

#### Diagnostic
```bash
# Vérifier les logs ML
docker logs predykt-app-cabinet-test | grep -i "ml\|xgboost\|training"

# Vérifier la mémoire disponible
docker stats predykt-app-cabinet-test
```

#### Erreurs Communes

**1. Pas assez de données**
```
Training failed: Minimum 100 samples required, found 0
```
Solution: Importer d'abord `activités.csv` (1500+ transactions)

**2. Out of Memory**
```
java.lang.OutOfMemoryError: Java heap space
```
Solution: Augmenter JAVA_OPTS dans `.env.cabinet-test`:
```bash
JAVA_OPTS=-Xms1024m -Xmx4096m
```

**3. XGBoost library non trouvée**
```
UnsatisfiedLinkError: no xgboost4j in java.library.path
```
Solution: Vérifier que `xgboost4j` est dans `pom.xml` et rebuild

### 10.6 PgAdmin ne se connecte pas à PostgreSQL

#### Symptôme
- PgAdmin affiche "Unable to connect to server"

#### Solution
1. Ouvrir PgAdmin: http://localhost:5050
2. Login: `admin@predykt.com` / `admin123`
3. Add New Server:
   - **Name**: PREDYKT Cabinet
   - **Host**: `postgres-cabinet-test` (nom du container, pas `localhost`)
   - **Port**: 5432
   - **Database**: `predykt_cabinet_test`
   - **Username**: `predykt_test`
   - **Password**: (voir `.env.cabinet-test`)

### 10.7 Ports déjà utilisés

#### Symptôme
```
Error: Bind for 0.0.0.0:8080 failed: port is already allocated
```

#### Solution
```bash
# Windows: Trouver le processus
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac: Trouver le processus
lsof -i :8080
kill -9 <PID>

# Ou changer le port dans .env.cabinet-test
APP_PORT=8081
```

---

## 11. Annexes

### 11.1 Architecture MODE CABINET

```
Cabinet Comptable (ID=1)
  │
  ├── Dossier Client A (company_id=10)
  │   ├── Plan Comptable OHADA
  │   ├── Écritures comptables
  │   ├── Clients/Fournisseurs
  │   ├── Factures/Paiements
  │   └── Modèles ML
  │
  ├── Dossier Client B (company_id=11)
  │   └── ...
  │
  └── Dossier Client C (company_id=12)
      └── ...

Utilisateurs:
  ├── Admin Cabinet (accès tous dossiers)
  ├── Collaborateur 1 (accès dossiers A, B)
  └── Client A (lecture seule dossier A)
```

### 11.2 Commandes Docker Utiles

```bash
# Voir tous les conteneurs
docker ps -a

# Logs en temps réel
docker logs -f predykt-app-cabinet-test

# Se connecter au conteneur PostgreSQL
docker exec -it predykt-postgres psql -U predykt -d predykt_db

# Se connecter au conteneur Redis
docker exec -it predykt-redis redis-cli -a redis_secure_2024

# Vérifier l'espace disque
docker system df

# Nettoyer les images inutilisées
docker system prune -a

# Backup de la base
docker exec predykt-postgres pg_dump -U predykt predykt_db > backup_$(date +%Y%m%d).sql

# Restore de la base
docker exec -i predykt-postgres psql -U predykt predykt_db < backup_20251219.sql
```

### 11.3 SQL Queries Utiles

```sql
-- Voir toutes les entreprises du cabinet
SELECT id, name, cabinet_id, is_active, created_at
FROM companies
WHERE cabinet_id = 1
ORDER BY created_at DESC;

-- Voir tous les utilisateurs
SELECT u.id, u.email, u.first_name, u.last_name, c.name AS company
FROM users u
LEFT JOIN companies c ON u.company_id = c.id
WHERE u.cabinet_id = 1;

-- Vérifier accès utilisateur aux dossiers
SELECT uca.user_id, u.email, uca.company_id, c.name, uca.access_level
FROM user_company_access uca
JOIN users u ON uca.user_id = u.id
JOIN companies c ON uca.company_id = c.id
WHERE u.email = 'admin@localhost';

-- Balance de vérification (trial balance)
SELECT coa.account_number, coa.account_name,
       SUM(CASE WHEN gl.debit_credit = 'DEBIT' THEN gl.amount ELSE 0 END) AS total_debit,
       SUM(CASE WHEN gl.debit_credit = 'CREDIT' THEN gl.amount ELSE 0 END) AS total_credit
FROM general_ledger gl
JOIN chart_of_accounts coa ON gl.account_id = coa.id
WHERE gl.company_id = 10
GROUP BY coa.account_number, coa.account_name
ORDER BY coa.account_number;

-- Migrations Flyway appliquées
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC;

-- Dernières écritures comptables
SELECT gl.id, gl.entry_date, gl.description, coa.account_number, coa.account_name,
       gl.debit_credit, gl.amount
FROM general_ledger gl
JOIN chart_of_accounts coa ON gl.account_id = coa.id
WHERE gl.company_id = 10
ORDER BY gl.entry_date DESC, gl.id DESC
LIMIT 50;
```

### 11.4 Endpoints Swagger

**Documentation complète**: http://localhost:8080/api/v1/swagger-ui.html

Fonctionnalités Swagger:
- ✓ Tous les 354 endpoints documentés
- ✓ Schémas de requêtes/réponses
- ✓ Test direct depuis l'interface
- ✓ Authentification JWT intégrée (bouton "Authorize")

**Comment autoriser dans Swagger**:
1. Cliquer sur le bouton **Authorize** (🔓) en haut
2. Entrer: `Bearer eyJhbGciOiJIUzUxMiJ9...` (votre token complet)
3. Cliquer **Authorize**
4. Tous les endpoints nécessitant auth fonctionneront

### 11.5 Checklist de Test Complète

#### Phase Setup
- [ ] Docker démarré
- [ ] PostgreSQL running
- [ ] Redis running
- [ ] Application démarrée (health check OK)
- [ ] Postman collection importée
- [ ] Environnement sélectionné

#### Phase Authentification
- [ ] Login admin réussi
- [ ] Token JWT stocké dans `{{access_token}}`
- [ ] Refresh token fonctionne

#### Phase Données de Base
- [ ] 3 entreprises listées (IDs: 10, 11, 12)
- [ ] Plan comptable OHADA initialisé (500+ comptes)
- [ ] Client créé
- [ ] Fournisseur créé

#### Phase Transactions
- [ ] Facture client créée
- [ ] Facture validée (écriture auto)
- [ ] Paiement enregistré
- [ ] Facture marquée payée

#### Phase Import CSV
- [ ] Preview import OK
- [ ] Import 1500+ activités réussi
- [ ] Balance équilibrée (débits = crédits)

#### Phase Rapports
- [ ] Bilan généré
- [ ] Compte de résultat généré
- [ ] Ratios calculés (20+)
- [ ] TAFIRE généré

#### Phase ML
- [ ] Modèle XGBoost entraîné
- [ ] Modèle déployé
- [ ] Suggestions de matching > 80% confidence
- [ ] Feedback ML enregistré

#### Phase Exports
- [ ] Export Bilan PDF
- [ ] Export Grand-livre Excel
- [ ] Export Clients CSV

#### Phase Cabinet
- [ ] Profil cabinet affiché
- [ ] Collaborateur ajouté
- [ ] Suivi temps créé
- [ ] Facture honoraires créée

---

## Conclusion

Ce guide couvre **100% des fonctionnalités** testables via Postman pour PREDYKT MODE CABINET.

**Prochaines Étapes**:
1. Suivre l'ordre de test recommandé (Section 6)
2. Exécuter les 354 endpoints progressivement
3. Vérifier les variables auto-remplies après chaque création
4. Consulter les logs en cas d'erreur
5. Utiliser PgAdmin pour inspecter la base de données

**Support**:
- Documentation API: http://localhost:8080/api/v1/swagger-ui.html
- Logs application: `docker logs -f predykt-app-cabinet-test`
- DB inspection: http://localhost:5050 (PgAdmin)

**Version**: 1.0 - 2025-12-19

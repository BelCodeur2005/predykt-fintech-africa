# 🧪 GUIDE DE TEST - MODE CABINET (Développement)

## 📋 Table des Matières

1. [Prérequis](#prérequis)
2. [Configuration Environnement](#configuration-environnement)
3. [Démarrage Mode Cabinet](#démarrage-mode-cabinet)
4. [Préparation des Données](#préparation-des-données)
5. [Tests avec Postman](#tests-avec-postman)
6. [Scénarios de Test](#scénarios-de-test)
7. [Troubleshooting](#troubleshooting)

---

## 🎯 Prérequis

### Logiciels Nécessaires

- ✅ Java 17+
- ✅ PostgreSQL 15+
- ✅ Redis 7+
- ✅ Postman (ou équivalent)
- ✅ Git Bash (Windows) ou terminal Unix

### Vérification

```bash
# Java
java -version  # Doit afficher Java 17+

# PostgreSQL
psql --version  # Doit afficher PostgreSQL 15+

# Redis
redis-cli --version  # Doit afficher Redis 7+

# Maven
./mvnw --version
```

---

## ⚙️ Configuration Environnement

### Étape 1 : Créer le Fichier de Configuration

Créez `.env.cabinet-test` à la racine du projet :

```bash
# ============================================
# MODE CABINET - Configuration Test
# ============================================

# ===== TENANT CONFIGURATION =====
PREDYKT_TENANT_MODE=CABINET
PREDYKT_TENANT_CABINET_ID=cabinet-test-dev
SPRING_PROFILES_ACTIVE=cabinet

# Cabinet Info
CABINET_NAME=Cabinet Expert Comptable Test
CABINET_DOMAIN=cabinet-test.local

# ===== DATABASE =====
DB_HOST=localhost
DB_PORT=5432
DB_NAME=predykt_cabinet_test
DB_USER=predykt
DB_PASSWORD=predykt_dev_password_123

# ===== REDIS =====
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ===== JWT =====
# Clé de 512 bits (64 caractères) pour HS512
JWT_SECRET=VGhpc0lzQVZlcnlTZWN1cmVTZWNyZXRLZXlGb3JKV1RUb2tlbkF1dGhlbnRpY2F0aW9uMTIzNDU2Nzg5MA==
JWT_EXPIRATION=86400000

# ===== APPLICATION =====
APP_PORT=8100
SERVER_PORT=8100

# ===== LOGGING =====
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_PREDYKT=DEBUG
```

### Étape 2 : Créer la Base de Données

```bash
# Se connecter à PostgreSQL
psql -U postgres

# Dans psql :
CREATE DATABASE predykt_cabinet_test;
CREATE USER predykt WITH PASSWORD 'predykt_dev_password_123';
GRANT ALL PRIVILEGES ON DATABASE predykt_cabinet_test TO predykt;
\q
```

### Étape 3 : Configurer application.yaml

Vérifiez que `src/main/resources/application.yaml` contient le profil `cabinet` :

```yaml
# Profil CABINET (lignes 146-166)
---
spring:
  config:
    activate:
      on-profile: cabinet
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:predykt_cabinet_test}
    username: ${DB_USER:predykt}
    password: ${DB_PASSWORD:predykt}

predykt:
  tenant:
    mode: CABINET
    cabinet-id: ${PREDYKT_TENANT_CABINET_ID:cabinet-test-dev}

logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [🟡CABINET-${PREDYKT_TENANT_CABINET_ID}] [%thread] %-5level %logger{36} - %msg%n"
```

---

## 🚀 Démarrage Mode Cabinet

### Option 1 : Via Maven (Recommandé pour Dev)

```bash
# Charger les variables d'environnement
export $(cat .env.cabinet-test | xargs)

# OU sur Windows (Git Bash)
export $(cat .env.cabinet-test | sed 's/\r$//' | xargs)

# OU sur Windows (PowerShell)
Get-Content .env.cabinet-test | ForEach-Object {
    $name, $value = $_.split('=')
    Set-Content env:\$name $value
}

# Démarrer l'application
./mvnw spring-boot:run -Dspring-boot.run.profiles=cabinet

# OU directement avec les variables
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=cabinet \
  -DPREDYKT_TENANT_MODE=CABINET \
  -DPREDYKT_TENANT_CABINET_ID=cabinet-test-dev
```

### Option 2 : Via Docker Compose

```bash
# Utiliser le fichier cabinet
docker-compose -f docker-compose.cabinet.yml --env-file .env.cabinet-test up -d

# Vérifier les logs
docker-compose -f docker-compose.cabinet.yml logs -f predykt-api
```

### Vérification du Démarrage

```bash
# Health check
curl http://localhost:8100/api/v1/health

# Devrait retourner :
{
  "status": "UP",
  "tenantMode": "CABINET",
  "cabinetId": "cabinet-test-dev"
}

# Vérifier les logs
# Vous devriez voir : [🟡CABINET-cabinet-test-dev]
```

---

## 📊 Préparation des Données

### Script SQL de Test

Créez `test-data-cabinet.sql` :

```sql
-- ============================================
-- DONNÉES DE TEST - MODE CABINET
-- ============================================

-- 1. CRÉER LE CABINET
INSERT INTO cabinets (id, name, domain, is_active, created_at)
VALUES (1, 'Cabinet Expert Comptable Test', 'cabinet-test.local', TRUE, NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. CRÉER LES DOSSIERS CLIENTS (Companies)
INSERT INTO companies (id, name, siret, tax_id, address, city, postal_code, country,
                       currency, fiscal_year_end_month, fiscal_year_end_day,
                       is_active, cabinet_id, created_at)
VALUES
  -- Dossier 1 : Entreprise A
  (10, 'Entreprise A SARL', '12345678900001', 'CM-12345678',
   '123 Rue de Douala', 'Douala', '00237', 'Cameroun',
   'XAF', 12, 31, TRUE, 1, NOW()),

  -- Dossier 2 : Entreprise B
  (11, 'Entreprise B SA', '23456789000002', 'CM-23456789',
   '456 Rue de Yaoundé', 'Yaoundé', '00237', 'Cameroun',
   'XAF', 12, 31, TRUE, 1, NOW()),

  -- Dossier 3 : Entreprise C
  (12, 'Entreprise C SAS', '34567890000003', 'CM-34567890',
   '789 Boulevard de Bafoussam', 'Bafoussam', '00237', 'Cameroun',
   'XAF', 12, 31, TRUE, 1, NOW())
ON CONFLICT (id) DO NOTHING;

-- Réinitialiser la séquence
SELECT setval('companies_id_seq', 13);

-- 3. CRÉER LES UTILISATEURS DU CABINET

-- User 1 : Admin Cabinet
INSERT INTO users (id, company_id, cabinet_id, email, password_hash,
                   first_name, last_name, phone, is_active, is_email_verified,
                   failed_login_attempts, created_at)
VALUES
  (100, 10, 1, 'admin@cabinet-test.local',
   '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIRJ9T.HEG', -- password: Admin123!
   'Jean', 'Dupont', '+237690000001', TRUE, TRUE, 0, NOW())
ON CONFLICT (id) DO NOTHING;

-- User 2 : Collaborateur
INSERT INTO users (id, company_id, cabinet_id, email, password_hash,
                   first_name, last_name, phone, is_active, is_email_verified,
                   failed_login_attempts, created_at)
VALUES
  (101, 10, 1, 'collab@cabinet-test.local',
   '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIRJ9T.HEG', -- password: Collab123!
   'Marie', 'Martin', '+237690000002', TRUE, TRUE, 0, NOW())
ON CONFLICT (id) DO NOTHING;

-- User 3 : Client (Entreprise A uniquement)
INSERT INTO users (id, company_id, cabinet_id, email, password_hash,
                   first_name, last_name, phone, is_active, is_email_verified,
                   failed_login_attempts, created_at)
VALUES
  (102, 10, 1, 'client@entreprise-a.com',
   '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIRJ9T.HEG', -- password: Client123!
   'Pierre', 'Njoya', '+237690000003', TRUE, TRUE, 0, NOW())
ON CONFLICT (id) DO NOTHING;

-- Réinitialiser la séquence
SELECT setval('users_id_seq', 103);

-- 4. ATTRIBUER LES RÔLES

-- Admin Cabinet → ROLE_ADMIN
INSERT INTO user_roles (user_id, role_id, granted_at, granted_by)
VALUES (100, 1, NOW(), 'SYSTEM')  -- role_id 1 = ROLE_ADMIN
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Collaborateur → ROLE_ACCOUNTANT
INSERT INTO user_roles (user_id, role_id, granted_at, granted_by)
VALUES (101, 2, NOW(), 'SYSTEM')  -- role_id 2 = ROLE_ACCOUNTANT
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Client → ROLE_VIEWER
INSERT INTO user_roles (user_id, role_id, granted_at, granted_by)
VALUES (102, 4, NOW(), 'SYSTEM')  -- role_id 4 = ROLE_VIEWER
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 5. DÉFINIR LES ACCÈS AUX DOSSIERS (user_company_access)

-- Admin Cabinet : Accès TOUS les dossiers (ADMIN)
INSERT INTO user_company_access (id, user_id, company_id, access_level,
                                 can_view_confidential, granted_at, granted_by)
VALUES
  (1, 100, 10, 'ADMIN', TRUE, NOW(), 'SYSTEM'),  -- Entreprise A
  (2, 100, 11, 'ADMIN', TRUE, NOW(), 'SYSTEM'),  -- Entreprise B
  (3, 100, 12, 'ADMIN', TRUE, NOW(), 'SYSTEM')   -- Entreprise C
ON CONFLICT (id) DO NOTHING;

-- Collaborateur : Accès Entreprise A et B (READ_WRITE)
INSERT INTO user_company_access (id, user_id, company_id, access_level,
                                 can_view_confidential, granted_at, granted_by)
VALUES
  (4, 101, 10, 'READ_WRITE', TRUE, NOW(), 'SYSTEM'),  -- Entreprise A
  (5, 101, 11, 'READ_WRITE', FALSE, NOW(), 'SYSTEM')  -- Entreprise B
ON CONFLICT (id) DO NOTHING;

-- Client : Accès uniquement Entreprise A (READ_ONLY)
INSERT INTO user_company_access (id, user_id, company_id, access_level,
                                 can_view_confidential, granted_at, granted_by)
VALUES
  (6, 102, 10, 'READ_ONLY', FALSE, NOW(), 'SYSTEM')  -- Entreprise A uniquement
ON CONFLICT (id) DO NOTHING;

-- Réinitialiser la séquence
SELECT setval('user_company_access_id_seq', 7);

-- 6. CRÉER QUELQUES DONNÉES COMPTABLES DE TEST

-- Plan Comptable Entreprise A
INSERT INTO chart_of_accounts (id, company_id, account_number, account_name,
                                account_type, is_active, created_at)
VALUES
  (1, 10, '401000', 'Fournisseurs', 'DETTES', TRUE, NOW()),
  (2, 10, '411000', 'Clients', 'ACTIF_CIRCULANT', TRUE, NOW()),
  (3, 10, '512000', 'Banque', 'ACTIF_CIRCULANT', TRUE, NOW()),
  (4, 10, '701000', 'Ventes de marchandises', 'PRODUITS_EXPLOITATION', TRUE, NOW()),
  (5, 10, '601000', 'Achats de marchandises', 'CHARGES_EXPLOITATION', TRUE, NOW())
ON CONFLICT (id) DO NOTHING;

-- Écritures Comptables Entreprise A
INSERT INTO general_ledger (id, company_id, account_id, entry_date, description,
                            debit_amount, credit_amount, journal_code, reference,
                            fiscal_year, period, is_locked, created_at)
VALUES
  -- Vente
  (1, 10, 2, '2024-01-15', 'Vente Client A - Facture FA001',
   1000000, 0, 'VE', 'FA001', '2024', '2024-01', FALSE, NOW()),
  (2, 10, 4, '2024-01-15', 'Vente Client A - Facture FA001',
   0, 1000000, 'VE', 'FA001', '2024', '2024-01', FALSE, NOW()),

  -- Achat
  (3, 10, 5, '2024-01-20', 'Achat Fournisseur B - Facture FB001',
   500000, 0, 'AC', 'FB001', '2024', '2024-01', FALSE, NOW()),
  (4, 10, 1, '2024-01-20', 'Achat Fournisseur B - Facture FB001',
   0, 500000, 'AC', 'FB001', '2024', '2024-01', FALSE, NOW())
ON CONFLICT (id) DO NOTHING;

-- ============================================
-- AFFICHER UN RÉSUMÉ
-- ============================================

SELECT
  '✅ CABINET CRÉÉ' as status,
  (SELECT COUNT(*) FROM cabinets WHERE id = 1) as cabinet_count,
  (SELECT name FROM cabinets WHERE id = 1) as cabinet_name;

SELECT
  '✅ DOSSIERS CLIENTS' as status,
  COUNT(*) as total_companies,
  STRING_AGG(name, ', ') as companies
FROM companies WHERE cabinet_id = 1;

SELECT
  '✅ UTILISATEURS' as status,
  COUNT(*) as total_users,
  STRING_AGG(email, ', ') as users
FROM users WHERE cabinet_id = 1;

SELECT
  '✅ ACCÈS DOSSIERS' as status,
  u.email as user,
  c.name as company,
  uca.access_level as access
FROM user_company_access uca
JOIN users u ON uca.user_id = u.id
JOIN companies c ON uca.company_id = c.id
ORDER BY u.id, c.id;

SELECT
  '✅ ÉCRITURES COMPTABLES' as status,
  COUNT(*) as total_entries,
  SUM(debit_amount) as total_debit
FROM general_ledger WHERE company_id = 10;
```

### Exécuter le Script

```bash
# Charger les données de test
psql -U predykt -d predykt_cabinet_test -f test-data-cabinet.sql

# Vérifier
psql -U predykt -d predykt_cabinet_test -c "SELECT * FROM cabinets;"
psql -U predykt -d predykt_cabinet_test -c "SELECT id, name, cabinet_id FROM companies;"
psql -U predykt -d predykt_cabinet_test -c "SELECT id, email, cabinet_id FROM users;"
```

---

## 🧪 Tests avec Postman

### Collection Postman

Créez une collection `PREDYKT - Mode Cabinet Tests` avec les variables suivantes :

**Variables de Collection** :
```json
{
  "baseUrl": "http://localhost:8100/api/v1",
  "cabinetId": "cabinet-test-dev",
  "accessToken": "",
  "refreshToken": "",
  "companyId": "10"
}
```

---

### 1️⃣ Test : Inscription (Register)

**Requête** :
```http
POST {{baseUrl}}/auth/register
Content-Type: application/json

{
  "email": "nouveau@cabinet-test.local",
  "password": "Nouveau123!",
  "firstName": "Nouveau",
  "lastName": "User",
  "phone": "+237690000999",
  "companyId": 10
}
```

**Tests Postman** :
```javascript
// Vérifier status 201
pm.test("Status is 201", function () {
    pm.response.to.have.status(201);
});

// Vérifier structure response
pm.test("Response has tokens", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data).to.have.property("accessToken");
    pm.expect(jsonData.data).to.have.property("refreshToken");
    pm.expect(jsonData.data).to.have.property("userId");
});

// Sauvegarder tokens
var jsonData = pm.response.json();
pm.collectionVariables.set("accessToken", jsonData.data.accessToken);
pm.collectionVariables.set("refreshToken", jsonData.data.refreshToken);

console.log("✅ User registered with ID:", jsonData.data.userId);
```

**Réponse Attendue** :
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "userId": 103,
    "email": "nouveau@cabinet-test.local",
    "fullName": "Nouveau User",
    "roles": ["ROLE_ACCOUNTANT"]
  },
  "message": "Compte créé avec succès",
  "timestamp": "2024-12-15T10:30:00"
}
```

---

### 2️⃣ Test : Connexion Admin Cabinet

**Requête** :
```http
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "admin@cabinet-test.local",
  "password": "Admin123!"
}
```

**Tests Postman** :
```javascript
pm.test("Status is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has JWT tokens", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data).to.have.property("accessToken");
    pm.expect(jsonData.data.accessToken).to.match(/^eyJ/);
});

pm.test("User has ROLE_ADMIN", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data.roles).to.include("ROLE_ADMIN");
});

// Sauvegarder tokens dans variables de collection
var jsonData = pm.response.json();
pm.collectionVariables.set("accessToken", jsonData.data.accessToken);
pm.collectionVariables.set("refreshToken", jsonData.data.refreshToken);

// Décoder JWT pour vérifier claims (facultatif)
function parseJwt(token) {
    var base64Url = token.split('.')[1];
    var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    var jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    return JSON.parse(jsonPayload);
}

var decoded = parseJwt(jsonData.data.accessToken);
console.log("JWT Claims:", decoded);
console.log("Cabinet ID:", decoded.cabinetId);
console.log("Company ID:", decoded.companyId);
console.log("Roles:", decoded.roles);

pm.test("JWT contains cabinetId", function () {
    pm.expect(decoded).to.have.property("cabinetId");
    pm.expect(decoded.cabinetId).to.equal("cabinet-test-dev");
});
```

**Réponse Attendue** :
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOjEwMCwiZW1haWwiOiJhZG1pbkBjYWJpbmV0LXRlc3QubG9jYWwiLCJjb21wYW55SWQiOjEwLCJjYWJpbmV0SWQiOiJjYWJpbmV0LXRlc3QtZGV2Iiwicm9sZXMiOlsiUk9MRV9BRE1JTiJdLCJwZXJtaXNzaW9ucyI6WyJDT01QQU5ZOlJFQUQiLCJDT01QQU5ZOldSSVRFIiwiR0w6UkVBRCIsIkdMOldSSVRFIl0sImlzcyI6IlBSRURZS1QiLCJhdWQiOiJjYWJpbmV0LXRlc3QubG9jYWwiLCJpYXQiOjE3MzQyNzg0MDAsImV4cCI6MTczNDM2NDgwMH0...",
    "refreshToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "userId": 100,
    "email": "admin@cabinet-test.local",
    "fullName": "Jean Dupont",
    "roles": ["ROLE_ADMIN"]
  },
  "message": "Connexion réussie",
  "timestamp": "2024-12-15T10:30:00"
}
```

---

### 3️⃣ Test : Lister les Dossiers Clients

**Requête** :
```http
GET {{baseUrl}}/companies
Authorization: Bearer {{accessToken}}
```

**Tests Postman** :
```javascript
pm.test("Status is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Returns companies list", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data).to.be.an('array');
    pm.expect(jsonData.data.length).to.be.greaterThan(0);
});

pm.test("All companies belong to cabinet", function () {
    var jsonData = pm.response.json();
    jsonData.data.forEach(function(company) {
        pm.expect(company).to.have.property("cabinetId");
    });
});

console.log("Companies found:", pm.response.json().data.length);
```

**Réponse Attendue** :
```json
{
  "success": true,
  "data": [
    {
      "id": 10,
      "name": "Entreprise A SARL",
      "siret": "12345678900001",
      "taxId": "CM-12345678",
      "address": "123 Rue de Douala",
      "city": "Douala",
      "country": "Cameroun",
      "currency": "XAF",
      "cabinetId": 1,
      "isActive": true
    },
    {
      "id": 11,
      "name": "Entreprise B SA",
      "siret": "23456789000002",
      "taxId": "CM-23456789",
      "address": "456 Rue de Yaoundé",
      "city": "Yaoundé",
      "country": "Cameroun",
      "currency": "XAF",
      "cabinetId": 1,
      "isActive": true
    },
    {
      "id": 12,
      "name": "Entreprise C SAS",
      "siret": "34567890000003",
      "taxId": "CM-34567890",
      "address": "789 Boulevard de Bafoussam",
      "city": "Bafoussam",
      "country": "Cameroun",
      "currency": "XAF",
      "cabinetId": 1,
      "isActive": true
    }
  ],
  "message": "Liste des entreprises",
  "timestamp": "2024-12-15T10:35:00"
}
```

---

### 4️⃣ Test : Accéder au Grand Livre d'un Dossier

**Requête** :
```http
GET {{baseUrl}}/companies/{{companyId}}/general-ledger?startDate=2024-01-01&endDate=2024-12-31
Authorization: Bearer {{accessToken}}
```

**Tests Postman** :
```javascript
pm.test("Status is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Returns ledger entries", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data).to.be.an('array');
});

pm.test("All entries belong to requested company", function () {
    var jsonData = pm.response.json();
    var requestedCompanyId = parseInt(pm.collectionVariables.get("companyId"));

    jsonData.data.forEach(function(entry) {
        pm.expect(entry.companyId).to.equal(requestedCompanyId);
    });
});

console.log("Ledger entries found:", pm.response.json().data.length);
```

**Réponse Attendue** :
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "companyId": 10,
      "accountNumber": "411000",
      "accountName": "Clients",
      "entryDate": "2024-01-15",
      "description": "Vente Client A - Facture FA001",
      "debitAmount": 1000000.00,
      "creditAmount": 0.00,
      "journalCode": "VE",
      "reference": "FA001",
      "fiscalYear": "2024",
      "period": "2024-01"
    },
    {
      "id": 2,
      "companyId": 10,
      "accountNumber": "701000",
      "accountName": "Ventes de marchandises",
      "entryDate": "2024-01-15",
      "description": "Vente Client A - Facture FA001",
      "debitAmount": 0.00,
      "creditAmount": 1000000.00,
      "journalCode": "VE",
      "reference": "FA001",
      "fiscalYear": "2024",
      "period": "2024-01"
    }
  ],
  "message": "Grand livre récupéré",
  "timestamp": "2024-12-15T10:40:00"
}
```

---

### 5️⃣ Test : Vérifier Isolation Multi-Dossier

**Scénario** : Collaborateur (accès Entreprise A et B) essaie d'accéder à Entreprise C

**Connexion Collaborateur** :
```http
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "collab@cabinet-test.local",
  "password": "Collab123!"
}
```

**Tentative d'accès Entreprise C (ID=12)** :
```http
GET {{baseUrl}}/companies/12/general-ledger
Authorization: Bearer {{accessToken}}
```

**Tests Postman** :
```javascript
pm.test("Status is 403 Forbidden", function () {
    pm.response.to.have.status(403);
});

pm.test("Error message indicates no access", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.message).to.include("accès");
});
```

**Réponse Attendue** :
```json
{
  "success": false,
  "error": "FORBIDDEN",
  "message": "Vous n'avez pas accès à ce dossier client",
  "timestamp": "2024-12-15T10:45:00"
}
```

---

### 6️⃣ Test : Connexion Client (Accès Limité)

**Requête** :
```http
POST {{baseUrl}}/auth/login
Content-Type: application/json

{
  "email": "client@entreprise-a.com",
  "password": "Client123!"
}
```

**Vérifier Accès READ_ONLY** :
```http
POST {{baseUrl}}/companies/10/general-ledger
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{
  "entryDate": "2024-01-25",
  "description": "Tentative d'écriture par client",
  "journalCode": "OD",
  "lines": [
    {
      "accountNumber": "512000",
      "debitAmount": 100000,
      "creditAmount": 0
    },
    {
      "accountNumber": "701000",
      "debitAmount": 0,
      "creditAmount": 100000
    }
  ]
}
```

**Tests Postman** :
```javascript
pm.test("Status is 403 Forbidden", function () {
    pm.response.to.have.status(403);
});

pm.test("Error indicates read-only access", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.message).to.include("lecture seule");
});
```

**Réponse Attendue** :
```json
{
  "success": false,
  "error": "FORBIDDEN",
  "message": "Vous avez uniquement un accès en lecture seule à ce dossier",
  "timestamp": "2024-12-15T10:50:00"
}
```

---

### 7️⃣ Test : Refresh Token

**Requête** :
```http
POST {{baseUrl}}/auth/refresh
Content-Type: application/json

{
  "refreshToken": "{{refreshToken}}"
}
```

**Tests Postman** :
```javascript
pm.test("Status is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("New access token generated", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data.accessToken).to.not.equal(pm.collectionVariables.get("accessToken"));
});

// Mettre à jour le token
var jsonData = pm.response.json();
pm.collectionVariables.set("accessToken", jsonData.data.accessToken);
```

---

### 8️⃣ Test : Déconnexion

**Requête** :
```http
POST {{baseUrl}}/auth/logout
Authorization: Bearer {{accessToken}}
```

**Tests Postman** :
```javascript
pm.test("Status is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Token is invalidated", function () {
    // Tenter une requête avec le token révoqué
    pm.sendRequest({
        url: pm.collectionVariables.get("baseUrl") + "/companies",
        method: "GET",
        header: {
            "Authorization": "Bearer " + pm.collectionVariables.get("accessToken")
        }
    }, function (err, res) {
        pm.expect(res.code).to.equal(401);
    });
});

// Nettoyer les tokens
pm.collectionVariables.set("accessToken", "");
pm.collectionVariables.set("refreshToken", "");
```

---

## 🎬 Scénarios de Test Complets

### Scénario 1 : Workflow Admin Cabinet

```
1. Connexion admin → GET JWT
2. Lister tous les dossiers (3 dossiers)
3. Accéder dossier Entreprise A → GET écritures
4. Créer une écriture comptable → POST success
5. Accéder dossier Entreprise B → GET écritures
6. Créer facture client → POST success
7. Exporter rapport → GET PDF
8. Déconnexion → POST logout
```

### Scénario 2 : Workflow Collaborateur

```
1. Connexion collab → GET JWT
2. Lister dossiers accessibles (2 dossiers : A et B)
3. Tentative accès dossier C → 403 Forbidden
4. Accéder dossier A → GET écritures OK
5. Créer écriture dossier A → POST success
6. Accéder dossier B → GET écritures OK
7. Créer écriture dossier B → POST success
8. Déconnexion
```

### Scénario 3 : Workflow Client

```
1. Connexion client → GET JWT
2. Lister dossiers (1 seul : Entreprise A)
3. Tentative accès dossier B → 403 Forbidden
4. Accéder dossier A → GET écritures OK
5. Consulter rapports → GET OK
6. Tentative création écriture → 403 Forbidden (READ_ONLY)
7. Télécharger bilan → GET PDF OK
8. Déconnexion
```

---

## 🐛 Troubleshooting

### Problème 1 : "PREDYKT_TENANT_CABINET_ID is required"

**Erreur** :
```
java.lang.IllegalStateException: PREDYKT_TENANT_CABINET_ID is required in CABINET mode
```

**Solution** :
```bash
# Vérifier la variable d'environnement
echo $PREDYKT_TENANT_CABINET_ID

# Si vide, charger .env
export $(cat .env.cabinet-test | xargs)

# Ou lancer avec la variable
./mvnw spring-boot:run -DPREDYKT_TENANT_CABINET_ID=cabinet-test-dev
```

---

### Problème 2 : "Database 'predykt_cabinet_test' does not exist"

**Solution** :
```bash
# Créer la base
createdb -U postgres predykt_cabinet_test

# Donner les droits
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE predykt_cabinet_test TO predykt;"

# Vérifier
psql -U predykt -d predykt_cabinet_test -c "\dt"
```

---

### Problème 3 : "Token invalide pour ce tenant"

**Erreur** :
```json
{
  "error": "Token invalide pour ce tenant"
}
```

**Cause** : Le `cabinetId` dans le JWT ne correspond pas à `PREDYKT_TENANT_CABINET_ID`

**Solution** :
1. Vérifier la variable ENV :
   ```bash
   echo $PREDYKT_TENANT_CABINET_ID
   ```

2. Reconnecter pour obtenir un nouveau token avec le bon `cabinetId`

3. Décoder le JWT pour vérifier :
   ```bash
   # jwt.io
   # Vérifier claim "cabinetId"
   ```

---

### Problème 4 : "User not found" après inscription

**Cause** : `isEmailVerified=false` et `User.isEnabled()` vérifie cette condition

**Solution Temporaire** :
```sql
-- Activer manuellement la vérification
UPDATE users SET is_email_verified = TRUE WHERE email = 'nouveau@cabinet-test.local';
```

**Solution Permanente** :
```java
// User.java ligne 154
@Override
public boolean isEnabled() {
    // MVP: Ne pas vérifier email pour le dev
    return isActive != null && isActive;
    // Production: return isActive != null && isActive && isEmailVerified;
}
```

---

### Problème 5 : Ports déjà utilisés

**Erreur** :
```
Port 8100 is already in use
```

**Solution** :
```bash
# Vérifier quel processus utilise le port
lsof -i :8100  # Linux/Mac
netstat -ano | findstr :8100  # Windows

# Tuer le processus
kill -9 <PID>  # Linux/Mac
taskkill /PID <PID> /F  # Windows

# Ou changer le port dans .env
APP_PORT=8101
```

---

### Problème 6 : JWT Secret trop court

**Erreur** :
```
WeakKeyException: The specified key byte array is 320 bits which is not secure enough for any JWT HMAC-SHA algorithm.
```

**Solution** :
```bash
# Générer clé 512 bits (64 caractères)
openssl rand -base64 64 | tr -d "=+/" | cut -c1-64

# Mettre dans .env
JWT_SECRET=<résultat>
```

---

## 📊 Vérifications Finales

### Checklist de Test Complet

```bash
# 1. Environnement
✓ PostgreSQL démarré
✓ Redis démarré
✓ Variables ENV chargées
✓ Base de données créée
✓ Données de test chargées

# 2. Application
✓ App démarrée sur port 8100
✓ Health check OK
✓ Logs affichent [🟡CABINET-cabinet-test-dev]

# 3. Authentification
✓ Inscription fonctionne
✓ Connexion admin OK
✓ Connexion collab OK
✓ Connexion client OK
✓ JWT contient cabinetId
✓ Refresh token OK
✓ Logout OK

# 4. Isolation Multi-Dossier
✓ Admin voit 3 dossiers
✓ Collab voit 2 dossiers
✓ Client voit 1 dossier
✓ Accès refusé à dossier non autorisé (403)

# 5. Permissions
✓ Admin peut CRUD sur tous dossiers
✓ Collab peut CRUD sur dossiers autorisés
✓ Client READ_ONLY sur son dossier
✓ Création écriture par client refusée (403)

# 6. Données
✓ Écritures comptables visibles
✓ Filtrage par company_id fonctionne
✓ Pas de fuite de données entre dossiers
```

---

## 🚀 Prochaines Étapes

### Pour Aller Plus Loin

1. **Tests Automatisés** : Convertir tests Postman en Newman (CLI)
   ```bash
   npm install -g newman
   newman run PREDYKT-Cabinet-Tests.postman_collection.json
   ```

2. **Tests d'Intégration** : Créer tests JUnit
   ```java
   @SpringBootTest
   @ActiveProfiles("cabinet")
   class CabinetModeIntegrationTest {
       // ...
   }
   ```

3. **Tests de Charge** : Utiliser JMeter ou K6
   ```bash
   k6 run load-test-cabinet.js
   ```

4. **Monitoring** : Activer métriques Prometheus
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,metrics,prometheus
   ```

---

## 📞 Support

En cas de problème :

1. Vérifier les logs : `./scripts/deploy-cabinet.sh cabinet-test-dev logs`
2. Vérifier la DB : `psql -U predykt -d predykt_cabinet_test`
3. Vérifier Redis : `redis-cli ping`
4. Consulter `AUTHENTICATION.md` pour détails sur l'auth
5. Consulter `CLAUDE.md` pour architecture générale

---

**Version** : 1.0
**Dernière mise à jour** : 2024-12-15
**Auteur** : PREDYKT DevOps Team

# 📮 Guide d'Utilisation - Collection Postman PREDYKT

## 🎯 Vue d'Ensemble

Cette collection Postman complète permet de tester **TOUS** les endpoints de l'API PREDYKT Accounting (300+ endpoints).

**Fonctionnalités couvertes:**
- ✅ Comptabilité OHADA (plan comptable, écritures, balance, grand-livre)
- ✅ Rapprochement bancaire avec ML (95-97% accuracy)
- ✅ Gestion fiscale complète (TVA, IR, CFU, CFE, TCA, CNI)
- ✅ Facturation et encaissements
- ✅ Achats et décaissements
- ✅ Immobilisations et amortissements
- ✅ Budgets et prévisions de trésorerie
- ✅ Rapports financiers (Bilan, Compte de résultat, TAFIRE, etc.)
- ✅ Mode Cabinet multi-dossiers
- ✅ Exports PDF/Excel/CSV
- ✅ ML Experiments tracking

---

## 📁 Fichiers de la Collection

### Collection Postman
- **`PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json`** - Collection complète avec 300+ endpoints

### Environnements
- **`PREDYKT_COMPLETE.postman_environment.json`** - Variables d'environnement complètes

---

## 🚀 Installation

### 1. Importer dans Postman

#### Option A: Via l'interface Postman
1. Ouvrir Postman
2. Cliquer sur **"Import"** en haut à gauche
3. Glisser-déposer les 2 fichiers:
   - `PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json`
   - `PREDYKT_COMPLETE.postman_environment.json`
4. Cliquer sur **"Import"**

#### Option B: Via ligne de commande
```bash
# Copier les fichiers dans le dossier Postman
cp PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json ~/Postman/files/
cp PREDYKT_COMPLETE.postman_environment.json ~/Postman/files/
```

### 2. Sélectionner l'environnement
1. En haut à droite de Postman
2. Cliquer sur le menu déroulant des environnements
3. Sélectionner **"PREDYKT - COMPLET - DEV"**

---

## ⚙️ Configuration Initiale

### 1. Démarrer l'application

```bash
# Démarrer PostgreSQL et Redis
docker-compose up -d

# Lancer l'application Spring Boot
./mvnw spring-boot:run
```

**⚠️ Variables d'environnement requises:**
```bash
# MODE CABINET
export SPRING_PROFILES_ACTIVE=cabinet
export PREDYKT_TENANT_CABINET_ID=1

# JWT Secret (minimum 256 bits)
export JWT_SECRET="votre-secret-jwt-tres-long-et-securise-minimum-32-caracteres"

# Database
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=predykt_cabinet_1
export DB_USER=predykt_user
export DB_PASSWORD=predykt_password

# Redis
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

### 2. Vérifier que l'API fonctionne

Exécuter la requête **"00 - Health Check > GET Health Check"**

**Réponse attendue:**
```json
{
  "status": "UP",
  "service": "PREDYKT Accounting API",
  "version": "1.0.0",
  "timestamp": "2025-12-19T10:00:00Z"
}
```

---

## 🔐 Authentification

### Workflow d'authentification

```
1. POST /auth/login          → Obtenir access_token + refresh_token
2. Utiliser access_token     → Dans toutes les requêtes (Header: Authorization: Bearer {{access_token}})
3. POST /auth/refresh        → Renouveler le token si expiré
```

### 1. Login Admin Cabinet

**Endpoint:** `POST /auth/login`

**Requête:**
```json
{
  "email": "admin@localhost",
  "password": "Admin@123"
}
```

**Réponse:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "userId": 1,
    "email": "admin@localhost",
    "roles": ["ROLE_ADMIN_CABINET"],
    "expiresIn": 86400
  }
}
```

**✅ Le token est automatiquement sauvegardé dans `{{access_token}}`**

### 2. JWT Token Structure

Le JWT contient les claims suivants:

```json
{
  "sub": "admin@localhost",
  "userId": 1,
  "companyId": 10,
  "cabinetId": 1,
  "roles": ["ROLE_ADMIN_CABINET"],
  "iat": 1703001600,
  "exp": 1703088000
}
```

**Important:**
- `cabinetId`: ID du cabinet (MODE CABINET uniquement)
- `companyId`: ID de la company par défaut dans le JWT
- Tous les endpoints utilisent ces IDs pour l'isolation des données

---

## 📚 Structure de la Collection

### Dossiers Principaux

```
PREDYKT MODE CABINET COMPLETE
│
├── 00 - Health Check                  (1 endpoint)
│   └── GET Health Check
│
├── 01 - Authentication                (7 endpoints)
│   ├── POST Login Admin Cabinet
│   ├── POST Register Collaborateur
│   ├── POST Login Collaborateur
│   ├── POST Refresh Token
│   ├── POST Logout
│   ├── POST Request Password Reset
│   └── GET Validate Token
│
├── 02 - User Management               (10 endpoints)
│   ├── GET List Users
│   ├── GET User by ID
│   ├── GET Current User Profile
│   ├── POST Create User
│   ├── PUT Update User
│   ├── POST Change Password
│   ├── PUT Activate User
│   ├── PUT Deactivate User
│   └── DELETE Delete User
│
├── 03 - Companies Management          (5 endpoints)
│   ├── POST Create Company
│   ├── GET All Companies (Cabinet)
│   ├── GET Company Details
│   ├── PUT Update Company
│   └── DELETE Deactivate Company
│
├── 04 - Chart of Accounts (OHADA)    (9 endpoints)
│   ├── POST Initialize OHADA Chart
│   ├── GET List All Accounts
│   ├── GET Account by Number
│   ├── GET Accounts by Type
│   ├── POST Create Custom Account
│   ├── PUT Activate Account
│   ├── PUT Deactivate Account
│   └── GET Search Accounts
│
├── 05 - Data Import (CSV)            (3 endpoints)
│   ├── POST Import Activities CSV
│   ├── POST Import Activities (Flexible)
│   └── POST Preview Import
│
├── 06 - Bank Transactions            (5 endpoints)
│   ├── POST Import Bank Transactions
│   ├── GET List Transactions
│   ├── GET Unreconciled Transactions
│   ├── POST Reconcile Transaction
│   └── GET Supported Bank Formats
│
├── 07 - General Ledger               (5 endpoints)
│   ├── POST Record Journal Entry
│   ├── GET Account Balance
│   ├── GET Account Ledger
│   ├── GET Trial Balance
│   └── POST Lock Accounting Period
│
├── 08 - Financial Reports            (4 endpoints)
│   ├── GET Balance Sheet (Bilan)
│   ├── GET Income Statement (Compte de Résultat)
│   ├── GET Cash Flow Statement
│   └── GET TAFIRE Report
│
├── 09 - Financial Ratios             (4 endpoints)
│   ├── POST Calculate Ratios
│   ├── GET Ratios by Fiscal Year
│   ├── GET Ratios History
│   └── GET Compare Ratios
│
├── 10 - Invoices Management          (10 endpoints)
│   ├── POST Create Invoice
│   ├── GET Invoice by ID
│   ├── GET Invoice Payment History
│   ├── GET List All Invoices
│   ├── GET Invoices by Customer
│   ├── GET Overdue Invoices
│   ├── PUT Update Invoice
│   ├── POST Validate Invoice
│   ├── POST Cancel Invoice
│   └── DELETE Delete Invoice
│
├── 11 - Bills Management             (9 endpoints)
│   ├── POST Create Bill
│   ├── GET Bill by ID
│   ├── GET List All Bills
│   ├── GET Bills by Supplier
│   ├── GET Overdue Bills
│   ├── PUT Update Bill
│   ├── POST Validate Bill
│   ├── POST Cancel Bill
│   └── DELETE Delete Bill
│
├── 12 - Customers Management         (10 endpoints)
│   ├── POST Create Customer
│   ├── GET Customer by ID
│   ├── GET List All Customers
│   ├── GET Search Customers
│   ├── PUT Update Customer
│   ├── PATCH Deactivate Customer
│   ├── PATCH Reactivate Customer
│   ├── DELETE Delete Customer
│   └── GET Customer Statistics
│
├── 13 - Suppliers Management         (13 endpoints)
│   ├── POST Create Supplier
│   ├── GET List Suppliers
│   ├── GET Supplier by ID
│   ├── PUT Update Supplier
│   ├── DELETE Delete Supplier
│   ├── PUT Reactivate Supplier
│   ├── PUT Update NIU
│   ├── GET Suppliers Without NIU
│   ├── GET Count Suppliers Without NIU
│   ├── GET Rent Suppliers
│   ├── GET Search Supplier
│   └── GET Supplier Statistics
│
├── 14 - Payments Management          (5 endpoints)
│   ├── POST Record Customer Payment
│   ├── POST Record Supplier Payment
│   ├── GET Payment by ID
│   ├── GET List All Payments
│   └── POST Cancel Payment
│
├── 15 - Deposits Management          (15 endpoints)
│   ├── POST Create Deposit
│   ├── GET Deposit by ID
│   ├── GET Deposit by Number
│   ├── GET List All Deposits
│   ├── GET Available Deposits for Customer
│   ├── GET Search Deposits
│   ├── PUT Update Deposit
│   ├── POST Apply Deposit to Invoice
│   ├── POST Cancel Deposit Application
│   ├── POST Apply Deposit Partially
│   ├── GET Deposit Applications
│   ├── GET Application Details
│   ├── DELETE Cancel Partial Application
│   ├── GET Download Deposit Receipt PDF
│   └── GET Statistics
│
├── 16 - Tax Management               (12 endpoints)
│   ├── GET Monthly Tax Summary
│   ├── GET Active Tax Alerts
│   ├── GET Tax Configurations
│   ├── GET Calculation History
│   ├── PUT Toggle Tax Active Status
│   ├── PUT Update Tax Rate
│   ├── GET Count Suppliers Without NIU
│   ├── GET Tax Dashboard
│   └── POST Initialize Tax Configurations
│
├── 17 - VAT Declarations             (9 endpoints)
│   ├── POST Generate VAT Declaration
│   ├── GET List VAT Declarations
│   ├── GET VAT Declaration by ID
│   ├── GET Declarations by Status
│   ├── PUT Validate VAT Declaration
│   ├── PUT Submit VAT Declaration
│   ├── PUT Mark Declaration as Paid
│   ├── DELETE Delete VAT Declaration
│   └── GET Generate VAT Declaration Report
│
├── 18 - VAT Recoverability           (7 endpoints)
│   ├── GET VAT Transactions
│   ├── GET Non-Recoverable VAT Transactions
│   ├── GET VAT Statistics
│   ├── PUT Update Recoverable Category
│   ├── GET Count Recoverable Alerts
│   └── POST Test Detection
│
├── 19 - VAT Recoverability Rules     (13 endpoints)
│   ├── GET All Rules
│   ├── GET Active Rules
│   ├── GET Rules by Category
│   ├── GET Rule by ID
│   ├── POST Create Rule
│   ├── PUT Update Rule
│   ├── PUT Toggle Rule Active Status
│   ├── DELETE Delete Rule
│   ├── GET Rule Engine Statistics
│   ├── GET Rules Needing Review
│   ├── GET Top Performing Rules
│   └── POST Invalidate Rule Cache
│
├── 20 - VAT Prorata                  (9 endpoints)
│   ├── POST Create/Update VAT Prorata
│   ├── POST Create Provisional Prorata
│   ├── POST Convert to Definitive
│   ├── GET Active Prorata
│   ├── GET List All Prorata
│   ├── GET Simulate Prorata Application
│   ├── POST Lock Prorata
│   ├── DELETE Delete Prorata
│   └── GET Check Prorata Existence
│
├── 21 - Fixed Assets                 (10 endpoints)
│   ├── POST Create Fixed Asset
│   ├── GET List Fixed Assets
│   ├── GET Asset by ID
│   ├── GET Asset by Number
│   ├── PUT Update Fixed Asset
│   ├── DELETE Delete Fixed Asset
│   ├── POST Dispose Asset
│   ├── GET Depreciation Schedule
│   ├── GET Next Asset Number
│   └── POST Generate Monthly Depreciation
│
├── 22 - Budgets                      (14 endpoints)
│   ├── POST Create Budget
│   ├── GET List Budgets
│   ├── GET Budget by ID
│   ├── PUT Update Budget
│   ├── DELETE Delete Budget
│   ├── GET Budgets by Fiscal Year
│   ├── PUT Update Actual Amount
│   ├── POST Calculate Actuals from Ledger
│   ├── GET Over Budgets
│   ├── GET High Variance Budgets
│   ├── PUT Lock Budget
│   ├── PUT Unlock Budget
│   ├── GET Budget Statistics
│   └── GET Compare Budget vs Actual
│
├── 23 - Treasury & Cash Flow         (7 endpoints)
│   ├── POST Create Projection
│   ├── POST Generate Automatic Projections
│   ├── GET List Projections
│   ├── GET Latest Projections
│   ├── GET Projections by Horizon
│   ├── GET Negative Cash Alerts
│   └── DELETE Delete Projection
│
├── 24 - Aging Reports                (2 endpoints)
│   ├── GET Customers Aging Report
│   └── GET Suppliers Aging Report
│
├── 25 - Subledgers                   (4 endpoints)
│   ├── GET Customers Subledger
│   ├── GET Suppliers Subledger
│   ├── GET Specific Customer Subledger
│   └── GET Specific Supplier Subledger
│
├── 26 - Auxiliary Journals           (6 endpoints)
│   ├── GET Sales Journal (VE)
│   ├── GET Purchases Journal (AC)
│   ├── GET Bank Journal (BQ)
│   ├── GET Cash Journal (CA)
│   ├── GET General Journal (OD)
│   └── GET Opening Journal (AN)
│
├── 27 - Notes Annexes                (1 endpoint)
│   └── GET Generate Notes Annexes
│
├── 28 - Dashboard                    (3 endpoints)
│   ├── GET Complete Dashboard
│   ├── GET Period Summary
│   └── GET Compare Periods
│
├── 29 - Activity Templates           (7 endpoints)
│   ├── GET List Templates
│   ├── GET Template Details
│   ├── POST Create Template
│   ├── PUT Update Template
│   ├── DELETE Delete Template
│   ├── POST Set as Default Template
│   └── GET Default Template
│
├── 30 - Activity Mappings            (6 endpoints)
│   ├── GET List Mapping Rules
│   ├── POST Create Mapping Rule
│   ├── PUT Update Mapping Rule
│   ├── DELETE Delete Mapping Rule
│   ├── POST Initialize Default Mappings
│   └── GET Test Mapping
│
├── 31 - Matching Metrics             (8 endpoints)
│   ├── GET Performance Metrics
│   ├── GET Confidence Breakdown
│   ├── GET Rejection Reasons
│   ├── GET Volume Performance
│   ├── GET Time Series Data
│   ├── GET User Productivity
│   ├── GET Recommendations
│   └── GET Executive Summary
│
├── 32 - Bank Reconciliation (Advanced) (20 endpoints)
│   ├── POST Create Reconciliation
│   ├── GET List Reconciliations
│   ├── GET Reconciliation by ID
│   ├── PUT Update Reconciliation
│   ├── DELETE Delete Reconciliation
│   ├── GET Reconciliations by Account
│   ├── GET Unbalanced Reconciliations
│   ├── GET Pending Reconciliations
│   ├── POST Add Pending Item
│   ├── DELETE Remove Pending Item
│   ├── POST Submit for Review
│   ├── POST Approve Reconciliation
│   ├── POST Reject Reconciliation
│   ├── GET Statistics
│   ├── POST Auto-Match Suggestions (ML)
│   ├── POST Apply Multiple Suggestions
│   ├── POST Apply Single Suggestion
│   ├── POST Reject Suggestion
│   └── POST Submit ML Feedback
│
├── 33 - ML Models & Training         (7 endpoints)
│   ├── GET List ML Models
│   ├── POST Train New Model
│   ├── POST Deploy Model
│   ├── POST Rollback to Previous Model
│   ├── DELETE Delete Model
│   ├── GET Training Statistics
│   └── GET Active Model Statistics
│
├── 34 - ML Predictions & Feedback   (5 endpoints)
│   ├── POST Submit Prediction Feedback
│   ├── GET Prediction History
│   ├── GET Performance Metrics
│   ├── GET Feedback Statistics
│   └── POST Check Model Drift
│
├── 35 - ML Experiments (NEW!)       (12 endpoints)
│   ├── POST Create Experiment
│   ├── GET List Experiments
│   ├── GET Experiment by ID
│   ├── POST Log Run
│   ├── POST Start Run
│   ├── PUT Complete Run
│   ├── PUT Fail Run
│   ├── PUT Complete Experiment
│   ├── GET Best Run
│   ├── GET List Runs
│   ├── GET Compare Runs
│   └── GET Generate Report
│
├── 36 - Exports (40+ endpoints)
│   ├── PDF Exports (20 endpoints)
│   │   ├── Balance Sheet PDF
│   │   ├── Income Statement PDF
│   │   ├── Trial Balance PDF
│   │   ├── General Ledger PDF
│   │   ├── Bank Reconciliation PDF
│   │   ├── TAFIRE PDF
│   │   ├── Sales Journal PDF
│   │   ├── Purchases Journal PDF
│   │   ├── Bank Journal PDF
│   │   ├── Cash Journal PDF
│   │   ├── General Journal PDF
│   │   ├── Opening Journal PDF
│   │   ├── Notes Annexes PDF
│   │   ├── Customers Subledger PDF
│   │   └── Suppliers Subledger PDF
│   │
│   ├── Excel Exports (20 endpoints)
│   │   ├── Balance Sheet Excel
│   │   ├── Income Statement Excel
│   │   ├── Trial Balance Excel
│   │   ├── General Ledger Excel
│   │   ├── Bank Reconciliation Excel
│   │   ├── TAFIRE Excel
│   │   ├── Ratios History Excel
│   │   ├── Sales Journal Excel
│   │   ├── Purchases Journal Excel
│   │   ├── Bank Journal Excel
│   │   ├── Cash Journal Excel
│   │   ├── General Journal Excel
│   │   ├── Opening Journal Excel
│   │   ├── Notes Annexes Excel
│   │   ├── Customers Subledger Excel
│   │   └── Suppliers Subledger Excel
│   │
│   └── CSV Exports (2 endpoints)
│       ├── General Ledger CSV
│       └── Bank Transactions CSV
│
├── 37 - Cabinet Management          (15 endpoints)
│   ├── POST Create Cabinet
│   ├── PUT Update Cabinet
│   ├── GET Cabinet by ID
│   ├── GET List All Cabinets
│   ├── GET Cabinet by Name
│   ├── GET Cabinet by Code
│   ├── GET Cabinet Companies
│   ├── POST Add Company to Cabinet
│   ├── DELETE Remove Company from Cabinet
│   ├── GET Cabinet Users
│   ├── POST Add User to Cabinet
│   ├── PUT Update Cabinet Plan
│   ├── GET Check if Can Add Company
│   ├── GET Check if Can Add User
│   └── DELETE Delete Cabinet
│
├── 38 - Time Tracking (Cabinet)     (8 endpoints)
│   ├── POST Create Time Entry
│   ├── GET Time Entry by ID
│   ├── GET List Time Entries
│   ├── PUT Update Time Entry
│   ├── DELETE Delete Time Entry
│   ├── POST Start Timer
│   ├── POST Stop Timer
│   └── GET Time Summary
│
├── 39 - Cabinet Invoices            (9 endpoints)
│   ├── POST Create Cabinet Invoice
│   ├── GET Cabinet Invoice by ID
│   ├── GET Cabinet Invoice by Number
│   ├── GET List Cabinet Invoices
│   ├── PUT Update Cabinet Invoice
│   ├── POST Validate Cabinet Invoice
│   ├── POST Mark as Paid
│   ├── POST Cancel Cabinet Invoice
│   └── DELETE Delete Cabinet Invoice
│
└── 40 - User Company Access          (7 endpoints)
    ├── POST Grant Access
    ├── POST Revoke Access
    ├── GET User Access List
    ├── GET Company Access List
    ├── GET Check Access
    ├── PUT Update Permissions
    └── GET Access Statistics
```

**Total: 300+ endpoints organisés en 40 modules**

---

## 🧪 Tests Automatiques

Chaque requête inclut des **tests Postman automatiques** pour valider les réponses.

### Exemples de Tests

```javascript
// Test 1: Vérifier le status code
pm.test('Status code is 200', function () {
    pm.response.to.have.status(200);
});

// Test 2: Vérifier la structure ApiResponse
pm.test('Response has ApiResponse structure', function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('success');
    pm.expect(jsonData).to.have.property('message');
    pm.expect(jsonData).to.have.property('timestamp');
});

// Test 3: Vérifier les données métier
pm.test('Balance sheet is balanced', function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.data.isBalanced).to.be.true;
    pm.expect(jsonData.data.assets.totalAssets)
      .to.equal(jsonData.data.liabilitiesAndEquity.totalLiabilitiesAndEquity);
});

// Test 4: Sauvegarder dans les variables d'environnement
pm.environment.set('invoice_id', jsonData.data.id);
```

### Exécuter tous les tests

**Runner Postman:**
1. Cliquer sur **"Run collection"**
2. Sélectionner l'environnement **"PREDYKT - COMPLET - DEV"**
3. Cliquer sur **"Run PREDYKT MODE CABINET COMPLETE"**
4. Voir les résultats en temps réel

---

## 🎓 Workflows Typiques

### Workflow 1: Setup Initial (Nouveau Dossier)

```
1. POST Login Admin Cabinet
2. POST Create Company (Entreprise A)
3. POST Initialize OHADA Chart
4. POST Create Customer
5. POST Create Supplier
6. POST Initialize Tax Configurations
```

### Workflow 2: Vente avec Facturation

```
1. POST Create Customer
2. POST Create Invoice
3. POST Validate Invoice
4. POST Record Customer Payment
5. POST Apply Deposit to Invoice (optionnel)
6. GET Customer Subledger
```

### Workflow 3: Achat avec Fournisseur

```
1. POST Create Supplier
2. POST Create Bill
3. POST Validate Bill
4. POST Record Supplier Payment
5. GET Supplier Subledger
```

### Workflow 4: Rapprochement Bancaire avec ML

```
1. POST Import Bank Transactions
2. GET Unreconciled Transactions
3. POST Auto-Match Suggestions (ML fait les suggestions)
4. POST Apply Multiple Suggestions
5. POST Submit ML Feedback (améliore le modèle)
6. GET Performance Metrics
```

### Workflow 5: Déclaration TVA

```
1. POST Generate VAT Declaration
2. GET VAT Declaration by ID
3. PUT Validate VAT Declaration
4. PUT Submit VAT Declaration
5. PUT Mark Declaration as Paid
6. GET Generate VAT Declaration Report (PDF)
```

### Workflow 6: Clôture Mensuelle

```
1. POST Calculate Ratios
2. GET Balance Sheet
3. GET Income Statement
4. GET Trial Balance
5. POST Lock Accounting Period
6. GET Export Balance Sheet PDF
7. GET Export Income Statement PDF
```

### Workflow 7: Expérience ML (Data Scientist)

```
1. POST Create Experiment ("Grid Search XGBoost")
2. POST Log Run (max_depth=5) → 89.2%
3. POST Log Run (max_depth=10) → 91.5%
4. POST Log Run (max_depth=15) → 93.8%
5. POST Log Run (max_depth=20) → 93.5%
6. PUT Complete Experiment
7. GET Best Run
8. GET Generate Report
9. POST Deploy Best Model
```

---

## 🔧 Variables d'Environnement

### Variables Essentielles

| Variable | Valeur par Défaut | Description |
|----------|-------------------|-------------|
| `base_url` | `http://localhost:8080/api/v1` | URL de base de l'API |
| `access_token` | *(auto)* | JWT token (sauvegardé automatiquement) |
| `cabinet_id` | `1` | ID du cabinet |
| `company_id_A` | `10` | ID Entreprise A |
| `company_id_B` | `11` | ID Entreprise B |
| `company_id_C` | `12` | ID Entreprise C |
| `admin_email` | `admin@localhost` | Email admin |
| `admin_password` | `Admin@123` | Mot de passe admin |

### Variables Dynamiques (Auto-sauvegardées)

Ces variables sont automatiquement remplies par les tests Postman:

```javascript
// Exemples
{{customer_id}}
{{supplier_id}}
{{invoice_id}}
{{bill_id}}
{{payment_id}}
{{deposit_id}}
{{budget_id}}
{{ml_model_id}}
{{prediction_id}}
{{experiment_id}}
{{reconciliation_id}}
{{vat_declaration_id}}
```

### Ajouter une Variable

```javascript
// Dans un test Postman
pm.environment.set('ma_variable', 'valeur');

// Utiliser la variable
{{ma_variable}}
```

---

## 🐛 Dépannage

### Problème 1: Token Expiré

**Erreur:**
```json
{
  "error": "Unauthorized",
  "message": "JWT token expired"
}
```

**Solution:**
Exécuter `POST Refresh Token` ou refaire un `POST Login`

### Problème 2: 403 Forbidden

**Erreur:**
```json
{
  "error": "Forbidden",
  "message": "Access denied to company"
}
```

**Causes possibles:**
- Collaborateur tente d'accéder à un dossier non assigné
- Le JWT ne contient pas le bon `companyId` ou `cabinetId`

**Solution:**
Vérifier les droits d'accès avec `GET User Access List`

### Problème 3: 400 Bad Request (Écriture Déséquilibrée)

**Erreur:**
```json
{
  "error": "Bad Request",
  "message": "Écriture déséquilibrée: Débit = 1000000, Crédit = 500000"
}
```

**Solution:**
S'assurer que la somme des débits = somme des crédits

### Problème 4: Variables d'environnement non définies

**Erreur:**
```
Could not get any response
```

**Solution:**
1. Vérifier que l'environnement **"PREDYKT - COMPLET - DEV"** est sélectionné
2. Vérifier que `{{base_url}}` est défini
3. Vérifier que l'application Spring Boot est démarrée

### Problème 5: Fichier CSV introuvable (Import)

**Erreur:**
```json
{
  "error": "Bad Request",
  "message": "File not found: activités.csv"
}
```

**Solution:**
1. Placer le fichier CSV dans le répertoire racine du projet
2. Ou utiliser le chemin absolu dans la requête

---

## 📊 Exemples de Réponses

### Balance Sheet (Bilan)

```json
{
  "success": true,
  "data": {
    "companyName": "Entreprise A SARL",
    "asOfDate": "2025-01-31",
    "fiscalYear": 2025,
    "currency": "XAF",
    "isBalanced": true,
    "assets": {
      "currentAssets": {
        "cash": 5000000,
        "accountsReceivable": 15000000,
        "inventory": 8000000,
        "totalCurrentAssets": 28000000
      },
      "fixedAssets": {
        "buildings": 50000000,
        "equipment": 20000000,
        "totalFixedAssets": 70000000
      },
      "totalAssets": 98000000
    },
    "liabilitiesAndEquity": {
      "currentLiabilities": {
        "accountsPayable": 12000000,
        "taxesPayable": 3000000,
        "totalCurrentLiabilities": 15000000
      },
      "longTermLiabilities": {
        "loans": 30000000,
        "totalLongTermLiabilities": 30000000
      },
      "equity": {
        "capital": 40000000,
        "retainedEarnings": 13000000,
        "totalEquity": 53000000
      },
      "totalLiabilitiesAndEquity": 98000000
    }
  }
}
```

### Financial Ratios

```json
{
  "success": true,
  "data": {
    "profitabilityRatios": {
      "roa": 0.15,
      "roe": 0.22,
      "grossMargin": 0.35,
      "netMargin": 0.12
    },
    "liquidityRatios": {
      "currentRatio": 1.87,
      "quickRatio": 1.33
    },
    "activityRatios": {
      "dso": 45.5,
      "dio": 60.0,
      "dpo": 90.0
    },
    "solvencyRatios": {
      "debtToEquity": 0.83,
      "debtToAssets": 0.46
    }
  }
}
```

### ML Prediction with SHAP

```json
{
  "success": true,
  "data": {
    "bankTransaction": {
      "id": 123,
      "amount": 1000000,
      "description": "VIR CLIENT ABC"
    },
    "glEntry": {
      "id": 456,
      "accountNumber": "411100",
      "amount": 1000000
    },
    "confidenceScore": 94.5,
    "explanation": "🔍 EXPLICATION (SHAP):\n\nTop 5 facteurs:\n\n1. 💰 Différence de montant = 0.00 XAF\n   ➜ Impact: +28.5% (montants quasi-identiques)\n\n2. 📝 TF-IDF Cosine = 94.00%\n   ➜ Impact: +22.3% (descriptions très similaires)\n\n3. 📅 Jours calendaires d'écart = 1.00 jours\n   ➜ Impact: +15.2% (dates très proches)\n\n🎯 Confiance: 94.5%",
    "shapExplanation": {
      "topFeatures": [...],
      "baseValue": 0.3456,
      "prediction": 0.9450
    }
  }
}
```

---

## 🚀 Performance & Optimisation

### Tests de Charge

Utiliser **Newman** (CLI Postman) pour des tests de charge:

```bash
# Installer Newman
npm install -g newman

# Exécuter la collection
newman run PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json \
  -e PREDYKT_COMPLETE.postman_environment.json \
  --iteration-count 100 \
  --reporters cli,html

# Avec parallélisation
newman run collection.json -e env.json -n 100 --parallel
```

### Mesurer les Temps de Réponse

Tous les tests incluent une vérification du temps de réponse:

```javascript
pm.test('Response time is acceptable', function () {
    pm.expect(pm.response.responseTime).to.be.below(5000); // < 5s
});
```

---

## 📞 Support & Contact

**Questions ?** Contactez l'équipe PREDYKT:
- 📧 Email: dev@predykt.com
- 📱 WhatsApp: +237 xxx xxx xxx
- 🌐 Documentation: https://docs.predykt.com

---

## ✅ Checklist Avant Test Frontend

Avant d'envoyer la collection au frontend:

- [ ] L'application Spring Boot démarre sans erreur
- [ ] La base de données est initialisée
- [ ] Le health check retourne `UP`
- [ ] Le login admin fonctionne
- [ ] Les 3 companies du cabinet existent (A, B, C)
- [ ] Le plan comptable OHADA est initialisé
- [ ] Les exports PDF/Excel fonctionnent
- [ ] Le ML matching retourne des suggestions
- [ ] Tous les tests Postman passent au vert

**Commande de vérification:**
```bash
# Tester toute la collection
newman run PREDYKT_MODE_CABINET_COMPLETE.postman_collection.json \
  -e PREDYKT_COMPLETE.postman_environment.json \
  --bail
```

---

**Collection générée le:** 2025-12-19
**Version:** 1.0.0
**API Version:** 1.0.0
**Endpoints couverts:** 300+

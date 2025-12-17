# 📮 Guide de Test Postman - MODE CABINET

Guide complet pour tester l'API PREDYKT en MODE CABINET avec Postman.

## 📋 Table des Matières

1. [Configuration Initiale](#configuration-initiale)
2. [Variables d'Environnement](#variables-denvironnement)
3. [Collection Postman](#collection-postman)
4. [Scénarios de Test](#scénarios-de-test)
5. [Tests Automatiques](#tests-automatiques)
6. [Troubleshooting](#troubleshooting)

---

## 🔧 Configuration Initiale

### Prérequis

1. **Postman Desktop** installé (v10+)
2. **Application PREDYKT** démarrée en MODE CABINET
3. **Données de test** créées (via `provision-new-cabinet.sh`)

### Vérification du Démarrage

Avant de commencer, vérifiez que l'application est bien démarrée :

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Réponse attendue : 200 OK
```

---

## 🌍 Variables d'Environnement

### Créer un Environnement Postman

1. **Ouvrir Postman** → Cliquer sur **Environments** (à gauche)
2. **Cliquer sur "+"** pour créer un nouvel environnement
3. **Nommer** : `PREDYKT - MODE CABINET - DEV`

### Variables à Configurer

| Variable | Initial Value | Current Value | Description |
|----------|--------------|---------------|-------------|
| `base_url` | `http://localhost:8080/api/v1` | (auto) | URL de base de l'API |
| `access_token` | (vide) | (auto) | JWT access token (rempli automatiquement après login) |
| `refresh_token` | (vide) | (auto) | JWT refresh token |
| `cabinet_id` | `1` | `1` | ID du cabinet de test |
| `cabinet_domain` | `localhost` | `localhost` | Domaine du cabinet |
| `admin_email` | `admin@localhost` | `admin@localhost` | Email admin cabinet |
| `admin_password` | `Admin@123` | `Admin@123` | Mot de passe admin |
| `company_id_A` | `10` | `10` | ID Entreprise A SARL |
| `company_id_B` | `11` | `11` | ID Entreprise B SA |
| `company_id_C` | `12` | `12` | ID Entreprise C SAS |
| `user_id` | (vide) | (auto) | ID utilisateur courant |
| `test_user_email` | `collaborateur@localhost` | (peut changer) | Email pour tests |

### Comment Ajouter les Variables

```
1. Cliquer sur l'environnement créé
2. Pour chaque variable :
   - Remplir "VARIABLE"
   - Remplir "INITIAL VALUE"
   - Laisser "CURRENT VALUE" vide (sera rempli automatiquement)
3. Cliquer "Save"
```

---

## 📚 Collection Postman

### Structure de la Collection

```
PREDYKT MODE CABINET
│
├── 📁 00 - Health Check
│   └── GET Health Check
│
├── 📁 01 - Authentication
│   ├── POST Login Admin Cabinet
│   ├── POST Register Collaborateur
│   ├── POST Register Client
│   ├── POST Refresh Token
│   └── POST Logout
│
├── 📁 02 - Cabinets Management
│   ├── GET Cabinet Details
│   ├── GET Cabinet Stats
│   ├── PUT Update Cabinet
│   └── GET Cabinet Companies
│
├── 📁 03 - Companies Management (Multi-Dossiers)
│   ├── GET All Companies (Cabinet)
│   ├── POST Create Company (Dossier)
│   ├── GET Company Details
│   ├── PUT Update Company
│   └── DELETE Company (Archive)
│
├── 📁 04 - Users Management
│   ├── GET All Users (Cabinet)
│   ├── POST Create User (Collaborateur)
│   ├── GET User Details
│   ├── PUT Update User Roles
│   └── DELETE User
│
├── 📁 05 - User-Company Access (Affectation Dossiers)
│   ├── GET User Access Rights
│   ├── POST Grant Access (Affecter dossier)
│   ├── PUT Update Access Level
│   └── DELETE Revoke Access
│
├── 📁 06 - Chart of Accounts (OHADA)
│   ├── GET Chart of Accounts
│   ├── POST Create Account
│   └── GET Account Details
│
├── 📁 07 - General Ledger (Comptabilité)
│   ├── GET All Entries (Filtered by Company)
│   ├── POST Create Entry
│   ├── GET Entry Details
│   └── POST Validate Entry
│
├── 📁 08 - Financial Reports
│   ├── GET Balance Sheet (Bilan)
│   ├── GET Income Statement (Compte de Résultat)
│   ├── GET Trial Balance
│   └── GET General Ledger Report
│
├── 📁 09 - Financial Ratios
│   ├── POST Calculate Ratios
│   ├── GET Ratios History
│   └── GET Ratio Details
│
├── 📁 10 - CSV Import
│   ├── POST Import Activities CSV
│   ├── POST Import Bank Transactions
│   └── GET Import History
│
└── 📁 11 - Audit & Logs
    ├── GET Audit Logs
    └── GET User Activity
```

---

## 🧪 Scénarios de Test Détaillés

### 📌 Scénario 1 : Authentification Admin Cabinet

#### 🎯 Objectif
Vérifier que l'admin du cabinet peut se connecter et obtenir un JWT valide contenant `cabinetId`.

#### 📝 Étapes

**1. Health Check (Sans Authentication)**

```
Method: GET
URL: {{base_url}}/health
Headers: (aucun)

Expected Response: 200 OK
{
  "status": "UP"
}
```

**2. Login Admin Cabinet**

```
Method: POST
URL: {{base_url}}/auth/login
Headers:
  Content-Type: application/json

Body (raw JSON):
{
  "email": "{{admin_email}}",
  "password": "{{admin_password}}"
}

Expected Response: 200 OK
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "userId": 100,
  "email": "admin@localhost",
  "fullName": "Admin Cabinet",
  "roles": ["ROLE_ADMIN_CABINET"]
}
```

**3. Script Postman (Tests Tab)**

Ajouter ce script dans l'onglet **Tests** de la requête Login :

```javascript
// Vérifier le statut 200
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

// Parser la réponse
var jsonData = pm.response.json();

// Vérifier que le token existe
pm.test("Access token is present", function () {
    pm.expect(jsonData.accessToken).to.be.a('string');
    pm.expect(jsonData.accessToken).to.have.lengthOf.above(50);
});

// Sauvegarder les tokens dans les variables d'environnement
pm.environment.set("access_token", jsonData.accessToken);
pm.environment.set("refresh_token", jsonData.refreshToken);
pm.environment.set("user_id", jsonData.userId);

console.log("✅ Access token saved:", jsonData.accessToken.substring(0, 20) + "...");

// Vérifier le rôle
pm.test("User has ROLE_ADMIN_CABINET", function () {
    pm.expect(jsonData.roles).to.include("ROLE_ADMIN_CABINET");
});

// Décoder le JWT pour vérifier cabinetId (base64)
var token = jsonData.accessToken;
var base64Url = token.split('.')[1];
var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
var jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
}).join(''));

var payload = JSON.parse(jsonPayload);
console.log("📄 JWT Payload:", payload);

// Vérifier que cabinetId est dans le JWT
pm.test("JWT contains cabinetId", function () {
    pm.expect(payload.cabinetId).to.exist;
    pm.expect(payload.cabinetId).to.equal(1);
});

pm.test("JWT contains companyId", function () {
    pm.expect(payload.companyId).to.exist;
    pm.expect(payload.companyId).to.equal(10);
});
```

**4. Vérification Visuelle**

Après l'exécution :
- ✅ Onglet **Tests** : Tous les tests doivent être verts
- ✅ Console Postman : Affiche le JWT payload avec `cabinetId: 1`
- ✅ Environnement : Variable `access_token` remplie automatiquement

---

### 📌 Scénario 2 : Lister les Dossiers (Companies) du Cabinet

#### 🎯 Objectif
Vérifier que l'admin peut voir tous les dossiers clients du cabinet.

#### 📝 Étapes

**1. Lister les Companies**

```
Method: GET
URL: {{base_url}}/companies
Headers:
  Authorization: Bearer {{access_token}}

Expected Response: 200 OK
{
  "success": true,
  "data": [
    {
      "id": 10,
      "name": "Entreprise A SARL",
      "email": "contact@entreprise-a.cm",
      "country": "CM",
      "currency": "XAF",
      "accountingStandard": "OHADA",
      "cabinetId": 1
    },
    {
      "id": 11,
      "name": "Entreprise B SA",
      "email": "contact@entreprise-b.cm",
      "country": "CM",
      "currency": "XAF",
      "accountingStandard": "OHADA",
      "cabinetId": 1
    },
    {
      "id": 12,
      "name": "Entreprise C SAS",
      "email": "contact@entreprise-c.cm",
      "country": "CM",
      "currency": "XAF",
      "accountingStandard": "OHADA",
      "cabinetId": 1
    }
  ],
  "message": "Companies retrieved successfully"
}
```

**2. Script Postman (Tests Tab)**

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

var jsonData = pm.response.json();

pm.test("Response has data array", function () {
    pm.expect(jsonData.data).to.be.an('array');
});

pm.test("Cabinet has exactly 3 companies", function () {
    pm.expect(jsonData.data).to.have.lengthOf(3);
});

// Vérifier que toutes les companies appartiennent au cabinet 1
pm.test("All companies belong to cabinet 1", function () {
    jsonData.data.forEach(function(company) {
        pm.expect(company.cabinetId).to.equal(1);
    });
});

// Vérifier les noms
pm.test("Company names are correct", function () {
    var names = jsonData.data.map(c => c.name);
    pm.expect(names).to.include("Entreprise A SARL");
    pm.expect(names).to.include("Entreprise B SA");
    pm.expect(names).to.include("Entreprise C SAS");
});
```

**3. Test Manuel (Sans JWT) - Doit Échouer**

```
Method: GET
URL: {{base_url}}/companies
Headers: (aucun - retirer Authorization)

Expected Response: 401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Token JWT manquant ou invalide"
}
```

---

### 📌 Scénario 3 : Créer un Collaborateur Affecté à un Dossier

#### 🎯 Objectif
Créer un utilisateur ROLE_COLLABORATOR affecté au dossier "Entreprise B SA".

#### 📝 Étapes

**1. Register Collaborateur**

```
Method: POST
URL: {{base_url}}/auth/register
Headers:
  Authorization: Bearer {{access_token}}
  Content-Type: application/json

Body (raw JSON):
{
  "email": "{{test_user_email}}",
  "password": "Collab@123",
  "firstName": "Jean",
  "lastName": "Dupont",
  "phone": "+237 690 123 456",
  "companyId": {{company_id_B}},
  "cabinetId": {{cabinet_id}}
}

Expected Response: 201 Created
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "userId": 101,
  "email": "collaborateur@localhost",
  "fullName": "Jean Dupont",
  "roles": ["ROLE_COLLABORATOR"]
}
```

**2. Script Postman (Tests Tab)**

```javascript
pm.test("Status code is 201", function () {
    pm.response.to.have.status(201);
});

var jsonData = pm.response.json();

pm.test("User has ROLE_COLLABORATOR", function () {
    pm.expect(jsonData.roles).to.include("ROLE_COLLABORATOR");
});

// Sauvegarder l'ID du collaborateur
pm.environment.set("collaborator_id", jsonData.userId);
pm.environment.set("collaborator_token", jsonData.accessToken);

// Décoder le JWT
var token = jsonData.accessToken;
var base64Url = token.split('.')[1];
var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
var jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
    return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
}).join(''));

var payload = JSON.parse(jsonPayload);
console.log("📄 Collaborateur JWT:", payload);

pm.test("JWT contains cabinetId", function () {
    pm.expect(payload.cabinetId).to.equal(1);
});

pm.test("JWT contains companyId for Entreprise B", function () {
    pm.expect(payload.companyId).to.equal(11);
});
```

---

### 📌 Scénario 4 : Tester l'Isolation des Dossiers (Sécurité)

#### 🎯 Objectif
Vérifier qu'un collaborateur ne peut accéder qu'aux dossiers qui lui sont affectés.

#### 📝 Étapes

**1. Login Collaborateur**

```
Method: POST
URL: {{base_url}}/auth/login
Headers:
  Content-Type: application/json

Body:
{
  "email": "collaborateur@localhost",
  "password": "Collab@123"
}

➡️ Sauvegarder le token dans {{collaborator_token}}
```

**2. Tester l'Accès au Dossier Affecté (Entreprise B)**

```
Method: GET
URL: {{base_url}}/companies/{{company_id_B}}
Headers:
  Authorization: Bearer {{collaborator_token}}

Expected Response: 200 OK
{
  "success": true,
  "data": {
    "id": 11,
    "name": "Entreprise B SA",
    ...
  }
}
```

**3. Tester l'Accès à un Dossier NON Affecté (Entreprise A) - Doit Échouer**

```
Method: GET
URL: {{base_url}}/companies/{{company_id_A}}
Headers:
  Authorization: Bearer {{collaborator_token}}

Expected Response: 403 Forbidden
{
  "error": "Forbidden",
  "message": "Vous n'avez pas accès à ce dossier"
}
```

**4. Script Postman (Tests Tab pour la requête #3)**

```javascript
pm.test("Status code is 403 Forbidden", function () {
    pm.response.to.have.status(403);
});

pm.test("Access denied to non-assigned company", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.error).to.equal("Forbidden");
});
```

---

### 📌 Scénario 5 : Créer une Écriture Comptable (General Ledger)

#### 🎯 Objectif
Créer une écriture comptable équilibrée pour un dossier client.

#### 📝 Étapes

**1. Sélectionner le Dossier (Company)**

Utiliser le token admin qui a accès à tous les dossiers.

**2. Créer une Écriture Comptable**

```
Method: POST
URL: {{base_url}}/general-ledger
Headers:
  Authorization: Bearer {{access_token}}
  Content-Type: application/json

Body (raw JSON):
{
  "companyId": {{company_id_A}},
  "entryDate": "2025-01-15",
  "description": "Vente de marchandises - Facture #2025-001",
  "journalCode": "VE",
  "reference": "FAC-2025-001",
  "entries": [
    {
      "accountNumber": "411100",
      "accountName": "Clients",
      "debit": 1180000,
      "credit": 0,
      "description": "Client ABC"
    },
    {
      "accountNumber": "701100",
      "accountName": "Ventes de marchandises",
      "debit": 0,
      "credit": 1000000,
      "description": "Vente marchandises"
    },
    {
      "accountNumber": "443100",
      "accountName": "TVA collectée",
      "debit": 0,
      "credit": 180000,
      "description": "TVA 18%"
    }
  ]
}

Expected Response: 201 Created
{
  "success": true,
  "data": {
    "id": 1,
    "companyId": 10,
    "entryDate": "2025-01-15",
    "description": "Vente de marchandises - Facture #2025-001",
    "totalDebit": 1180000,
    "totalCredit": 1180000,
    "isBalanced": true,
    "isValidated": false,
    "entries": [...]
  },
  "message": "Journal entry created successfully"
}
```

**3. Script Postman (Tests Tab)**

```javascript
pm.test("Status code is 201", function () {
    pm.response.to.have.status(201);
});

var jsonData = pm.response.json();

pm.test("Entry is balanced", function () {
    pm.expect(jsonData.data.isBalanced).to.be.true;
    pm.expect(jsonData.data.totalDebit).to.equal(jsonData.data.totalCredit);
});

pm.test("Entry has 3 lines", function () {
    pm.expect(jsonData.data.entries).to.have.lengthOf(3);
});

// Sauvegarder l'ID de l'écriture
pm.environment.set("entry_id", jsonData.data.id);
```

**4. Tester une Écriture Déséquilibrée - Doit Échouer**

```
Method: POST
URL: {{base_url}}/general-ledger
Body:
{
  "companyId": {{company_id_A}},
  "entryDate": "2025-01-15",
  "description": "Écriture déséquilibrée",
  "journalCode": "OD",
  "entries": [
    {
      "accountNumber": "411100",
      "debit": 1000000,
      "credit": 0
    },
    {
      "accountNumber": "701100",
      "debit": 0,
      "credit": 500000  // ❌ Déséquilibre
    }
  ]
}

Expected Response: 400 Bad Request
{
  "error": "Bad Request",
  "message": "Écriture déséquilibrée : débit (1000000) ≠ crédit (500000)"
}
```

---

### 📌 Scénario 6 : Importer un Fichier CSV

#### 🎯 Objectif
Importer des activités depuis un fichier CSV.

#### 📝 Étapes

**1. Préparer le Fichier CSV**

Créer un fichier `test-activities.csv` :

```csv
date de saisie;Activitées;description;Montant Brut;Type;Années
15/01/2025;Wholesale Sales;Vente client XYZ;2500000;Revenu;2025
16/01/2025;Administrative Salaries;Salaire janvier;800000;Dépenses;2025
17/01/2025;Rent;Loyer bureau;350000;Dépenses;2025
```

**2. Importer le CSV**

```
Method: POST
URL: {{base_url}}/companies/{{company_id_A}}/import/activities-csv
Headers:
  Authorization: Bearer {{access_token}}
  Content-Type: multipart/form-data

Body (form-data):
  Key: file
  Type: File
  Value: [Sélectionner test-activities.csv]

Expected Response: 200 OK
{
  "success": true,
  "data": {
    "totalRows": 3,
    "successCount": 3,
    "errorCount": 0,
    "errors": []
  },
  "message": "Import completed successfully"
}
```

**3. Script Postman (Tests Tab)**

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

var jsonData = pm.response.json();

pm.test("All rows imported successfully", function () {
    pm.expect(jsonData.data.successCount).to.equal(3);
    pm.expect(jsonData.data.errorCount).to.equal(0);
});
```

---

### 📌 Scénario 7 : Générer un Bilan Comptable

#### 🎯 Objectif
Générer le bilan (balance sheet) d'un dossier client.

#### 📝 Étapes

**1. Calculer le Bilan**

```
Method: GET
URL: {{base_url}}/companies/{{company_id_A}}/reports/balance-sheet
Query Params:
  asOfDate: 2025-01-31
Headers:
  Authorization: Bearer {{access_token}}

Expected Response: 200 OK
{
  "success": true,
  "data": {
    "companyId": 10,
    "companyName": "Entreprise A SARL",
    "asOfDate": "2025-01-31",
    "assets": {
      "currentAssets": {
        "cash": 5000000,
        "accountsReceivable": 3500000,
        "inventory": 2000000,
        "total": 10500000
      },
      "fixedAssets": {
        "property": 15000000,
        "equipment": 8000000,
        "total": 23000000
      },
      "totalAssets": 33500000
    },
    "liabilitiesAndEquity": {
      "currentLiabilities": {
        "accountsPayable": 2500000,
        "shortTermDebt": 1000000,
        "total": 3500000
      },
      "longTermLiabilities": {
        "longTermDebt": 10000000,
        "total": 10000000
      },
      "equity": {
        "capital": 15000000,
        "retainedEarnings": 5000000,
        "total": 20000000
      },
      "totalLiabilitiesAndEquity": 33500000
    },
    "isBalanced": true
  }
}
```

**2. Script Postman (Tests Tab)**

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

var jsonData = pm.response.json();

pm.test("Balance sheet is balanced", function () {
    pm.expect(jsonData.data.isBalanced).to.be.true;
    pm.expect(jsonData.data.assets.totalAssets)
      .to.equal(jsonData.data.liabilitiesAndEquity.totalLiabilitiesAndEquity);
});
```

---

### 📌 Scénario 8 : Calculer les Ratios Financiers

#### 🎯 Objectif
Calculer les 20+ ratios financiers (ROA, ROE, liquidité, etc.).

#### 📝 Étapes

**1. Calculer les Ratios**

```
Method: POST
URL: {{base_url}}/companies/{{company_id_A}}/ratios/calculate
Query Params:
  startDate: 2025-01-01
  endDate: 2025-01-31
Headers:
  Authorization: Bearer {{access_token}}

Expected Response: 200 OK
{
  "success": true,
  "data": {
    "companyId": 10,
    "fiscalPeriod": "2025-01",
    "profitabilityRatios": {
      "roa": 8.5,
      "roe": 15.2,
      "grossMargin": 35.0,
      "netMargin": 12.5,
      "operatingMargin": 18.3
    },
    "liquidityRatios": {
      "currentRatio": 3.0,
      "quickRatio": 2.4,
      "cashRatio": 1.43
    },
    "activityRatios": {
      "dso": 42,
      "dio": 30,
      "dpo": 45,
      "cashConversionCycle": 27
    },
    "solvencyRatios": {
      "debtToEquity": 0.55,
      "debtToAssets": 0.40,
      "interestCoverage": 8.5
    }
  }
}
```

**2. Script Postman (Tests Tab)**

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

var jsonData = pm.response.json();

pm.test("All ratio categories present", function () {
    pm.expect(jsonData.data).to.have.property('profitabilityRatios');
    pm.expect(jsonData.data).to.have.property('liquidityRatios');
    pm.expect(jsonData.data).to.have.property('activityRatios');
    pm.expect(jsonData.data).to.have.property('solvencyRatios');
});

pm.test("ROA is a valid percentage", function () {
    var roa = jsonData.data.profitabilityRatios.roa;
    pm.expect(roa).to.be.a('number');
    pm.expect(roa).to.be.at.least(0);
    pm.expect(roa).to.be.at.most(100);
});
```

---

### 📌 Scénario 9 : Gestion des Accès Dossiers (User-Company Access)

#### 🎯 Objectif
Affecter un collaborateur à un nouveau dossier et modifier son niveau d'accès.

#### 📝 Étapes

**1. Accorder l'Accès à un Dossier**

```
Method: POST
URL: {{base_url}}/user-company-access
Headers:
  Authorization: Bearer {{access_token}}
  Content-Type: application/json

Body:
{
  "userId": {{collaborator_id}},
  "companyId": {{company_id_C}},
  "accessLevel": "READ_WRITE",
  "canViewConfidential": false
}

Expected Response: 201 Created
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 101,
    "companyId": 12,
    "companyName": "Entreprise C SAS",
    "accessLevel": "READ_WRITE",
    "canViewConfidential": false,
    "grantedAt": "2025-01-15T10:30:00",
    "grantedBy": "Admin Cabinet"
  }
}
```

**2. Modifier le Niveau d'Accès**

```
Method: PUT
URL: {{base_url}}/user-company-access/1
Headers:
  Authorization: Bearer {{access_token}}
  Content-Type: application/json

Body:
{
  "accessLevel": "ADMIN",
  "canViewConfidential": true
}

Expected Response: 200 OK
```

**3. Lister les Accès d'un Utilisateur**

```
Method: GET
URL: {{base_url}}/users/{{collaborator_id}}/company-access
Headers:
  Authorization: Bearer {{access_token}}

Expected Response: 200 OK
{
  "success": true,
  "data": [
    {
      "companyId": 11,
      "companyName": "Entreprise B SA",
      "accessLevel": "READ_WRITE"
    },
    {
      "companyId": 12,
      "companyName": "Entreprise C SAS",
      "accessLevel": "ADMIN"
    }
  ]
}
```

**4. Révoquer l'Accès**

```
Method: DELETE
URL: {{base_url}}/user-company-access/1
Headers:
  Authorization: Bearer {{access_token}}

Expected Response: 204 No Content
```

---

### 📌 Scénario 10 : Refresh Token

#### 🎯 Objectif
Renouveler le token d'accès avec le refresh token.

#### 📝 Étapes

**1. Refresh Token**

```
Method: POST
URL: {{base_url}}/auth/refresh
Headers:
  Content-Type: application/json

Body:
{
  "refreshToken": "{{refresh_token}}"
}

Expected Response: 200 OK
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

**2. Script Postman (Tests Tab)**

```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

var jsonData = pm.response.json();

pm.test("New access token received", function () {
    pm.expect(jsonData.accessToken).to.be.a('string');
    pm.expect(jsonData.accessToken).to.not.equal(pm.environment.get("access_token"));
});

// Mettre à jour le token
pm.environment.set("access_token", jsonData.accessToken);
```

---

## 🤖 Tests Automatiques (Collection Runner)

### Exécuter Toute la Collection

1. **Ouvrir Postman** → Sélectionner la collection
2. **Cliquer sur "Run"** (icône ▶️)
3. **Configurer** :
   - Environment: `PREDYKT - MODE CABINET - DEV`
   - Iterations: 1
   - Delay: 500ms (entre chaque requête)
4. **Cliquer "Run PREDYKT MODE CABINET"**

### Ordre d'Exécution Recommandé

```
1. Health Check
2. Login Admin Cabinet
3. Get All Companies
4. Register Collaborateur
5. Login Collaborateur
6. Test Company Access (Collaborateur)
7. Create General Ledger Entry
8. Import CSV
9. Generate Balance Sheet
10. Calculate Financial Ratios
11. Grant Company Access
12. Revoke Company Access
13. Refresh Token
```

### Résultats Attendus

- ✅ **Passed**: Tous les tests doivent passer (0 failures)
- ⏱️ **Duration**: ~5-10 secondes total
- 📊 **Coverage**: 50+ tests automatiques

---

## 🔍 Troubleshooting

### ❌ Erreur 401 Unauthorized

**Problème** : Token manquant ou expiré

**Solutions** :
1. Vérifier que `{{access_token}}` est bien défini dans l'environnement
2. Re-login pour obtenir un nouveau token
3. Vérifier le header `Authorization: Bearer {{access_token}}`

```bash
# Vérifier le token dans Postman Console
console.log(pm.environment.get("access_token"));
```

---

### ❌ Erreur 403 Forbidden

**Problème** : Permissions insuffisantes

**Solutions** :
1. Vérifier le rôle de l'utilisateur (ROLE_ADMIN_CABINET vs ROLE_COLLABORATOR)
2. Vérifier que l'utilisateur a accès au dossier (user_company_access)
3. Re-login avec un utilisateur ayant les bonnes permissions

---

### ❌ Erreur 400 Bad Request - "Écriture déséquilibrée"

**Problème** : Total débit ≠ Total crédit

**Solution** :
Vérifier que dans le body de la requête :
```javascript
totalDebit = sum(entries.debit)
totalCredit = sum(entries.credit)
totalDebit === totalCredit  // Doit être true
```

---

### ❌ Erreur 404 Not Found - Company

**Problème** : Company ID n'existe pas ou n'appartient pas au cabinet

**Solutions** :
1. Vérifier que `{{company_id_A}}` est bien défini (10, 11, 12)
2. Lister toutes les companies du cabinet : `GET /companies`
3. Vérifier que la company a bien `cabinetId = 1`

---

### ❌ Token Expiré

**Problème** : JWT expiré après 24h

**Solution** :
```javascript
// Dans Tests de la requête Login, ajouter :
var expiresAt = new Date(Date.now() + jsonData.expiresIn * 1000);
pm.environment.set("token_expires_at", expiresAt.toISOString());
console.log("Token expires at:", expiresAt);
```

Puis créer une requête "Pre-request Script" globale :
```javascript
// Vérifier si le token est expiré
var expiresAt = new Date(pm.environment.get("token_expires_at"));
if (Date.now() > expiresAt.getTime()) {
    console.log("⚠️ Token expired, please re-login");
}
```

---

### ❌ Problème de CORS

**Problème** : Erreur CORS dans Postman (rare)

**Solution** :
Postman ne devrait PAS avoir de problèmes CORS. Si vous en avez :
1. Désactiver "Proxy" dans Postman Settings
2. Vérifier que l'application Spring Boot est bien démarrée
3. Tester avec curl pour isoler le problème

---

## 📊 Postman Collection JSON (Import Direct)

Pour importer directement la collection, créez un fichier `PREDYKT_MODE_CABINET.postman_collection.json` :

```json
{
  "info": {
    "name": "PREDYKT MODE CABINET",
    "description": "Collection complète pour tester l'API PREDYKT en MODE CABINET",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "00 - Health Check",
      "item": [
        {
          "name": "Health Check",
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200', function () {",
                  "    pm.response.to.have.status(200);",
                  "});"
                ],
                "type": "text/javascript"
              }
            }
          ],
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/health",
              "host": ["{{base_url}}"],
              "path": ["health"]
            }
          }
        }
      ]
    },
    {
      "name": "01 - Authentication",
      "item": [
        {
          "name": "Login Admin Cabinet",
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200', function () {",
                  "    pm.response.to.have.status(200);",
                  "});",
                  "",
                  "var jsonData = pm.response.json();",
                  "",
                  "pm.test('Access token is present', function () {",
                  "    pm.expect(jsonData.accessToken).to.be.a('string');",
                  "});",
                  "",
                  "pm.environment.set('access_token', jsonData.accessToken);",
                  "pm.environment.set('refresh_token', jsonData.refreshToken);",
                  "pm.environment.set('user_id', jsonData.userId);",
                  "",
                  "pm.test('User has ROLE_ADMIN_CABINET', function () {",
                  "    pm.expect(jsonData.roles).to.include('ROLE_ADMIN_CABINET');",
                  "});"
                ],
                "type": "text/javascript"
              }
            }
          ],
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"email\": \"{{admin_email}}\",\n  \"password\": \"{{admin_password}}\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/auth/login",
              "host": ["{{base_url}}"],
              "path": ["auth", "login"]
            }
          }
        }
      ]
    }
  ]
}
```

---

## 📁 Fichiers CSV de Test

### activités-test.csv

```csv
date de saisie;Activitées;description;Montant Brut;Type;Années
15/01/2025;Wholesale Sales;Vente marchandises client ABC;5000000;Revenu;2025
16/01/2025;Export Sales;Vente export vers Gabon;3500000;Revenu;2025
17/01/2025;Administrative Salaries;Salaires administratifs janvier;1200000;Dépenses;2025
18/01/2025;Rent;Loyer bureau mensuel;450000;Dépenses;2025
19/01/2025;Maintenance;Maintenance équipements;280000;Dépenses;2025
20/01/2025;Marketing & Sales;Campagne publicitaire;350000;Dépenses;2025
```

---

## 🎯 Checklist Finale

Avant de valider que tout fonctionne :

- [ ] ✅ Health check répond 200 OK
- [ ] ✅ Login admin retourne JWT avec `cabinetId`
- [ ] ✅ Lister companies retourne 3 dossiers
- [ ] ✅ Créer collaborateur avec `ROLE_COLLABORATOR`
- [ ] ✅ Collaborateur accède uniquement à ses dossiers
- [ ] ✅ Créer écriture comptable équilibrée
- [ ] ✅ Écriture déséquilibrée rejetée (400)
- [ ] ✅ Import CSV réussit
- [ ] ✅ Bilan comptable généré
- [ ] ✅ Ratios financiers calculés
- [ ] ✅ Accorder/révoquer accès dossier
- [ ] ✅ Refresh token fonctionne
- [ ] ✅ Tous les tests Postman passent (0 failures)

---

## 📞 Support

- **Documentation API** : `http://localhost:8080/api/v1/swagger-ui.html`
- **Guide d'authentification** : `./AUTHENTICATION.md`
- **Guide de test général** : `./TESTING_MODE_CABINET.md`

---

**Version** : 2.0.0
**Date** : Janvier 2025
**Auteur** : PREDYKT Team

# 🚀 Postman Quick Start - MODE CABINET

Guide rapide pour importer et utiliser la collection Postman en 5 minutes.

## 📥 Étape 1 : Importer la Collection

1. **Ouvrir Postman Desktop**

2. **Importer la Collection**
   - Cliquer sur **Import** (en haut à gauche)
   - Sélectionner le fichier : `PREDYKT_MODE_CABINET.postman_collection.json`
   - Cliquer **Import**
   - ✅ Vous devez voir la collection "PREDYKT MODE CABINET" apparaître

3. **Importer l'Environnement**
   - Cliquer sur **Import** à nouveau
   - Sélectionner le fichier : `PREDYKT_MODE_CABINET.postman_environment.json`
   - Cliquer **Import**
   - ✅ Vous devez voir l'environnement "PREDYKT - MODE CABINET - DEV" apparaître

4. **Activer l'Environnement**
   - En haut à droite, sélectionner **"PREDYKT - MODE CABINET - DEV"** dans le dropdown
   - ✅ L'environnement est maintenant actif

---

## ⚙️ Étape 2 : Démarrer l'Application

### Option A : Utiliser le script de provisioning (Recommandé)

```bash
cd /d/Bel/projets/predykt-backend-java

# Lancer le provisioning
./scripts/provision-new-cabinet.sh test-cabinet "Cabinet Test" localhost

# Quand demandé "Créer des données de test ?" → Répondre "y"
```

### Option B : Démarrage manuel

```bash
# Variables d'environnement
export PREDYKT_TENANT_MODE=CABINET
export PREDYKT_TENANT_CABINET_ID=1
export SPRING_PROFILES_ACTIVE=cabinet

# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

### Vérifier le Démarrage

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Réponse attendue : 200 OK
```

---

## 🧪 Étape 3 : Exécuter les Tests

### Test Individuel

1. **Ouvrir la collection** "PREDYKT MODE CABINET"
2. **Naviguer vers** : `01 - Authentication` → `POST Login Admin Cabinet`
3. **Cliquer sur "Send"**
4. **Vérifier** :
   - ✅ Status: 200 OK
   - ✅ Onglet "Tests" : Tous verts (6/6 passed)
   - ✅ Console : JWT payload avec `cabinetId: 1`
   - ✅ Environment : Variable `access_token` remplie

### Exécuter Toute la Collection (Collection Runner)

1. **Cliquer sur la collection** "PREDYKT MODE CABINET"
2. **Cliquer sur "Run"** (bouton ▶️ à droite)
3. **Configurer** :
   - ✅ Environment: "PREDYKT - MODE CABINET - DEV"
   - ✅ Iterations: 1
   - ✅ Delay: 500ms
4. **Cliquer "Run PREDYKT MODE CABINET"**

### Résultats Attendus

```
Iterations: 1/1
Requests: 15/15
Test Scripts: 50 passed, 0 failed
Duration: ~8 seconds
```

---

## 📋 Étape 4 : Scénario de Test Complet (Pas à Pas)

### 1️⃣ Health Check (Sans Auth)

```
Requête: GET Health Check
Status attendu: 200 OK
Auth: ❌ Aucune
```

**Résultat** :
```json
{
  "status": "UP"
}
```

---

### 2️⃣ Login Admin Cabinet

```
Requête: POST Login Admin Cabinet
Status attendu: 200 OK
Auth: ❌ Aucune
```

**Résultat** :
```json
{
  "accessToken": "eyJhbGc...",
  "userId": 100,
  "email": "admin@localhost",
  "roles": ["ROLE_ADMIN_CABINET"]
}
```

**✅ Vérification** :
- Console → JWT contient `cabinetId: 1` et `companyId: 10`
- Environment → `access_token` rempli automatiquement

---

### 3️⃣ Lister les Dossiers (Companies)

```
Requête: GET All Companies (Cabinet)
Status attendu: 200 OK
Auth: ✅ Bearer {{access_token}}
```

**Résultat** : 3 companies (ID 10, 11, 12)

**❌ Test Sans JWT** :
- Retirer le header `Authorization`
- Envoyer la requête
- Status attendu : **401 Unauthorized**

---

### 4️⃣ Créer un Collaborateur

```
Requête: POST Register Collaborateur
Status attendu: 201 Created
Auth: ✅ Bearer {{access_token}}
Body: Contient companyId=11 et cabinetId=1
```

**Résultat** :
```json
{
  "userId": 101,
  "email": "collaborateur@localhost",
  "roles": ["ROLE_COLLABORATOR"]
}
```

**✅ Vérification** :
- JWT contient `cabinetId: 1` et `companyId: 11`
- Environment → `collaborator_token` rempli

---

### 5️⃣ Tester l'Isolation (Sécurité)

**Test 1 : Accès Autorisé**
```
Requête: GET Company (Collaborateur - Dossier Autorisé)
URL: /companies/11 (Entreprise B)
Auth: ✅ Bearer {{collaborator_token}}
Status attendu: 200 OK ✅
```

**Test 2 : Accès Refusé**
```
Requête: GET Company (Collaborateur - Dossier NON Autorisé)
URL: /companies/10 (Entreprise A)
Auth: ✅ Bearer {{collaborator_token}}
Status attendu: 403 Forbidden ✅
```

**✅ Vérification** : L'isolation fonctionne !

---

### 6️⃣ Créer une Écriture Comptable

```
Requête: POST Create Balanced Entry
Status attendu: 201 Created
Auth: ✅ Bearer {{access_token}}
Body: 3 lignes (débit = crédit = 1 180 000 XAF)
```

**Résultat** :
```json
{
  "data": {
    "id": 1,
    "isBalanced": true,
    "totalDebit": 1180000,
    "totalCredit": 1180000
  }
}
```

**❌ Test Écriture Déséquilibrée** :
```
Requête: POST Create Unbalanced Entry (Should Fail)
Status attendu: 400 Bad Request ✅
Message: "Écriture déséquilibrée..."
```

---

### 7️⃣ Importer un CSV

```
Requête: POST Import Activities CSV
Status attendu: 200 OK
Auth: ✅ Bearer {{access_token}}
Body: form-data avec fichier test-activities-cabinet.csv
```

**Comment faire dans Postman** :
1. Onglet **Body** → Sélectionner **form-data**
2. Key : `file` (Type: File)
3. Value : Cliquer **Select Files** → Choisir `test-activities-cabinet.csv`
4. Envoyer

**Résultat** :
```json
{
  "data": {
    "totalRows": 16,
    "successCount": 16,
    "errorCount": 0
  }
}
```

---

### 8️⃣ Générer un Bilan

```
Requête: GET Balance Sheet (Bilan)
Status attendu: 200 OK
Auth: ✅ Bearer {{access_token}}
Query Param: asOfDate=2025-01-31
```

**Résultat** : Bilan équilibré (Actif = Passif)

---

### 9️⃣ Calculer les Ratios

```
Requête: POST Calculate Ratios
Status attendu: 200 OK
Auth: ✅ Bearer {{access_token}}
Query Params: startDate=2025-01-01, endDate=2025-01-31
```

**Résultat** : 20+ ratios financiers (ROA, ROE, liquidité, etc.)

---

### 🔟 Refresh Token

```
Requête: POST Refresh Token
Status attendu: 200 OK
Auth: ❌ Aucune
Body: { "refreshToken": "{{refresh_token}}" }
```

**Résultat** : Nouveau `accessToken` généré

---

## 🎯 Checklist de Validation

Après avoir exécuté tous les tests :

- [ ] ✅ Health check fonctionne (200 OK)
- [ ] ✅ Login retourne JWT avec `cabinetId`
- [ ] ✅ Lister companies retourne 3 dossiers
- [ ] ✅ Requête sans JWT retourne 401
- [ ] ✅ Collaborateur créé avec bon rôle
- [ ] ✅ Isolation testée (403 sur dossier non autorisé)
- [ ] ✅ Écriture équilibrée créée
- [ ] ✅ Écriture déséquilibrée rejetée (400)
- [ ] ✅ Import CSV réussi (16/16 lignes)
- [ ] ✅ Bilan généré et équilibré
- [ ] ✅ Ratios calculés
- [ ] ✅ Refresh token fonctionne
- [ ] ✅ Collection Runner : 50+ tests passés, 0 échecs

---

## 📚 Documentation Complète

Pour les détails complets, consultez :
- **Guide détaillé** : `POSTMAN_TESTING_CABINET.md`
- **Guide d'authentification** : `AUTHENTICATION.md`
- **Guide de test général** : `TESTING_MODE_CABINET.md`

---

## 🆘 Problèmes Courants

### ❌ "access_token" is undefined

**Solution** : Exécuter d'abord la requête "POST Login Admin Cabinet" pour obtenir le token.

---

### ❌ 401 Unauthorized sur toutes les requêtes

**Vérification** :
1. Environment "PREDYKT - MODE CABINET - DEV" est sélectionné ?
2. Variable `access_token` est remplie ?
3. Token n'est pas expiré (24h) ?

**Solution** : Re-login.

---

### ❌ Application ne démarre pas

**Vérification** :
```bash
# Vérifier Docker
docker ps

# Vérifier les logs
docker logs predykt-app-cabinet-test-cabinet
```

---

### ❌ Import CSV échoue

**Vérification** :
1. Le fichier `test-activities-cabinet.csv` est bien sélectionné ?
2. Type du champ est bien "File" (pas "Text") ?
3. Key est bien "file" (minuscule) ?

---

## 🎉 Succès !

Si tous les tests passent, félicitations ! Votre installation MODE CABINET fonctionne parfaitement.

**Prochaines Étapes** :
1. Tester avec vos propres données
2. Créer des utilisateurs supplémentaires
3. Importer vos fichiers CSV réels
4. Générer des rapports pour vos clients

---

**Version** : 2.0.0
**Date** : Janvier 2025

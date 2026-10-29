# PREDYKT — API comptable et financière OHADA

API REST de comptabilité et de pilotage financier pour les entreprises d'Afrique francophone, conforme au référentiel **OHADA (SYSCOHADA)**. Elle couvre la tenue comptable, la TVA, le rapprochement bancaire assisté par machine learning et la production des états financiers.

L'application fonctionne en **trois modes de déploiement** : multi-tenant mutualisé, instance dédiée par client, ou mode **cabinet comptable** qui gère plusieurs entreprises clientes.

## Fonctionnalités

### Comptabilité
- Plan comptable OHADA initialisé automatiquement
- Écritures comptables, grand livre, journaux auxiliaires, comptes de tiers (clients, fournisseurs)
- Génération automatique des écritures à partir des factures, paiements et acomptes
- Immobilisations et plans d'amortissement
- Paiements fractionnés, acomptes, réductions et escomptes

### TVA
- Moteur de détection automatique de la TVA sur les opérations
- TVA récupérable et non récupérable, règles de récupérabilité
- Prorata de TVA

### Rapprochement bancaire
- Import des relevés bancaires aux formats OFX, MT940, CAMT.053, QIF et CSV
- Rapprochement automatique des transactions avec les écritures, par un modèle de **machine learning** (bibliothèque Smile)
- Explication des correspondances proposées (valeurs SHAP), suivi des expériences et des métriques des modèles

### États financiers et pilotage
- Bilan, compte de résultat, TAFIRE, notes annexes
- Ratios financiers, balance âgée, budget, trésorerie, tableau de bord
- Exports PDF et Excel

### Mode cabinet comptable
- Gestion de plusieurs entreprises clientes, avec accès par utilisateur
- Suivi du temps passé et facturation du cabinet

## Stack technique

| Couche | Technologie |
|---|---|
| Langage et framework | Java 17, Spring Boot 3.4 |
| Sécurité | Spring Security, JWT (jjwt) |
| Données | PostgreSQL, Spring Data JPA, Flyway |
| Cache | Redis |
| Machine learning | Smile, Apache Commons Math |
| Import et export | OpenCSV, Apache POI (Excel), PDF |
| Mapping | MapStruct, Lombok |
| Documentation | springdoc-openapi (Swagger) |
| Déploiement | Docker, Docker Compose, Traefik |

## Architecture

```
src/main/java/com/predykt/accounting/
├── controller/   36 contrôleurs REST
├── service/      logique métier (comptabilité, TVA, rapprochement, ML, rapports)
├── domain/       entités JPA
├── repository/   accès aux données
├── dto/          objets de requête et de réponse
├── mapper/       conversions entités ↔ DTO (MapStruct)
├── security/     authentification JWT
├── config/       configuration, routage multi-tenant des sources de données
└── validator/    règles de validation métier
```

### Modes de déploiement

| Mode | Fichier | Usage |
|---|---|---|
| `SHARED` | `docker-compose.shared.yml` | une instance, plusieurs entreprises isolées |
| `DEDICATED` | `docker-compose.dedicated.yml` | une instance et une base par client |
| `CABINET` | `docker-compose.cabinet.yml` | un cabinet comptable et ses clients |

Le mode est choisi avec la variable `PREDYKT_TENANT_MODE`.

## Installation

Prérequis : Java 17, Docker et Docker Compose.

```bash
git clone https://github.com/BelCodeur2005/predykt-fintech-africa.git
cd predykt-fintech-africa
cp .env.example .env
```

Renseigner au minimum `DB_PASSWORD` et `JWT_SECRET` dans `.env` (générer la clé avec `openssl rand -hex 64`). L'application refuse de démarrer si ces variables manquent.

Démarrer PostgreSQL et Redis, puis l'API :

```bash
docker-compose up -d
./mvnw spring-boot:run
```

L'API répond sur `http://localhost:8080`, la documentation Swagger sur `http://localhost:8080/swagger-ui.html`.

## Tests

```bash
./mvnw test
```

Une collection Postman complète est disponible dans [`postman/`](postman/), avec des données d'exemple dans [`exemples/`](exemples/).

## Documentation

| Dossier | Contenu |
|---|---|
| [`docs/comptabilite/`](docs/comptabilite/) | journaux, acomptes, paiements fractionnés, amortissements, conformité OHADA |
| [`docs/tva/`](docs/tva/) | moteur de TVA, prorata, TVA récupérable |
| [`docs/rapprochement-bancaire/`](docs/rapprochement-bancaire/) | import bancaire, rapprochement, système de matching ML |
| [`docs/api/`](docs/api/) | authentification, multi-tenant, tableau de bord, tests Postman |
| [`docs/deploiement/`](docs/deploiement/) | déploiement Docker et VPS |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | prochaines étapes |
| [`docs/conception/`](docs/conception/) | cahier des charges et présentation du projet |

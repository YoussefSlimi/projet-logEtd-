# logEtd — Backend

API REST pour la plateforme de location étudiante à Monastir.

## Stack

- Java 17, Spring Boot **4.2.0**
- Spring WebMVC, Spring Security (JWT), Bean Validation
- Spring Data JPA + Hibernate, PostgreSQL, Flyway
- WebSocket brut JSON, AOP/AspectJ pour l'audit
- jjwt 0.12.6, Lombok
- JUnit / MockMvc / tests d'intégration

Le modèle de données, les statuts et les règles métier sont décrits dans `../../docs/` (spécifications communes) et dans `docs/` (contrat API, décisions, journal). Les montants sont stockés en **millimes** (`Long`).

## Configuration

Fichier : `src/main/resources/application.properties`

| Propriété | Valeur de développement |
|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/logetd` |
| `spring.datasource.username` / `password` | `postgres` / `admin` |
| `server.port` | **8082** |
| `app.jwt.access-token-ttl-seconds` | `900` (15 min) |
| `app.jwt.refresh-token-ttl-seconds` | `604800` (7 jours) |
| `app.storage.photos-dir` | `uploads/photos` |
| `app.storage.payment-proofs-dir` | `uploads/payment-proofs` |
| `app.admin.bootstrap.enabled` | `true` |
| `app.admin.bootstrap.email` / `password` | `admin@logetd.local` / `Admin123!` |

⚠️ Mot de passe PostgreSQL, secret JWT et mot de passe admin sont des valeurs de **développement**. À externaliser via variables d'environnement en production.

Flyway s'exécute après Hibernate via un `ApplicationRunner` et la migration `V1__exclusion_constraint.sql` ajoute la contrainte PostgreSQL anti-chevauchement sur les réservations actives.

## Lancer le projet

### IntelliJ (recommandé)

1. Ouvrir le dossier `backendWeb/logEtd`.
2. Project SDK = **Java 17**.
3. Vérifier que PostgreSQL est démarré et que la base `logetd` existe.
4. Lancer la classe `org.logetd.LogEtdApplication`.

Console attendue : `Tomcat started on port(s): 8082`.

### Ligne de commande

`mvn` n'est pas sur le PATH de la machine ; utiliser le wrapper :

```powershell
.\mvnw.cmd -B clean test
.\mvnw.cmd spring-boot:run
```

Les tests : **145 tests, 0 échec, 0 erreur** (dernière exécution validée).

## Au premier démarrage

Une seule donnée est créée : le compte **ADMIN** de bootstrap (`admin@logetd.local` / `Admin123!`), uniquement si l'email et le téléphone sont libres. Aucune annonce, réservation ou paiement de test n'est inséré automatiquement.

Endpoints immédiats avec ce compte :

```http
POST /api/v1/auth/login   {"identifier":"admin@logetd.local","password":"Admin123!"}
GET  /api/v1/admin/queue
GET  /api/v1/admin/audit?page=0&size=20
GET  /api/v1/listings
```

## API

Préfixe : `/api/v1`. Contrat complet : `docs/05-api/API_ENDPOINTS.md`.

Collection Postman prête à importer : `docs/postman/logEtd.postman_collection.json` (baseUrl `http://localhost:8082`, 31 requêtes, scripts pour stocker les tokens).

Groupes :

- Authentification : `register`, `login`, `logout`
- Annonces : recherche, fiche, création, modification, photos, places
- Réservations : création + transitions `accept`, `deposit-pending`, `confirm`
- Visites : demande, confirmation avec `proofCode`, preuve
- Paiements : déclaration multipart + `Idempotency-Key`, confirmation, historique
- Messagerie : REST + WebSocket `/ws/messages`
- Signalements : `POST /reports`
- Administration : `queue`, `validate`, `refuse`, `audit`

Format d'erreur standard : `{ code, message, details, timestamp }`.

Règles clés :

- Une annonce passe `DRAFT → PENDING` automatiquement dès 3 photos (dont 1 extérieure) et 1 place, puis `PENDING → PUBLISHED` uniquement par décision admin.
- La recherche publique ne renvoie que les annonces `PUBLISHED`.
- Les téléphones sont masqués en `+216******` dans les messages.
- 3 signalants distincts suspendent une annonce.
- Les paiements exigent un `Idempotency-Key` à la déclaration et à la confirmation.

## Structure du code

```
src/main/java/org/logetd/
├── config/        sécurité, Flyway, bootstrap admin, gestion des erreurs
├── controller/    endpoints REST
├── domain/        entités JPA et repositories
├── dto/           objets de transfert (aucune entité exposée directement)
├── job/           alerte des paiements déclarés depuis plus de 48 h
├── security/      JWT
├── service/       logique métier
├── shared/        enums et erreurs API
└── websocket/     handler et diffusion des messages
```

## Documentation

| Fichier | Contenu |
|---|---|
| `docs/00-context/` | contexte initial (valeurs historiques) |
| `../../docs/` | spécifications Phase 1 / Phase 2 retenues (commun backend + frontend, source de vérité) |
| `../../docs/archive/` | versions antérieures des spécifications (non implémentées) |
| `docs/03-tasks/TASKS.md` | étapes 1 à 7, journal de validation |
| `docs/04-conventions/` | conventions |
| `docs/05-api/API_ENDPOINTS.md` | contrat API |
| `docs/06-decisions/DECISIONS.md` | décisions D-001 à D-007 |
| `docs/postman/` | collection Postman |
| `docs/AGENT_1_PROMPT.md` | prompt ayant servi à générer le backend |

> L'ancien dossier `docs/01-specs/` a été supprimé après déplacement des specs vers `../../docs/`. Les règles générales de l'ancien `02-architecture/API_CONTRACT.md` ont été fusionnées dans `API_ENDPOINTS.md` (voir D-009). Le contrat d'API est désormais le seul fichier de référence.

Ces fichiers sont versionnés volontairement : ils servent de contrat pour le frontend, les agents et les tests.

## État

Étapes 1 à 7 **terminées et validées** par l'humain (dernière validation le 2026-11-24). Aucun blocage en cours.

# logEtd — plateforme de location étudiante à Monastir

Dépôt unique contenant le backend Spring Boot et le frontend React de **logEtd**, ainsi que les spécifications et la documentation vivante du projet.

## Méthode de développement — plusieurs agents, une revue humaine

Le projet a été construit avec plusieurs agents spécialisés, chacun cadré par un prompt et un journal de tâches. Aucun agent n'a enchaîné les étapes tout seul : à la fin de chaque étape, il s'arrêtait et attendait la validation humaine (`passer`).

| Agent / outil | Périmètre | Trace dans le dépôt |
|---|---|---|
| Agent backend | Étapes 1 à 7 : JPA, sécurité JWT, annonces, recherche, réservations, visites, paiements, messagerie, back-office | `backendWeb/logEtd/docs/AGENT_1_PROMPT.md`, `backendWeb/logEtd/docs/03-tasks/TASKS.md`, `backendWeb/logEtd/docs/06-decisions/DECISIONS.md` |
| Agent frontend | Étape F1 : scaffold React/Vite, client API typé, AuthContext, gardes de rôles, i18n fr/ar | `frontendWeb/docs/FRONT_TASKS.md` |
| Skill Hallmark | Direction visuelle, tokens CSS, sweep de rendu | `frontendWeb/src/styles/tokens.css`, `frontendWeb/src/styles/base.css`, rapport dans `frontendWeb/docs/FRONT_TASKS.md` |
| Humain | Cadrage, arbitrages, tests Postman, validation de chaque étape | `backendWeb/logEtd/docs/05-api/API_ENDPOINTS.md`, journaux `TASKS.md` et `FRONT_TASKS.md` |

Les prompts et journaux sont conservés volontairement : ils montrent comment le travail a été découpé, vérifié et corrigé, et servent de contrat pour reprendre le projet.

## Contenu du dépôt

| Dossier | Rôle |
|---|---|
| `backendWeb/logEtd/` | API Spring Boot 4.2.0 / Java 17 / PostgreSQL (étapes 1 à 7 terminées et validées) |
| `frontendWeb/` | Client web React + TypeScript + Vite (étape F1 terminée, en attente de validation) |
| `docs/` | Spécifications communes Phase 1 / Phase 2 (.docx, .html), source de vérité partagée |
| `README.md` | Vue d'ensemble du dépôt et méthode de développement |

## Démarrage rapide

### 1. Prérequis

- JDK 17
- PostgreSQL avec une base `logetd` (utilisateur `postgres`, mot de passe `admin` en développement)
- Node.js 20.19+ ou 22.12+ et npm
- IntelliJ IDEA (recommandé pour le backend) ou Maven wrapper

### 2. Backend

Ouvrir `backendWeb/logEtd` dans IntelliJ, sélectionner JDK 17, puis lancer `org.logetd.LogEtdApplication`.

Le serveur écoute sur **http://localhost:8082** (`server.port` dans `application.properties`).

Sans IntelliJ :

```powershell
cd backendWeb\logEtd
.\mvnw.cmd -B clean test
.\mvnw.cmd spring-boot:run
```

Voir `backendWeb/logEtd/README.md` pour le détail (configuration, comptes de démarrage, tests, endpoints).

### 3. Frontend

```powershell
cd frontendWeb
npm ci
npm run dev
```

Le client est servi sur **http://localhost:5173**. Le proxy Vite redirige `/api` vers `http://localhost:8082` et `/ws` vers `ws://localhost:8082`, donc le backend doit être démarré en premier.

Voir `frontendWeb/README.md` pour le détail (stack, variables d'environnement, Hallmark, tests).

## Comptes de développement

| Rôle | Identifiant | Mot de passe |
|---|---|---|
| ADMIN | `admin@logetd.local` | `Admin123!` |
| OWNER / TENANT | créés via `POST /api/v1/auth/register` | — |

Le compte admin est créé automatiquement au premier démarrage si `app.admin.bootstrap.enabled=true`. L'inscription publique refuse le rôle `ADMIN`.

⚠️ Ces valeurs, ainsi que le secret JWT de développement, sont dans `backendWeb/logEtd/src/main/resources/application.properties`. À remplacer par des variables d'environnement avant toute mise en ligne réelle.

## Documentation

Les dossiers `docs/` sont **versionnés volontairement** : ils sont le contrat et la mémoire du projet.

### Commun — `docs/` (source de vérité, ne pas dupliquer)

- `docs/README.md` : index des documents communs et des docs spécifiques.
- `docs/Specifications_Phase1_Etudiants_Monastir (1).docx` : cahier des charges Phase 1 retenu (17 nov, Monastir / étudiants).
- `docs/RAPPORT_PHASE2_Architecture_MAJ.html` : architecture Phase 2 retenue (23 nov).
- `docs/archive/` : versions antérieures conservées pour la traçabilité, non implémentées.

### Spécifique au backend — `backendWeb/logEtd/docs/`

- `00-context/` (contexte initial, valeurs historiques), `04-conventions/` : cadrage et conventions.
- `03-tasks/TASKS.md` : journal d'étapes et validations humaines.
- `05-api/API_ENDPOINTS.md` : contrat API officiel.
- `06-decisions/DECISIONS.md` : décisions D-001 à D-008.
- `postman/logEtd.postman_collection.json` : collection Postman importable.
- `AGENT_1_PROMPT.md` : prompt de l'agent backend.

### Spécifique au frontend — `frontendWeb/docs/` et `frontendWeb/src/styles/`

- `docs/FRONT_TASKS.md` : journal des étapes frontend et rapports Hallmark.
- `src/styles/tokens.css`, `src/styles/base.css` : design system Hallmark.

## Règle de travail

Le projet avance **une étape à la fois**. Chaque étape se termine par des tests, une mise à jour de la documentation, puis une attente de validation humaine (`passer`) avant la suivante.

## État actuel

- ✅ Backend : étapes 1 à 7 validées, 145 tests réussis.
- ✅ Frontend : F1 (authentification + design Hallmark) livrée, en attente de `passer` pour F2.

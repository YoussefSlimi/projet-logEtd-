# logEtd — Frontend

Client web React de la plateforme logEtd. Il consomme l'API du backend situé dans `../backendWeb/logEtd`.

Les spécifications communes (Phase 1 / Phase 2) sont centralisées dans `../docs/` à la racine du dépôt. Le journal propre au frontend reste dans `docs/FRONT_TASKS.md`.

## Stack

- React 19 + TypeScript 5.9 + Vite 7 (pas de Next.js)
- React Router 7, TanStack Query 5
- MUI 7 + Framer Motion + Recharts
- React Hook Form + Zod
- react-i18next (français par défaut, arabe avec RTL)
- Vitest + Testing Library + jsdom
- Client API typé écrit à la main (aucun OpenAPI généré côté backend pour l'instant)
- Design piloté par la skill **Hallmark** (`tokens.css`, `base.css`, sweep `sloplint`)

## Prérequis

- Node.js **20.19+** ou **22.12+**
- npm
- Backend logEtd démarré sur `http://localhost:8082`

## Installation et commandes

```powershell
npm ci
npm run dev       # http://localhost:5173
npm run build     # tsc -b && vite build
npm test          # vitest run
npm run preview   # sert le build de production
```

## Configuration

`vite.config.ts` contient les proxies :

```ts
'/api' -> http://localhost:8082
'/ws'  -> ws://localhost:8082   (ws: true)
```

Le client API utilise donc `baseURL = '/api/v1'`. Aucune URL absolue du backend n'est codée en dur dans les composants.

`.env.local` est **ignoré par Git** et contient les identifiants de développement :

```
ADMIN_DEV_EMAIL=admin@logetd.local
ADMIN_DEV_PASSWORD=Admin123!
```

Ces noms ne sont pas préfixés `VITE_`, donc ils ne sont pas injectés dans le bundle navigateur.

## Session et autorisations

- `POST /auth/login` stocke `accessToken` + `refreshToken` dans le `AuthContext`.
- Toutes les requêtes protégées envoient `Authorization: Bearer <accessToken>`.
- `/auth/logout` envoie le **refresh token** dans l'en-tête.
- Aucun endpoint `/refresh` n'existe côté backend.
- Un `401` déconnecte et redirige vers `/login` ; un `403` affiche la page « Accès refusé ».
- Routes protégées par rôle : `TENANT`, `OWNER`, `ADMIN`.

## Structure

```
src/
├── api/client.ts     client typé + intercepteurs
├── auth/             AuthContext et gardes de rôles
├── pages/            écrans (actuellement AccessPages.tsx)
├── styles/           tokens.css, base.css, app.css (Hallmark)
├── test/             configuration Vitest
├── i18n.ts           français / arabe + RTL
├── main.tsx
└── theme.ts          MUI ThemeProvider alimenté par les tokens Hallmark
```

## Design Hallmark

La skill Hallmark fournit les tokens et le floor visuel. `theme.ts` consomme `tokens.css` via `createTheme()` : aucune couleur ni police littérale ne doit apparaître en dehors des tokens.

Vérification avant de considérer un écran terminé :

```powershell
node C:\Users\pc\.agents\skills\hallmark\scripts\sloplint.mjs src --genre modern-minimal --scope app --render --url http://localhost:5173/login
```

Tout `FAIL` doit être corrigé. Les `REFLEX` sont corrigés ou waivés par écrit, les `Finish` reçoivent une réponse. Le rapport est consigné dans `docs/FRONT_TASKS.md`.

## État actuel

**F1 — Authentification : livrée, en attente de validation humaine.**

Livré : scaffold Vite/TS, proxy, client API, login/register/logout, AuthContext, gardes de rôles, i18n fr/ar + RTL, rapport Hallmark avec sweep `0 FAIL / 0 REFLEX / 0 WARN` sur `/login` et `/register`.

Vérifications F1 : `npm run build` réussi, `npm test` 3 tests réussis.

À venir, une étape à la fois et toujours après validation `passer` :

- F2 recherche et fiche annonce
- F3 espace propriétaire (création, photos, places)
- F4 réservations et visites
- F5 paiements, messagerie, signalements
- F6 back-office ADMIN
- F7 WebSocket optionnel

## Notes techniques

- Les photos renvoyées par l'API sont des noms de fichiers ; le backend n'expose pas encore de serveur de fichiers, donc un placeholder est utilisé côté client.
- Aucune carte géographique n'est prévue au niveau 1 (élément reporté aux niveaux 2-4 de la spec).
- Journal détaillé : `docs/FRONT_TASKS.md`. Spécifications communes : `../docs/`.

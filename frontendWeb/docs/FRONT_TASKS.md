# Journal frontend logEtd

## F1 — Authentification (en attente de validation humaine)

### Livré

- Vite + React + TypeScript avec React Router, TanStack Query, MUI, Framer Motion, React Hook Form, Zod, react-i18next et Recharts déclarés.
- Proxy Vite `/api` vers `http://localhost:8082` et proxy WebSocket `/ws` vers le backend.
- Client API typé sur `/api/v1`, sans modification backend.
- Connexion `POST /auth/login`, inscription `POST /auth/register` et logout `POST /auth/logout`. Le logout transmet le refresh token dans `Authorization`; aucun renouvellement `/refresh` n'est ajouté.
- AuthContext conserve les deux tokens de session dans le stockage local, déconnecte sur 401 et redirige sur la page d'accès refusé sur 403.
- Routes protégées pour TENANT, OWNER et ADMIN; les écrans de rôle affichent un état d'attente pour les étapes suivantes.
- Formulaires validés côté client, erreurs serveur conservées et boutons désactivés uniquement pendant les requêtes.
- Français par défaut, traduction arabe de F1 et mise à jour de `lang`/`dir` à la sélection de langue.
- Compte de développement référencé dans `.env.local` avec des noms non préfixés `VITE_`, donc non injectés dans le bundle navigateur. Le fichier est ignoré par Git.

### Hallmark · v2.0.0

- **Idea** · l'écran d'accès est un guichet numérique précis pour locataires et propriétaires à Monastir.
- **Look** · papier bleu très clair et encre marine; turquoise limité à l'action principale; Segoe UI système, sans police sous licence fournie.
- **Chrome** · barre courte avec logEtd, contexte de ville et bascule de langue.
- **Screen** · connexion ou création de compte; état d'erreur API, chargement, accès refusé et état de rôle prêt pour les étapes ultérieures.
- **Signature** · formulaire d'accès lié aux rôles TENANT, OWNER et ADMIN réels.
- **Material** · routes, rôles, ville et contraintes de login/register du backend; aucune photo, aucun logo externe, aucun témoignage ni chiffre inventé.
- **Absent** · photographies et police de marque. Aucun stand-in photo n'est requis pour ces écrans d'application.
- **Waivers** · Gate 37: police système faute de fichier de police licencié fourni.
- **Reflex** · les seuls constats 24b du premier sweep ont été corrigés avec l'échelle d'espacement; sweep final: aucun FAIL, REFLEX ou WARN.
- **Finish** · F1 guillemets droits: sans objet; F2 ellipses: caractère typographique; F3 titres équilibrés; F4 un bouton primaire par écran; F5 aucune icône dans une pastille; F6 aucun grand chiffre; F7 largeur de conteneur et rayons cohérents; F8 aucune chorégraphie de scroll; F9 aucun avatar fictif; F10 aucune animation Lottie; F11 aucun surlignage; F12 note flex alignée au centre; F13 une seule famille de police; F14 ligne d'aide réservée par MUI; F15 invariants d'app respectés; F16 sélection, curseur, barre de défilement et focus thémés; F17 aucun texte en colonne étroite; F18 aucun slot image; F19 une famille et deux graisses; F20 aucun item orphelin; F21 hiérarchie h1/h2 continue; F22 aucun texte justifié; F23 `lang`, titre et description présents; F24 aucune ombre avec bordure; F25 aucun contenu retiré au téléphone; F26 titres vérifiés aux captures 375/1280, sans ligne finale orpheline.
- **Sweep** · `sloplint --genre modern-minimal --scope app --render` sur `/login` et `/register`: 0 FAIL, 0 REFLEX, 0 WARN.

### Vérifications

- `npm run build` — réussi. Vite signale une taille de chunk initial supérieure à 500 kB; le découpage sera à traiter quand les écrans ultérieurs utiliseront leurs dépendances.
- `npm test` — 3 tests réussis (requête de login/token, validation sans requête, payload register et en-tête logout).
- Les sweeps ont rendu les routes `/login` et `/register` aux largeurs 1280, 1440, 1920 et 375 px.

### En attente

- Arrêt après F1 conformément au découpage demandé. Attendre « passer » avant F2.

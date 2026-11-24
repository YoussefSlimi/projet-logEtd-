# Endpoints de l'API — contrat officiel logEtd

Préfixe : `/api/v1/`. Ce fichier est le contrat vivant de l'API : mis à jour après chaque étape backend terminée, il sert de référence au frontend.

Statut : ✅ Implémenté | ⏳ En cours | ❌ À faire

## Règles générales

- Préfixe : `/api/v1/`.
- Format des dates : ISO 8601 (`2026-11-23T10:00:00Z`) ; dates de réservation en `YYYY-MM-DD`.
- Format des montants : millimes (entier `Long`), jamais de virgule flottante.
- En-tête d'authentification : `Authorization: Bearer <JWT>` (access token ; le refresh token sert uniquement au logout).
- Format d'erreur standard : `{ code, message, details, timestamp }`.

```json
{
  "code": "BOOKING_DATES_UNAVAILABLE",
  "message": "Ces dates ne sont plus disponibles.",
  "details": [{"field": "startDate", "issue": "overlap"}],
  "timestamp": "2026-11-23T10:42:00Z"
}
```

## Authentification
| Méthode | Route | Statut | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | ✅ Implémenté | Inscription (TENANT, OWNER) |
| POST | `/api/v1/auth/login` | ✅ Implémenté | Connexion, retourne JWT |
| POST | `/api/v1/auth/logout` | ✅ Implémenté | Révocation refresh token |

### Détails — `POST /api/v1/auth/register`
- Corps (`application/json`) : `{ "phone", "email", "password", "fullName", "role" }` avec `role` ∈ {`TENANT`, `OWNER`}.
- Validation : téléphone/email/nom obligatoires, email valide, mot de passe 8–72 caractères, rôle obligatoire.
- Succès : `201 Created` avec `{ "id", "phone", "email", "fullName", "role", "status" }` (jamais le hash du mot de passe).
- Erreurs : `400 VALIDATION_ERROR`, `400 REGISTER_ROLE_NOT_ALLOWED` (ADMIN refusé), `409 EMAIL_ALREADY_USED`, `409 PHONE_ALREADY_USED`.

### Détails — `POST /api/v1/auth/login`
- Corps : `{ "identifier", "password" }` — `identifier` = email **ou** téléphone.
- Succès : `200 OK` avec `{ "accessToken", "refreshToken", "tokenType": "Bearer", "expiresIn": 900, "user": {...} }`.
- Le `accessToken` sert d'`Authorization: Bearer <JWT>` sur les routes protégées ; le `refreshToken` sert uniquement au logout (et au futur endpoint `/refresh`).
- Erreurs : `400 VALIDATION_ERROR`, `401 INVALID_CREDENTIALS`, `403 ACCOUNT_NOT_ACTIVE`.

### Détails — `POST /api/v1/auth/logout`
- En-tête requis : `Authorization: Bearer <refresh token>` (le refresh token de la réponse de login, **pas** l'access token).
- Succès : `204 No Content`. Révocation idempotente (un token inconnu ou déjà révoqué ne provoque pas d'erreur).
- Erreur : `400 REFRESH_TOKEN_MISSING` si l'en-tête est absent ou mal formé.

## Annonces (Listings)
| Méthode | Route | Statut | Description |
|---|---|---|---|
| GET | `/api/v1/listings` | ✅ Implémenté | Liste filtrée et paginée (projection + coût réel) |
| GET | `/api/v1/listings/{id}` | ✅ Implémenté | Détail d'une annonce (adresse masquée) |
| POST | `/api/v1/listings` | ✅ Implémenté | Créer une annonce (OWNER) |
| PUT | `/api/v1/listings/{id}` | ✅ Implémenté | Modifier (OWNER propriétaire) |
| POST | `/api/v1/listings/{id}/photos` | ✅ Implémenté | Upload photos (multipart, OWNER propriétaire) |
| POST | `/api/v1/listings/{id}/places` | ✅ Implémenté | Créer une place (OWNER propriétaire) |

### Détails — `GET /api/v1/listings`
- Auth : `Authorization: Bearer <access token>` (tout rôle authentifié) ; sans token → `401 UNAUTHENTICATED`.
- Paramètres de requête (tous optionnels) :

| Paramètre | Type | Description |
|---|---|---|
| `city` | string | Ville, comparaison insensible à la casse (`Monastir` par défaut côté création). |
| `district` | string | Quartier, recherche partielle (`LIKE %...%`). |
| `budgetMin`, `budgetMax` | Long (millimes) | Filtrent le **loyer mensuel** des places ; `budgetMin <= budgetMax` requis. |
| `genderRule` | enum | `MALE_ONLY`, `FEMALE_ONLY`, `MIXED`. |
| `furnished` | boolean | Logement meublé. |
| `chargesIncluded` | boolean | Charges comprises. |
| `internetIncluded` | boolean | Internet compris. |
| `startDate`, `endDate` | `YYYY-MM-DD` | Période souhaitée : exclut les places réservées sur des dates chevauchantes. **Doivent être fournis ensemble**, `endDate >= startDate`. |
| `page` | int | Page (0-based, défaut `0`). |
| `size` | int | Taille (défaut `20`, **max 50**). |

- Statuts visibles : **`PUBLISHED` uniquement** depuis l'Étape 7. `PENDING` attend la validation de l'administrateur et n'est plus exposé à la recherche publique (`ListingVisibilityTest`).
- Succès : `200 OK` avec `{ "content": [...], "page", "size", "totalElements", "totalPages" }`. Chaque élément de `content` est un `ListingSummaryDTO` :

```json
{
  "id": 1,
  "title": "Studio campus",
  "city": "Monastir",
  "district": "Sidi El Hani",
  "genderRule": "MIXED",
  "status": "PUBLISHED",
  "photoPath": "vitrine.jpg",
  "monthlyCostFromMillimes": 415000,
  "places": [
    { "id": 10, "label": "Chambre A", "monthlyRentMillimes": 350000,
      "chargesMillimes": 50000, "estimationsMillimes": 15000,
      "realMonthlyCostMillimes": 415000 }
  ]
}
```

- **Aucune adresse exacte** n'est exposée dans la recherche (projection dédiée, pas d'entité JPA).
- `realMonthlyCostMillimes = monthlyRentMillimes + chargesMillimes + estimationsMillimes` ; `monthlyCostFromMillimes` = le coût réel le moins cher parmi les places disponibles de l'annonce. Si une période est demandée et que toutes les places d'une annonce sont réservées, l'annonce est omise des résultats.
- Erreurs `400` (format standard) : `PAGE_SIZE_TOO_LARGE` (`size > 50`), `INVALID_PAGE_SIZE` (`size < 1`), `INVALID_PAGE` (`page < 0`), `INVALID_BUDGET_RANGE`, `INVALID_PERIOD` (dates manquantes ou inversées).

### Détails — `POST /api/v1/listings`
- Auth : `Authorization: Bearer <access token>` + rôle `OWNER` (sinon `403 ACCESS_DENIED`).
- Corps : `{ "title", "description", "city", "exactAddress", "genderRule", "district", "furnished", "chargesIncluded", "internetIncluded" }` ; `title` obligatoire, `city` par défaut `Monastir`, booléens `false` par défaut.
- Succès : `201 Created` avec `{ "id", "title", "description", "city", "exactAddress", "genderRule", "status": "DRAFT", "ownerId", "photos": [], "places": [], "district", "furnished", "chargesIncluded", "internetIncluded" }`.
- Erreurs : `400 VALIDATION_ERROR`, `400 LISTING_LIMIT_REACHED` (3 annonces maximum), `403 OWNER_ROLE_REQUIRED`.

### Détails — `GET /api/v1/listings/{id}`
- Succès : `200 OK` avec le DTO `ListingResponse` (photos et places incluses).
- L'`exactAddress` est remplacée par `null` pour tout utilisateur autre que le propriétaire ou un `ADMIN`.
- Erreur : `404 LISTING_NOT_FOUND`.

### Détails — `PUT /api/v1/listings/{id}`
- Même corps que la création ; seuls les champs fournis sont appliqués, le statut n'est pas modifié.
- Erreurs : `404 LISTING_NOT_FOUND`, `403 LISTING_NOT_OWNED`, `400 VALIDATION_ERROR`.

### Détails — `POST /api/v1/listings/{id}/photos`
- `Content-Type: multipart/form-data`.
- Parties : `files` (1 à 15 fichiers image `jpg`, `jpeg`, `png`, `webp`, 5 Mo max par fichier) et paramètres répétés `exterior=true|false` alignés sur `files` (drapeau absent = `false`).
- Le total des photos (existante + nouvelles) doit être compris entre **3 et 15** et contenir **au moins 1 photo extérieure**.
- Succès : `200 OK` avec le `ListingResponse` mis à jour (statut `PENDING` dès qu'une place existe).
- Erreurs : `400 PHOTO_COUNT_INVALID`, `400 PHOTO_EXTERIOR_REQUIRED`, `400 INVALID_PHOTO_TYPE`, `400 PHOTO_TOO_LARGE`, `403 LISTING_NOT_OWNED`.

### Détails — `POST /api/v1/listings/{id}/places`
- Corps : `{ "label", "monthlyRentMillimes", "chargesMillimes", "estimations", "estimationsMillimes" }` — montants en millimes (`Long`). `estimations` est le texte libre, `estimationsMillimes` le montant chiffré des estimations (défaut `0`) utilisé dans le coût réel.
- Succès : `201 Created` avec le `ListingResponse` mis à jour (chaque place inclut `estimationsMillimes` et `realMonthlyCostMillimes`).
- Erreurs : `400 VALIDATION_ERROR`, `403 LISTING_NOT_OWNED`, `404 LISTING_NOT_FOUND`.

## Réservations (Bookings)
| Méthode | Route | Statut | Description |
|---|---|---|---|
| POST | `/api/v1/bookings` | ✅ Implémenté | Demander une réservation (TENANT) |
| POST | `/api/v1/bookings/{id}/accept` | ✅ Implémenté | Accepter la demande (OWNER) |
| POST | `/api/v1/bookings/{id}/deposit-pending` | ✅ Implémenté | Passer en attente d'acompte (OWNER) |
| POST | `/api/v1/bookings/{id}/confirm` | ✅ Implémenté | Confirmer après acompte (OWNER) |

Toutes ces routes exigent `Authorization: Bearer <access token>`. Le corps de réponse est toujours le DTO `BookingResponse` :

```json
{
  "id": 7,
  "placeId": 42,
  "listingId": 10,
  "tenantId": 1,
  "status": "REQUESTED",
  "startDate": "2026-12-01",
  "endDate": "2026-12-10",
  "depositMillimes": 50000
}
```

### Détails — `POST /api/v1/bookings`
- Auth : rôle `TENANT` (sinon `403 ACCESS_DENIED` via Spring Security).
- Corps : `{ "placeId": 42, "startDate": "2026-12-01", "endDate": "2026-12-10", "depositMillimes": 50000 }`. `depositMillimes` est facultatif (défaut `0`) ; montant en **millimes**. Les dates sont inclusives et `endDate >= startDate`, `startDate >= aujourd'hui`.
- Succès : `201 Created` avec `status: "REQUESTED"`.
- **Anti-chevauchement** : un verrou pessimiste sur la place sérialise les demandes ; une réservation dont le statut est `REQUESTED`, `ACCEPTED`, `DEPOSIT_PENDING`, `CONFIRMED` ou `IN_PROGRESS` et dont la période chevauche (bornes inclusives) fait échouer la demande avec `409 BOOKING_DATES_UNAVAILABLE`.
- Erreurs : `400 VALIDATION_ERROR`, `400 INVALID_BOOKING_PERIOD`, `403 TENANT_ROLE_REQUIRED` / `ACCESS_DENIED`, `404 PLACE_NOT_FOUND`, `409 BOOKING_DATES_UNAVAILABLE`, `409 DATA_CONFLICT`.

### Détails — transitions de statut
- `POST /bookings/{id}/accept` : `REQUESTED → ACCEPTED`
- `POST /bookings/{id}/deposit-pending` : `ACCEPTED → DEPOSIT_PENDING`
- `POST /bookings/{id}/confirm` : `DEPOSIT_PENDING → CONFIRMED`
- Auth : rôle `OWNER` **et** propriétaire de l'annonce de la place (sinon `403 OWNER_ROLE_REQUIRED` / `ACCESS_DENIED` / `BOOKING_NOT_OWNED`). Aucun corps requis.
- Succès : `200 OK` avec le `BookingResponse` mis à jour. Chaque transition est flushée : la contrainte PostgreSQL `no_overlap_active_booking` est vérifiée à nouveau.
- Erreurs : `404 BOOKING_NOT_FOUND`, `403 BOOKING_NOT_OWNED`, `409 BOOKING_STATUS_INVALID` (statut courant incompatible), `409 BOOKING_DATES_UNAVAILABLE` (violation d'exclusion `23P01` ou deadlock `40P01` entre deux acceptations concurrentes — **jamais 500**).
- La confirmation effective du dépôt (preuve de paiement, double validation) sera implémentée à l'Étape 6.

## Visites
| Méthode | Route | Statut | Description |
|---|---|---|---|
| POST | `/api/v1/visits` | ✅ Implémenté | Demander une visite (TENANT) |
| POST | `/api/v1/visits/{id}/confirm` | ✅ Implémenté | Confirmer et générer le `proofCode` (OWNER) |
| POST | `/api/v1/visits/{id}/proof` | ✅ Implémenté | Présenter le code + position (TENANT) |

Le corps de réponse est le DTO `VisitResponse` :

```json
{
  "id": 100,
  "listingId": 10,
  "tenantId": 1,
  "ownerId": 2,
  "scheduledAt": "2026-12-01T10:00:00Z",
  "status": "CONFIRMED",
  "provenAt": null,
  "proofCode": "123456"
}
```

`proofCode` est `null` dans toutes les réponses **sauf** `POST /visits/{id}/confirm`, où il est révélé au propriétaire.

### Détails — `POST /api/v1/visits`
- Auth : rôle `TENANT`. Corps : `{ "listingId": 10, "scheduledAt": "2026-12-01T10:00:00Z" }` (date future obligatoire).
- Succès : `201 Created` avec `status: "REQUESTED"` et `proofCode: null`.
- Erreurs : `400 VALIDATION_ERROR`, `400 LISTING_NOT_VISITABLE` (annonce `DRAFT`/`REFUSED`/`SUSPENDED`/`TO_RECONFIRM`), `400 INVALID_VISIT_DATE`, `403 TENANT_ROLE_REQUIRED` / `ACCESS_DENIED`, `404 LISTING_NOT_FOUND`.

### Détails — `POST /api/v1/visits/{id}/confirm`
- Auth : rôle `OWNER` **et** propriétaire de l'annonce. Aucun corps requis.
- Succès : `200 OK` avec `status: "CONFIRMED"` et un `proofCode` à **6 chiffres** (généré par `SecureRandom`).
- Erreurs : `403 OWNER_ROLE_REQUIRED` / `ACCESS_DENIED` / `VISIT_NOT_OWNED`, `404 VISIT_NOT_FOUND`, `409 VISIT_ALREADY_CONFIRMED`.

### Détails — `POST /api/v1/visits/{id}/proof`
- Auth : rôle `TENANT` **et** étudiant ayant demandé la visite.
- Corps : `{ "proofCode": "123456", "latitude": 35.8, "longitude": 10.6 }` — la position est **facultative**.
- Succès : `200 OK` avec `status: "PROVEN"`, `provenAt` renseigné et `proofCode: null`. Le code est à **usage unique** : il est effacé après acceptation (comparaison en temps constant).
- Erreurs : `400 VALIDATION_ERROR`, `400 INVALID_PROOF_CODE`, `403 TENANT_ROLE_REQUIRED` / `ACCESS_DENIED` / `VISIT_NOT_FOR_TENANT`, `404 VISIT_NOT_FOUND`, `409 VISIT_NOT_CONFIRMED` (visite déjà prouvée ou pas encore confirmée).

## Paiements
| Méthode | Route | Statut | Description |
|---|---|---|---|
| POST | `/api/v1/payments` | ✅ Implémenté | Déclarer un paiement + preuve (TENANT) |
| POST | `/api/v1/payments/{id}/confirm` | ✅ Implémenté | Confirmer réception (OWNER) |
| GET | `/api/v1/payments/booking/{bookingId}` | ✅ Implémenté | Paiements d'une réservation (pagé) |

Le corps de réponse est le DTO `PaymentResponse` :

```json
{
  "id": 9,
  "bookingId": 7,
  "tenantId": 1,
  "paymentType": "DEPOSIT",
  "paymentMethod": "D17",
  "amountMillimes": 50000,
  "status": "DECLARED",
  "tenantDeclared": true,
  "ownerConfirmed": false,
  "declaredAt": "2026-11-24T11:30:00Z",
  "confirmedAt": null,
  "proofPath": "8f14e45f-ea3b-4c2d-9a10-6b2c1d9f0a3e.pdf"
}
```

`proofPath` contient uniquement le **nom de fichier généré** : le binaire reste sur le disque dans `uploads/payment-proofs` et aucun endpoint de téléchargement n'est exposé à cette étape.

### Détails — `POST /api/v1/payments`

- Auth : `Authorization: Bearer <access token>` + rôle `TENANT` (sinon `403 ACCESS_DENIED` / `TENANT_ROLE_REQUIRED`).
- `Content-Type: multipart/form-data`. En-tête obligatoire : `Idempotency-Key`.
- Paramètres de formulaire : `bookingId` (Long), `paymentType` ∈ {`DEPOSIT`, `RENT`, `CAUTION`}, `paymentMethod` ∈ {`D17`, `BANK_TRANSFER`, `CASH`}, `amountMillimes` (Long > 0, en millimes).
- Partie fichier : `proof` (obligatoire, `jpg`, `jpeg`, `png`, `webp` ou `pdf`, 5 Mo maximum).
- Succès : `201 Created` avec `status: "DECLARED"`, `tenantDeclared: true`, `ownerConfirmed: false`, `declaredAt` renseigné et `confirmedAt: null`. Si la réservation était `ACCEPTED`, elle passe en `DEPOSIT_PENDING` ; si elle était déjà `DEPOSIT_PENDING`, elle reste inchangée.
- **Idempotence** : deux appels avec la même clé sur la même réservation renvoient le même `PaymentResponse`, sans second `saveAndFlush` ni second fichier. La clé est hachée en SHA-256 (`declaration|<bookingId>|<clé>`) et stockée dans `declaration_idempotency_hash` (unique). Les appels concurrents sont sérialisés par un verrou pessimiste sur la réservation.
- Erreurs : `400 IDEMPOTENCY_KEY_MISSING`, `400 IDEMPOTENCY_KEY_INVALID` (clé > 255 caractères), `400 INVALID_PAYMENT`, `400 INVALID_PAYMENT_AMOUNT`, `400 VALIDATION_ERROR` (paramètre ou partie multipart absent/invalide), `400 PAYMENT_PROOF_REQUIRED`, `400 INVALID_PAYMENT_PROOF_TYPE`, `400 PAYMENT_PROOF_TOO_LARGE`, `400 INVALID_PAYMENT_PROOF_PATH`, `500 PAYMENT_PROOF_STORAGE_ERROR`, `401 UNAUTHENTICATED`, `403 PAYMENT_NOT_FOR_TENANT`, `404 BOOKING_NOT_FOUND`, `409 BOOKING_NOT_PAYABLE`.

### Détails — `POST /api/v1/payments/{id}/confirm`

- Auth : rôle `OWNER` **et** propriétaire de l'annonce liée à la réservation. Aucun corps requis ; en-tête `Idempotency-Key` obligatoire.
- Succès : `200 OK` avec `ownerConfirmed: true`, `confirmedAt` renseigné et `status: "CONFIRMED"` **uniquement si** `tenantDeclared && ownerConfirmed`. La réservation passe de `DEPOSIT_PENDING` à `CONFIRMED`.
- Tant que le propriétaire n'a pas confirmé, le paiement reste `DECLARED` et la réservation reste `DEPOSIT_PENDING` (interprétation du « statut PENDING », voir D-006). `PaymentStatus.PENDING` n'est pas produit par le flux normal.
- **Idempotence** : hash SHA-256 `confirmation|<paymentId>|<clé>` stocké dans `confirmation_idempotency_hash` (unique), avec verrou pessimiste sur le paiement. Un replay avec la même clé renvoie la même réponse sans nouvelle transition ; réutiliser la clé sur un autre appel donne `409 IDEMPOTENCY_KEY_REUSE`.
- Erreurs : `400 IDEMPOTENCY_KEY_MISSING`, `400 IDEMPOTENCY_KEY_INVALID`, `401 UNAUTHENTICATED`, `403 ACCESS_DENIED` / `OWNER_ROLE_REQUIRED` / `PAYMENT_NOT_OWNED`, `404 PAYMENT_NOT_FOUND`, `409 PAYMENT_NOT_DECLARED`, `409 PAYMENT_STATUS_INVALID` (déjà `CONFIRMED`/`BLOCKED` avec une autre clé), `409 IDEMPOTENCY_KEY_REUSE`.

### Détails — `GET /api/v1/payments/booking/{bookingId}`

- Auth : toute partie prenante (locataire ou propriétaire) ou `ADMIN` ; sinon `403 PAYMENT_NOT_FOR_BOOKING`.
- Paramètres : `page` (défaut `0`), `size` (défaut `20`, max `50`).
- Succès : `200 OK` avec `{ "content": [...], "page", "size", "totalElements", "totalPages" }`, tri par `id` croissant.
- Erreurs : `400 INVALID_PAGE`, `400 INVALID_PAGE_SIZE`, `400 PAGE_SIZE_TOO_LARGE`, `400 VALIDATION_ERROR`, `401 UNAUTHENTICATED`, `403 PAYMENT_NOT_FOR_BOOKING`, `404 BOOKING_NOT_FOUND`.

### Alerte 48 h (job interne, pas d'endpoint)

- `PaymentAlertScheduler` tourne toutes les heures (`app.payment.alert-check-interval-ms=3600000`) et sélectionne les paiements `DECLARED` dont `declaredAt < now - 48h`.
- Chaque cas est journalisé en `WARN` sous la forme `ALERTE_ADMIN PAYMENT_UNCONFIRMED_48H paymentId={id} bookingId={id}` : identifiants techniques uniquement, aucune donnée personnelle.
- Aucun envoi d'e-mail ni WebSocket n'est implémenté pour cette alerte : l'Étape 7 expose simplement ces paiements dans la file admin (`GET /api/v1/admin/queue`).

## Messagerie

| Méthode | Route | Statut | Description |
|---|---|---|---|
| WS | `/ws/messages` | ✅ Implémenté | WebSocket brut JSON (JWT au handshake, abonnement + envoi) |
| POST | `/api/v1/messages` | ✅ Implémenté | Repli REST : envoyer un message (parties prenantes) |
| GET | `/api/v1/messages/booking/{bookingId}` | ✅ Implémenté | Historique paginé d'une réservation |

### Détails — `WebSocket /ws/messages`

- Handshake HTTP : `/ws/messages` est `permitAll` dans Spring Security ; l'authentification est faite dans `MessagingWebSocketHandler` avec le JWT fourni soit par le paramètre de requête `?token=<access token>`, soit par l'en-tête `Authorization: Bearer <access token>`. Seul un **access token** d'un utilisateur `ACTIVE` est accepté ; sinon la session reçoit un JSON d'erreur standard et est fermée avec le code `1008` (`UNAUTHENTICATED` / `INVALID_TOKEN`).
- Trames entrantes (JSON) :
  - `{"type":"SUBSCRIBE","bookingId":7}` — vérifie que l'utilisateur est locataire ou propriétaire de l'annonce, puis abonne la session ; accusé `{"type":"SUBSCRIBED","bookingId":7}`.
  - `{"type":"MESSAGE","bookingId":7,"content":"Bonjour"}` — passe par `MessagingService` (mêmes contrôles que le REST) et diffuse le message à tous les abonnés de la réservation.
- Trame sortante diffusée : `{"type":"MESSAGE","bookingId":7,"message":{...}}` avec le DTO `MessageResponse` ci-dessous. Les erreurs métier sont envoyées sous la forme `{code, message, details, timestamp}`.
- Une session non authentifiée ne peut ni s'abonner ni envoyer. Trame limitée à 8192 caractères.

### Détails — `POST /api/v1/messages`

- Auth : tout utilisateur authentifié, puis contrôle métier « parties prenantes de la réservation » (locataire, propriétaire ou `ADMIN`) ; sinon `403 MESSAGE_NOT_FOR_BOOKING`.
- Corps : `{ "bookingId": 7, "content": "Bonjour" }` — `content` obligatoire, 2000 caractères maximum.
- Succès : `201 Created` avec `MessageResponse` :

```json
{
  "id": 11,
  "bookingId": 7,
  "senderId": 1,
  "content": "Appelez +216******",
  "sentAt": "2026-11-24T12:00:00Z",
  "readAt": null
}
```

- Le contenu est **filtré avant persistance et avant diffusion** : les numéros tunisiens (`+216…`, `216…`, `00216…`) et les numéros locaux à 8 chiffres sont remplacés par `+216******`. Le message est aussi poussé aux abonnés WebSocket de la réservation.
- Erreurs : `400 INVALID_MESSAGE`, `400 VALIDATION_ERROR`, `401 UNAUTHENTICATED`, `403 MESSAGE_NOT_FOR_BOOKING`, `404 BOOKING_NOT_FOUND`.

### Détails — `GET /api/v1/messages/booking/{bookingId}`

- Auth : parties prenantes ou `ADMIN`. Paramètres `page` (défaut `0`) et `size` (défaut `20`, max `50`).
- Succès : `200 OK` avec `{ "content": [...], "page", "size", "totalElements", "totalPages" }`, tri par `sentAt` puis `id` croissant.
- Erreurs : `400 INVALID_PAGE`, `401 UNAUTHENTICATED`, `403 MESSAGE_NOT_FOR_BOOKING`, `404 BOOKING_NOT_FOUND`.

## Signalements

| Méthode | Route | Statut | Description |
|---|---|---|---|
| POST | `/api/v1/reports` | ✅ Implémenté | Signaler une annonce (motif prédéfini) |

### Détails — `POST /api/v1/reports`

- Auth : tout utilisateur authentifié ; le propriétaire de l'annonce ne peut pas signaler la sienne (`403 REPORT_OWN_LISTING`).
- Corps : `{ "listingId": 5, "reason": "FAKE_LISTING", "details": "…" }` avec `reason` ∈ {`FAKE_LISTING`, `INAPPROPRIATE_PHOTO`, `SCAM`, `OTHER`} et `details` facultatif (1000 caractères max).
- Succès : `201 Created` avec `{ "id", "listingId", "reporterId", "reason", "details", "status": "OPEN", "createdAt" }`.
- **Auto-masquage** : dès que 3 **signalants distincts** ont signalé la même annonce (`count(distinct reporter_id) >= 3`), l'annonce passe automatiquement en `ListingStatus.SUSPENDED` et l'action est journalisée (`LISTING_SUSPENDED_BY_REPORT`, acteur `null`). Les signalements répétés d'un même utilisateur ne comptent qu'une fois.
- Erreurs : `400 INVALID_REPORT`, `400 VALIDATION_ERROR`, `401 UNAUTHENTICATED`, `403 REPORT_OWN_LISTING`, `404 LISTING_NOT_FOUND`.

## Administration (`ROLE_ADMIN`)

| Méthode | Route | Statut | Description |
|---|---|---|---|
| GET | `/api/v1/admin/queue` | ✅ Implémenté | File : annonces `PENDING`, signalements `OPEN`, paiements en retard |
| POST | `/api/v1/admin/listings/{id}/validate` | ✅ Implémenté | Valider une annonce → `PUBLISHED` |
| POST | `/api/v1/admin/listings/{id}/refuse` | ✅ Implémenté | Refuser une annonce (motif obligatoire) → `REFUSED` |
| GET | `/api/v1/admin/audit` | ✅ Implémenté | Journal d'audit paginé |

- Auth : `Authorization: Bearer <access token>` avec le rôle `ADMIN` ; sinon `401 UNAUTHENTICATED` ou `403 ACCESS_DENIED`. Un contrôle complémentaire `ADMIN_ROLE_REQUIRED` est fait dans le service.
- Un compte admin de démarrage est créé au lancement si `app.admin.bootstrap.enabled=true` (`admin@logetd.local` / `+21600000000` / `Admin123!` en développement, **à remplacer en production**). L'inscription publique refuse toujours le rôle `ADMIN`.

### Détails — `GET /api/v1/admin/queue`

- Succès : `200 OK` avec trois listes : `pendingListings` (`AdminListingResponse`, adresse exacte incluse), `openReports` (`ReportResponse`), `latePayments` (`LatePaymentResponse` : paiements `DECLARED` dont `declaredAt` est antérieur à `now - 48h`, comme l'alerte de l'Étape 6).
- Aucun paramètre ; les listes sont triées par `id` croissant.

### Détails — `POST /api/v1/admin/listings/{id}/validate`

- Aucun corps. L'annonce doit être `PENDING` ; elle passe en `PUBLISHED` et `lastConfirmedAt` est horodaté.
- Succès : `200 OK` avec `AdminListingResponse`. Erreurs : `401 UNAUTHENTICATED`, `403 ACCESS_DENIED` / `ADMIN_ROLE_REQUIRED`, `404 LISTING_NOT_FOUND`, `409 LISTING_STATUS_INVALID`.
- Une entrée d'audit `LISTING_VALIDATED` est écrite automatiquement (acteur = admin, cible = annonce).

### Détails — `POST /api/v1/admin/listings/{id}/refuse`

- Corps obligatoire : `{ "reason": "Photos trompeuses" }` — motif non vide, 500 caractères maximum, conservé dans les détails de l'audit.
- Succès : `200 OK` avec `AdminListingResponse` au statut `REFUSED`. Mêmes erreurs que la validation, plus `400 VALIDATION_ERROR` / `400 REFUSAL_REASON_REQUIRED` si le motif manque.
- Une entrée d'audit `LISTING_REFUSED` est écrite automatiquement avec le motif.

### Détails — `GET /api/v1/admin/audit`

- Paramètres `page` (défaut `0`) et `size` (défaut `20`, max `50`).
- Succès : `200 OK` avec `{ "content": [...], "page", "size", "totalElements", "totalPages" }`, tri par `createdAt` décroissant. Chaque entrée contient `id`, `actorId` (ou `null` pour une action système), `action`, `targetType`, `targetId`, `details`, `createdAt`.
- Erreurs : `400 INVALID_PAGE`, `401 UNAUTHENTICATED`, `403 ACCESS_DENIED`.
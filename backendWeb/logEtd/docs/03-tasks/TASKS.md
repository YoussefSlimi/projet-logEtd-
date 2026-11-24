# Suivi des tâches — Agent 1 (Backend Spring Boot)

## Règle de travail
L'agent réalise UNE étape à la fois. Il s'arrête à la fin de chaque étape et demande validation humaine. L'agent ne passe JAMAIS à l'étape suivante sans validation explicite.

## Légende
- [ ] À faire
- [~] En cours
- [x] Terminé et validé par l'humain
- [!] Bloqué

---

## ÉTAPE 1 — Initialisation (TERMINÉE — validée par l'humain)

### Configuration
- [x] Vérifier `src/main/resources/application.properties` (connexion PostgreSQL + `ddl-auto=update`)
- [x] Ajouter les dépendances manquantes dans `pom.xml` (si besoin)

### Énumérations (créer dans `src/main/java/org/logetd/shared/`)
- [x] `Role.java` (TENANT, OWNER, ADMIN)
- [x] `UserStatus.java` (ACTIVE, SUSPENDED, BANNED)
- [x] `ListingStatus.java` (DRAFT, PENDING, PUBLISHED, REFUSED, SUSPENDED, TO_RECONFIRM)
- [x] `GenderRule.java` (MALE_ONLY, FEMALE_ONLY, MIXED)
- [x] `BookingStatus.java` (REQUESTED, ACCEPTED, DEPOSIT_PENDING, CONFIRMED, IN_PROGRESS, CLOSED, REFUSED, EXPIRED, CANCELLED)
- [x] `PaymentType.java` (DEPOSIT, RENT, CAUTION)
- [x] `PaymentMethod.java` (D17, BANK_TRANSFER, CASH)
- [x] `PaymentStatus.java` (PENDING, DECLARED, CONFIRMED, BLOCKED)
- [x] `VisitStatus.java` (REQUESTED, CONFIRMED, CANCELLED, EXPIRED, PROVEN)

### Entités JPA (créer dans `src/main/java/org/logetd/domain/...`)
- [x] `org.logetd.domain.user.User` (table `users`)
- [x] `org.logetd.domain.listing.Listing` (table `listings`)
- [x] `org.logetd.domain.listing.Place` (table `places`)
- [x] `org.logetd.domain.booking.Booking` (table `bookings`)
- [x] `org.logetd.domain.booking.Visit` (table `visits`)
- [x] `org.logetd.domain.payment.Payment` (table `payments`)

### Vérification
- [x] L'application démarre sans erreur (`mvn spring-boot:run` — validé via `mvn test` / `@SpringBootTest`)
- [x] Les tables sont créées dans PostgreSQL (vérifiable dans pgAdmin)
- [x] `DECISIONS.md` mis à jour

---

## ÉTAPE 2 — Auth (TERMINÉE — validée par l'humain)
- [x] `POST /api/v1/auth/register` (TENANT, OWNER)
- [x] `POST /api/v1/auth/login` (JWT)
- [x] `POST /api/v1/auth/logout`
- [x] BCrypt coût 12
- [x] Tests unitaires et MockMvc

### Détails de l'Étape 2
- Sécurité stateless (Spring Security) : session `STATELESS`, CSRF désactivé, filtre JWT propriétaire.
- Access token JWT (TTL 900 s) et refresh token JWT (TTL 604800 s) : seul le hash SHA-256 du refresh token est stocké en base (`refresh_tokens`).
- Réponses via DTO (`RegisterRequest`, `LoginRequest`, `UserResponse`, `TokenResponse`) : aucune entité JPA n'est exposée.
- Format d'erreur standard `{code, message, details, timestamp}` via `GlobalExceptionHandler` (validation, erreurs métier, 401/403).
- Vérification : `mvn test` → **15 tests, 0 échec, 0 erreur** (`AuthServiceTest` 8 tests unitaires, `AuthControllerTest` 6 tests MockMvc + 1 test de sécurité existant).

---


## ÉTAPE 3 — Annonces & Places (TERMINÉE — validée par l'humain)

### Objectif
Un propriétaire peut publier un logement avec ses places et ses photos.

### Tâches
- [x] `ListingRepository`, `PlaceRepository`, `ListingPhotoRepository`
- [x] `FileStorageService.java` (stockage local des photos)
- [x] `ListingService.java` (règles métier : plafond 3 annonces, statuts)
- [x] `ListingController.java` :
    - [x] `POST /api/v1/listings` (OWNER)
    - [x] `GET /api/v1/listings/{id}`
    - [x] `PUT /api/v1/listings/{id}` (OWNER)
    - [x] `POST /api/v1/listings/{id}/photos` (multipart, min 3, max 15, dont 1 extérieure)
    - [x] `POST /api/v1/listings/{id}/places` (OWNER)
- [x] DTOs : `ListingRequest`, `ListingResponse`, `PlaceRequest`, `PlaceResponse` (+ `PhotoResponse` imbriqué)
- [x] Validation : 3 photos min, 1 extérieure, plafond 3 logements (`LISTING_LIMIT_REACHED`)
- [x] Masquage de l'adresse exacte pour les visiteurs non autorisés
- [x] Tests : `ListingServiceTest`, `ListingControllerTest`
- [x] Documentation mise à jour (`API_ENDPOINTS.md`, `DECISIONS.md`)

### Détails de l'Étape 3
- Nouvelle entité `ListingPhoto` (table `listing_photos`) : chemin relatif, drapeau `exterior`, ordre, horodatage. Le binaire reste sur le disque (`uploads/photos`), seule la référence est en base.
- Statuts : création en `DRAFT`, passage automatique en `PENDING` dès que l'annonce a ≥ 3 photos dont 1 extérieure **et** ≥ 1 place (la publication `PUBLISHED` reste à l'Étape 7, validation admin).
- Contrôle d'accès en deux niveaux : `hasRole("OWNER")` dans `SecurityConfig` + vérification de propriété dans `ListingService` (`LISTING_NOT_OWNED`).
- Adresse exacte masquée (`null`) dans `GET /listings/{id}` pour tout utilisateur autre que le propriétaire ou un `ADMIN`.

### Critères d'acceptation
- [x] `mvn clean compile` sans erreur
- [x] `mvn test` passe (tous les tests, y compris ceux des Étapes 1 et 2) — **31 tests, 0 échec, 0 erreur**
- [x] Un propriétaire peut créer une annonce → 201 Created
- [x] Upload de 3 photos → 200 OK
- [x] Créer une place → 201 Created
- [x] 4ème annonce → 400 `LISTING_LIMIT_REACHED`
- [x] Un étudiant ne peut pas créer d'annonce → 403 Forbidden

---

## ÉTAPE 4 — Recherche & Filtres (TERMINÉE — validée par l'humain)

### Objectif
Les étudiants peuvent chercher avec des critères, paginer, et voir le coût réel mensuel.

### Tâches
- [x] `ListingSearchService.java` (JPA Specification, critères dynamiques)
- [x] Endpoint `GET /api/v1/listings` avec filtres :
    - [x] `city`, `district`, `budgetMin`, `budgetMax`
    - [x] `genderRule`, `furnished`, `chargesIncluded`, `internetIncluded`
    - [x] `page`, `size` (max 50)
- [x] Projection DTO (`ListingSummaryDTO`) pour éviter le N+1
- [x] Exclusion des places déjà réservées sur la période demandée
- [x] Calcul du coût réel mensuel (loyer + charges + estimations)
- [x] Tests : `ListingSearchServiceTest`, `ListingSearchControllerTest`
- [x] Documentation mise à jour (`API_ENDPOINTS.md`, `DECISIONS.md`)

### Détails de l'Étape 4
- Nouveaux champs filtrables sur `Listing` : `district`, `furnished`, `chargesIncluded`, `internetIncluded` (booléens `false` par défaut, colonnes `NOT NULL`) ; sur `Place` : `estimationsMillimes` (Long, défaut 0) pour chiffrer le coût réel. Les constructeurs DTO de l'Étape 3 sont conservés pour compatibilité.
- `ListingRepository` étend `JpaSpecificationExecutor<Listing>` : les filtres dynamiques sont composés dans une `Specification` (ville en égalité insensible à la casse, quartier en `LIKE`, budget sur le loyer des places avec `distinct`).
- **Projection sans N+1** : 1 page d'annonces + 1 requête groupée pour les places + 1 pour les photos + 1 pour les places réservées, puis assemblage en mémoire. `ListingSummaryDTO` n'expose ni adresse exacte ni entité JPA.
- **Coût réel mensuel** = `monthlyRentMillimes + chargesMillimes + estimationsMillimes` (millimes) ; `monthlyCostFromMillimes` est le minimum des places disponibles d'une annonce.
- **Exclusion des réservations** : si `startDate`/`endDate` sont fournis, les places avec une réservation active (`ACCEPTED`, `DEPOSIT_PENDING`, `CONFIRMED`, `IN_PROGRESS`) chevauchant la période sont retirées (`startDate <= endDate` ET `endDate >= startDate`, bornes inclusives alignées sur `daterange(..., '[]')` — voir D-005) ; une annonce dont toutes les places sont indisponibles est masquée. `BookingRepository` est créé ici et sera réutilisé à l'Étape 5.
- Visibilité : `PUBLISHED` **et** `PENDING` tant que la validation admin de l'Étape 7 n'existe pas. Pagination par défaut `page=0`, `size=20`, tri par `id` croissant, `size` max 50.

### Critères d'acceptation
- [x] `GET /listings?city=Monastir&budgetMax=600000` → résultats filtrés
- [x] `GET /listings?page=0&size=100` → 400 BAD_REQUEST (`PAGE_SIZE_TOO_LARGE`)
- [x] Les places déjà réservées sur la période n'apparaissent pas dans les résultats
- [x] Le coût réel mensuel est affiché dans la réponse (`realMonthlyCostMillimes`, `monthlyCostFromMillimes`)
- [x] `mvn test` passe : **41 tests, 0 échec, 0 erreur** (Étapes 1 à 4)

---

## ÉTAPE 5 — Visites & Réservations (CRITIQUE) (TERMINÉE — validée par l'humain le 2026-11-24)

### Objectif
Un étudiant visite, puis réserve. **La contrainte anti-chevauchement doit être testée.**

### Tâches
- [x] `VisitRepository`, `BookingRepository` (ce dernier créé à l'Étape 4, étendu ici)
- [x] `VisitService.java` (demande, confirmation, code à usage unique)
- [x] `BookingService.java` (demande, acceptation, gestion statuts)
- [x] `VisitController.java` :
    - [x] `POST /api/v1/visits`
    - [x] `POST /api/v1/visits/{id}/confirm` (OWNER, génère `proofCode`)
    - [x] `POST /api/v1/visits/{id}/proof` (TENANT, saisie code + position)
- [x] `BookingController.java` :
    - [x] `POST /api/v1/bookings`
    - [x] `POST /api/v1/bookings/{id}/accept`
    - [x] `POST /api/v1/bookings/{id}/deposit-pending` (OWNER, transition supplémentaire)
    - [x] `POST /api/v1/bookings/{id}/confirm` (OWNER, transition supplémentaire)
- [x] **Gestion de la contrainte** : `@ExceptionHandler` qui attrape `DataIntegrityViolationException` et `CannotAcquireLockException` → `409 BOOKING_DATES_UNAVAILABLE` (jamais 500)
- [x] **Test critique** : deux threads simultanés → 1 succès, 1 erreur 409 (à la création **et** à l'acceptation)
- [x] Tests : `BookingServiceTest` (12), `VisitServiceTest` (11), `BookingControllerTest` (6), `VisitControllerTest` (6), `BookingConcurrencyTest` (2)
- [x] Documentation mise à jour (`API_ENDPOINTS.md`, `DECISIONS.md`)

### Détails de l'Étape 5
- **Visites** : `POST /visits` crée une visite `REQUESTED` (annonce `PUBLISHED` ou `PENDING`, date future obligatoire). Seul le propriétaire peut confirmer : il reçoit alors un `proofCode` à 6 chiffres généré par `SecureRandom`. Ce code est comparé en temps constant (`MessageDigest.isEqual`) lors de `POST /visits/{id}/proof`, puis **effacé** : il est à usage unique. La position (`latitude`/`longitude`) transmise avec la preuve est facultative et stockée sur la visite. Les visiteurs ne voient jamais le code.
- **Anti-chevauchement à deux niveaux** : (1) verrou pessimiste `Place.findByIdForUpdate` + pré-contrôle `existsOverlapping` sur les statuts bloquants (`REQUESTED`, `ACCEPTED`, `DEPOSIT_PENDING`, `CONFIRMED`, `IN_PROGRESS`) à la création → 409 immédiat et sérialisation des demandes concurrentes ; (2) `saveAndFlush` aux transitions de statut pour que la contrainte PostgreSQL `no_overlap_active_booking` joue aussi en cas de course entre deux acceptations.
- **Concurrence réelle** : `BookingConcurrencyTest` utilise deux vrais threads et la vraie base PostgreSQL. Il a mis en évidence un deadlock (`SQLSTATE 40P01`, `CannotAcquireLockException`) puis une violation d'exclusion (`SQLSTATE 23P01`) ; les deux sont désormais traduits en `409 BOOKING_DATES_UNAVAILABLE`. Aucun `500` n'est toléré par le test.
- **Chaîne de statuts** : `REQUESTED → ACCEPTED → DEPOSIT_PENDING → CONFIRMED`. Les transitions sont exposées séparément (`/accept`, `/deposit-pending`, `/confirm`) pour rendre la séquence testable ; la confirmation effective du dépôt (paiement, double validation) reste à l'Étape 6. `depositMillimes` est facultatif et vaut `0` par défaut.
- **Sécurité** : règles `hasRole` dans `SecurityConfig` (TENANT pour demander visite/réservation et prouver une visite, OWNER pour confirmer une visite et gérer les réservations) + contrôle de propriété dans les services (`VISIT_NOT_OWNED`, `VISIT_NOT_FOR_TENANT`, `BOOKING_NOT_OWNED`). Aucune entité JPA n'est exposée : DTO `VisitResponse`, `BookingResponse` etc.

### Critères d'acceptation
- [x] Créer une visite → 201 (code non exposé)
- [x] Confirmer une visite (OWNER) → reçoit `proofCode`
- [x] Valider une visite avec le code → 200, code effacé ensuite (2ᵉ usage → 409 `VISIT_NOT_CONFIRMED`)
- [x] **CRITIQUE** : deux réservations concurrentes sur les mêmes dates → 1× 201, 1× 409, **jamais 500** (vérifié pour la création et l'acceptation)
- [x] Le statut de la réservation évolue : `REQUESTED` → `ACCEPTED` → `DEPOSIT_PENDING` → `CONFIRMED`
- [x] `mvn clean test` passe (Étapes 1 à 5) : **78 tests, 0 échec, 0 erreur**, BUILD SUCCESS — dont `BookingConcurrencyTest` (2 tests) sur la vraie base PostgreSQL

---

## ÉTAPE 6 — Paiements (TERMINÉE — validée par l'humain le 2026-11-24)

### Objectif
Acompte / loyer / caution avec double confirmation (étudiant déclare, propriétaire confirme).

### Tâches
- [x] `PaymentRepository` (verrou pessimiste, recherches par hash d'idempotence, liste paginée, paiements en retard)
- [x] `Payment` étendu : `proofPath`, `declaredAt`, `confirmedAt`, hashes d'idempotence uniques
- [x] `PaymentProofStorageService.java` (preuves locales, 5 Mo max, jpg/jpeg/png/webp/pdf)
- [x] `PaymentService.java` (déclaration, confirmation, double validation, idempotence, contrôles de partie prenante)
- [x] `PaymentController.java` :
    - [x] `POST /api/v1/payments` (multipart + upload de preuve, TENANT)
    - [x] `POST /api/v1/payments/{id}/confirm` (OWNER)
    - [x] `GET /api/v1/payments/booking/{bookingId}` (pagé, parties prenantes ou ADMIN)
- [x] DTOs : `PaymentDeclaration`, `PaymentResponse`, `PaymentPageResponse` (aucune entité JPA exposée)
- [x] Règle métier : `status = CONFIRMED` **uniquement si** `tenantDeclared && ownerConfirmed`
- [x] Transition réservation : `ACCEPTED → DEPOSIT_PENDING` à la déclaration, `DEPOSIT_PENDING → CONFIRMED` à la confirmation
- [x] Job `@Scheduled` (`PaymentAlertScheduler`) : log `ALERTE_ADMIN PAYMENT_UNCONFIRMED_48H` chaque heure si non confirmé depuis 48 h
- [x] `Idempotency-Key` obligatoire sur les deux endpoints d'écriture (hash SHA-256 unique par portée)
- [x] Sécurité : règles `hasRole` + vérification de la relation réservation/annonce dans le service
- [x] `GlobalExceptionHandler` : paramètres multipart/requête manquants ou invalides → `400 VALIDATION_ERROR` (jamais 500)
- [x] Tests : `PaymentServiceTest` (15), `PaymentControllerTest` (7), `PaymentAlertSchedulerTest` (2), `PaymentIdempotencyTest` (3, vraie base + concurrence)
- [x] Documentation mise à jour (`API_ENDPOINTS.md`, `DECISIONS.md`)

### Détails de l'Étape 6
- **Déclaration** : l'étudiant envoie `bookingId`, `paymentType`, `paymentMethod`, `amountMillimes` et une preuve (`proof`) avec un `Idempotency-Key`. Le paiement est créé en `DECLARED` avec `tenantDeclared=true`, `ownerConfirmed=false`, `declaredAt` renseigné et `confirmedAt=null`. Seul le nom de fichier généré est persisté dans `proofPath`.
- **Double validation** : la confirmation propriétaire pose `ownerConfirmed=true` et `confirmedAt` ; le statut passe en `CONFIRMED` seulement si `tenantDeclared && ownerConfirmed`. Sans déclaration préalable, la confirmation est refusée (`409 PAYMENT_NOT_DECLARED`).
- **Statut d'attente** : interprétation retenue de « le statut reste PENDING tant que le propriétaire n'a pas confirmé » — le **paiement** reste `DECLARED` et la **réservation** reste `DEPOSIT_PENDING` ; `PaymentStatus.PENDING` désigne un paiement non déclaré et n'est pas produit par le flux normal (voir D-006).
- **Idempotence** : SHA-256 de `scope|targetId|clé`, avec `scope=declaration` + `bookingId` et `scope=confirmation` + `paymentId`. Les hashes sont stockés dans des colonnes uniques ; un verrou pessimiste sur la réservation (déclaration) et sur le paiement (confirmation) sérialise les appels concurrents. Un même couple clé/cible renvoie toujours la même réponse, sans doublon ni second effet de bord.
- **Preuves** : stockage local dans `uploads/payment-proofs`, extensions `jpg/jpeg/png/webp/pdf`, 5 Mo maximum, nom UUID, contrôle anti path traversal ; le fichier est supprimé de façon best-effort si la transaction échoue après écriture.
- **Alerte 48 h** : `@EnableScheduling` + `@Scheduled(fixedDelayString=...)` toutes les heures ; les paiements `DECLARED` dont `declaredAt` est antérieur à `now - 48h` produisent un log `ALERTE_ADMIN PAYMENT_UNCONFIRMED_48H paymentId=… bookingId=…`, sans donnée personnelle. Aucune notification admin réelle avant l'Étape 7.
- **Sécurité** : `TENANT` pour déclarer, `OWNER` pour confirmer, avec vérification métier de l'appartenance de la réservation/annonce ; la lecture paginée est limitée au locataire, au propriétaire ou à un `ADMIN`.

### Critères d'acceptation
- [x] Déclarer un paiement → `201 Created`, statut `DECLARED`, `tenantDeclared=true`, `ownerConfirmed=false`
- [x] Le statut d'attente est maintenu tant que le propriétaire n'a pas confirmé : paiement `DECLARED` et réservation `DEPOSIT_PENDING` (interprétation explicite, voir D-006)
- [x] Confirmer (OWNER) → `200 OK`, statut `CONFIRMED` (uniquement si `tenantDeclared && ownerConfirmed`) et réservation `CONFIRMED`
- [x] Deux appels avec la même `Idempotency-Key` → pas de doublon, y compris en concurrence sur la vraie base PostgreSQL
- [x] `mvn clean test` passe (Étapes 1 à 6) : **105 tests, 0 échec, 0 erreur**, BUILD SUCCESS — dont 27 tests dédiés à l'Étape 6

---

## ÉTAPE 7 — Messagerie & Admin (TERMINÉE — validée par l'humain le 2026-11-24)

### Objectif
Communication temps réel, signalements, et back-office administrateur complet.

### Tâches
- [x] `MessageRepository`, `ReportRepository`, `AuditLogRepository` + entités `Message`, `Report`, `AuditLog`
- [x] Nouveaux enums `ReportReason`, `ReportStatus`, `AuditAction`
- [x] `MessagingController.java` :
    - [x] WebSocket brut `/ws/messages` (JWT au handshake, trames `SUBSCRIBE` / `MESSAGE`)
    - [x] Repli REST `POST /api/v1/messages` + `GET /api/v1/messages/booking/{bookingId}`
    - [x] Filtre des numéros de téléphone (regex) avant persistance **et** diffusion
- [x] `ReportController.java` :
    - [x] `POST /api/v1/reports` (motifs prédéfinis)
    - [x] Auto-masquage d'une annonce après 3 signalements **distincts** → `SUSPENDED`
- [x] `AdminController.java` (ROLE_ADMIN) :
    - [x] `GET /api/v1/admin/queue` (annonces `PENDING`, signalements `OPEN`, paiements déclarés depuis > 48 h)
    - [x] `POST /api/v1/admin/listings/{id}/validate` → `PUBLISHED`
    - [x] `POST /api/v1/admin/listings/{id}/refuse` (motif obligatoire) → `REFUSED`
    - [x] `GET /api/v1/admin/audit` (pagé, tri décroissant)
- [x] Journal d'audit automatique : AOP `@Audited` + `AuditAspect` + `AuditService`
- [x] Compte ADMIN initial bootstrapé par configuration (aucune inscription publique ADMIN)
- [x] Recherche publique limitée au statut `PUBLISHED` (visible par test sur la vraie base)
- [x] Tests : `MessagingServiceTest`, `ReportServiceTest`, `AdminServiceTest`, `AdminControllerTest`,
      `PhoneMaskingServiceTest`, `MessagingWebSocketHandlerTest`, `AuditAspectTest`, `ListingVisibilityTest`
- [x] Documentation mise à jour (`API_ENDPOINTS.md`, `DECISIONS.md`)

### Détails de l'Étape 7
- **WebSocket** : `/ws/messages` est un WebSocket brut JSON. Le handshake est autorisé par Spring Security puis le `MessagingWebSocketHandler` vérifie le JWT (query `token` ou en-tête `Authorization: Bearer`), charge l'utilisateur `ACTIVE` et ferme la session sinon (`1008`). `SUBSCRIBE` abonne une session à une réservation après contrôle de participation ; `MESSAGE` appelle le même `MessagingService` que le REST, puis le `MessageBroadcaster` diffuse à tous les abonnés de la réservation.
- **Téléphones** : `PhoneMaskingService` remplace les formats `+216…`, `216…`, `00216…` et les numéros locaux à 8 chiffres par `+216******`, avant `save` et avant diffusion. Le masquage s'applique donc aussi au repli REST.
- **Signalements** : motifs `FAKE_LISTING`, `INAPPROPRIATE_PHOTO`, `SCAM`, `OTHER`. Un signalement sur sa propre annonce est refusé. Le décompte porte sur les **signalants distincts** (`count(distinct reporter_id)`) : au seuil 3, l'annonce passe en `SUSPENDED` et une entrée d'audit système (acteur `null`) est écrite. Les doublons d'un même utilisateur ne comptent qu'une fois.
- **Back-office** : la file réunit les annonces `PENDING`, les signalements `OPEN` et les paiements `DECLARED` plus vieux que 48 h (même fenêtre que l'alerte de l'Étape 6). Validation/refus ne s'appliquent qu'à une annonce `PENDING` (`409 LISTING_STATUS_INVALID` sinon) ; le motif de refus est obligatoire et conservé dans les détails de l'audit.
- **Audit** : l'annotation `@Audited` (action, type de cible, expressions SpEL pour la cible et les détails) est interceptée par `AuditAspect`, qui journalise après le succès de la méthode avec l'acteur issu du `SecurityContext`. Les entrées contiennent date, acteur (ou `null` pour une action système), action, type/cible et détails. Aucune entité JPA n'est exposée : DTO dédiés partout.
- **Sécurité** : `/ws/messages` est permis au niveau HTTP pour le handshake uniquement ; `/api/v1/admin/**` exige `ROLE_ADMIN`, complété par un contrôle `ADMIN_ROLE_REQUIRED` dans `AdminService`. Le compte admin initial (`admin@logetd.local`) est créé au démarrage uniquement si `app.admin.bootstrap.enabled=true` et si l'email/le téléphone sont libres ; identifiants de développement à remplacer en production.

### Critères d'acceptation
- [x] Envoi message WebSocket → reçu en temps réel par les sessions abonnées (même service que le REST)
- [x] Message avec numéro de téléphone → filtré (`+216******`)
- [x] 3 signalements distincts sur une annonce → `SUSPENDED` automatique
- [x] Admin valide une annonce → `PUBLISHED`
- [x] Le journal d'audit contient les actions (date, acteur, action, cible)
- [x] `mvn clean test` passe (Étapes 1 à 7) : **145 tests, 0 échec, 0 erreur**, BUILD SUCCESS — dont 40 tests dédiés à l'Étape 7

---

## 🎯 Blocages en cours

| Étape | Raison | Décision attendue |
|---|---|---|
| (vide) | | |

---

## 📊 Journal de progression

| Étape | Date de début | Date de validation | Validé par |
|---|---|---|---|
| 1 | 2026-11-23 | 2026-11-23 | Humain |
| 2 | 2026-11-23 | 2026-11-23 | Humain |
| 3 | 2026-11-23 | 2026-11-23 | Humain |
| 4 | 2026-11-23 | 2026-11-23 | Humain |
| 5 | 2026-11-23 | 2026-11-24 | Humain |
| 6 | 2026-11-24 | 2026-11-24 | Humain (« passer ») |
| 7 | 2026-11-24 | 2026-11-24 | Humain (« passer ») |

### Opération transverse — organisation de la documentation (2026-11-24)

- [x] Créer `docs/` à la racine du dépôt avec les 5 documents communs Phase 1 / Phase 2.
- [x] Supprimer les 3 copies backend de `docs/01-specs/` après vérification de leur empreinte SHA-256.
- [x] Mettre à jour les trois README et ajouter les aiguillages dans `AGENT_1_PROMPT.md` et `00-context/PROJECT.md` ; décision enregistrée en **D-008**.
- Aucun code, aucune route et aucun test modifiés : la suite reste à **145 tests réussis**.

### Nettoyage des docs (D-009)

- [x] Fusionner `API_CONTRACT.md` dans `API_ENDPOINTS.md` et corriger le bloc `markdown` non fermé en tête de fichier.
- [x] Archiver les 3 versions antérieures dans `docs/archive/` avec un index des versions retenues.
- [x] Supprimer `docs/01-specs/` et `docs/02-architecture/` devenus vides ; ajouter les bandeaux « document historique ».

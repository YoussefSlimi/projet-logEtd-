# Journal des décisions

Format : une entrée par décision importante.
L'agent ajoute une entrée à chaque décision structurante.

---

## D-001 — Initialisation du backend (Étape 1)

- Date : 2026-11-23
- Étape : 1
- Décision :
  - Cibler **Java 17** (`<java.version>17</java.version>`) : c'est le JDK par défaut de la machine (`JAVA_HOME`) et l'une des versions autorisées par `PROJECT.md`. Le scaffold initial ciblait Java 25, ce qui empêchait la compilation avec l'environnement local.
  - Conserver le schéma généré par Hibernate (`ddl-auto=update`) pour toutes les tables, et n'utiliser **Flyway que pour la contrainte anti-chevauchement** (`V1__exclusion_constraint.sql`), conformément au rapport d'architecture.
  - Reporter la migration Flyway **après** la création du schéma Hibernate grâce à un `FlywayMigrationStrategy` neutre et un `ApplicationRunner` qui appelle `flyway.migrate()` : sans cela, le `ALTER TABLE bookings` échoue car la table n'existe pas encore. L'import est `org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy` (package Spring Boot 4).
  - Poser `spring.flyway.baseline-on-migrate=true` **et** `spring.flyway.baseline-version=0` : Hibernate ayant déjà créé les tables, Flyway refusait sinon de migrer un schéma non vide sans table d'historique (`FlywayException: Found non-empty schema(s) "public"`). Un baseline en version 0 laisse la migration `V1` s'appliquer normalement (un baseline en version 1 l'aurait ignorée).
  - Stocker les énumérations en **chaîne** (`@Enumerated(EnumType.STRING)`) et les montants en **millimes** (`Long`), jamais en `ORDINAL` ni en virgule flottante.
  - Concentrer les 9 énumérations dans `org.logetd.shared` et les 6 entités dans les packages `domain.*`, sans exposer d'entité via l'API.
  - Configurer PostgreSQL local (`jdbc:postgresql://localhost:5432/logetd`) dans `application.properties`.
- Alternatives écartées :
  - `ddl-auto=validate` / `create` : `validate` exigerait un schéma préexistant complet, `create` détruirait les données à chaque démarrage.
  - Définir la contrainte d'exclusion directement dans une annotation Hibernate : la portabilité et la lisibilité sont moindres qu'un fichier SQL versionné.
  - Passer en Java 25 : inutile pour l'Étape 1 et non compilable avec le `JAVA_HOME` courant.
- Impact :
  - Le démarrage crée les tables `users`, `listings`, `places`, `bookings`, `visits`, `payments`, puis applique la contrainte `no_overlap_active_booking` (extension `btree_gist`).
  - Point à valider par l'humain : le `pom.xml` garde la version Spring Boot **4.2.0** issue du scaffold initial, alors que `PROJECT.md` mentionne 3.5.x. Aucun changement de version n'a été fait pendant l'Étape 1 afin de ne pas casser les noms de starters générés ; à arbitrer avant l'Étape 2.

---

## D-002 — Authentification (Étape 2)

- Date : 2026-11-23
- Étape : 2
- Décision :
  - **jjwt 0.12.6** (`jjwt-api` + `jjwt-impl` + `jjwt-jackson`) pour la génération/validation des JWT : API moderne `Jwts.builder()/parser()`, signature HMAC via une clé secrète Base64 (`app.jwt.secret`). Secret de développement codé dans `application.properties` ; **à remplacer par une variable d'environnement en production**.
  - Deux types de tokens différenciés par la claim `type` : **access token** (TTL 900 s, claims `sub` = id utilisateur et `role`) et **refresh token** (TTL 604800 s). Le filtre `JwtAuthenticationFilter` n'accepte que les access tokens et vérifie que l'utilisateur est `ACTIVE`.
  - Le refresh token en clair n'est **jamais** stocké : seule son empreinte **SHA-256** (HexFormat) est persistée dans `refresh_tokens.token_hash` (unique). Le logout révoque par hash et est idempotent.
  - **BCrypt coût 12** (`new BCryptPasswordEncoder(12)`) pour le hash des mots de passe ; borne de validation 8–72 caractères (limite BCrypt).
  - Inscription limitée aux rôles `TENANT` et `OWNER` (le rôle `ADMIN` est refusé avec `REGISTER_ROLE_NOT_ALLOWED`) ; les comptes créés sont `ACTIVE`.
  - Login par email **ou** téléphone (`identifier`) avec un message d'erreur unique `INVALID_CREDENTIALS` pour ne pas révéler si le compte existe.
  - Couche **DTO** systématique (`RegisterRequest`, `LoginRequest`, `UserResponse`, `TokenResponse`) : aucune entité JPA n'est sérialisée par l'API, le champ `passwordHash` n'apparaît jamais dans une réponse.
  - Format d'erreur standard du contrat `{code, message, details, timestamp}` centralisé dans `GlobalExceptionHandler`, y compris pour les `401/403` émis par Spring Security (`UNAUTHENTICATED`, `ACCESS_DENIED`).
  - Dépendances ajoutées : `spring-boot-starter-security`, `spring-boot-starter-validation` et `spring-boot-starter-json` (Spring Boot 4 n'inclut pas Jackson dans le starter `webmvc`).
- Spécificités Spring Boot 4 rencontrées :
  - Jackson 3 : import `tools.jackson.databind.ObjectMapper` (et non `com.fasterxml.jackson.databind.ObjectMapper`).
  - Tests MockMvc : import `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (et non `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`).
- Alternatives écartées :
  - Stocker le refresh token en clair en base : risque de vol de session en cas de fuite de la base.
  - Réponse de login incluant l'entité `User` : exposerait le hash du mot de passe et violerait le contrat d'API.
  - Distinguer « email inconnu » et « mot de passe incorrect » : permettrait une énumération des comptes.
  - Downgrader vers Spring Boot 3.5.x : reporté, la décision D-001 reste à arbitrer par l'humain.
- Impact :
  - Routes publiques : `/api/v1/auth/**` ; toutes les autres routes exigent `Authorization: Bearer <access token>` et renvoient `401 UNAUTHENTICATED` sinon.
  - `mvn test` : 15 tests, 0 échec, 0 erreur (`AuthServiceTest` 8 tests unitaires, `AuthControllerTest` 6 tests MockMvc + 1 test de sécurité).
  - Point à valider par l'humain : le secret JWT de développement et l'absence de mécanisme de rotation/refresh automatique (le refresh token sert uniquement au logout pour l'instant).

---

## D-003 — Annonces, photos et places (Étape 3)

- Date : 2026-11-23
- Étape : 3
- Décision :
  - Ajout d'une **7ᵉ entité** `ListingPhoto` (table `listing_photos`) absente des 6 entités de l'Étape 1 mais nécessaire au dépôt des photos : `path`, `exterior`, `sortOrder`, `uploadedAt`. Conformément au rapport d'architecture, le binaire reste sur le **disque local** (`app.storage.photos-dir=uploads/photos`) et seul le nom de fichier généré est persisté.
  - `FileStorageService` génère un nom UUID, n'accepte que `jpg/jpeg/png/webp`, limite à 5 Mo par fichier et rejette tout nom de fichier résolvant hors du dossier configuré (path traversal). Les fichiers déjà écrits sont supprimés si une règle métier échoue ensuite.
  - Multipart `POST /listings/{id}/photos` : les drapeaux `exterior` sont transmis comme paramètres répétés **alignés par index** sur les fichiers. Règles : 3 photos minimum, 15 maximum (existante + nouvelles), au moins 1 extérieure — codes dédiés `PHOTO_COUNT_INVALID` / `PHOTO_EXTERIOR_REQUIRED` / `INVALID_PHOTO_TYPE` / `PHOTO_TOO_LARGE`.
  - Transition de statut : création en `DRAFT`, passage automatique en `PENDING` dès que l'annonce réunit ≥ 3 photos dont 1 extérieure et ≥ 1 place. Le passage en `PUBLISHED` est volontairement reporté à l'Étape 7 (validation administrateur).
  - **Double contrôle d'accès** : `hasRole("OWNER")` dans `SecurityConfig` pour les écritures, puis vérification `LISTING_NOT_OWNED` dans `ListingService` pour empêcher un propriétaire de modifier l'annonce d'un autre.
  - **Masquage de l'adresse exacte** : `GET /listings/{id}` renvoie `exactAddress: null` pour tout utilisateur autre que le propriétaire ou un `ADMIN` (mesure de lutte contre la fuite de données avant visite).
  - Aucune entité JPA exposée : DTO `ListingRequest`, `ListingResponse`, `PlaceRequest`, `PlaceResponse` et `PhotoResponse` ; les montants restent des `Long` en millimes.
  - Tests MockMvc sans dépendance supplémentaire : de **vrais access tokens JWT** sont générés par `JwtService` et le `UserRepository` est mocké pour simuler le propriétaire/le locataire — le filtre `JwtAuthenticationFilter` et `hasRole` sont donc réellement exercés.
- Alternatives écartées :
  - Stocker les photos en BLOB dans PostgreSQL : alourdit la base et complique la substitution par un stockage objet (S3) plus tard.
  - Faire confiance uniquement à `hasRole("OWNER")` : n'empêcherait pas un propriétaire d'agir sur l'annonce d'un autre.
  - Exposer `Listing`/`Place` directement : violerait la convention « aucune entité JPA dans l'API » et risquerait de fuir l'adresse exacte et les données du propriétaire.
  - Ajouter `spring-security-test` : inutile, les JWT réels couvrent mieux le chemin de sécurité complet.
- Impact :
  - Table `listing_photos` créée et vérifiée dans PostgreSQL ; répertoire `uploads/photos` utilisé au premier upload.
  - `mvn clean compile` puis `mvn test` : **31 tests, 0 échec, 0 erreur** (`AuthServiceTest` 8, `AuthControllerTest` 6, `ListingServiceTest` 9, `ListingControllerTest` 7, `LogEtdApplicationTests` 1).
  - Point à valider par l'humain : le contrat d'upload (parties `files` + paramètres `exterior` alignés) et le passage automatique `DRAFT` → `PENDING`, qui seront à réconcilier avec la future validation admin de l'Étape 7.

---

## D-004 — Recherche, projection et coût réel (Étape 4)

- Date : 2026-11-23
- Étape : 4
- Décision :
  - **JPA `Specification`** (via `JpaSpecificationExecutor`) plutôt que des requêtes dérivées combinées : les critères sont optionnels et hétérogènes (texte, booléens, intervalle, enum), la composition dynamique reste lisible et testable. `city` en égalité `lower()`, `district` en `LIKE`, budget appliqué sur `places.monthlyRentMillimes` avec `query.distinct(true)` pour ne pas dupliquer les annonces.
  - **Projection en 4 requêtes au plus** au lieu du chargement des relations en cascade : 1 page d'annonces, 1 `findByListingIdIn...` pour les places, 1 pour les photos, 1 pour les places réservées ; assemblage en mémoire dans `ListingSummaryDTO` / `PlaceSummaryDTO`. Aucune entité JPA ni adresse exacte n'est exposée.
  - **Coût réel mensuel** = loyer + charges + `estimationsMillimes` (nouveau champ `Long` sur `Place`, défaut 0). `estimations` (texte libre) reste conservé pour l'affichage. `monthlyCostFromMillimes` est le minimum des coûts réels des places disponibles d'une annonce. Les constructeurs DTO de l'Étape 3 sont conservés pour ne pas casser les tests existants.
  - **Exclusion des réservations** : seules les places avec une réservation active (`ACCEPTED`, `DEPOSIT_PENDING`, `CONFIRMED`, `IN_PROGRESS`) et dont l'intervalle chevauche la période demandée (`start < end` ET `end > start`) sont retirées. Les statuts `CANCELLED`, `REFUSED`, `EXPIRED`, `CLOSED`, `REQUESTED` ne bloquent pas une place. Sans période fournie, aucune exclusion n'est appliquée. `BookingRepository` est introduit ici (réutilisé à l'Étape 5).
  - **Visibilité transitoire** : le filtre admet `PUBLISHED` **et** `PENDING` jusqu'à l'Étape 7 (validation admin) — sinon aucune annonce de démonstration ne serait retournée. À réévaluer quand le back-office existera.
  - **Validation explicite des paramètres** : `PAGE_SIZE_TOO_LARGE`, `INVALID_PAGE_SIZE`, `INVALID_PAGE`, `INVALID_BUDGET_RANGE`, `INVALID_PERIOD` — tous `400` avec le format d'erreur standard (pas de `500` sur une entrée utilisateur).
- Alternatives écartées :
  - `@Query` JPQL figé avec de multiples `(:param is null or ...)` : moins lisible et plus difficile à faire évoluer qu'une `Specification`.
  - Chargement `EAGER` des photos/places puis filtrage Java : risque de N+1 et de fuite de l'adresse exacte via les entités.
  - Exclure les places réservées dans la `Specification` elle-même : la période est optionnelle et le calcul de coût porte sur les places restantes ; le filtrage après récupération groupée est plus simple et reste en 4 requêtes.
  - Filtrer uniquement `PUBLISHED` dès maintenant : rendrait la recherche vide avant l'Étape 7.
- Impact :
  - `mvn test` (Étapes 1 à 4) : **41 tests, 0 échec, 0 erreur** — `ListingSearchServiceTest` 7 et `ListingSearchControllerTest` 3 ajoutés.
  - Endpoint `GET /api/v1/listings` désormais implémenté et documenté dans `API_ENDPOINTS.md`.
  - Point à valider par l'humain : la limitation de la recherche au loyer (et non au coût réel) pour `budgetMin/budgetMax`, et l'inclusion temporaire des annonces `PENDING`.

---

## D-005 — Visites, réservations et concurrence (Étape 5)

- Date : 2026-11-23
- Étape : 5
- Décision :
   - **Code de preuve de visite** : à la confirmation par le propriétaire, `VisitService` génère un code à 6 chiffres avec `SecureRandom` et ne le renvoie que dans la réponse de `POST /visits/{id}/confirm`. Le code est comparé avec `MessageDigest.isEqual` (temps constant) puis **mis à `null`** dès que l'étudiant le présente avec succès : un code ne peut servir qu'une fois. La position (`proof_latitude`, `proof_longitude`, nouvelles colonnes de `visits` créées par `ddl-auto=update`) est facultative et enregistrée avec `provenAt`. Le statut évolue `REQUESTED → CONFIRMED → PROVEN`.
   - **Anti-chevauchement en deux couches** : la contrainte SQL `no_overlap_active_booking` (V1) ne couvre pas `REQUESTED`. La création prend donc un **verrou pessimiste** sur la place (`Place.findByIdForUpdate`) et vérifie `existsOverlapping` sur les statuts bloquants `REQUESTED`, `ACCEPTED`, `DEPOSIT_PENDING`, `CONFIRMED`, `IN_PROGRESS` : les deux demandes concurrentes sont sérialisées et la seconde reçoit un `409` immédiat. Les transitions de statut utilisent `saveAndFlush` pour que la contrainte d'exclusion PostgreSQL joue également en cas de course entre deux acceptations.
   - **Bornes de dates inclusives** : la contrainte Flyway utilise `daterange(start_date, end_date, '[]')`, donc la recherche de l'Étape 4 et le pré-contrôle `existsOverlapping` passent de `start < end / end > start` à `start <= end / end >= start`. Cela corrige D-004 : une réservation se terminant le même jour qu'une autre en commence est désormais considérée comme chevauchante, comme en base.
   - **Conflits DB traduits en 409, jamais 500** : `DataIntegrityViolationException` (violation d'exclusion `SQLSTATE 23P01`) **et** `CannotAcquireLockException` (deadlock `SQLSTATE 40P01` observé lors de deux acceptations concurrentes sur la contrainte d'exclusion) sont transformés en `409 BOOKING_DATES_UNAVAILABLE`, à la fois dans `BookingService` et dans `GlobalExceptionHandler`. Le test `BookingConcurrencyTest` interdit explicitement tout résultat inattendu (`UNEXPECTED:*`).
   - **Transitions explicites** : la chaîne `REQUESTED → ACCEPTED → DEPOSIT_PENDING → CONFIRMED` est exposée par trois endpoints distincts (`/accept`, `/deposit-pending`, `/confirm`) plutôt qu'un unique endpoint générique : chaque appel vérifie le statut attendu (`409 BOOKING_STATUS_INVALID` sinon) et la propriété (`403 BOOKING_NOT_OWNED`). Le versement lui-même reste à l'Étape 6 ; `depositMillimes` est facultatif et vaut `0` par défaut.
   - **Sécurité en deux niveaux** (comme à l'Étape 3) : `hasRole` dans `SecurityConfig` puis vérification de la relation propriétaire/locataire dans les services. Aucune entité JPA n'est exposée ; les DTO `VisitResponse` / `BookingResponse` ne renvoient le `proofCode` qu'au propriétaire au moment de la confirmation.
- Alternatives écartées :
   - Se fier uniquement à la contrainte PostgreSQL : `REQUESTED` n'y figure pas, deux demandes concurrentes pouvaient être acceptées avant toute transition ; le verrou + pré-contrôle comble ce vide.
   - Laisser remonter la violation SQL en `500` : inacceptable pour un cas métier attendu (deux étudiants visent la même place) ; le contrat exige un `409` stable.
   - Conserver le code de preuve indéfiniment ou le renvoyer dans toutes les réponses : risque de réutilisation et de fuite ; suppression après usage et exposition unique au propriétaire.
   - Un seul endpoint de changement de statut avec un corps libre : validation plus faible et transitions non testables individuellement.
   - Ajouter une nouvelle migration Flyway : inutile, les champs ajoutés sont créés par Hibernate et la contrainte V1 existe déjà.
- Impact :
   - `mvn clean test` (Étapes 1 à 5) : **78 tests, 0 échec, 0 erreur**, BUILD SUCCESS — nouveaux : `VisitServiceTest` 11, `BookingServiceTest` 12, `VisitControllerTest` 6, `BookingControllerTest` 6, `BookingConcurrencyTest` 2 (vrais threads + vraie PostgreSQL, conflits `23P01` et `40P01` observés puis traduits en 409).
   - Routes documentées dans `API_ENDPOINTS.md` ; colonnes `proof_latitude` / `proof_longitude` ajoutées à `visits`.
   - Point à valider par l'humain : la traduction large de tout `CannotAcquireLockException` en `BOOKING_DATES_UNAVAILABLE`, l'absence d'expiration automatique du code de preuve (statut `EXPIRED` non implémenté), les trois endpoints de transition supplémentaires, l'acompte à `0` par défaut et l'absence de paiement effectif avant l'Étape 6.

---

## D-006 — Paiements, idempotence et alerte 48 h (Étape 6)

- Date : 2026-11-24
- Étape : 6
- Décision :
    - **Idempotence par hash stocké** : `Idempotency-Key` est obligatoire sur `POST /payments` et `POST /payments/{id}/confirm`. Le service calcule SHA-256 de `scope|targetId|clé` (`declaration` + `bookingId`, `confirmation` + `paymentId`) et persiste le résultat dans `declaration_idempotency_hash` / `confirmation_idempotency_hash` (colonnes uniques créées par `ddl-auto=update`). Un appel identique renvoie la réponse déjà calculée ; la contrainte unique sert de filet de sécurité, pas de mécanisme principal. Des verrous pessimistes sur la réservation puis sur le paiement sérialisent les courses concurrentes : `PaymentIdempotencyTest` vérifie deux appels séquentiels **et** deux appels simultanés sur la vraie PostgreSQL.
    - **Double validation** : la déclaration étudiante crée un paiement `DECLARED` (`tenantDeclared=true`, `ownerConfirmed=false`, `declaredAt` renseigné) et fait passer la réservation `ACCEPTED → DEPOSIT_PENDING`. La confirmation propriétaire pose `ownerConfirmed=true` et `confirmedAt` ; `status = CONFIRMED` seulement si `tenantDeclared && ownerConfirmed`, puis la réservation passe `DEPOSIT_PENDING → CONFIRMED`. Une confirmation sans déclaration est refusée (`409 PAYMENT_NOT_DECLARED`).
    - **Interprétation du statut d'attente** : la formulation « le statut reste PENDING tant que le propriétaire n'a pas confirmé » est comprise comme **le maintien de l'état d'attente de la réservation** (`DEPOSIT_PENDING`) pendant que le **paiement est `DECLARED`**. `PaymentStatus.PENDING` reste réservé à un paiement non déclaré et n'est pas produit par le flux normal ; le rendre explicite évite d'écraser l'information `tenantDeclared=true`.
    - **Preuve de paiement locale** : `PaymentProofStorageService` écrit le binaire dans `app.storage.payment-proofs-dir=uploads/payment-proofs`, accepte `jpg/jpeg/png/webp/pdf`, limite à 5 Mo (`app.storage.max-payment-proof-size-bytes=5242880`), génère un nom UUID et ne persiste que ce nom dans `Payment.proofPath`. Un contrôle `startsWith(directory)` bloque le path traversal et la suppression est best-effort si une règle métier échoue après l'écriture. Aucun endpoint de téléchargement de preuve n'est ajouté à cette étape.
    - **Alerte 48 h en journal uniquement** : `@EnableScheduling` + `PaymentAlertScheduler` (`fixedDelayString=${app.payment.alert-check-interval-ms:3600000}`, valeur par défaut 1 h) interroge `status = DECLARED` et `declaredAt < now - 48h`, puis journalise `ALERTE_ADMIN PAYMENT_UNCONFIRMED_48H paymentId=… bookingId=…` en `WARN`. Seuls des identifiants techniques apparaissent dans les logs (convention « aucune donnée personnelle »). Aucun e-mail, WebSocket ou enregistrement d'alerte en base : la notification admin réelle est repoussée à l'Étape 7, qui introduit le back-office.
    - **Erreurs d'entrée cadrées** : en plus des codes métier, `GlobalExceptionHandler` traduit `MissingServletRequestParameterException`, `MissingServletRequestPartException` et `MethodArgumentTypeMismatchException` en `400 VALIDATION_ERROR` ; une clé absente donne `400 IDEMPOTENCY_KEY_MISSING` et une clé trop longue `400 IDEMPOTENCY_KEY_INVALID`. Aucune entrée utilisateur ne doit produire un `500`.
    - **Sécurité en deux niveaux** (cohérent avec D-003/D-005) : `hasRole("TENANT")` à la déclaration, `hasRole("OWNER")` à la confirmation, puis vérification de la relation réservation ↔ locataire/propriétaire dans `PaymentService` (`PAYMENT_NOT_FOR_TENANT`, `PAYMENT_NOT_OWNED`). `GET /payments/booking/{bookingId}` est limité aux parties prenantes ou à un `ADMIN`. Aucune entité JPA n'est exposée : DTO `PaymentDeclaration`, `PaymentResponse`, `PaymentPageResponse`.
- Alternatives écartées :
    - Table dédiée `idempotency_keys` avec politique d'expiration : rajouterait une table et un nettoyage à gérer ; le hash sur le paiement suffit ici car la portée est toujours une ressource métier existante.
    - Se fier à l'unicité SQL seule : la seconde requête lèverait `DataIntegrityViolationException` (500) au lieu de renvoyer la réponse initiale ; le contrôle applicatif précède l'insert et la contrainte ne reste qu'un filet.
    - Conserver `PaymentStatus.PENDING` après déclaration : perdrait la distinction entre « rien n'a été déclaré » et « déclaré mais pas confirmé / en alerte 48 h ».
    - Stocker la preuve en BLOB PostgreSQL : alourdirait la base et rendrait difficile la migration future vers un stockage objet, comme pour les photos (D-003).
    - Envoyer une vraie notification admin dès maintenant : aucun canal/admin back-office n'existe avant l'Étape 7 ; un log horodaté avec identifiants techniques est vérifiable immédiatement sans dépendance externe.
- Impact :
    - Nouveaux fichiers : `PaymentRepository`, `PaymentProofStorageService`, `PaymentService`, `PaymentController`, `PaymentAlertScheduler`, DTOs `PaymentDeclaration`/`PaymentResponse`/`PaymentPageResponse`, 4 classes de test dédiées (27 tests).
    - `Payment` gagne `proof_path`, `declared_at`, `confirmed_at`, `declaration_idempotency_hash` et `confirmation_idempotency_hash` (colonnes créées par Hibernate, aucune migration Flyway supplémentaire). `@EnableScheduling` est ajouté à `LogEtdApplication`.
    - `mvn clean test` (Étapes 1 à 6) : **105 tests, 0 échec, 0 erreur**, BUILD SUCCESS. Routes documentées dans `API_ENDPOINTS.md`.
    - Point à valider par l'humain : l'interprétation du « statut PENDING » comme état d'attente de la réservation, l'obligation de `Idempotency-Key` également sur la confirmation, le stockage local des preuves et l'alerte 48 h limitée à un log.

---

## D-007 — Messagerie, signalements et back-office (Étape 7)

- Date : 2026-11-24
- Étape : 7
- Décision :
  - **Dépendances Boot 4** : ajout de `spring-boot-starter-websocket` et de `spring-boot-starter-aspectj`. Le starter `spring-boot-starter-aop` n'existe plus dans le BOM Spring Boot 4.2.0 (Maven refusait une version manquante) : c'est `spring-boot-starter-aspectj` qui fournit `spring-aop`, AspectJ Weaver et l'auto-configuration AOP.
  - **WebSocket brut plutôt que STOMP** : `/ws/messages` échange des trames JSON (`SUBSCRIBE` puis `MESSAGE`) sans broker STOMP/SockJS, suffisant pour la messagerie demandée. Le handshake est `permitAll` côté Spring Security car un client WebSocket natif ne peut pas toujours poser d'en-tête `Authorization` : le `MessagingWebSocketHandler` vérifie ensuite le JWT (paramètre `token` ou en-tête Bearer), exige un **access token** d'utilisateur `ACTIVE`, puis ferme la session avec `1008` sinon. `MessageBroadcaster` maintient les sessions par `bookingId` et diffuse la même trame à tous les abonnés.
  - **Un seul chemin métier** : la trame WebSocket `MESSAGE` et `POST /api/v1/messages` appellent le même `MessagingService.send` (participation, filtrage, persistance, broadcast). Le REST n'est pas une simple file « offline » : il notifie lui aussi les abonnés, ce qui évite deux règles divergentes.
  - **Masquage conservatif des téléphones** : `PhoneMaskingService` remplace les formats tunisiens internationaux (`+216`, `216`, `00216`, avec séparateurs) et les numéros locaux à 8 chiffres commençant par 2/4/5/9 par `+216******`, **avant** persistance et **avant** diffusion. Les séparateurs ne sont consommés que s'ils précèdent un chiffre, afin de ne pas avaler l'espace suivant le numéro. Le masquage s'applique aussi au corps renvoyé au client : aucun numéro en clair ne transite par l'API ni par la base.
  - **Suspension après 3 signalants distincts** : `ReportRepository.countDistinctReportersByListingId` compte `count(distinct reporter_id)`. Au seuil 3, `Listing.status` passe automatiquement en `SUSPENDED` et une entrée d'audit système est écrite avec `actorId = null`. Un même utilisateur peut signaler plusieurs fois mais ne compte qu'une fois ; signaler sa propre annonce est interdit. Les motifs sont limités à l'enum `ReportReason`.
  - **Audit par AOP `@Audited`** : l'annotation porte l'action, le type de cible et des expressions SpEL pour l'identifiant (`#result.id()`) et les détails (`#args[2].reason()`). `AuditAspect` journalise **après** le succès de la méthode avec l'acteur issu du `SecurityContext` (ou `null` pour une action système) ; une cible introuvable lève une erreur explicite plutôt que d'écrire une entrée incomplète. `@Transactional` sur `AuditService.log` permet de joindre la transaction appelante. Le motif de refus est conservé dans les détails de l'audit sans code spécifique dans le contrôleur.
  - **Back-office minimal** : `GET /admin/queue` réunit les annonces `PENDING`, les signalements `OPEN` et les paiements `DECLARED` vieux de plus de 48 h (même fenêtre que le job de l'Étape 6, sans nouvel e-mail). La validation ne concerne que `PENDING` (`409 LISTING_STATUS_INVALID` sinon) et horodate `lastConfirmedAt` ; le refus exige un motif. `ROLE_ADMIN` est imposé par `SecurityConfig` et revérifié dans `AdminService`.
  - **Compte ADMIN bootstrapé** : pas d'inscription publique ADMIN (D-002 maintenu). `AdminBootstrap` crée au démarrage un administrateur uniquement si `app.admin.bootstrap.enabled=true` et si l'email et le téléphone configurés sont libres. Les valeurs de `application.properties` (`admin@logetd.local`, `+21600000000`, `Admin123!`) sont des identifiants de développement à remplacer par des variables d'environnement en production.
  - **Visibilité resserrée** : `ListingSearchService` n'expose plus que `PUBLISHED`. Le statut `PENDING` reste visible pour son propriétaire et l'admin via `GET /listings/{id}`, mais disparaît de la recherche publique ; la note transitoire de D-004 est donc supprimée et `ListingVisibilityTest` vérifie la nouvelle règle sur la vraie PostgreSQL.
  - **Nouvelles tables** : `messages`, `reports`, `audit_logs` sont créées par Hibernate (`ddl-auto=update`) comme les tables précédentes ; aucune migration Flyway supplémentaire n'est nécessaire. Les enums restent stockés en chaîne. Aucune entité JPA n'est sérialisée : DTO `MessageResponse`, `ReportResponse`, `AdminQueueResponse`, `AuditLogPageResponse`, etc.
- Alternatives écartées :
  - STOMP + broker en mémoire : ajoute une couche de protocole et un courtier à configurer pour un usage simple ; le JSON brut suffit et reste testable avec une session mockée.
  - Faire confiance au seul filtre HTTP pour le WebSocket : les clients natifs ne peuvent pas toujours envoyer d'en-tête au handshake ; l'authentification est donc refaite dans le handler après ouverture, avec fermeture explicite en cas d'échec.
  - Appliquer le filtre téléphone uniquement à l'affichage : les numéros resteraient en clair en base et seraient diffusés aux autres participants pendant la fenêtre de persistance.
  - Compter tous les signalements (ou dédupliquer par annonce) : un usage répété par un seul utilisateur pourrait suspendre une annonce ; le décompte par signalant distinct est la règle demandée.
  - `@EntityListener` pour l'audit : il ne voit pas l'acteur du `SecurityContext` et ne permet pas de conserver le motif d'un refus sans annoter l'entité ; l'AOP au niveau service est plus expressif.
  - Inscription publique d'un ADMIN : risque d'élévation de privilèges ; le bootstrap local est conditionné par une propriété et l'unicité email/téléphone.
  - Garder `PENDING` visible dans la recherche : la validation admin n'aurait aucun effet sur la visibilité et l'Étape 7 perdrait son sens.
- Impact :
  - Nouveaux fichiers : entités/repositories `Message`, `Report`, `AuditLog` ; enums `ReportReason`, `ReportStatus`, `AuditAction`, `Audited` ; services `MessagingService`, `ReportService`, `AdminService`, `AuditService`, `PhoneMaskingService`, `MessageBroadcaster` ; `MessagingWebSocketHandler`, `WebSocketConfig`, `AuditAspect`, `AdminBootstrap` ; contrôleurs `MessagingController`, `ReportController`, `AdminController` ; 11 DTO ; 8 classes de test dédiées (40 tests).
  - `SecurityConfig` : `/ws/messages` est permis au niveau HTTP pour le handshake, `/api/v1/admin/**` exige `ROLE_ADMIN` ; les routes messages/signalements restent authentifiées avec contrôle métier de participation.
  - Suite ciblée Étape 7 : **40 tests, 0 échec, 0 erreur**, BUILD SUCCESS. Suite complète Étapes 1 à 7 : **145 tests, 0 échec, 0 erreur**, BUILD SUCCESS (`mvn clean test`).
  - Validation humaine : **validée le 2026-11-24** via « passer ». Les points soumis à validation — protocole WebSocket (trames `SUBSCRIBE`/`MESSAGE` et query `token`), accès des ADMIN aux messages d'une réservation, masquage des numéros locaux à 8 chiffres, seuil de 3 signalants distincts, compte ADMIN bootstrapé et retrait des annonces `PENDING` de la recherche publique — sont acceptés en l'état.

---

## D-008 — Organisation de la documentation commune (transverse, hors étape d'implémentation)

- Date : 2026-11-24
- Étape : transverse — aucun changement de code ni d'API
- Décision :
  - Créer `docs/` à la racine du dépôt comme **source de vérité unique** des documents lus par les deux agents : 3 fichiers Phase 1 (`.docx`) et 2 fichiers Phase 2 (`.html`).
  - Supprimer les 3 copies présentes dans `backendWeb/logEtd/docs/01-specs/` après vérification de leur empreinte SHA-256 (identiques aux originaux). Le dossier a ensuite été entièrement retiré (voir D-009).
  - Laisser les documents spécifiques dans leur projet : `backendWeb/logEtd/docs/` (contexte, architecture, conventions, tâches, API, décisions, Postman, prompt agent) et `frontendWeb/docs/FRONT_TASKS.md` (journal + rapports Hallmark). Les tokens Hallmark restent dans `frontendWeb/src/styles/`.
  - Mettre à jour les trois README et ajouter un aiguillage dans `AGENT_1_PROMPT.md` et `00-context/PROJECT.md` pour référencer `docs/` et rappeler la règle « ne pas dupliquer ».
- Alternatives écartées :
  - Conserver une copie des specs dans chaque projet : risque de divergence (une copie modifiée, l'autre lue par l'agent).
  - Déplacer `API_ENDPOINTS.md` et la collection Postman dans `docs/` : ces artefacts sont produits et maintenus par le backend ; ils restent dans `backendWeb/logEtd/docs/` et sont consommés par le frontend par référence.
- Impact :
  - Aucun changement de code, de route ni de test : `API_ENDPOINTS.md` et la suite de 145 tests restent inchangés.

---

## D-009 — Nettoyage des docs et fusion du contrat d'API (transverse)

- Date : 2026-11-24
- Étape : transverse — aucun changement de code ni d'API
- Décision :
  - Fusionner les 4 règles générales de `02-architecture/API_CONTRACT.md` (518 octets, aucune route) dans `05-api/API_ENDPOINTS.md` sous `## Règles générales`, puis supprimer ce fichier et le dossier `02-architecture/` devenu vide.
  - Corriger `API_ENDPOINTS.md` : retrait de la note de travail d'intégration et du bloc ```` ```markdown ```` non fermé en tête de fichier, qui faisait rendre tout le contrat comme un bloc de code sur GitHub.
  - Déplacer les 3 documents antérieurs (2 specs Phase 1 du 16 nov, rapport Phase 2 du 16 nov) dans `docs/archive/` avec un index indiquant la version réellement implémentée. Ces fichiers ne sont pas des doublons (SHA-256 différents) mais des versions remplacées.
  - Supprimer `backendWeb/logEtd/docs/01-specs/README.md` et le dossier vide ; mettre à jour les README et ajouter les bandeaux « document historique » dans `AGENT_1_PROMPT.md` et `00-context/PROJECT.md`.
- Alternatives écartées :
  - Laisser `API_CONTRACT.md` : contenu redondant avec `CONVENTIONS.md` et jamais consulté hors du prompt de l'Étape 1.
  - Supprimer purement les anciennes versions : l'option B retenue conserve la traçabilité de l'évolution du cahier des charges.
- Impact :
  - Aucun changement de code, de route ni de test : la suite reste à 145 tests réussis.
  - `API_ENDPOINTS.md` reste la seule source de vérité de l'API et s'affiche désormais correctement sur GitHub.
  - Le contrat d'API et les décisions D-001 à D-007 restent en place ; seule l'organisation des fichiers de spécification évolue.
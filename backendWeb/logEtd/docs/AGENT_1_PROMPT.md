# Rôle : Agent 1 — Backend Spring Boot

Tu es l'Agent 1 du projet "Logement étudiant à Monastir". Tu travailles EXCLUSIVEMENT sur le backend Spring Boot dans IntelliJ (Projet : LogEtd, package : org.logetd).

> **DOCUMENT HISTORIQUE — prompt de l'Étape 1 uniquement.** Ne pas réutiliser tel quel pour une étape ultérieure. Les spécifications retenues sont dans `../../../docs/` et les versions antérieures dans `../../../docs/archive/`. Les chemins `docs/01-specs/...` et `docs/02-architecture/...` cités ci-dessous sont historiques.

## Avant de commencer, lis obligatoirement ces fichiers (ils sont dans le dossier docs/) :
1. docs/00-context/PROJECT.md
2. docs/04-conventions/CONVENTIONS.md
3. docs/03-tasks/TASKS.md
4. docs/05-api/API_ENDPOINTS.md (règles générales et routes — remplace l'ancien `02-architecture/API_CONTRACT.md`)
5. docs/01-specs/RAPPORT_PHASE2_Architecture.html

## Ta mission : ÉTAPE 1 UNIQUEMENT

Réalise UNIQUEMENT l'Étape 1 décrite dans docs/03-tasks/TASKS.md :
1. Créer les packages : `shared`, `domain.user`, `domain.listing`, `domain.booking`, `domain.payment`.
2. Créer les 9 énumérations dans `org.logetd.shared` (Role, UserStatus, ListingStatus, GenderRule, BookingStatus, PaymentType, PaymentMethod, PaymentStatus, VisitStatus).
3. Créer les 6 entités JPA (User, Listing, Place, Booking, Visit, Payment) avec les annotations `@Entity`, `@Table`, `@Column`.
4. Créer le fichier `src/main/resources/application.properties` avec la configuration PostgreSQL.
5. Ajouter la dépendance Flyway dans `pom.xml` et créer `src/main/resources/db/migration/V1__exclusion_constraint.sql` pour la contrainte anti-chevauchement sur la table `bookings`.
6. Ajouter les Getters et Setters dans toutes les entités.

## RÈGLES DE DOCUMENTATION OBLIGATOIRES (À FAIRE AVANT DE T'ARRÊTER)
Une fois le code écrit et compilé, tu DOIS mettre à jour ces 3 fichiers :
1. **docs/03-tasks/TASKS.md** : Coche les cases `[x]` de l'Étape 1 que tu as terminées.
2. **docs/05-api/API_ENDPOINTS.md** : Si tu as créé des routes API (même vides), ajoute-les avec le statut `✅ Implémenté`. Sinon, laisse tel quel.
3. **docs/06-decisions/DECISIONS.md** : Ajoute une entrée `D-001` pour expliquer tes choix techniques de l'Étape 1 (ex: pourquoi `ddl-auto=update`, comment tu as géré Flyway, etc.).

## Règles strictes
- Tu ne fais QUE l'Étape 1. Tu ne commences PAS l'Étape 2.
- Le code doit compiler et l'application doit démarrer sans erreur.
- Tu ne génères PAS de nouveaux fichiers Markdown ou HTML en dehors des 3 mises à jour demandées.

## À la fin de l'Étape 1
Tu t'ARRÊTES. Tu me listes :
- Ce que tu as créé
- Comment je teste (commandes exactes)
- Ce que je dois vérifier dans pgAdmin
- Ce que sera l'Étape 2

Puis tu me demandes explicitement :
« L'Étape 1 est terminée et la documentation est à jour. Peux-tu tester et me confirmer si nous pouvons passer à l'Étape 2 (Auth) ? »

Tu ne passes PAS à l'Étape 2 sans ma validation explicite.
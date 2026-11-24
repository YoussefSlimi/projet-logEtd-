# Projet : Logement étudiant à Monastir — Backend Spring Boot

> **Document historique — contexte initial du projet.** Certaines valeurs ci-dessous sont périmées : la version retenue est **Spring Boot 4.2.0** et non 3.5.x (voir D-001). Les spécifications font foi dans `../../../docs/`.

## Vision
Backend de la plateforme de location de logements pour étudiants de Monastir.
Projet éducatif, jury de professeurs. Développement multi-agents, étape par étape.

## Contraintes non négociables
- Spring Boot 3.5.x, Java 17 (ou 25), Maven
- PostgreSQL (création automatique des tables via `ddl-auto=update`)
- Architecture monolithe modulaire (packages stricts)
- Une seule ville : Monastir (configurable)
- Un seul public : étudiants
- Paiement hors plateforme avec double confirmation
- Outils 100% gratuits
- Développement dans IntelliJ IDEA

## Règle d'or
À tout moment, il existe une version qui compile.
L'agent réalise UNE étape à la fois et s'arrête pour validation humaine.

## Architecture du projet (IntelliJ)
- Nom du projet : LogEtd
- Package racine : `org.logetd`
- Dossier des sources : `src/main/java/org/logetd/`
- Dossier des ressources : `src/main/resources/`

### Structure des packages cibles
- `org.logetd.config` : Configuration (Security, JPA, etc.)
- `org.logetd.shared` : Énumérations et classes communes
- `org.logetd.domain.user` : Entités User
- `org.logetd.domain.listing` : Entités Listing, Place, Photo
- `org.logetd.domain.booking` : Entités Booking, Visit
- `org.logetd.domain.payment` : Entités Payment
- `org.logetd.domain.messaging` : Entités Message
- `org.logetd.domain.admin` : Entités Report, AuditLog

## Documents de référence (à lire obligatoirement)

> Les spécifications communes sont centralisées dans `../../../docs/` à la racine du dépôt ; les noms ci-dessous sont historiques.

- docs/01-specs/PHASE1_Etudiants_Monastir.docx
- docs/01-specs/RAPPORT_PHASE2_Fonctionnel.html
- docs/01-specs/RAPPORT_PHASE2_Architecture.html
# Documentation commune — logEtd

Ce dossier contient les documents **partagés par le backend et le frontend**. C'est la **source de vérité unique** : ne dupliquez pas ces fichiers dans `backendWeb/` ni dans `frontendWeb/`.

## Documents retenus (implémentés)

| Fichier | Contenu |
|---|---|
| `Specifications_Phase1_Etudiants_Monastir (1).docx` | Cahier des charges Phase 1 retenu — version recentrée du 17 nov 2026 : Monastir, étudiants, 0 DT. |
| `RAPPORT_PHASE2_Architecture_MAJ.html` | Architecture Phase 2 retenue — version du 23 nov 2026, alignée sur LogEtd / IntelliJ / Spring Boot. |

## Versions antérieures — `archive/`

| Fichier | Pourquoi il est archivé |
|---|---|
| `archive/Specifications_Phase1_Plateforme_Location.docx` | 16 nov 2026 : 3 villes, 3 segments de clientèle. |
| `archive/Specifications_Phase1_Version_Finale.docx` | 16 nov 2026 : version consolidée avant le recentrage. |
| `archive/PHASE 2 — ASPECTS TECHNIQUES (ARCHITECTURE & IMPLÉMENTATION).html` | 16 nov 2026 : rapport technique large, remplacé par la version du 23 nov. |

Ces fichiers ne sont pas des doublons (empreintes SHA-256 différentes) mais des versions **remplacées**. Ils sont conservés pour la traçabilité de l'évolution du cahier des charges (option B). **Ne pas les utiliser comme référence d'implémentation.**

## Documentation spécifique — ne pas déplacer ici

| Dossier | Propriétaire | Contenu |
|---|---|---|
| `backendWeb/logEtd/docs/` | Agent backend | Contexte, conventions, tâches, contrat API (`05-api/API_ENDPOINTS.md`), décisions (`06-decisions/DECISIONS.md`), collection Postman, prompt de l'agent |
| `frontendWeb/docs/` | Agent frontend | Journal `FRONT_TASKS.md` et rapports de conformité Hallmark |
| `frontendWeb/src/styles/` | Skill Hallmark | `tokens.css`, `base.css`, `app.css` (design system du client) |

## Règle

Un document lu par les deux agents va ici. Un document produit et maintenu par un seul projet reste dans le `docs/` de ce projet et est référencé depuis l'autre. En cas de conflit, la source de vérité est : `docs/` pour les spécifications retenues, `backendWeb/logEtd/docs/05-api/API_ENDPOINTS.md` pour l'API.

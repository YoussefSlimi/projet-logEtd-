# Conventions Backend — Spring Boot

## Général
- Package racine : `org.logetd`
- Dates en UTC en base, converties en `Africa/Tunis` à l'affichage
- Montants en millimes (entiers `Long`), jamais en flottants
- Aucune entité JPA exposée directement par l'API (toujours des DTO)
- Aucune donnée personnelle dans les logs

## Architecture
- Un module = un package (domain, shared, config)
- Règle de dépendance stricte : un module n'utilise que l'interface publique d'un autre

## Persistance
- `spring.jpa.hibernate.ddl-auto=update` (création automatique des tables)
- Les énumérations sont stockées en `STRING`, jamais en `ORDINAL`
- Contrainte anti-chevauchement : ajoutée par un fichier SQL séparé (Flyway)

## API
- Préfixe : `/api/v1/`
- Pagination obligatoire sur les listes (`page`, `size`, max 50)
- Format d'erreur standard : code métier, message, détails, timestamp
- Idempotency-Key sur les endpoints de paiement

## Tests
- JUnit 5 + Mockito pour les tests unitaires
- Test critique obligatoire : deux demandes concurrentes sur les mêmes dates → 409, jamais 500
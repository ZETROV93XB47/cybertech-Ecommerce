
---

## Wave Keycloak-Outbox — saga CREATE + UPDATE + DELETE crash-safe (this session)

Spec: `docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md` (révisée : scope élargi à UPDATE)
Plan: `docs/superpowers/plans/2026-06-04-keycloak-outbox.md`

- [x] Task 1 — Data layer: `OutboxOperationType` {CREATE,UPDATE,DELETE}, `OutboxStatus`, `KeycloakOutboxEntity` (+`payload` pour UPDATE), `KeycloakOutboxRepository.findByStatusAndUpdatedAtBefore`, table `keycloak_outbox` dans le SQL init — commit `6b24724`
- [x] Task 2 — `KeycloakUserManagementService.searchByEmail` + `deleteUser` 404-tolérant (et surfaçage des vrais 4xx/5xx, silencieusement avalés avant) — commit `6b24724`
- [x] Task 3 — `KeycloakOutboxService(+Imp)`: record*/markDone/markFailed + `reconcile` idempotent (CREATE=lookup+compensation, UPDATE=ré-application forward + supersede guard, DELETE=re-issue idempotent; plafond attempts → FAILED terminal = dead-letter implicite) — commit `6b24724`
- [x] Task 4 — Sagas câblées dans `UserManagementServiceImp`: create (breadcrumb→KC→DB→DONE), update/updateMe (helper partagé `updateWithOutbox`; rejet KC=FAILED terminal, échec DB post-KC=reste PENDING pour le job), delete/deleteByUUIDs (ligne DELETE co-commitée, `UserDeletedEvent` porte l'outboxUuid, listener réconcilie immédiatement) — commit `504766f`
- [x] Task 5 — Batch: `KeycloakOutboxReconciliationTasklet` + Job/Step beans + `@Scheduled` (cron 15 min UTC) + propriétés `cybertech.keycloak.outbox.*` — commit `35d797d`
- [x] Task 6 — `KeycloakOutboxFlowIT` (register→DONE, orphan→compensation par le vrai tasklet, delete→DONE via listener) + `keycloak_outbox` dans `TestDataCleaner` + docs révisées
- [x] Full unit suite verte (1855/1855, 0 échec) + IT exécuté (Docker up) : `KeycloakOutboxFlowIT` 3/3 PASS (159 s, vrais containers)
- [x] Commit final docs + IT

### Déviations vs plan original
- Pas de `@DataJpaTest` (aucun précédent dans le projet ; derived query couverte par l'IT).
- `create()` marque aussi FAILED sur échec Keycloak propre (le plan laissait la ligne PENDING → lookup inutile du job 5 min plus tard).
- Échec DB d'un UPDATE après Keycloak OK = ligne laissée PENDING **volontairement** (forward recovery du job) — c'est le fix de la divergence "CRITICAL/manual reconciliation".

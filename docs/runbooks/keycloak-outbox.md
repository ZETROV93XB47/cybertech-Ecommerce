# Runbook — Keycloak outbox (saga user CREATE/UPDATE/DELETE)

> Architecture de référence : [`docs/architecture/keycloak-outbox-saga.md`](../architecture/keycloak-outbox-saga.md)

## 1. Symptoms

- Log `ERROR` : `Outbox row <uuid> (<TYPE>) gave up after N attempts — manual reconciliation required` (plafond `attempts` atteint → ligne `FAILED`).
- Log `ERROR` : `Immediate reconcile of delete outbox <uuid> failed — batch job will retry` (listener delete en échec — bénin si transitoire, le job repasse derrière).
- Log `WARN` répété : `Outbox row <uuid> (<TYPE>) transient failure, attempt N — will retry` (Keycloak probablement down — corréler avec le runbook [`keycloak-outage.md`](./keycloak-outage.md)).
- Backlog `PENDING` qui ne se vide pas (job désactivé ou scheduler mort).
- Utilisateur qui se plaint : « j'ai supprimé mon compte mais je peux encore me connecter » (DELETE pas drainé) ou « mon profil affiche un ancien nom » (UPDATE divergent).

## 2. Quick triage

```sql
-- Vue d'ensemble : il ne doit y avoir quasiment que des DONE
SELECT status, operationType, COUNT(*) FROM keycloak_outbox GROUP BY status, operationType;

-- Les cadavres en attente (le job doit les vider à chaque tick de 15 min)
SELECT BIN_TO_UUID(uuid), operationType, email, keycloakId, attempts, lastError, updatedAt
FROM keycloak_outbox
WHERE status = 'PENDING' AND updatedAt < NOW() - INTERVAL 5 MINUTE
ORDER BY updatedAt;

-- La dead-letter implicite (triage manuel requis)
SELECT BIN_TO_UUID(uuid), operationType, email, keycloakId, attempts, lastError, createdAt
FROM keycloak_outbox
WHERE status = 'FAILED'
ORDER BY createdAt DESC;
```

Vérifier que le job tourne : log `No stale PENDING keycloak-outbox rows to reconcile` toutes
les 15 min, et `cybertech.keycloak.outbox.job.activated=true`.

## 3. Root cause options

1. **Keycloak down / lent** (le plus probable) — les `attempts` grimpent en WARN. Le job absorbe
   ~75 min d'outage (5 tentatives × 15 min) avant de basculer les lignes en `FAILED`.
2. **Job désactivé ou scheduler mort** — backlog `PENDING` croissant sans logs de tick.
3. **Ligne empoisonnée** — `lastError` identique à chaque passage (ex. payload UPDATE
   indésérialisable après un changement de DTO) ; elle finira `FAILED` sans bloquer les autres
   (isolation par ligne).

## 4. Mitigation

### FAILED CREATE (`lastError = "orphan Keycloak user compensated"`)
Rien à faire — c'est le fonctionnement **nominal** de la compensation post-crash : l'orphelin a
été supprimé, l'utilisateur réessaiera son inscription. La ligne est une trace d'audit.

### FAILED CREATE (`lastError = "create never reached Keycloak — abandoned"`)
Rien à faire non plus : l'inscription n'a jamais abouti nulle part, aucun système n'est sale.

### FAILED DELETE (plafond atteint, Keycloak était down trop longtemps)
Le compte Keycloak existe encore alors que la ligne DB est supprimée — **l'utilisateur peut
encore s'authentifier**. Re-driver à la main une fois Keycloak revenu :
```sql
-- Re-arme la ligne : le prochain tick du job la re-réconciliera (deleteUser est idempotent)
UPDATE keycloak_outbox SET status='PENDING', attempts=0
WHERE uuid = UUID_TO_BIN('<uuid>') AND status='FAILED' AND operationType='DELETE';
```
Ou supprimer l'utilisateur directement dans la console Keycloak puis laisser la ligne `FAILED`
comme trace (préférer le re-drive SQL : il passe par le chemin testé).

### FAILED UPDATE (plafond atteint)
Divergence possible Keycloak-ahead. Comparer les deux systèmes pour le `keycloakId` de la
ligne ; si divergents, re-driver comme ci-dessus (la ré-application est idempotente et protégée
par le supersede guard) ou corriger à la main côté DB.

### Backlog PENDING qui ne bouge pas
Vérifier `cybertech.keycloak.outbox.job.activated`, le log du scheduler, et que le pod n'a pas
un `TaskScheduler` saturé. Un run manuel est possible en abaissant temporairement le cron — ou
en re-déployant : le job rattrape tout ce qui est périmé, peu importe l'ancienneté.

## 5. Verification

```sql
SELECT COUNT(*) FROM keycloak_outbox WHERE status='PENDING' AND updatedAt < NOW() - INTERVAL 20 MINUTE;
-- attendu : 0 (deux ticks de job sont passés)
```
Et pour un DELETE re-drivé : l'utilisateur ne doit plus apparaître dans la console Keycloak ni
pouvoir obtenir un token.

## 6. Verrou ShedLock (multi-replica, cf. architecture D12)

Depuis 2026-08-16, `startJob()` est protégé par `@SchedulerLock` (backend Redis). En
`replicas: 1` (config actuelle) ce verrou n'a jamais de raison d'être disputé — s'il l'est,
c'est le signe que **deux pods tournent en même temps** (scale-up, ou deux versions en
transition lors d'un rolling deploy).

```bash
# Le verrou est-il actuellement tenu, et pour combien de temps encore (secondes) ?
redis-cli GET job-lock:cybertech:keycloakOutboxReconciliationJob
redis-cli TTL job-lock:cybertech:keycloakOutboxReconciliationJob
```

- **Clé absente** : aucun tick en cours — normal la majorité du temps (le job clôt son tick en
  quelques secondes, le TTL de 10 min ne traîne jamais longtemps derrière une exécution saine).
- **Clé présente avec un TTL qui redescend normalement** : un tick est en cours ailleurs — rien
  à faire, c'est le fonctionnement voulu si plusieurs pods tournent.
- **Rien à « débloquer » à la main** : contrairement à un verrou DB sans expiration, celui-ci
  s'auto-libère via le TTL Redis — même si le pod qui le détenait meurt en plein run, le
  prochain tick (ou un autre pod) récupère le verrou au plus tard `lockAtMostFor` (10 min) après
  sa pose. Un `DEL` manuel n'est utile que pour forcer un run immédiat en debug — jamais requis
  en fonctionnement normal.
- **Si le backlog `PENDING` grossit alors que le verrou tourne bien** (clé présente, TTL sain,
  logs de tick réguliers) : ce n'est pas un problème ShedLock — retourner à la §3 (root cause
  options) de ce runbook.

## 7. Postmortem checklist

- Durée de l'outage Keycloak vs fenêtre d'absorption (5 × 15 min) — faut-il monter `max-attempts` ?
- Nombre de lignes passées `FAILED` et leur type — un pattern (toutes UPDATE ?) indique un bug, pas un outage.
- Le `lastError` était-il exploitable ? Sinon, enrichir le message au point d'échec.
- L'alerte Grafana sur `status='FAILED'` existe-t-elle déjà ? (gap connu — architecture §9.3)

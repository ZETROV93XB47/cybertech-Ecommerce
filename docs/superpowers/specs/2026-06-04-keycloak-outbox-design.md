# Spec — Outbox Keycloak minimal pour la saga utilisateur

> Date : 2026-06-04 · Statut : validé en design, en attente de relecture spec

## 1. Contexte & motivation

La création/mise à jour/suppression d'un utilisateur touche **deux systèmes de vérité** non
transactionnables ensemble : **Keycloak** (HTTP, admin client) et **MySQL** (JPA). C'est le
*dual-write problem* : deux écritures non atomiques qui peuvent diverger.

La saga actuelle (`UserManagementServiceImp` : Keycloak-first + compensation in-line pour `create`,
event `AFTER_COMMIT` pour `delete`) couvre les échecs **attrapés**, mais **pas un crash du process**
entre deux écritures :

- `create` : crash après `Keycloak.createUser` mais avant le `catch` ⇒ **user Keycloak orphelin**,
  aucune compensation (le `catch` ne tourne jamais).
- `delete` : l'`ApplicationEvent` `AFTER_COMMIT` vit en mémoire ⇒ perdu si le process meurt après le
  commit DB mais avant le listener.

L'outbox rend l'**intention durable** (committée en DB) ⇒ un job de réconciliation peut finir/nettoyer
ce qu'un crash a laissé en suspens.

## 2. Décisions de design (verrouillées)

1. **Contrat synchrone.** `/register` et delete restent **synchrones** : succès = effet
   immédiat (compte utilisable tout de suite). Un échec **propre** (Keycloak down) renvoie une erreur
   et marque la ligne outbox **terminale** ; l'utilisateur réessaie. Le job ne rattrape **que** les
   lignes laissées `PENDING` par un **vrai crash** (au-delà d'une fenêtre de staleness). Pas de
   « 500 puis le compte apparaît plus tard ».

   **Scope : CREATE + DELETE uniquement.** `update` est **laissé tel quel** (Keycloak-first + DB
   REQUIRES_NEW + log de réconciliation manuelle) — pas d'outbox sur update (décision utilisateur :
   le gain y est marginal). Il pourra recevoir le même breadcrumb plus tard (cf. §8).

2. **Le job ne crée JAMAIS d'user Keycloak (contrainte sécurité décisive).** Créer un user Keycloak
   exige le **mot de passe brut**. On ne le **stocke jamais** au repos. Conséquence : le mot de passe
   ne vit qu'**en mémoire**, le temps de la requête synchrone ; le job ne fait que **réconcilier**
   (lookup / compensation / ré-application idempotente), jamais créer. Ça dicte la forme des 3 sagas.

3. **Pas de table dead-letter séparée.** Toutes les actions consumer sont **idempotentes**
   (delete = 404-as-ok ; update = ré-application ; create-recovery = lookup/compensation). Un
   `attempts` + un statut terminal `FAILED` **dans la même table** suffisent : les lignes `FAILED`
   *sont* la dead-letter (requêtables, hors du scan `PENDING`). Une vraie DLB est documentée comme
   évolution future, non implémentée.

4. **Une seule table** dans la MySQL existante (pas de base séparée — sinon on recrée un dual-write).

## 3. Modèle de données

`KeycloakOutboxEntity extends BaseEntity<Long>` → table `keycloak_outbox`. `BaseEntity` fournit déjà
`uuid`, `createdAt` (@CreatedDate), `updatedAt` (@LastModifiedDate), `version` (optimistic lock).

| colonne | type | rôle |
|---|---|---|
| `operation_type` | enum `OutboxOperationType` { CREATE, DELETE } | type d'action Keycloak |
| `status` | enum `OutboxStatus` { PENDING, DONE, FAILED } | cycle de vie |
| `keycloak_id` | String, nullable | rempli pour DELETE ; pour CREATE au DONE (audit) |
| `email` | String, nullable, indexé | clé de réconciliation pour CREATE (lookup Keycloak) |
| `attempts` | int, default 0 | incrémenté à chaque passage job ; plafond → FAILED |
| `last_error` | String, nullable, tronqué | dernier message d'échec (debug / lignes FAILED) |

La **fenêtre de staleness** s'appuie sur `updatedAt` (`status='PENDING' AND updatedAt < now - window`)
pour ne pas courir après une requête en vol.

Nouvelles enums sous `entities/enums/`. Entité sous `entities/`. Repo
`repositories/KeycloakOutboxRepository`.

## 4. Les 3 sagas

### CREATE — compensation in-line conservée + breadcrumb durable
```
TX1 : INSERT outbox(CREATE, email, PENDING)              ── commit (intention durable)
Keycloak.createUser(…, password)  → keycloakId            (password en mémoire uniquement)
TX2 : INSERT userTable(user, keycloakId) + status=DONE   ── un seul commit
échec DB propre → compensation in-line (Keycloak.deleteUser) + status=FAILED, rethrow
```
**Job (crash uniquement)** : ligne `PENDING` CREATE périmée → **lookup Keycloak par email** →
- orphelin trouvé ⇒ `Keycloak.deleteUser` (compensation arrière) + `FAILED` ;
- rien ⇒ `FAILED`.

Garantie : **cohérence (zéro orphelin)**. Compte **non** garanti après crash (l'utilisateur réessaie).
Aucun mot de passe requis par le job (il compense, il ne crée pas).

### DELETE — outbox co-commit (remplace l'event AFTER_COMMIT)
```
TX : DB delete + INSERT outbox(DELETE, keycloakId, PENDING)   ── un seul commit ACID
AFTER_COMMIT : Keycloak.deleteUser + status=DONE              (immédiat, happy path)
```
**Job (backstop)** : `PENDING` DELETE périmée → `Keycloak.deleteUser` (idempotent : 404 = ok) + `DONE`.
Remplace `UserDeletedEvent` / `UserDeletionListener` (le cousin non durable) ; `deleteByUUIDs` (bulk)
écrit une ligne outbox par user supprimé.

### UPDATE — inchangé (hors scope)
`update` garde sa saga actuelle : Keycloak-first (fail-fast sur rejet de validation) + DB
`REQUIRES_NEW` + log de réconciliation manuelle sur le cas rare (crash Keycloak→DB). **Aucune ligne
outbox.** Décision utilisateur : le gain y est marginal. Évolution possible en §8.

## 5. Composants

- `entities/KeycloakOutboxEntity` + `entities/enums/OutboxOperationType` + `entities/enums/OutboxStatus`.
- `repositories/KeycloakOutboxRepository` : `findByStatusAndUpdatedAtBefore(PENDING, threshold, pageable)`.
- `services/core/KeycloakOutboxService` (interface) + `…/implementation/KeycloakOutboxServiceImp` :
  - écriture des lignes (`recordCreate/recordUpdate/recordDelete`), `markDone/markFailed`,
  - **logique de réconciliation par type** (`reconcile(row)`), réutilisée par le tasklet **et**
    potentiellement par l'AFTER_COMMIT du delete. Toute la logique idempotente vit ici → testable
    sans Spring.
- Saga : `UserManagementServiceImp.create/deleteByUUID/deleteByUUIDs` et `UserPersistenceService`
  écrivent/clôturent les lignes outbox aux bons points de transaction. `update` **non touché**.
- `KeycloakUserManagementService` : ajouter `searchByEmail(email) → Optional<keycloakId>` (lookup de
  réconciliation CREATE) ; `deleteUser` déjà idempotent-friendly (traiter 404 comme succès).
- Batch (réplique de `RedeliverFailedNotificationsJob`) :
  - `batch/job/KeycloakOutboxReconciliationJob` : `@Scheduled(cron=…)` + `JobLauncher` + flag `activated`.
  - config Job + `batch/task/KeycloakOutboxReconciliationTasklet` : page les `PENDING` périmées,
    `outboxService.reconcile(row)` chacune (CREATE → lookup/compensation ; DELETE → idempotent delete),
    `attempts++`, plafond → `FAILED` + log fort.
  - cron par défaut ~15 min (UTC), propriété override, comme les jobs existants.

## 6. Gestion des erreurs / cohérence

- Échec transitoire → reste `PENDING`, rejoué au prochain tick.
- Échec permanent (poison) → après `MAX_ATTEMPTS` → `FAILED` + `last_error` + log « réconciliation
  manuelle ». Les `FAILED` sont la dead-letter implicite.
- Le tasklet est idempotent et borne sa page (pas de balayage illimité) ; concurrence inter-instances
  gérée par le verrouillage Spring Batch (un seul JobInstance par paramètre date) — comme l'existant.

## 7. Tests

- **Unitaires (Mockito)** :
  - `KeycloakOutboxServiceImp` : chaque `reconcile` (CREATE orphelin→compensation, CREATE rien→FAILED,
    DELETE→idempotent), plafond attempts→FAILED, idempotence (rejouer ne duplique pas).
  - Sagas `UserManagementServiceImp` : happy paths + chaque fenêtre de crash simulée (mock qui jette
    entre les phases) → on asserte l'état outbox attendu et l'absence de double-effet.
  - Tasklet : PENDING périmées dispatché, non-périmées ignorées, plafond.
- **IT (Testcontainers, infra ES réparée)** : `/register` happy → ligne outbox `DONE` ; scénario
  orphelin (créer un user Keycloak via le stub puis lancer le tasklet → vérifier compensation + `FAILED`).
  Keycloak reste **mocké** en IT (cf. `UserRegistrationFlowIT` qui stubbe déjà
  `KeycloakUserManagementService`).

## 8. Hors scope / évolutions futures (documentées, non implémentées)

- **Breadcrumb outbox sur `update`** (même pattern que create : ligne pré-appel + ré-application
  idempotente par le job) — laissé de côté car gain marginal.
- Vraie **dead-letter box** séparée + alerting.
- **Outbox transactionnel générique** (CDC/Debezium, broker) pour d'autres effets externes.
- Réconciliation inverse périodique (scan Keycloak ↔ DB) pour détecter des divergences hors outbox.
- Nettoyage/archivage des lignes `DONE` anciennes (table petite pour l'instant).

## 9. Note de cohérence avec l'existant

- Respecte « interface avant impl », `*Service`/`*ServiceImp`, batch `job`/`task`, `@Scheduled` cron
  configurable (réplique `RedeliverFailedNotificationsJob`/`StockCleanupJob`).
- `@Transactional` reste dans les services (jamais sur les repos).
- Aucune modification des fichiers notification interdits.

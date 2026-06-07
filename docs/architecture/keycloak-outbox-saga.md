# Saga Keycloak ↔ MySQL — Outbox de réconciliation (CREATE / UPDATE / DELETE)

> **Statut :** implémenté et livré sur `dev/develop` — commits `6b24724`, `504766f`, `35d797d`, `ccb478a` (2026-06-08).
> **Spec d'origine :** [`docs/superpowers/specs/2026-06-04-keycloak-outbox-design.md`](../superpowers/specs/2026-06-04-keycloak-outbox-design.md)
> **Plan d'exécution :** [`docs/superpowers/plans/2026-06-04-keycloak-outbox.md`](../superpowers/plans/2026-06-04-keycloak-outbox.md)
> **Runbook ops :** [`docs/runbooks/keycloak-outbox.md`](../runbooks/keycloak-outbox.md)
> **Vérification :** 1855/1855 tests unitaires verts · `KeycloakOutboxFlowIT` 3/3 PASS (Testcontainers réels)

Ce document est la référence complète des choix de design, de leur justification, des
chorégraphies de saga et de la matrice de pannes. Il est volontairement exhaustif — pour le
résumé exécutif, lire uniquement §1 et §3.

---

## 1. Résumé exécutif

La gestion utilisateur écrit dans **deux systèmes non transactionnables ensemble** : Keycloak
(identité, via admin client HTTP) et MySQL (profil métier, via JPA). C'est le *dual-write
problem* : aucune transaction distribuée n'existe entre un appel REST Keycloak et un commit
MySQL, donc toute séquence « écrire ici puis là » possède des fenêtres où un crash laisse les
deux systèmes divergents.

Les vagues précédentes (BUG-H1, Bug 3, Bug 4, FIX-2) avaient traité les **échecs attrapés**
(exception → compensation in-line ou event `AFTER_COMMIT`). Restait le cas du **crash du
process** : un `kill -9`, un OOM-kill Kubernetes, une coupure réseau JVM-fatale entre deux
écritures. Un `catch` ne s'exécute pas dans un process mort ; un `ApplicationEvent` en mémoire
ne survit pas à un redémarrage.

**Le fix : un outbox minimal.** Chaque intention d'effet Keycloak est d'abord **committée en
base** (table `keycloak_outbox`) avant — ou avec — l'opération risquée. Le chemin synchrone
exécute l'effet immédiatement et clôt la ligne ; un job Spring Batch périodique réconcilie les
lignes qu'un crash a laissées `PENDING`. Garantie obtenue : **convergence éventuelle des deux
systèmes, sans jamais stocker de secret au repos.**

---

## 2. Historique — comment on est arrivé là

| Étape | Commit(s) | Ce qui a été fait | Ce qui restait cassé |
|---|---|---|---|
| **BUG-H1** (Wave 2) | `59ea6b6` | `create()` : Keycloak-first hors TX + `UserPersistenceService.saveNewUser` en `REQUIRES_NEW` + compensation in-line (`deleteUser`) si la DB échoue | Crash entre le create Keycloak et le `catch` ⇒ user Keycloak orphelin, compensation jamais exécutée |
| **Bug 3** (Option B) | `cae8341` | `update()` : Keycloak-first (fail-fast sur rejet) puis DB `REQUIRES_NEW` ; échec DB → log `CRITICAL` + réconciliation **manuelle** | Crash OU échec DB après Keycloak OK ⇒ divergence (Keycloak en avance), au mieux loggée, au pire silencieuse |
| **Bug 4** | `cae8341` | `deleteByUUID()` : DB-first + `UserDeletedEvent` consommé `AFTER_COMMIT` → Keycloak touché seulement après commit | L'event vit **en mémoire** : crash entre le commit DB et le listener ⇒ intent perdu, compte Keycloak fantôme qui peut encore s'authentifier |
| **FIX-2** (Hardening) | `cae8341` | `deleteByUUIDs` (bulk) aligné sur le pattern event du singulier | Même faiblesse en-mémoire que Bug 4 |
| **Outbox** (ce fix) | `6b24724`, `504766f`, `35d797d`, `ccb478a` | Intention durable en DB + job de réconciliation idempotent pour les **trois** opérations | — (limites résiduelles en §9) |

> Le scope initial de la spec était CREATE + DELETE (l'UPDATE jugé « gain marginal »). Décision
> utilisateur du 2026-06-08 : **embarquer l'UPDATE aussi**, parce que c'est la seule des trois
> opérations dont le payload est rejouable sans secret — donc la seule réconciliable en
> *forward recovery* (cf. §6.2), ce qui ferme aussi la divergence *attrapée* que Bug 3 se
> contentait de logger.

---

## 3. Décisions de design verrouillées — et pourquoi

### D1 — Contrat synchrone conservé
`/register`, update et delete restent **synchrones** : succès HTTP = effet immédiat et complet.
Un échec **propre** (Keycloak down, rejet de validation) renvoie une erreur, marque la ligne
outbox **terminale** (`FAILED`), et c'est à l'appelant de réessayer. Le job ne rattrape **que**
les lignes laissées `PENDING` par un vrai crash.

*Pourquoi :* l'alternative (accepter la requête, retourner 202, laisser le job « finir » la
création plus tard) donne le pire des UX : « 500 puis le compte apparaît mystérieusement
10 minutes après ». Un utilisateur qui voit une erreur s'attend à ce que rien n'ait été créé.

*Alternative rejetée :* file de commandes asynchrone (le POST écrit l'intent, un worker
exécute). Plus robuste sur le papier, mais change le contrat API, complexifie le front
(polling/websocket pour savoir si le compte existe), et le mot de passe devrait transiter par
la file — inacceptable (cf. D2).

### D2 — Le job ne crée JAMAIS d'utilisateur Keycloak (contrainte structurante)
Créer un user Keycloak exige le **mot de passe brut** (poussé une seule fois via l'admin API ;
Keycloak le hash immédiatement — Argon2id par défaut depuis KC 24 — et il est irrécupérable
ensuite). On refuse de stocker ce mot de passe au repos, où que ce soit : ni colonne outbox, ni
log, ni cache. Conséquence mécanique : **le job ne peut pas rejouer un CREATE**. Il ne peut que
*constater* (lookup par email) et *compenser* (supprimer l'orphelin).

*Pourquoi :* un mot de passe en clair dans une table MySQL est une faille de sécurité
disqualifiante (PCI/GDPR mindset du projet). Ça dicte la forme asymétrique des trois sagas :
CREATE = backward recovery (compensation), UPDATE = forward recovery (ré-application), DELETE =
ré-émission idempotente.

*Alternative rejetée :* chiffrer le mot de passe dans l'outbox (AES-GCM comme les PAN). Même
chiffré, c'est un secret d'authentification au repos avec sa clé dans la même infra — surface
d'attaque inutile pour gagner un seul cas (re-création post-crash) que l'utilisateur résout en
réessayant son inscription.

### D3 — Une seule table, dans la MySQL existante
`keycloak_outbox` vit dans le même schéma que `userTable`. **Pas** de base dédiée, pas de
broker.

*Pourquoi :* tout l'intérêt de l'outbox est de pouvoir **co-committer** l'intention avec
l'écriture métier dans **une seule transaction ACID** (c'est exactement ce que fait le DELETE).
Une base séparée recréerait le dual-write qu'on cherche à éliminer — on aurait juste déplacé le
problème.

*Alternative rejetée :* outbox générique + CDC (Debezium → Kafka). C'est le pattern
industriel complet, mais le projet n'a pas de Kafka actif (cf. LAUNCH_PROGRESS — l'event flow
est `ApplicationEventPublisher`), et l'infra CDC est disproportionnée pour 3 types d'opérations
sur une seule entité. Documenté comme évolution future (§10).

### D4 — Pas de table dead-letter séparée
Toutes les actions de réconciliation sont **idempotentes** (delete = 404-as-ok ; update =
ré-application ; create = lookup/compensation). Un compteur `attempts` + un statut terminal
`FAILED` **dans la même table** suffisent : les lignes `FAILED` *sont* la dead-letter —
requêtables (`WHERE status='FAILED'`), hors du scan `PENDING`, avec `lastError` pour le triage.

*Pourquoi :* une vraie DLB (table + re-drive endpoint + alerting) est de l'outillage, pas de la
garantie. Le moteur de convergence n'en a pas besoin ; on l'ajoutera si le volume de FAILED le
justifie (§10).

### D5 — Staleness window sur `updatedAt`, pas de flag « in-flight »
Une ligne est réconciliable si `status='PENDING' AND updatedAt < now() - staleness-minutes`
(défaut **5 min**). Une requête synchrone saine passe sa ligne de `PENDING` à terminal en
quelques millisecondes : une ligne `PENDING` vieille de 5 minutes ne peut être qu'un cadavre de
crash.

*Pourquoi :* c'est le mécanisme le plus simple qui évite au job de **courir après une requête
en vol** (réconcilier une création en cours = compenser un user en train d'être inséré = race).
`updatedAt` vient de `@LastModifiedDate` (auditing Spring déjà en place sur `BaseEntity`) —
zéro mécanique ajoutée.

*Alternative rejetée :* verrou/claim par ligne (`SELECT ... FOR UPDATE SKIP LOCKED` ou statut
`IN_PROGRESS`). Utile si plusieurs instances de job concourent ; ici Spring Batch verrouille
déjà par JobInstance (un seul run par paramètre date), et le single-node minikube ne justifie
pas la complexité.

### D6 — UPDATE : supersede guard plutôt que versionnage du payload
À la ré-application d'un UPDATE, si `user.updatedAt > row.createdAt`, une écriture **plus
récente** a déjà gagné → le payload périmé n'est **pas** rejoué (ligne close `DONE`,
`lastError` documentaire).

*Pourquoi :* sans garde, le job pourrait ressusciter un prénom/email vieux de 15 minutes
par-dessus une correction que l'utilisateur vient de faire — un *lost update* fabriqué par le
mécanisme de réparation lui-même. Le timestamp est une heuristique (un write **non lié** au
profil — ex. `numberOfHatefulComments` incrémenté par la modération — peut faussement
« superseder » et faire sauter une ré-application), mais l'asymétrie des risques est claire :
*sauter* une convergence laisse une divergence détectable et rattrapable à la main ;
*écraser* des données fraîches détruit de l'information silencieusement. On choisit l'erreur
récupérable.

### D7 — DELETE : co-commit + listener immédiat + job backstop (3 couches)
La ligne outbox DELETE est écrite en `Propagation.REQUIRED` — elle **rejoint la transaction du
delete SQL** : soit les deux committent, soit aucun. L'event `AFTER_COMMIT` ne porte plus que
l'`outboxUuid` et son listener n'est plus le porteur de la garantie : c'est juste le chemin
basse-latence (révoquer l'identité tout de suite). Le job est le filet.

*Pourquoi :* c'est l'inversion clé vs Bug 4. Avant : durabilité = l'event (RAM). Maintenant :
durabilité = la ligne co-commitée ; l'event est une **optimisation de latence** dont la perte
est sans conséquence.

### D8 — Échec DB d'un UPDATE après Keycloak OK ⇒ la ligne RESTE `PENDING` (délibéré)
C'est la **seule** exception au principe « échec propre = terminal » (D1). Quand Keycloak a
accepté l'update mais que le save DB échoue, les deux systèmes **divergent réellement** — et le
payload permet de converger automatiquement. Marquer `FAILED` reviendrait à reproduire le
comportement Bug 3 (log CRITICAL + intervention manuelle) alors qu'on possède tout ce qu'il
faut pour réparer sans humain.

*Trace pinnée par test :* `update_dbFailureAfterKeycloak_leavesRowPendingForJob` vérifie
explicitement `never().markFailed` + `never().markDone`.

### D9 — `deleteUser` rendu réellement idempotent — et un bug latent corrigé au passage
`UsersResource.delete(id)` retourne une `Response` JAX-RS que l'ancien code **ignorait
totalement** : un delete Keycloak en échec (500, 403...) passait pour un succès silencieux.
Le nouveau code inspecte le statut : `404` = succès idempotent (la ré-émission par le job d'un
delete déjà fait est un no-op), tout autre ≥ 400 = `IllegalStateException` surfacée. La
`Response` est fermée en try-with-resources (même pattern que `createUser`, pin BUG-085).

---

## 4. Modèle de données

Table `keycloak_outbox` — entité `KeycloakOutboxEntity extends BaseEntity<Long>` (hérite
`id`, `uuid`, `createdAt`, `updatedAt`, `version`).

| Colonne | Type | Rempli pour | Rôle |
|---|---|---|---|
| `operationType` | enum STRING {`CREATE`,`UPDATE`,`DELETE`} | tous | sélectionne la branche de `reconcile()` |
| `status` | enum STRING {`PENDING`,`DONE`,`FAILED`} | tous | cycle de vie ; `FAILED` = dead-letter implicite |
| `email` | String, indexé | CREATE | clé de réconciliation (lookup Keycloak par email exact) |
| `keycloakId` | String | UPDATE/DELETE à l'écriture ; CREATE backfillé au `markDone` (audit) | cible des opérations idempotentes |
| `payload` | String JSON (2000) | UPDATE | `UserUpdateRequestDto` sérialisé (Jackson 3) — ré-appliqué par le job. **Jamais de secret** |
| `attempts` | int | tous | incrémenté par passage job ; plafond (`max-attempts`, défaut 5) → `FAILED` |
| `lastError` | String (1000, tronqué) | échecs | dernier message — triage ops |

Index : `idx_outbox_status` (le scan du job), `idx_outbox_email` (lookup CREATE).
Schéma SQL : `src/main/resources/sql/databaseSchemaInitFile.sql` (+ métadonnées JPA pour
Hibernate `ddl-auto=update` et Testcontainers — doublement volontaire, cf. piège documenté dans
`BUGS_REPORT.md` §7 sur ddl-auto vs SQL init).

---

## 5. Chorégraphies des trois sagas

### 5.1 CREATE — breadcrumb + compensation (backward recovery)

```
Phase 0   TX outbox (REQUIRES_NEW) : INSERT (CREATE, email, PENDING)        ── commit
Phase 1   Keycloak.createUser(email, ..., password)  → keycloakId          (password : mémoire seulement)
Phase 2   TX persistence (REQUIRES_NEW) : INSERT userTable (+ bank card)   ── commit
Phase 3   TX outbox (REQUIRES_NEW) : status=DONE, keycloakId backfillé     ── commit
```

| Fenêtre de panne | Avant le fix | Avec l'outbox |
|---|---|---|
| Échec propre Phase 1 (Keycloak down/rejet) | OK (rien à compenser) | idem + ligne `FAILED` (trace, le job n'a rien à chercher) |
| Échec propre Phase 2 (DB) | compensation in-line | idem + ligne `FAILED` ; si la compensation échoue aussi, le job retrouvera l'orphelin |
| **Crash entre Phase 1 et 2** | **orphelin Keycloak définitif** | ligne `PENDING` périmée → job : lookup email → orphelin → `deleteUser` + `FAILED` |
| **Crash entre Phase 2 et 3** | (pas de trace du tout) | ligne `PENDING` périmée → job : lookup → Keycloak **et** DB existent → close `DONE`, **sans compenser** |

Garantie : **zéro orphelin**. Non-garanti (assumé, D2) : le compte après crash — l'utilisateur
réessaie son inscription.

Le cas « crash entre 2 et 3 » est la raison du test `reconcileCreate_fullyRegistered_isClosedNotCompensated`
— le job ne doit **jamais** supprimer un utilisateur réellement inscrit juste parce que le
breadcrumb n'a pas été clos.

### 5.2 UPDATE — breadcrumb + ré-application (forward recovery)

```
Phase 0   TX outbox (REQUIRES_NEW) : INSERT (UPDATE, keycloakId, payloadJson, PENDING)
Phase 1   Keycloak.updateUser(keycloakId, dto)        rejet → markFailed + rethrow (rien n'a muté)
Phase 2   TX persistence (REQUIRES_NEW) : patch + save  échec → ligne RESTE PENDING + rethrow (D8)
Phase 3   markDone
```

| Fenêtre de panne | Avant le fix | Avec l'outbox |
|---|---|---|
| Rejet Keycloak Phase 1 (mode d'échec courant) | OK — rien n'a muté | idem + `FAILED` terminal |
| Échec DB Phase 2 (attrapé) | log `CRITICAL` + **réconciliation manuelle** | ligne `PENDING` → job **ré-applique** le payload aux deux systèmes → convergence automatique |
| **Crash entre Phase 1 et 2** | **divergence silencieuse** (Keycloak en avance, rien de loggé) | ligne `PENDING` périmée → job ré-applique (Keycloak PUT idempotent + patch DB) → `DONE` |
| Crash entre Phase 2 et 3 | n/a | job ré-applique — la ré-application sur un état déjà à jour est un no-op sémantique |

Gardes du `reconcileUpdate` (ordre d'évaluation) :
1. user introuvable par `keycloakId` → `DONE` (« user gone ») — la saga DELETE possède le nettoyage ;
2. `user.updatedAt > row.createdAt` → `DONE` (« superseded ») — D6 ;
3. sinon : `objectMapper.readValue(payload)` → `Keycloak.updateUser` → `UserPersistenceService.updateUser` → `DONE`.

`updateMe` (self-service) passe par le **même** helper privé `updateWithOutbox` — une seule
chorégraphie à maintenir. Le patch téléphone (DB-only, non synchronisé Keycloak) reste hors
saga, après le retour du helper.

### 5.3 DELETE — co-commit + reconcile immédiat (3 couches, D7)

```
TX métier (@Transactional du service) :
    DELETE userTable WHERE uuid=...
    INSERT keycloak_outbox (DELETE, keycloakId, PENDING)      ── Propagation.REQUIRED : MÊME commit
    publishEvent(UserDeletedEvent(outboxUuid))                 (ne part que si la TX commit)
AFTER_COMMIT (listener, happy path) :
    row = findByUuid(outboxUuid) → reconcile(row) → Keycloak.deleteUser (404-tolérant) → DONE
Backstop (job, toutes les 15 min) :
    PENDING périmée → même reconcile idempotent
```

| Fenêtre de panne | Avant le fix | Avec l'outbox |
|---|---|---|
| Rollback de la TX métier (FK tardive...) | OK — event jamais publié | idem — la ligne outbox est **aussi** rollbackée (co-commit) : zéro trace fantôme |
| Échec du listener (Keycloak down) | log « manual cleanup required » — intent **perdu** | ligne reste `PENDING` → job ré-émet jusqu'à succès ou plafond |
| **Crash entre commit DB et listener** | **compte Keycloak fantôme qui peut encore se connecter** | ligne `PENDING` co-commitée a survécu → job révoque l'identité |
| Double exécution (listener + job, ou deux ticks) | n/a | `deleteUser` 404-tolérant : la deuxième passe est un no-op `DONE` |

`deleteByUUIDs` (bulk, niveau service — aucun endpoint REST ne l'expose depuis le retrait du
seeder) : une ligne + un event **par utilisateur résolu**, tous portés par la même TX.

---

## 6. Réconciliation — le job

- **`KeycloakOutboxReconciliationTasklet`** (`batch/task/`) : scan paginé
  (`findByStatusAndUpdatedAtBefore(PENDING, now - staleness, PageRequest.ofSize(batch-size))`),
  délègue chaque ligne à `KeycloakOutboxService.reconcile(row, max-attempts)`. Une page bornée
  par tick — pas de balayage illimité ; le backlog résiduel est pris au tick suivant.
- **`KeycloakOutboxReconciliationJob`** (`batch/job/`) : `@Scheduled(cron)` (défaut `0 */15 * * * *`
  UTC), flag `activated`, `JobLauncher` + paramètre date — réplique structurelle exacte de
  `RedeliverFailedNotificationsJob` (cohérence d'architecture, CLAUDE.md).
- **`reconcile()`** est `REQUIRES_NEW` et **avale ses propres échecs** : un throw incrémente
  `attempts` + écrit `lastError` ; sous le plafond la ligne reste `PENDING` (retry au tick
  suivant), au plafond elle bascule `FAILED` + log error « manual reconciliation required ».
  L'isolation par ligne garantit qu'une ligne empoisonnée ne bloque pas le drain des autres.
- **Coût en régime nominal : zéro.** Le chemin synchrone clôt ses lignes en millisecondes ; le
  scan ne trouve rien et se termine (`No stale PENDING keycloak-outbox rows to reconcile`).

### Propriétés (`application.properties`)

```properties
cybertech.keycloak.outbox.job.activated=true        # kill-switch du scheduler
cybertech.keycloak.outbox.job.cron=0 */15 * * * *   # tick (UTC)
cybertech.keycloak.outbox.staleness-minutes=5       # fenêtre anti-course (D5)
cybertech.keycloak.outbox.batch-size=50             # page max par tick
cybertech.keycloak.outbox.max-attempts=5            # plafond avant FAILED terminal
```

Dimensionnement : 5 min de staleness ≪ 15 min de cron ⇒ tout cadavre de crash est ramassé au
plus tard ~20 min après le crash. 5 tentatives × 15 min ⇒ une panne Keycloak < ~75 min est
absorbée sans intervention ; au-delà, lignes `FAILED` à re-driver à la main (runbook).

---

## 7. Carte des composants

| Fichier | Rôle |
|---|---|
| `entities/KeycloakOutboxEntity.java` · `entities/enums/OutboxOperationType.java` · `OutboxStatus.java` | la ligne d'intention durable |
| `repositories/KeycloakOutboxRepository.java` | derived query du scan de staleness |
| `services/core/KeycloakOutboxService.java` + `services/implementation/KeycloakOutboxServiceImp.java` | record*/markDone/markFailed + **toute** la logique `reconcile` (testable sans Spring) |
| `services/implementation/UserManagementServiceImp.java` | orchestration des 3 sagas (`create`, `updateWithOutbox`, `deleteByUUID(s)`) |
| `services/implementation/UserPersistenceService.java` | inchangé — les TX `REQUIRES_NEW` DB préexistantes |
| `services/implementation/KeycloakUserManagementService.java` | `searchByEmail` (lookup CREATE) + `deleteUser` 404-tolérant (D9) |
| `events/UserDeletedEvent.java` + `events/listener/UserDeletionListener.java` | chemin basse-latence du DELETE (porte l'`outboxUuid`, plus le `keycloakId`) |
| `batch/task/KeycloakOutboxReconciliationTasklet.java` + `batch/job/KeycloakOutboxReconciliationJob.java` + `config/BatchConfig.java` | crash recovery périodique |
| `sql/databaseSchemaInitFile.sql` | DDL `keycloak_outbox` |

## 8. Couverture de test (38 tests dédiés)

| Classe | Pins principaux |
|---|---|
| `KeycloakOutboxServiceImpTest` (12) | orphelin compensé / inscrit complet NON compensé / jamais de re-création ; ré-application UPDATE aux deux systèmes ; gardes user-gone + supersede ; attempts/plafond ; payload sans secret ; troncature lastError |
| `UserManagementServiceImpTest` (+9 outbox) | ordre breadcrumb→KC→DB→DONE (create + update) ; FAILED terminal sur échecs propres ; **PENDING conservé sur échec DB post-Keycloak (D8)** ; co-commit + event uuid (delete + bulk) |
| `UserDeletionListenerTest` (3) | résolution par uuid + reconcile ; ligne absente = no-op ; swallow post-commit |
| `KeycloakUserManagementServiceTest` (+5) | 404 idempotent ; 4xx/5xx surfacés ; Response fermée ; searchByEmail hit/miss |
| `KeycloakOutboxReconciliationTaskletTest` (4) | délégation avec plafond ; threshold = now − staleness ; page bornée ; no-op propre |
| `KeycloakOutboxFlowIT` (3, Testcontainers réels) | register → ligne `DONE` keycloakId backfillé ; orphelin compensé par le **vrai** tasklet ; delete → co-commit réconcilié `DONE` par le vrai listener AFTER_COMMIT |

## 9. Limites connues (assumées)

1. **Le supersede guard est une heuristique** (D6) : un write non-profil sur `userTable` peut
   faussement « superseder » une ré-application UPDATE. Erreur du côté sûr (on ne détruit
   jamais de données fraîches), mais une divergence Keycloak-ahead peut alors persister jusqu'à
   intervention manuelle (ligne close `DONE` avec `lastError="superseded"` — triable).
2. **Écritures hors application invisibles** : un admin qui modifie un user dans la console
   Keycloak ne génère aucune ligne outbox. Règle d'exploitation : la console est du
   break-glass ; le scan inverse périodique (Keycloak ↔ DB) est l'évolution prévue.
3. **Pas d'alerting sur les `FAILED`** : la dead-letter implicite est requêtable mais ne
   notifie personne. Une alerte Grafana sur `SELECT COUNT(*) WHERE status='FAILED'` est le
   premier ajout observabilité à faire.
4. **`markDone`/`markFailed` sont best-effort post-effet** : un crash juste après l'effet mais
   avant le mark laisse une ligne `PENDING` qui sera re-réconciliée — c'est précisément pour ça
   que chaque branche de `reconcile` est idempotente. Aucune incohérence possible, juste un
   passage de job « pour rien ».

## 10. Évolutions futures (documentées, non implémentées)

- Réconciliation **inverse** périodique (scan Keycloak ↔ DB) pour les divergences nées hors
  outbox (console admin, scripts).
- Vraie dead-letter box + endpoint admin de re-drive + alerting.
- Outbox transactionnel générique (CDC/Debezium) si d'autres effets externes rejoignent le
  pattern.
- Purge/archivage des lignes `DONE` anciennes (volume négligeable à l'échelle portfolio).

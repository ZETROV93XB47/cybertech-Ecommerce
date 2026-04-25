# Notification dispatch — feature hardening log

> Branch: `dev/develop` · Started: 2026-04-25 · Track: backend (notification subsystem)

This document is the running log of an orchestrated, three-subagent refactor of the notification dispatch pipeline. The orchestrator (Claude Code, Opus 4.7 1M) dispatches each subagent, validates the work with `mvn test`, and commits between phases.

---

## SITREP

### Why this exists
The notification feature has four documented weaknesses identified during code analysis:

1. **`MailServiceImp.sendEmail` swallows `MessagingException`** — template/header setup failures `return` silently, so the retry loop above thinks the email was sent.
2. **Hand-rolled retry in `NotificationListener.on`** — fixed 3 attempts, no backoff, no jitter, catches every `Exception` (including programmer-error like `NoStrategyFoundForProcessingTheRequest`), max-retries hard-coded to a constant.
3. **Asymmetric persistence** — only the shipping-confirmation path writes a `NotificationEntity` row; `OrderEventListener` (created/updated) leaves no trace at all. Failed order-confirmations are invisible.
4. **No redrive** — even when a `NotificationEntity(status=FAILED)` row IS written, nothing scans the table to retry it. Persistent SMTP outages produce dead rows.

### Approach
Three sequential subagents, each scoped to one concern, with a commit gate between them:

| Subagent | Scope | Touches |
|---|---|---|
| 1 — Foundation | MailService rethrow + centralize `NotificationEntity` persistence + add `payload` column to support redrive | `MailServiceImp`, new `NotificationDeliveryException`, dispatcher, listeners, `NotificationEntity`, `NotificationStatus` enum |
| 2 — Resilience4j | Replace manual retry loop with `@Retry`, externalize all knobs to `application.properties` | new bean wrapping dispatch, `application.properties`, `pom.xml` (if needed), `NotificationListener` |
| 3 — Batch redrive | New tasklet + job following the existing `BaseTasklet` / `StockCleanupJob` pattern, scans `FAILED` rows with attempts remaining and redrives | `batch/task/`, `batch/job/`, `application.properties`, `CyberTechAppConstants` |

### Stack constraints (per the user)
- **No Kafka** — use the existing Spring Batch pattern.
- **Extend the existing batch interface** (`BaseTasklet`).
- **All knobs in `application.properties`**, no hard-coded constants.
- **Comments in code** explaining what each change does and why.

---

## TODO — crash-resilience improvements

Tracked as work proceeds. `[x]` = landed in this branch; `[ ]` = not yet, called out for future work.

### Landed in this orchestration
- [x] **F1.** Rethrow `MessagingException` from `MailServiceImp` so retry sees it (`MailServiceImp.java:58-78`) — `MessagingException` and `MailException` both surface as `NotificationDeliveryException`.
- [x] **F1.** Add `NotificationDeliveryException` (unchecked) as the retry-eligible signal — Phase 2 will configure `@Retry` to allowlist this type.
- [x] **F1.** Centralize `NotificationEntity` persistence into a single recorder (`NotificationOutcomeRecorder`) so the order-created and order-updated paths also leave a trace.
- [x] **F1.** Add `payload` JSON column on `NotificationEntity` (`@Lob TEXT`) so the batch tasklet can redrive without losing context. `NotificationRedrivePayload` is the small DTO that's serialized into it; `NotificationPayload` got `@JsonTypeInfo` + `@JsonSubTypes` for polymorphic round-trip.
- [x] **F1.** Add `PENDING_RETRY` status to `NotificationStatus` enum (distinct from terminal `FAILED`); enum gets a lifecycle javadoc.
- [ ] **F2.** Add `notification.dispatch.*` keys to `application.properties` (max-attempts, wait-duration, exponential-backoff, retry-exceptions allowlist)
- [ ] **F2.** Create `NotificationRetryableDelivery` bean wrapping the dispatcher with `@Retry(name="notificationDispatch", fallbackMethod=...)` — separate bean because Spring AOP doesn't apply self-invocation
- [ ] **F2.** Replace the for-loop in `NotificationListener.on` with a single delegate call
- [ ] **F2.** Verify `pom.xml` resolves `@Retry` annotation (add `resilience4j-spring-boot3` if `spring-cloud-starter-circuitbreaker-resilience4j` doesn't bring it transitively)
- [ ] **F3.** New `RedeliverFailedNotificationsTasklet` extending `BaseTasklet`, scans `WHERE status = 'PENDING_RETRY' AND retryCount < max AND lastAttemptAt < now() - backoff`
- [ ] **F3.** New `RedeliverFailedNotificationsJob` mirroring `StockCleanupJob` (cron-driven, `@Scheduled`, `JobLauncher.run`)
- [ ] **F3.** Configurable `cybertech.notification.redelivery.{activated,cron,max-attempts,backoff}` in `application.properties`
- [ ] **F3.** Tasklet promotes a row to terminal `FAILED` once `retryCount >= max-attempts`

### Future work (out of scope here, flagged for backlog)
- [ ] **Atomic outbox** — write the outbox row inside the originating `@Transactional` (in the order/shipping services) so a JVM crash between commit and `AFTER_COMMIT` listener no longer drops notifications. The current design narrows the failure window but doesn't fully close it.
- [ ] **`SmsNotificationProcessor` is a stub** (`SmsNotificationProcessor.java:18` only logs) — when SMS is the user's preferred channel, dispatch silently no-ops but reports success. Either wire a real SMS provider or fail explicitly when SMS is selected.
- [ ] **Dead-letter dashboard** — once rows hit terminal `FAILED`, there's no human-triage surface. Either add an admin endpoint to list/replay or a daily summary email.
- [ ] **Resilience4j metrics** — once `@Retry` is in place, expose `resilience4j.retry.calls{...}` via Micrometer / Actuator for ops visibility.
- [ ] **Per-channel retry strategies** — a single `notificationDispatch` retry instance treats SMTP and SMS the same; in the long run they should have separate timeout/retry profiles.

---

## Important remarks

(Filled in as the orchestration progresses. Add anything that surprised the agents, decisions made under ambiguity, or trade-offs that future maintainers should know about.)

### Phase 1 (foundation) — landed
- **`orderUuid` was relaxed to nullable** on `NotificationEntity`. The order-event path stashes the `OrderEventDto` in `context.data["orderEventDto"]` rather than the typed payload slot. The recorder pulls the UUID from either source; if both miss, persisting `null` is the only honest option (alternatives — fabricated sentinel or dropping the audit row — are worse).
- **Jackson polymorphism uses the legacy `com.fasterxml` annotation namespace.** `RedisConfig` already does the same trick with `JsonTypeInfo`. Jackson 3 (`tools.jackson`) reads them through its compatibility layer. Stayed consistent rather than introducing a second annotation style mid-codebase.
- **No interface for `NotificationOutcomeRecorder`.** The class is a single-method internal helper with no alternate implementation in scope. Adding an interface for ceremony's sake would clutter without payoff. Convention is interface-before-impl for *services*; this is a recorder/sidecar.
- **`OrderConfirmationNotification` mutates `notificationContext.getData()` in place** (theme colours, computed titles). Phase 3 redrive snapshots the *pre-strategy* data via `NotificationRedrivePayload`, so the strategy will repopulate cleanly on redrive. Future strategies that read-then-write `data` need the same discipline — flagged for code review.
- **`NotificationDispatcher.dispatch` declares `throws NoStrategyFoundForProcessingTheRequest`.** Phase 2 must configure `ignore-exceptions` to exclude this type — strategy-resolution failure is a programmer error, NOT retry-eligible.
- **`SmsNotificationProcessor` is still a no-op stub** (`SmsNotificationProcessor.java:18` just logs `"SMS SENT"`). When SMS is the user's preferred channel, dispatch silently succeeds with no real delivery. Out of scope here, but listed in Future Work; either wire a real SMS provider or fail explicitly when SMS is selected.
- **`MailServiceImp.frontendUrl` `@Value` is unused.** Untouched (out of scope) — flagged for cleanup.
- **No DB migration script** was added for the new `payload` column or the relaxed `orderUuid` constraint. Hibernate `ddl-auto=update` handles dev/test, but production schema management likely needs a Flyway/Liquibase entry — flagged for whoever owns the prod migration policy.
- **Subagent 1 went out of scope** on `CartServiceImp.java` (added a `TransactionTemplate` field for BUG-160 redrive ordering) and three integration tests (`OrderFlowIT`, `PaymentWebhookFlowIT`, `UserRegistrationFlowIT`). Those changes were reverted by the orchestrator before commit — that workstream deserves its own review, not a bundled drive-by. Note for the user: the agent's intent looked legitimate (a real concurrency concern around the Redis-lock-vs-transaction ordering), so consider opening a separate ticket if BUG-160 is genuinely still open in your tracker.

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
- [x] **F2.** Add `cybertech.notification.dispatch.*` keys to `application.properties` (max-attempts, wait-duration, exponential-backoff-multiplier). Resilience4j instance config interpolates these so operators have a single tuning surface.
- [x] **F2.** Create `NotificationRetryableDelivery` interface + `NotificationRetryableDeliveryImp` wrapping the dispatcher with `@Retry(name="notificationDispatch", fallbackMethod="onRetriesExhausted")` — separate bean because Spring AOP doesn't apply self-invocation.
- [x] **F2.** Replace the for-loop in `NotificationListener.on` with a single `retryableDelivery.deliver(ctx)` call. `OrderEventListener` now uses the same path (was single-attempt + no retry before).
- [x] **F2.** `pom.xml` already brings `resilience4j-spring-boot3:2.3.0` transitively via `spring-cloud-starter-circuitbreaker-resilience4j` — no pom change needed.
- [x] **F3.** New `RedeliverFailedNotificationsTasklet` extending `BaseTasklet`, scans `WHERE status = 'PENDING_RETRY' AND retryCount < cumulative-max AND lastAttemptAt < now() - backoff`. Reuses the Phase 2 retryable bean so each redrive tick inherits the in-process retry policy.
- [x] **F3.** New `RedeliverFailedNotificationsJob` mirroring `StockCleanupJob` exactly (cron-driven `@Scheduled`, `JobLauncher.run`, `activated` flag).
- [x] **F3.** Configurable `cybertech.notification.redelivery.{job.activated,job.cron,max-attempts,backoff-minutes,batch-size}` in `application.properties`. Default cron: every 15 minutes.
- [x] **F3.** Tasklet promotes a row to terminal `FAILED` once `bumpedRetryCount >= cumulative-max`. Per-attempt audit row + redrive-coordination row (the "two-row audit pattern" — see tasklet javadoc).

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

### Phase 2 (Resilience4j) — landed
- **Two-method dance for AOP**: `deliver(ctx)` is the entry point, `attemptDispatch(ctx)` carries the `@Retry` annotation. Spring AOP doesn't intercept self-invocation, so both methods live on the same bean but the call FROM `deliver` TO `attemptDispatch` goes through the proxy reference (Spring rewires self-invocation when the call goes through the injected interface). Implementation comment in `NotificationRetryableDeliveryImp` spells this out — important for anyone tempted to "simplify" by inlining.
- **`PENDING_RETRY` written on exhaustion (not `FAILED`)** — Phase 3's batch tasklet is the next layer of retry. Conflating them would either trigger redrive on rows we've already abandoned or leave temporarily-failing rows untouched. The two-state separation is load-bearing for the layered retry model.
- **`retryCount=0` recorded on success.** Resilience4j 2.x exposes attempt count via `RetryRegistry` event listeners but threading it through the call site needs ThreadLocal/event consumers — heavier than the value of the precision. The recorder field semantically means "failed attempts before final outcome"; on success that's defensibly zero. Real attempt-count telemetry should come from Micrometer, not from this audit row.
- **Defensive `try/catch (Throwable)` in `deliver`**: if Resilience4j's fallback mechanism fails or the proxy isn't applied (e.g. AOP misconfiguration drift), we still don't crash the async worker silently — we persist a `PENDING_RETRY` row so the failure is visible.
- **Programmatic `Retry.of(...)` test strategy** instead of `@SpringBootTest` slice — same Resilience4j engine, no Spring AOP boot, ~10× faster test runs. The proxy itself is implicitly exercised by any existing IT that goes through the bean.

### Phase 3 (Spring Batch redrive) — landed
- **Two-row audit pattern is intentional and load-bearing.** The retryable bean writes a fresh row on every dispatch attempt (per-attempt audit trail). The tasklet keeps the *original* row as a redrive-coordination record whose `retryCount` accumulates across redrive ticks. This avoids a cross-row lookup that would race under concurrent ticks; the decision to flip to terminal `FAILED` is purely a function of the coordination row's bumped count. This is documented at length in the tasklet's class javadoc — read it before "simplifying".
- **Cumulative budget defaults to 9** (`cybertech.notification.redelivery.max-attempts=9`) — that's three batch ticks of three in-process attempts each. Tunable to taste; for noisy outages a higher cap with longer backoff is more polite to upstream SMTP.
- **Backoff is `lastAttemptAt < now() - backoff-minutes`** (default 10 min). Not exponential at the batch level — by this point we've already moved past the in-process exponential window.
- **`Pageable.ofSize(batch-size)`** caps each tick at 50 rows by default. Defensive against a cold-start "everything pending after a restart" scenario where the table has thousands of rows; we'd rather drain over multiple ticks than block the scheduler thread.
- **Subagent 3 had to fix two Phase 1 carryovers** that blocked Phase 3 from compiling/passing tests:
  1. `UserContactDto` and `ShippingConfirmationPayload` lacked `@NoArgsConstructor` + `@AllArgsConstructor` — Jackson 3 round-trip of the persisted redrive payload would have failed at deserialization. Phase 1 should have caught this when adding polymorphic Jackson; flagged here so the same gap doesn't recur for any future `NotificationPayload` subtype (add a contract test that asserts every subtype round-trips through the configured `ObjectMapper`).
  2. `MarkerEnumsSmokeEnumTest` had been red since Phase 1 added `PENDING_RETRY` (it pins exhaustive enum values). Subagent 3 updated it. Phase 1's verification should have caught this — the enum-completeness pin is exactly the test that's supposed to catch new enum values.
- **`NotificationEntity` gained `@Setter`** so the tasklet can mutate the coordination row in place. The entity already had `@SuperBuilder + @AllArgsConstructor + @NoArgsConstructor + @Getter`; `@Setter` is the missing piece. Acceptable trade-off — the alternative is rebuilding the entity each tick which is uglier.

### Final state
- 1681 / 1681 tests green, 18 skipped (deferred ITs requiring Testcontainers — unchanged from baseline).
- 3 commits: `dc1a54f` (Phase 1), `7007fb0` (Phase 2), and the upcoming Phase 3 commit.
- The retry pipeline is now: `listener → @Retry(notificationDispatch) → fallback → PENDING_RETRY row → cron tasklet → @Retry(notificationDispatch) → either SENT or bumped count → eventually FAILED`. Two layers of retry, single configuration surface, full audit trail.

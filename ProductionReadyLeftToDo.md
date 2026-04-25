# Production-Ready: Remaining Work to Launch Cybertech

> **Self-contained playbook.** A future Claude Code session can pick this up without prior context.
> Companion docs: `progress.md` (work already done across 6 bug-fix waves), `docs/openapi/openapi.yaml`, `docs/postman/`.

---

## 0. Prompt for Claude Code (READ THIS FIRST)

You are continuing the Cybertech production-launch effort. The application code is feature-complete and bug-free across 6 waves of fixes (see `progress.md`). What remains is everything between "code is correct" and "this serves real customers."

**Your task:** Dispatch up to 9 subagents in parallel — one per task in §4 below — to drive the application to production. Follow these rules:

1. **Read `progress.md` first.** It catalogs every prior fix, the residual issues (PRE-1 to PRE-11), the test posture, and orchestration lessons (commit-collision avoidance via `git diff --cached --name-only`). Reuse those patterns.
2. **Respect the dependency graph in §3.** Tasks 1, 7 can launch immediately. Tasks 2 must precede 3. Task 3 must precede 4. Task 4 should precede 5. Task 6 (pen test) usually runs last. Task 8 (load + UAT) needs staging, so after 4. Task 9 (ops readiness) overlaps with everything but signs off last.
3. **Several tasks need human decisions before code can be written** (managed DB provider, prod domain, SMTP provider, observability vendor). Each task section flags the decisions it needs. If a decision is missing, the subagent must STOP and report back rather than guess.
4. **Each subagent commits its own scope only.** Use `git add <explicit paths>` then `git diff --cached --name-only` to verify before commit. Never `git add -A`.
5. **Update `progress.md` after each subagent completes** so the campaign remains crash-resilient. Append to the SITREP table; tick the matching to-do; add any newly-discovered issues to the Remarks section.
6. **Do NOT touch `NotificationListener.java` / `NotificationOutcomeRecorder.java` / `NotificationRepository.java` / their tests** — the user is iterating on those (notification refactor Phase 1/3 and 2/3 already landed; Phase 3/3 may be in flight).
7. **The `progress.md` PRE-1..PRE-11 list should be addressed by Task 1 in this file** — they are the fastest tasks and unblock nothing else.

When all 9 tasks land cleanly, run a final smoke check: `mvn verify` (must stay green) + `cd front/app && npm run build` (must stay green) + a manual call against the staging URL.

If a task surfaces work that doesn't fit its scope, file it in `progress.md` under "Remarks / follow-ups" rather than expanding the agent's own scope.

---

## 1. Project context

**Stack:**
- **Backend:** Spring Boot 4.0.4, Java 26 (preview), Spring Cloud 2025.1.1, Maven (`./mvnw`).
- **Persistence:** MySQL 8.4 + JPA, MongoDB (events), Elasticsearch 7.17.10 (search — see PRE-6), Redis (cache + locks).
- **Auth:** Keycloak 26.0.4 (admin client + OAuth2 resource server). JWT via jjwt 0.12.6.
- **Payments:** Stripe Java SDK 31.4.0 (PaymentIntent + webhooks HMAC-SHA256). API_VERSION skew already mitigated in `PaymentWebhookServiceImp` via `deserializeUnsafe()` fallback (commit `04ea74b`).
- **External:** AWS S3 4.0.0 (product images), Spring Mail + Thymeleaf (currently Mailpit dev — see Task 2).
- **Async/Batch:** Spring Kafka, Spring Batch (cleanup, daily summary, ship-paid).
- **Resilience/Config:** Resilience4j, Spring Cloud Vault (already wired but needs prod values), springdoc-openapi 3.0.1.
- **Frontend:** Next.js 16 (App Router) + React + TypeScript + Auth.js (NextAuth v5) + Keycloak provider. Lives under `front/app/`.

**Recent state (from `progress.md`):**
- Wave 1: 14 Tier-1 backend fixes (data-loss / IDOR / business logic).
- Wave 2: 14 Tier-2 backend fixes (cache / TX / DTOs / N+1).
- Wave 3: 4 regression fixes + 6 IT triage agents. Stripe API_VERSION skew was the highest-impact prod-relevant fix.
- Wave 4: OpenAPI doc generated (`docs/openapi/openapi.yaml`).
- Wave 5: Postman collection (`docs/postman/`, 58 requests, Keycloak auto-token script).
- Wave 6: Frontend `OrderStatus` alignment + `RefreshTokenError` redirect.
- **Test posture:** 1681 / 1681 unit pass, 51 / 51 IT pass, 1 IT skipped (BUG-160 — closed by Task 1 PRE-2).

**API surface:** `/api/v1/services/...` (versioned via `spring.mvc.apiversion.enabled=true`, default `1.0`, header `X-API-VERSION`). Backend port: **8081**. Keycloak realm: `cybertech`. Client id: `cybertech-user-management-client`.

**Repository:** branch `dev/develop`. PR target: `master`. Current uncommitted work in the user's tree: `NotificationListener.java`, `NotificationOutcomeRecorder.java` (Phase 3/3 in flight — DO NOT TOUCH).

---

## 2. Estimate summary (one engineer, sequential)

| Task | Low | High |
|---|---|---|
| 1. Backend PRE cleanup | 3 d | 3 d |
| 2. Prod config + secrets | 3 d | 3 d |
| 3. Infrastructure / deploy | 9 d | 9 d |
| 4. CI/CD | 4 d | 4 d |
| 5. Observability | 5 d | 5 d |
| 6. Security hardening | 7 d | 12 d |
| 7. Frontend production | 4 d | 4 d |
| 8. Load + UAT | 5 d | 8 d |
| 9. Operational readiness | 5.5 d | 5.5 d |
| **TOTAL** | **~46 d** | **~54 d** |

**Calendar:**
- 1 engineer, sequential: **9–11 weeks**
- 2 engineers, mostly parallel: **5–6 weeks**
- 3 engineers + dedicated DevOps: **3–4 weeks**

---

## 3. Dependency graph

```
        ┌────► Task 1 (PRE cleanup)        ─── independent ─── runs anytime
        │
        │                                                    ┌─► Task 5 (observability)
        ├────► Task 2 (prod config) ─► Task 3 (infra) ─► Task 4 (CI/CD) ─┤
        │                                                    └─► Task 8 (load + UAT)
START ──┤
        ├────► Task 7 (frontend prod) ─── parallel with backend infra
        │
        ├────► Task 6 (security hardening: code-side parallel; pen test gates GA)
        │
        └────► Task 9 (ops readiness — overlaps everything; signs off last)
```

**Dispatch ordering:**
- **Wave A (immediate):** Tasks 1, 2, 7. (Task 7 only needs the prod domain; Task 2 needs all secret values.)
- **Wave B (after A):** Tasks 3, 6 (code parts), 9 (kickoff doc drafts).
- **Wave C (after B):** Tasks 4, 5.
- **Wave D (after C):** Task 8 (load + UAT) and Task 6 (pen test).
- **Sign-off:** Task 9 final review.

---

## 4. The 9 tasks

### Task 1 — Backend pre-existing cleanup (PRE-1 → PRE-11)

**Goal:** Close the 11 residual issues catalogued in `progress.md` § Remarks. None block deploy individually but together they're a quality bar.

**Decisions needed:** none.

**Sub-items:**
- **PRE-1** Add `orphanRemoval = true` to `OrderEntity.@OneToMany orderItemEntities`. Confirm `OrderManagementServiceImp.updateOrder` no longer leaves orphan rows (test by exercising the update path in an IT).
- **PRE-2** Schema migration: add `UNIQUE(userId)` to `cartTable` in **both** `src/main/resources/sql/databaseSchemaInitFile.sql` AND `src/main/resources/k8s/helm/charts/mysql-chart/databaseSchemaInitFile.sql`. Add retry-on-`DataIntegrityViolationException` in `CartServiceImp.addItemsToCart`. Re-enable `CartFlowIT.concurrentAddsFromTwoThreadsShouldSumNotRace` (currently `@Disabled` since commit `3da6c17`).
- **PRE-3** Whitelist `/actuator/health` and `/actuator/info` in `SecurityConfig.PUBLIC_URLS`. Keep other actuator endpoints behind ADMIN. Required for k8s probes and Prometheus scrape.
- **PRE-4** `BankCardCreationRequestDto.isDefault` — set a default `false` (Lombok `@Builder.Default`) OR make the entity column nullable. Pick whichever has smaller blast radius.
- **PRE-5** Add a real `<profile id="integration-test">` to `pom.xml` so `-P integration-test` actually gates ITs (currently a no-op, failsafe runs unconditionally).
- **PRE-6** Pick ES 7.17.10 OR 8.x and align both client and server. Current state: 8.x client + 7.17.10 server in `TestcontainersConfiguration` → `cluster.health` cannot decode. Recommend upgrading to 8.x server (Spring Data Elasticsearch 5.x supports 8.x); update `TestcontainersConfiguration` and any production helm chart.
- **PRE-7** Delete the orphaned `RegistrationControllerApiSpec.java` (no controller implements it; `register` endpoint lives on `UserManagementController`).
- **PRE-8** Extract `DiscountController` and `DiscountAdminController` into `*ApiSpec` interfaces for consistency.
- **PRE-9** Add `getOrderStatusByUuid` and `placeOrder2` to `OrderManagementControllerApiSpec`.
- **PRE-10** Trim leading whitespace in `@Tag(name = " CartController", ...)`, `@Tag(name = " OrderManagementController", ...)`, `@Tag(name = " BankCardManagementController", ...)`.
- **PRE-11** Normalize OpenAPI security scheme name: `OpenApiConfig` registers `keycloak` while older specs reference `bearerAuth`. Pick one (`keycloak` is more descriptive).

**Acceptance criteria:**
- All 11 items committed.
- `mvn verify` still green (1681+ unit / 51+ IT including the re-enabled BUG-160).
- Regenerate `docs/openapi/openapi.yaml` so the spec name normalization (PRE-11) is reflected.

**Files most likely touched:** `entities/OrderEntity.java`, `services/implementation/CartServiceImp.java`, both SQL init files, `config/SecurityConfig.java`, `dto/request/user/BankCardCreationRequestDto.java`, `pom.xml`, `api/controllers/spec/*ApiSpec.java`, `api/controllers/implementation/Discount*Controller.java`, `config/OpenApiConfig.java`, `docs/openapi/openapi.yaml`.

---

### Task 2 — Production configuration + secrets

**Goal:** Stand up `application-prod.properties`, real Vault-backed secrets, real CORS, prod SMTP.

**Decisions needed (from human BEFORE this task starts):**
- Prod base URL (frontend domain + API domain — same or different host?).
- SMTP provider (AWS SES? Mailgun? SendGrid? company's existing relay?).
- Vault server URL + auth method (Kubernetes service account? AppRole?).
- Stripe production keys + webhook signing secret.
- Keycloak prod realm + client (likely a separate realm from `cybertech` dev realm).
- AWS account / IAM role for S3.

**Sub-items:**
- Create `application-prod.properties` with profile-specific overrides for: DB connection, Mongo URI, Redis cluster nodes, Kafka brokers, Elasticsearch URI, Keycloak issuer URI + client id/secret references, Stripe keys + webhook secret, S3 bucket + region, SMTP host/credentials, mail-from address.
- Switch all secret values to `${vault.<path>.<key>}` placeholders. Verify Vault's `bootstrap.properties` already wires `spring.config.import=vault://`.
- Configure `KeycloakRoleConverter` for prod realm.
- Configure CORS in `SecurityConfig` for the prod frontend origin. Allow credentials. Restrict allowed methods to those actually used.
- Replace Mailpit (`src/main/resources/docker/mailpit-data/`) — keep for dev, add real SMTP for prod profile only.
- Add `spring.profiles.active=prod` to the prod container's environment, NOT to `application.properties`.

**Acceptance criteria:**
- `mvn verify -Dspring.profiles.active=prod` (with mock Vault) compiles and starts (smoke run, no real services needed).
- No production secret value committed in plaintext anywhere in the repo (check via `git secrets` or trufflehog).

---

### Task 3 — Infrastructure / deploy

**Goal:** Bring up the runtime: managed services, Helm charts, ingress + TLS, DB migration tool.

**Decisions needed:**
- Cloud provider (AWS / GCP / Azure / on-prem).
- Managed-service vs self-hosted for each of: MySQL, MongoDB, Elasticsearch, Redis, Kafka, Keycloak.
- DNS strategy + TLS issuer (Let's Encrypt via cert-manager? ACM?).

**Sub-items:**
- Audit existing `src/main/resources/k8s/helm/` charts (mysql-chart confirmed present; verify others).
- Write/extend Helm chart for the Spring Boot app: `Deployment`, `Service`, `ConfigMap` (non-secret config), `ExternalSecret` (Vault → k8s secret) or `SecretProviderClass` (CSI), liveness/readiness probes (use `/actuator/health/liveness` and `/actuator/health/readiness`), HPA on CPU + custom metric (orders/sec).
- Helm chart for the Next.js frontend (or use Vercel — decision needed).
- Provision managed services: RDS MySQL 8.4, MongoDB Atlas, Elastic Cloud (8.x — see PRE-6), ElastiCache Redis or Redis Cloud, MSK Kafka.
- Ingress: nginx-ingress + cert-manager + Let's Encrypt (or AWS ALB + ACM). Two routes: `api.<domain>` → Spring app, `<domain>` → Next.js.
- **DB migration:** add Flyway (preferred over Liquibase for SQL-first projects). Move both SQL init files into `src/main/resources/db/migration/V1__init.sql`. Add Flyway maven plugin and `spring.flyway.enabled=true` for the `prod` profile. Backwards-compat: leave the existing init files for `test` profile.
- Terraform / OpenTofu module for the cloud bits if the team uses IaC.

**Acceptance criteria:**
- `helm install --dry-run` succeeds for both charts.
- A staging deploy works end-to-end (frontend reaches backend, backend reaches DB / Redis / Mongo / ES / Kafka / Keycloak / Stripe).
- Flyway migration applies cleanly to a fresh DB and to a DB pre-populated by the old init files (no duplicate-table errors).

---

### Task 4 — CI/CD

**Goal:** Auto-build, test, deploy on push.

**Decisions needed:**
- CI provider (GitHub Actions / GitLab CI / Jenkins?).
- Container registry.
- Approval flow (auto to staging, manual to prod?).

**Sub-items:**
- `.github/workflows/ci.yml` (or equivalent): lint, unit test, IT (with Testcontainers — note Java 26 + `--enable-preview`), JaCoCo coverage gate, OpenAPI spec generation + diff check vs `docs/openapi/openapi.yaml`.
- Frontend job: `cd front/app && npm ci && npm run lint && npm run build`.
- Container image build (Spring Boot → Jib or Buildpacks for layered images; Next.js → multi-stage Dockerfile with standalone output) and push to registry on merge to `master`.
- Auto-deploy to staging Helm release.
- Manual approval gate to prod.
- Rollback: keep last N image tags; `helm rollback` runbook.
- Canary or blue-green via Argo Rollouts (optional but recommended for the order-placement path).

**Acceptance criteria:**
- A test PR runs the full pipeline in <15 minutes.
- A merge to `master` deploys to staging within 10 minutes of merge.
- A `helm rollback` returns the previous revision in <2 minutes.

---

### Task 5 — Observability

**Goal:** Logs, metrics, tracing, alerts.

**Decisions needed:**
- Log aggregator (Loki + Grafana? ELK? Datadog?).
- Metrics backend (Prometheus + Grafana? Datadog? CloudWatch?).
- Tracing backend (Tempo? Jaeger? Honeycomb?).
- Paging tool (PagerDuty? Opsgenie? Grafana OnCall?).

**Sub-items:**
- Switch logback to JSON encoder for prod profile (`logstash-logback-encoder`). Include MDC keys: `traceId`, `spanId`, `userKeycloakId` (already in `MdcTaskDecorator`), `orderUuid` where applicable.
- Expose `/actuator/prometheus`. Add to `management.endpoints.web.exposure.include=health,info,prometheus`.
- Grafana dashboards: orders/sec, payment success rate, Stripe webhook 4xx/5xx, cart cache hit rate, order status distribution, JVM heap + GC, Hibernate query rate, Kafka lag.
- Add `io.opentelemetry:opentelemetry-spring-boot-starter` (or OTel java agent in the container) and configure OTLP export.
- Distributed-trace propagation: the auto-instrumentation covers Spring MVC, JPA, Redis, Kafka, RestClient. Verify the trace flows from Next.js (use `@vercel/otel` or similar) → Spring → DB on a place-order request.
- Alert rules: 5xx rate > 1%/5min, payment success rate < 95%/15min, IT failure on `master`, JVM heap > 85%/10min, DB connection pool saturated, Stripe webhook signature failures > 5/min.

**Acceptance criteria:**
- A staging order placement produces a single trace spanning frontend → API → DB → Stripe.
- All 7 alert rules fire correctly when manually tripped on staging.

---

### Task 6 — Security hardening

**Goal:** Reach a defensible security baseline.

**Decisions needed:**
- Pen-test vendor (or in-house security team).
- Whether to use a WAF (CloudFront / Cloudflare / AWS WAF).

**Sub-items (code-side, parallelizable):**
- Add `Bucket4j` rate-limit filter on `/register`, `/auth/login`, `/order/place`, `/cart/*`. Tune limits per endpoint.
- Add Spring Security headers: HSTS (`Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`), CSP (start with report-only), X-Frame-Options, X-Content-Type-Options.
- Stripe webhook IP allowlist filter (Stripe publishes the IP ranges).
- Verify all `*ApiSpec` endpoints have a `@PreAuthorize` (sweep — use the regression-review pattern).
- Audit `SecurityConfig.PUBLIC_URLS` after Task 1 PRE-3 lands; ensure no PII-exposing endpoint slips through.
- Cookie flags: `Secure`, `HttpOnly`, `SameSite=Lax` for the Auth.js session cookie.
- GDPR right-to-erasure: implement `DELETE /user/me` that anonymizes (not deletes) `UserEntity` and tombstones related Orders/Reviews. (Coordinate with Task 9 GDPR runbook.)
- Secret rotation runbook: documented procedure for rotating Vault, Keycloak client secret, Stripe key, S3 IAM key.

**Sub-items (process / external):**
- External pen test (1–2 weeks calendar, 3–5 days remediation).

**Acceptance criteria:**
- OWASP ZAP baseline scan against staging shows no medium/high findings.
- Pen-test report has all findings either fixed or accepted-with-mitigation.

---

### Task 7 — Frontend production

**Goal:** Polish + observability + compliance for the Next.js app.

**Decisions needed:**
- Hosting (self-hosted on the k8s cluster vs Vercel).
- Analytics provider (Posthog? Amplitude? Plausible? or none).
- Error-tracking (Sentry vs alternatives).
- Cookie-consent vendor (Cookiebot? Osano? in-house?).

**Sub-items:**
- Sentry SDK wiring: capture client + server errors. Source-maps uploaded on build.
- Image domains allowlist in `next.config.ts` for the prod S3 bucket.
- SEO: `<head>` meta per page, OpenGraph tags, sitemap.xml, robots.txt.
- GDPR cookie banner with consent management; gate analytics on consent.
- `NEXT_PUBLIC_API_BASE_URL` env var pointed at the prod API host.
- Build-time strict mode: `next.config.ts` `reactStrictMode: true`, `eslint.ignoreDuringBuilds: false`, `typescript.ignoreBuildErrors: false`.
- Browser/responsive QA pass (BrowserStack or manual): Chrome, Safari, Firefox, Edge × desktop + mobile.

**Acceptance criteria:**
- `npm run build` produces optimized output with no warnings.
- A production-build smoke shows: place-order, view-order, refresh-token-error → redirect — all working.
- Sentry catches a manually-thrown error.

---

### Task 8 — Load testing + UAT

**Goal:** Prove the system holds under realistic load and stakeholders accept the build.

**Decisions needed:**
- Target SLOs (p95 latency, RPS, concurrent users).
- Stakeholder list for UAT.

**Sub-items:**
- Gatling or k6 scenarios:
  1. Browse catalog → add to cart → place order at 50 / 200 / 500 RPS.
  2. Hot-product flash sale: 1000 concurrent BUY_ONE_GET_ONE_FREE add-to-cart.
  3. Stripe webhook firehose: 100 PaymentSucceeded webhooks/sec.
- Run against staging. Record p50/p95/p99 latency, error rate, JVM/DB metrics.
- Tune: connection-pool sizes (HikariCP), Redis connection count, JVM heap, JPA batch size. The recently-added optimistic-lock retry in `cancelOrder` may need iteration count tuning.
- UAT: walkthrough sessions with stakeholders covering the user journeys defined in the Postman collection. Track defects in the project's issue tracker.
- Bug-bash with the dev team: 1-day session.

**Acceptance criteria:**
- Defined SLOs hit at target RPS without errors.
- All UAT defects resolved or accepted.

---

### Task 9 — Operational readiness

**Goal:** The team can run this in production: backups, runbooks, on-call, compliance.

**Decisions needed:**
- On-call rotation members + paging schedule.
- Data retention policy (orders: 7 years for accounting? reviews: indefinite? user events: 90 days?).
- Privacy policy + terms of service (legal).

**Sub-items:**
- Runbooks (markdown under `docs/runbooks/`):
  - `payment-outage.md` (Stripe down — orders stuck in AWAITING_PAYMENT).
  - `keycloak-outage.md` (auth down — what subset of the app keeps working).
  - `db-failover.md` (RDS multi-AZ failover behavior).
  - `redis-outage.md` (cache cold — graceful degradation paths).
  - `kafka-outage.md` (event publishing fails — consequences for review pipeline / events).
  - `webhook-replay.md` (Stripe replay for missed events; dedup ledger semantics).
  - `secret-rotation.md` (per-service procedure).
  - `release-rollback.md` (helm rollback steps).
- Backup + restore: validated for MySQL (RDS PITR), MongoDB (Atlas snapshots), Elasticsearch (S3 snapshot repository).
- DR drill: simulate region loss in staging.
- GDPR: data inventory (User PII, Order PII, Review content), DPA agreements with subprocessors (Stripe, Keycloak host, S3, Mongo, ES, Mailgun). Data-retention scheduled job (Spring Batch).
- Privacy policy + terms of service published.
- On-call: PagerDuty service for `cybertech-prod`, alert routing, escalation policy, schedule.
- SLO/SLA documented per endpoint (or tier).

**Acceptance criteria:**
- A backup-restore drill restores a known-good MySQL snapshot in <30 min.
- A simulated PagerDuty alert routes to the correct on-call.
- Privacy policy URL is live and linked from the frontend footer.

---

## 5. Risks (in priority order)

1. **Pen-test surprises (Task 6).** The biggest schedule risk. Budget 1-2 weeks of remediation on top of the test itself. Mitigate by running OWASP ZAP early in Task 6.
2. **Vault / Keycloak prod integration (Task 2).** Real auth flows often reveal CORS / cookie / redirect-URI issues that take days to debug. Mitigate by setting up a "prod-like" staging Keycloak realm in Task 3.
3. **Stripe live-mode mismatch (Task 2 / 6).** `BUG-522` already pins the live-mode-mismatch defense. Verify the production webhook secret matches the production account's livemode and the `application-prod.properties` setting.
4. **ES 7→8 migration (PRE-6 / Task 3).** If you upgrade the server, all existing indices may need reindexing. Mitigate by running a parallel 8.x cluster, dual-writing, then cutting over.
5. **Optimistic-lock retry contention (Task 8 finding).** The Wave 3 M2 fix added a 3-attempt retry on `cancelOrder`. Under high-concurrency cancel + paid-listener load, retries may exhaust. Tune iteration count + add metric.
6. **Flyway migration on existing data (Task 3).** If staging already has data, the V1 migration will collide. Add a checksum-bypass or `flyway:baseline` step.
7. **GDPR (Task 9).** French codebase; PII is real (email, address, phone, DOB). Right-to-erasure must work end-to-end including the Mongo events store.

---

## 6. Decisions the human owes the orchestrator

Before dispatch, get these from a stakeholder. Group them into a single decision-doc and reference it from each subagent's prompt.

1. Cloud provider + managed-service choices (Task 3).
2. Production domain (frontend + API).
3. SMTP provider (Task 2).
4. CI provider + container registry (Task 4).
5. Observability stack (logs / metrics / tracing / paging).
6. Pen-test vendor + scheduled date (Task 6).
7. Frontend hosting (k8s vs Vercel) (Task 7).
8. Target SLOs (Task 8).
9. On-call team + GDPR policies (Task 9).

If a decision is missing for a given task, the subagent must STOP and report rather than guess.

---

## 7. Reference

- `progress.md` — detailed log of every wave + commit + finding.
- `docs/openapi/openapi.yaml` — canonical API contract.
- `docs/postman/` — annotated collection + Keycloak auto-token script.
- `CLAUDE.md` — project conventions.
- Recent commits on `dev/develop` — the 6-wave fix history.

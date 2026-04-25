# Cybertech operational runbooks

This directory holds the operational runbooks used during incidents and routine operational tasks (rotations, rollbacks, GDPR requests).

> **Portfolio-scope disclaimer:** Cybertech is a local minikube portfolio deployment, not a real cloud production system. Some sections of these runbooks describe what would be done on real production infrastructure (PagerDuty, multi-AZ failover, automated backups, real DPAs). Those sections are explicitly marked as future work; on portfolio they are accepted gaps.

## Pre-incident — bookmark these before you need them

- **Grafana dashboards**: http://grafana.cybertech.local — start here for any "is something on fire?" question. RED dashboard, Auth dashboard, Payments dashboard, Cache dashboard, DB dashboard.
- **Postman collection**: [`docs/postman/`](../postman/) — full API surface as importable collection, used for smoke tests during recovery.
- **OpenAPI spec**: [`docs/openapi/openapi.yaml`](../openapi/openapi.yaml) — authoritative API contract.
- **Helm charts**: [`src/main/resources/k8s/helm/`](../../src/main/resources/k8s/helm/) — orchestrated by `helmfile.yaml`. README at `src/main/resources/k8s/helm/README.md`.
- **Frontend SITREP**: [`docs/FRONTEND_SITREP.md`](../FRONTEND_SITREP.md) — frontend status overview.

## Runbooks index

| Runbook | One-liner |
|---------|-----------|
| [`payment-outage.md`](./payment-outage.md) | Stripe down or webhook signature failures spiking; orders stuck in `AWAITING_PAYMENT`. |
| [`keycloak-outage.md`](./keycloak-outage.md) | Keycloak unreachable; 401 spike; frontend `RefreshTokenError` redirects. |
| [`db-failover.md`](./db-failover.md) | MySQL crashloop, PVC full or corrupt; `/actuator/health` reports DOWN. |
| [`redis-outage.md`](./redis-outage.md) | Redis pod down or out of memory; cart cache misses spike; rate limiter falls back. |
| [`webhook-replay.md`](./webhook-replay.md) | Replay missed Stripe events safely via the dedup ledger. |
| [`secret-rotation.md`](./secret-rotation.md) | Ordered procedure to rotate Vault, Keycloak client, Stripe API key, Stripe webhook secret, S3. |
| [`release-rollback.md`](./release-rollback.md) | `helm rollback` after a bad deploy, with DB-schema caveats. |
| [`gdpr-data-handling.md`](./gdpr-data-handling.md) | Data inventory, retention policy, right-to-erasure spec (Wave 7 Task 6 backlog), subprocessor list. |

## Conventions

Every runbook follows the same structure:
1. **Symptoms** — what alerts fire / what users complain about.
2. **Quick triage** — 1-2 commands to confirm the diagnosis.
3. **Root cause options** — 2-3 most-likely causes, ordered by probability.
4. **Mitigation** — step-by-step recovery, organized by cause.
5. **Verification** — how to confirm the system is healthy again.
6. **Postmortem checklist** — what to capture for the post-incident review.

## What's NOT here (explicitly out of scope on portfolio)

- **PagerDuty / on-call rotation** — there is no on-call. The operator is whoever is running the demo.
- **Multi-AZ failover** — single minikube node, single AZ.
- **Automated backups + point-in-time recovery** — backlog. See `db-failover.md` "Real prod note".
- **Right-to-erasure endpoint** — backlog. See `gdpr-data-handling.md` for the *intended* spec; manual SQL erasure is the current workaround.
- **Real DPAs** — backlog. See `gdpr-data-handling.md` subprocessor list.

These gaps are documented so future-you (or the portfolio reviewer) knows what would be needed for a real production deployment.

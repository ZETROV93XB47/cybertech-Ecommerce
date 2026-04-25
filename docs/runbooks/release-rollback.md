# Release rollback

## Trigger
Roll back when, within ~10 minutes of a deploy, you see any of:
- 5xx rate above ~1% on the Grafana RED dashboard.
- p95 latency above 500ms on `/cart/*` or `/order/*`.
- Order success rate (paid orders / checkout starts) drops > 10% from baseline.
- `/actuator/health` reports `DOWN` or transient flapping.
- Any regression confirmed by smoke-test failures in CI's post-deploy job.

## Quick triage (decide: rollback or fix-forward?)
- If the bad commit is **obvious and small** (typo, missing env var) AND a fix can ship in < 10 min: fix forward.
- Otherwise: roll back. Don't agonize. The DB caveats below are the only reason not to.

## Steps

### 1. Identify the last good revision
```bash
helm history cybertech-app

# Output looks like:
# REVISION  UPDATED                   STATUS      CHART          APP VERSION  DESCRIPTION
# 15        Sat Apr 25 15:30:00 2026  deployed    cybertech-1.4  1.4.0        Upgrade complete
# 14        Sat Apr 25 14:45:00 2026  superseded  cybertech-1.3  1.3.0        Upgrade complete
# ...
```
Pick the revision number that was healthy (typically the one immediately before the bad deploy).

### 2. Roll back
```bash
helm rollback cybertech-app 14
```
Watch the rollout:
```bash
kubectl rollout status deploy/cybertech-app
```

### 3. Monitor for 5 minutes
- `/actuator/health` returns UP.
- Grafana orders/sec dashboard returns to baseline.
- No new 5xx alerts.
- Spot-check a `payment_intent.succeeded` ledger insertion.

### 4. Roll back the frontend independently if needed
The Next.js app uses its own Helm chart (`front-app-chart`). Its image tag governs which version is deployed:
```bash
helm history front-app-chart
helm rollback front-app-chart <revision>
```
**Do this independently of the backend rollback.** A backend bug rarely requires a frontend rollback and vice-versa.

## Caveats — DB schema changes are NOT auto-rolled back

- Cybertech currently has **no Flyway / Liquibase**. The schema is bootstrapped from `databaseSchemaInitFile.sql` (run by the mysql init container on a fresh PVC).
- **Additive migrations** (new column, new table, new index): forward-compat — old code ignores them. Safe to roll back.
- **Destructive migrations** (drop column, rename table, narrow type): **NOT safe to auto-roll-back**. Old code may reference the dropped column and fail. Manual revert is required:
  1. Identify the destructive change in the deploy diff.
  2. Run the inverse SQL by hand, e.g. `ALTER TABLE orderTable ADD COLUMN <dropped_col> ...;`.
  3. Restore data from the last-good snapshot if data was destroyed (NOTE: backups not implemented in portfolio; see `db-failover.md` "Real prod note").
  4. Then `helm rollback` the app.

**Rule**: any destructive migration must ship in TWO releases (release N adds the new schema, release N+1 removes the old) so a single-step rollback is always safe.

## Verification
- Pod image tag reflects the rolled-back version: `kubectl get pod -l app=cybertech-app -o jsonpath='{.items[0].spec.containers[0].image}'`.
- `/actuator/info` endpoint returns the rolled-back commit SHA / build version.
- Smoke test: log in, place an order, see the webhook process, see the order at `PAID`.
- 5xx rate and p95 latency back to baseline on the Grafana RED dashboard.

## Postmortem checklist
- [ ] What was the bad change? (link to PR / commit)
- [ ] How was it detected? Could it have been caught earlier (more pre-deploy smokes)?
- [ ] Time-to-detect, time-to-rollback.
- [ ] Was the DB caveat hit? If yes, what manual SQL was run and was it scripted for next time?
- [ ] Did the frontend need a coordinated rollback? Why or why not?
- [ ] Action item: add a CI canary check that catches this regression before deploy.

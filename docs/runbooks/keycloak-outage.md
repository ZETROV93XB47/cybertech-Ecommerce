# Keycloak outage (auth unreachable, token refresh failing)

## Symptoms
- Spike in 401s on backend (Grafana -> Auth dashboard, `http_server_requests_seconds_count{status="401"}`).
- Frontend redirects users to `/signin` with a `?error=RefreshTokenError` query param (Wave 6 fix). Visible in Next.js access logs.
- Admin console (https://keycloak.cybertech.local) returns 502/504 or "infinite spinner".
- Backend logs: `JwtValidationException`, `Unable to obtain JWKs from issuer`, or `Connection refused: keycloak:8080`.

## Quick triage
```bash
# 1. Pod state
kubectl get pod -l app=keycloak

# 2. Logs - last 200 lines
kubectl logs -l app=keycloak --tail=200

# 3. Liveness / readiness from inside the cluster
kubectl run curl --rm -it --image=curlimages/curl --restart=Never -- \
  curl -v http://keycloak:8080/health/ready

# 4. JWKs endpoint reachability from the backend pod
kubectl exec deploy/cybertech-app -- curl -sI \
  http://keycloak:8080/realms/cybertech/protocol/openid-connect/certs

# 5. DB connection from Keycloak's perspective
kubectl exec -it $(kubectl get pod -l app=keycloak -o name | head -1) -- \
  sh -c 'echo "show databases;" | mysql -h mysql -uroot -p"$KC_DB_PASSWORD"'
```

## Root cause options (most likely first)

1. **Keycloak DB connection lost** — MySQL down or `keycloakDB` schema missing. See `db-failover.md`. Wave 8A1 introduced the mysql init script that creates the `keycloakDB` schema; if that didn't run on a fresh PVC, Keycloak crashloops on startup.
2. **JVM OOM** — heap too small (default `<256Mi` is borderline for Keycloak 26). `kubectl describe pod` shows `OOMKilled`.
3. **Cert / realm config drift** — realm import file changed but pod was not restarted, or HTTPS cert expired (admin console only).
4. **Admin credentials rotated improperly** — happens when Vault secret was rotated but Keycloak's master realm admin user was not re-seeded.

## Mitigation

### If cause = DB connection lost
- Triage the database first (see `db-failover.md`), then restart Keycloak:
  ```bash
  kubectl rollout restart deploy/keycloak
  ```
- Verify the `keycloakDB` schema exists. If missing (fresh PVC scenario), recreate it manually:
  ```bash
  kubectl exec -it mysql-0 -- mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
    "CREATE DATABASE IF NOT EXISTS keycloakDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  ```
  Then restart the deployment.

### If cause = JVM OOM
- Bump heap in the Helm values (chart at `src/main/resources/k8s/helm/charts/keycloak`):
  ```yaml
  resources:
    limits:
      memory: 1Gi
    requests:
      memory: 512Mi
  env:
    JAVA_OPTS_APPEND: "-Xms256m -Xmx768m"
  ```
- `helm upgrade keycloak src/main/resources/k8s/helm/charts/keycloak`.

### If cause = config drift
- Diff the live realm against the imported JSON: `kubectl get cm keycloak-realm -o yaml`.
- Roll back to the last good values (`helm rollback keycloak <revision>` — see `release-rollback.md`).

### If cause = admin credentials mismatch
- Confirm the admin password matches the Vault secret:
  ```bash
  kubectl get secret keycloak-admin -o jsonpath='{.data.password}' | base64 -d
  ```
- If mismatched, re-seed via the bootstrap admin env vars and restart:
  ```bash
  kubectl set env deploy/keycloak \
    KEYCLOAK_ADMIN=admin \
    KEYCLOAK_ADMIN_PASSWORD="$(vault kv get -field=password secret/keycloak/admin)"
  kubectl rollout restart deploy/keycloak
  ```

## Partial-failure mode (what stays up)
While Keycloak is down, only **read-only product browsing** works (the catalogue endpoints don't require JWT). The following are degraded:
- Cart, wishlist, checkout (need user identity)
- Order placement and order history
- Account self-service
- Admin moderation tooling

The frontend should display a banner: "Sign-in temporarily unavailable. You can still browse products."

## Verification
- `curl http://keycloak:8080/health/ready` returns `{"status":"UP"}`.
- Backend log: `JwkSetUriJwtDecoder` no longer emitting connection errors.
- End-to-end smoke: log in as `cybertech-test-user`, refresh token after 60s, place an order.
- Grafana 401 rate back to baseline.

## Postmortem checklist
- [ ] Duration of unavailability.
- [ ] How many users hit `RefreshTokenError`?
- [ ] Was the DB the root cause? Cross-link to `db-failover.md` postmortem.
- [ ] Was the realm import idempotent? If not, file an issue.
- [ ] Action item: add a synthetic check that hits `/health/ready` every 30s and fires an alert after 2 consecutive failures.

# Secret rotation

## Why order matters
Secrets must be rotated in this **specific sequence** so that in-flight requests are not lost mid-rotation. The Stripe webhook secret is rotated **last** because Stripe will keep signing events with the previous secret for a short overlap window — rotating it first would risk dropping events while other rotations are in progress.

## Order of operations
1. **Vault root token** — only the operator's auth credential, no app traffic affected.
2. **Keycloak client secret** — backend re-authenticates to Keycloak admin API.
3. **Stripe API key** — outbound calls to Stripe (charges, refunds).
4. **Stripe webhook secret** — inbound from Stripe to us. **Last.**
5. **S3 / localstack credentials** — read/write to product-images bucket.

Each step has 1-2 min of partial degradation. Schedule during a low-traffic window if possible (portfolio: any time is fine; document the maintenance window for the postmortem record).

---

## Step 1 — Vault root token (~30s)
- Generate the new token via the Vault UI / CLI.
- Update operator's `~/.vault-token` and the CI/CD secret store.
- No pod restart needed.
- **Verification**: `vault token lookup` succeeds with the new token.

## Step 2 — Keycloak client secret (~1-2 min)
1. In the Keycloak admin console: **Clients -> cybertech-backend -> Credentials -> Regenerate Secret**. Copy the new value.
2. Update Vault: `vault kv put secret/keycloak/client client_secret=<new>`.
3. Restart the backend so it picks up the new secret:
   ```bash
   kubectl rollout restart deploy/cybertech-app
   ```
4. **Verification**: `kubectl logs deploy/cybertech-app | grep "Keycloak admin client initialized"`. Place a test order — admin-side calls (e.g. role assignment) succeed.

## Step 3 — Stripe API key (~1-2 min)
1. In the Stripe Dashboard: **Developers -> API keys -> Roll secret key**. Note the rolling window — old key remains valid for 12h by default; you can shorten it but DO NOT revoke immediately.
2. Update Vault: `vault kv put secret/stripe/api secret_key=<new>`.
3. Restart the backend:
   ```bash
   kubectl rollout restart deploy/cybertech-app
   ```
4. **Verification**: place a £1 test order. The PaymentIntent is created, webhook fires, ledger row appears.
5. After confirming the new key works in production, revoke the old key in the Stripe Dashboard.

## Step 4 — Stripe webhook secret (~1-2 min) — DO THIS LAST
1. In the Stripe Dashboard: **Developers -> Webhooks -> select endpoint -> Roll secret**.
2. Stripe accepts BOTH the old and the new secret for ~24h, so you have a safe overlap window.
3. Update Vault: `vault kv put secret/stripe/webhook signing_secret=<new>`.
4. Restart backend: `kubectl rollout restart deploy/cybertech-app`.
5. **Verification**: send a test event from the Stripe Dashboard ("Send test webhook") with `payment_intent.succeeded`. See it 200 in the dashboard and a row appear in `stripe_event_ledger`.
6. After confirming, you can shorten the rolling window from 24h.

## Step 5 — S3 / localstack credentials (~30s on portfolio)
- **Portfolio (localstack)**: just rebuild the localstack pod with new credentials baked in:
  ```bash
  kubectl delete pod -l app=localstack
  ```
  Localstack accepts any AWS-style credentials, so this is mostly a config-drift exercise.
- **Real prod (real AWS)**: rotate IAM keys via AWS Console -> IAM -> Users -> Security credentials. Update Vault, restart pods.

## Verification (full smoke after all rotations)
```bash
# 1. Liveness
kubectl exec deploy/cybertech-app -- curl -s localhost:8080/actuator/health/livez

# 2. Readiness with all dependencies
kubectl exec deploy/cybertech-app -- curl -s localhost:8080/actuator/health | jq .components

# 3. End-to-end: log in as cybertech-test-user, place a £1 order, see webhook fire, see PAID status
```

## Postmortem checklist
- [ ] Total downtime per step (record actuals vs estimates).
- [ ] Were any in-flight requests lost? Cross-reference order success rate during the rotation window.
- [ ] Was the Stripe webhook overlap window confirmed before revoking the old secret?
- [ ] Are all rotations recorded in the secret-rotation log (date, operator, reason)?
- [ ] Action item: schedule the next rotation per security policy (e.g. every 90 days).

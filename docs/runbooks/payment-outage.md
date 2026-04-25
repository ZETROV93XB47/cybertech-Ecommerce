# Payment outage (Stripe down or webhook signature failures)

## Symptoms
- Grafana alert: `stripe_webhook_status{code=~"4..|5.."}` rate spike (`http://grafana.cybertech.local` -> Payments dashboard).
- Orders stuck in `AWAITING_PAYMENT` more than ~5 min after checkout.
- Increased customer reports: "I paid but my order is not confirmed."
- Backend logs: `SignatureVerificationException` from `StripeWebhookService` or repeated `PaymentIntent.requires_action` traces.

## Quick triage
```bash
# 1. Stripe-side health
curl -sI https://status.stripe.com/api/v2/status.json | head -5

# 2. App-side health
kubectl exec -it deploy/cybertech-app -- curl -s localhost:8080/actuator/health | jq

# 3. Stuck orders in the last hour
kubectl exec -it mysql-0 -- mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cybertech -e \
  "SELECT id, status, created_at FROM orderTable \
   WHERE status='AWAITING_PAYMENT' AND created_at > NOW() - INTERVAL 1 HOUR \
   ORDER BY created_at DESC LIMIT 50;"

# 4. Recent webhook ledger activity
kubectl exec -it mysql-0 -- mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cybertech -e \
  "SELECT event_id, event_type, received_at FROM stripe_event_ledger \
   ORDER BY received_at DESC LIMIT 20;"
```

## Root cause options (most likely first)

1. **Stripe API outage** — status page red/yellow. Nothing we can do but wait + replay later.
2. **Webhook signature mismatch** — happens when the Stripe webhook secret in Vault drifts from the one configured in the Stripe Dashboard, or when a *test-mode* key is used against *live-mode* events (and vice versa). See Wave 3 BUG-522 / commit `04ea74b`. Symptom: 100% of webhooks fail with `SignatureVerificationException`, none with 200.
3. **Stripe IP allowlist filter blocking legitimate traffic** — Wave 7B added an IP allowlist guard (`cybertech.security.stripe-ip-allowlist`). If Stripe's IP list rotated and our config was not updated, legitimate webhooks get rejected at the filter level (HTTP 403 before reaching the controller).
4. **Network partition** — egress from our cluster to `api.stripe.com` blocked (corp firewall, DNS issue inside minikube).

## Mitigation

### If cause = Stripe outage
- Confirm via https://status.stripe.com — if green but our errors elevated, page Stripe support with our account ID + webhook endpoint URL.
- Communicate via status banner on frontend: "Payments temporarily unavailable, your cart is saved."
- Once Stripe recovers, follow `webhook-replay.md`.

### If cause = webhook signature mismatch
- Verify which mode the dashboard endpoint is in (Test vs Live):
  ```bash
  # Compare the secret prefix - whsec_test_... vs whsec_...
  kubectl get secret stripe-webhook-secret -o jsonpath='{.data.value}' | base64 -d | head -c 12
  ```
- If mismatched, rotate the webhook secret following `secret-rotation.md` (Stripe webhook secret step). Do NOT manually edit pods.

### If cause = IP allowlist blocking
- Emergency bypass (use only as a temporary mitigation, REVERT once root cause identified):
  ```bash
  kubectl set env deploy/cybertech-app CYBERTECH_SECURITY_STRIPE_IP_ALLOWLIST_ENABLED=false
  kubectl rollout restart deploy/cybertech-app
  ```
- Then update the allowlist with the latest Stripe IP list (https://stripe.com/files/ips/ips_webhooks.json) and re-enable the filter.

### If cause = network partition
- Test egress: `kubectl exec deploy/cybertech-app -- curl -v https://api.stripe.com/v1/charges`.
- If blocked, check minikube tunnel / NetworkPolicies.

## Verification
- Webhook 2xx rate returns to baseline on the Grafana Payments dashboard.
- New checkouts move from `AWAITING_PAYMENT` -> `PAID` within seconds.
- `stripe_event_ledger` table receives new rows.
- End-to-end smoke: place a £1 test order, see the `payment_intent.succeeded` row land in the ledger.

## Postmortem checklist
- [ ] Number of orders stuck and total revenue affected.
- [ ] Was a webhook secret rotation involved? Capture pre/post secret hash diff.
- [ ] Did the IP allowlist contribute? If yes, schedule a recurring update of the Stripe IP list.
- [ ] Were users notified? Communication timeline.
- [ ] Did `webhook-replay.md` recover all events? Any orders that needed manual reconciliation.
- [ ] Action item: alert if `stripe_event_ledger` row rate drops to zero for > 5 min during business hours.

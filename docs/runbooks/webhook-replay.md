# Stripe webhook replay

## When to use
- After a Stripe-side outage (per https://status.stripe.com).
- After our app was unavailable (deploy gone bad, MySQL down, Keycloak failure cascading) and Stripe retries gave up.
- After a webhook secret rotation that left a window where signatures briefly failed.
- When `payment-outage.md` recovery instructs you to replay events.

## Why it's safe
- The `stripe_event_ledger` table (Wave 2 commit `16408e7`) **deduplicates by Stripe event ID**. Replaying the same event a second time is a no-op: the controller checks the ledger before invoking the domain handler.
- The `deserializeUnsafe()` fallback (Wave 3 commit `04ea74b`) means an `API_VERSION` skew between the SDK and the event payload **does NOT silently 200-ACK a malformed event**. A replay either succeeds end-to-end or surfaces the parse error properly.
- The handlers themselves are idempotent: setting an order to `PAID` when already `PAID` is a no-op; emitting a `PaymentSucceededEvent` is wrapped by `@TransactionalEventListener(AFTER_COMMIT)` so listeners only run once per real state transition.

## Procedure

### 1. Identify which events to replay
- In the Stripe Dashboard: **Developers -> Webhooks -> select your endpoint -> Events tab**.
- Filter by status `Failed` or `Pending`.
- Note the time range of failures (use this to bound the replay scope).

### 2. (Optional) Confirm what's already in our ledger
```bash
kubectl exec -it mysql-0 -- mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cybertech -e \
  "SELECT event_id, event_type, received_at \
   FROM stripe_event_ledger \
   WHERE received_at > '2026-04-25 12:00:00' \
   ORDER BY received_at;"
```
Anything in the ledger is already processed and a replay will be a safe no-op.

### 3. Resend the events
- For each failed event, click **Resend** in the Stripe Dashboard.
- For bulk replay, use the **Resend events** button at the endpoint level (resends the most recent N failed events).
- Stripe also supports CLI replay:
  ```bash
  stripe events resend evt_1Abc... --webhook-endpoint we_1Abc...
  ```
  See https://stripe.com/docs/cli/events/resend for CLI install instructions.

### 4. Verify each replay
- Watch the Stripe Dashboard event status flip to `Succeeded` (HTTP 200 from our endpoint).
- Confirm the row in `stripe_event_ledger`:
  ```sql
  SELECT * FROM stripe_event_ledger WHERE event_id = 'evt_1Abc...';
  ```
- For `payment_intent.succeeded`: the corresponding order should now be `PAID`:
  ```sql
  SELECT id, status, payment_intent_id FROM orderTable
   WHERE payment_intent_id = 'pi_1Abc...';
  ```
- For `charge.refunded`: the order should be `REFUNDED` and the refund record present.

### 5. Reconcile any orders that the replay didn't fix
If a replay completes (200 OK, ledger row inserted) but the order didn't transition, that means the event was *already* processed once but the handler had a bug — open a ticket; do NOT manually update the order in SQL without triaging.

## Caveats

- **API version skew**: events older than ~6 months may use an older Stripe API version. Thanks to `deserializeUnsafe()` we won't silently 200-ACK them, but you may need to update SDK + reprocess. If the dashboard shows a parse error, file a bug; do NOT bypass.
- **Listener side-effects** (emails, notifications): replay does *not* re-emit the `PaymentSucceededEvent` because the ledger short-circuits before the publisher runs. **This is intentional** — customers should not get duplicate "payment received" emails. If a notification needs to be re-sent, do it manually via the admin tooling.
- **Out-of-order replays**: replaying `charge.refunded` before `payment_intent.succeeded` is fine — handlers tolerate any order, the ledger guarantees each is applied at most once.

## Postmortem checklist
- [ ] How many events were replayed? Cross-reference Stripe Dashboard count vs ledger insert count.
- [ ] Any events that surfaced an error after replay? File bugs.
- [ ] Were any customer-facing notifications missed? Document the manual remediation.
- [ ] Was the original outage cause addressed? Cross-link `payment-outage.md`.

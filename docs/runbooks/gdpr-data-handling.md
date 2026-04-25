# GDPR data handling

This runbook documents Cybertech's data inventory, retention policy, right-to-erasure procedure, and subprocessor list. It is the single source of truth for GDPR-related operations.

> **Important:** Cybertech is a **portfolio project**. Some items below describe the *intended* behaviour and are flagged as future work. The right-to-erasure endpoint is currently **NOT implemented** (Wave 7 Task 6 backlog).

## Data inventory

| Table / Collection         | Storage   | PII columns                                          | Purpose                            | Retention                                    | Controller |
|----------------------------|-----------|------------------------------------------------------|------------------------------------|----------------------------------------------|------------|
| `userTable`                | MySQL     | `email`, `firstName`, `lastName`, `address`, `phone`, `dateOfBirth` | Account identity, shipping            | Until user deletes account, or 24 mo of inactivity (anonymized) | User       |
| `bankCardTable`            | MySQL     | `cardholderName`, masked PAN (last 4 only), `expiryMonth`, `expiryYear` | Saved payment methods           | Until user deletes card, or with account     | User       |
| `orderTable`, `orderItemTable` | MySQL | `shippingAddress`, `billingAddress`, link to `userTable.id` | Order history, accounting, invoicing | **7 years** (FR / EU accounting requirement) | Cybertech (legal obligation) |
| `reviewTable`              | MySQL     | `userId`, `body`                                     | Public product reviews             | **Indefinite** (public content; user can request deletion individually) | User / Cybertech |
| `userEvent` (collection)   | MongoDB   | `userId`, `sessionId`, `ip` (truncated), pageviews, clicks | Behavioural analytics            | **90 days**                                  | Cybertech (legitimate interest) |
| `cart` / Redis cart cache  | MySQL + Redis | `userId`, line items                              | Active shopping cart               | Cleared on order placement or 30 days idle    | User       |

### Notes on PII categorisation
- `bankCardTable` stores **only** PAN last-4 + cardholder name + expiry. Full PAN never touches our DB — that's Stripe's responsibility (PCI-DSS scope reduction).
- `orderTable.shippingAddress` is the address as captured at order time; it is *not* updated when the user later changes their saved address. This is **intentional** for accounting.
- `userEvent.ip` is truncated to /24 (IPv4) or /48 (IPv6) before persistence to reduce identifiability.

## Retention scheduled job (specification — NOT yet implemented)

A Spring Batch job should run daily at 02:00 UTC under the `data-retention` job group and perform:

### Step 1: Anonymize inactive users
- **Scope**: users in `userTable` where `lastLoginAt < NOW() - INTERVAL 24 MONTH` AND `accountStatus != 'ANONYMIZED'`.
- **Action** (per user, in a transaction):
  - Replace `email` with `redacted-<original-uuid>@redacted.local`.
  - Null out `firstName`, `lastName`, `phone`, `dateOfBirth`.
  - Replace `address` with the anonymized stub `'REDACTED'`.
  - Set `accountStatus = 'ANONYMIZED'`.
  - Disable the corresponding Keycloak user (set `enabled=false`).
  - Leave `orderTable` untouched (legal obligation), but the `userId` link is preserved so the join still works for accounting reports.
- **Reporting**: emit a summary event `UserAnonymizationCompleted` with the count.

### Step 2: Trim user event analytics
- **Scope**: `userEvent` MongoDB documents where `createdAt < NOW() - INTERVAL 90 DAY`.
- **Action**: bulk-delete via `db.userEvent.deleteMany({ createdAt: { $lt: cutoff } })`.
- **Reporting**: count + duration to the daily Spring Batch summary email.

### Idempotency & safety
- Both steps must be idempotent (re-running is safe; ANONYMIZED users are skipped on the second pass).
- Failures should fail the step but not the whole job — partial progress is acceptable.
- A `--dry-run` flag should be supported for first run / audit.

## Right-to-erasure (specification — NOT yet implemented)

> **Status:** This endpoint does NOT exist in the current codebase. It is **Wave 7 Task 6 backlog**. This section documents the *intended* contract so the team knows what to build.

### Intended endpoint
```
DELETE /api/v1/services/user/me
Authorization: Bearer <user-jwt>
```

### Intended behaviour
1. **Authenticate** the caller via JWT (must match the user being deleted — no admin bypass).
2. **Within a single transaction**:
   - Replace `userTable.email` with `redacted-<userId-uuid>@redacted.local`.
   - Null `firstName`, `lastName`, `phone`, `dateOfBirth`.
   - Replace `address` with `'REDACTED'`.
   - Set `accountStatus = 'ANONYMIZED'`.
   - **Tombstone** all referenced rows in `orderTable` / `orderItemTable`: keep the order data (legal obligation) but blank the `shippingAddress` and `billingAddress` columns (replace with `'REDACTED'`). The `userId` foreign key is preserved.
   - Delete all rows in `bankCardTable` for the user.
   - Delete all `cart` rows for the user.
   - Delete all `userEvent` MongoDB documents for the user (full purge — no legal retention).
   - Delete `reviewTable` rows authored by the user (public content; per current product decision, full delete is offered alongside anonymization choice — confirm with product).
3. **Outside the transaction**:
   - Delete the corresponding Keycloak user via the admin client.
   - Emit a `UserErasedEvent` for downstream listeners (e.g. invalidate caches, mailing-list opt-out).
4. **Response**: `204 No Content`.

### Operational notes for the runbook user
- Until the endpoint exists, manual erasure requests must be handled by an operator running the equivalent SQL by hand. **Document each manual erasure** (date, requester, ticket id) in a privacy log.
- Verify the user is the requester (not just an authenticated session) — for portfolio purposes, an admin manually checks the email matches.
- Provide the user with confirmation within 30 days (GDPR Art. 12.3).

## Subprocessor list

| Subprocessor    | Purpose                              | Data shared                                       | DPA status (portfolio) |
|-----------------|--------------------------------------|---------------------------------------------------|------------------------|
| Stripe          | Payment processing (PCI scope)       | Customer email, billing address, full PAN (Stripe-side, not ours) | N/A on portfolio; document for future deployment |
| Keycloak        | Authentication (self-hosted)         | None — runs in our cluster                        | N/A (not a third-party) |
| Mailpit         | Email capture (dev SMTP, self-hosted)| Email contents — but Mailpit is **internal only**, no real SMTP delivery on portfolio | N/A (no real delivery) |
| AWS S3 (localstack on portfolio) | Product image storage    | None — only product images, no PII                | N/A on localstack; real DPA required if migrating to real AWS |

### Future-work flags
- DPAs (Data Processing Agreements): not signed because this is a portfolio with no real users. Required before any real-customer deployment.
- A real-prod deployment must add: Sentry / error tracker DPA, real SMTP provider DPA, CDN DPA, etc.

## Postmortem / audit checklist (per privacy event)
- [ ] Was the request from a verified user?
- [ ] Was the action completed within the GDPR 30-day window?
- [ ] Was the privacy log entry written (date, ticket, operator)?
- [ ] If manual SQL was run: capture the exact statements for audit.
- [ ] If the right-to-erasure endpoint was used: confirm Keycloak user is also deleted (check `kubectl exec keycloak-0 -- /opt/keycloak/bin/kcadm.sh get users -q username=<...>` returns empty).

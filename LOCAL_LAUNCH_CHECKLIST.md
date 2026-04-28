# Local Launch Checklist — Cybertech (Spring Boot 4 + Next.js 16)

Goal: launch backend (Spring Boot, port `8081`) from IntelliJ + frontend (Next.js, port `3000`) via `npm run dev` and have them communicate end-to-end against a local Keycloak (port `8080`), with all third-party services running through the existing `docker-compose.yml`.

> NOTE — items marked `BLOCKER` are mandatory for any HTTP traffic between front and back to succeed. Items marked `RECOMMENDED` are smoke-test or hardening niceties.

---

## Section A — Prérequis externes (services Docker à lancer)

The backend `application.properties` (default profile, no `prod`) hardcodes localhost endpoints for every dependency. If a service is missing, Spring Boot will fail at startup or at the first call.

### Required services and where the backend reads them

| Service        | Local endpoint                       | Read from                                             | Required to boot? |
|----------------|--------------------------------------|-------------------------------------------------------|-------------------|
| MySQL 9.x      | `jdbc:mysql://localhost:3306/cybertechDB` | `spring.datasource.url`                               | YES (BLOCKER)     |
| Keycloak 24.x  | `http://localhost:8080`              | `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `keycloak.client.user.management.server.url` | YES (BLOCKER)     |
| Redis          | `localhost:6379`                     | `spring.data.redis.host`                              | YES (BLOCKER — `spring.cache.type=redis`) |
| MongoDB        | `mongodb://localhost:27017/userEventsDB` | `spring.data.mongodb.uri`                          | YES (BLOCKER)     |
| Elasticsearch  | `http://localhost:9200`              | `spring.elasticsearch.uris`                           | YES (BLOCKER)     |
| LocalStack S3  | `http://localhost:4566`              | `spring.cloud.aws.s3.endpoint`                        | YES (BLOCKER — bucket `cybertech-products`) |
| Mailpit        | `localhost:1025` (SMTP) / `:8025` (UI) | `spring.mail.host` / `.port`                        | RECOMMENDED (mail health probe is disabled, but order/notification listeners hit SMTP) |
| Vault          | `http://localhost:8200`              | `spring.cloud.vault.uri`                              | NOT required — `spring.cloud.vault.enabled=false` in default profile |
| Stripe CLI     | forwards events to `localhost:8081/api/v1/webhooks/stripe` | n/a                                | RECOMMENDED only for Stripe webhook test |
| moderation-api | `http://moderation-api:5000/analyze` | `moderation.api.url`                                  | RECOMMENDED — only hit when a review is posted; in-cluster DNS name will fail unless overridden, see Pitfall G3 |
| Gorse          | `localhost:8088/8089`                | n/a                                                   | NOT required — no live wiring in code today |

### One-shot bring-up

```bash
cd src/main/resources/docker
# .env file is already present (MYSQL_PORT=3306 etc.)
docker compose up -d mysql redis mongodb elasticsearch keycloak localstack mailpit
# OPTIONAL extras:
# docker compose up -d moderation-api stripe
docker compose ps    # verify all are healthy / running
```

> The `docker-compose.yml` lives at `src/main/resources/docker/docker-compose.yml` and the variable file `.env` next to it. Don't run from repo root — the relative volume mount `$PWD/../sql/databaseSchemaInitFile.sql` is computed from the compose file directory.

> The compose file references `mysql:9.3.0`, but the project ships a Hibernate `MySQLDialect` and `spring.jpa.hibernate.ddl-auto=update`, so the schema bootstraps automatically from the SQL init file plus Hibernate.

---

## Section B — Import du realm Keycloak

The Keycloak service from compose has NO realm preloaded — it boots empty. The realm export `cybertech-realm-export.json` lives at the repo root and must be imported once.

### B.1 Open Keycloak admin

- URL: <http://localhost:8080>
- Admin login (from `docker-compose.yml`):
  - `KEYCLOAK_ADMIN=admin`
  - `KEYCLOAK_ADMIN_PASSWORD=admin`

### B.2 Import the realm

Admin Console → top-left realm dropdown → "Create realm" → "Resource file" → select `cybertech-realm-export.json` → name remains `cybertech` → Create.

### B.3 ⚠ Existing clients in the realm export

The export ships TWO confidential clients:

| `clientId`                          | `publicClient` | Purpose                                                                 |
|-------------------------------------|----------------|-------------------------------------------------------------------------|
| `cybertech-postman-client-id`       | `false`        | Postman testing (service account)                                       |
| `cybertech-user-management-client`  | `false`        | Used by the backend admin client to provision users (server-to-server)  |

**The frontend client `cybertech-frontend` referenced in `front/app/.env.local.example` does NOT exist in the realm export.** This is a BLOCKER (see Section G, Pitfall 1). Two options:

**Option B-α (recommended — cleanest, no env change)**
1. In the imported `cybertech` realm → Clients → Create client
2. Client type: OpenID Connect, Client ID: `cybertech-frontend`
3. Capability config: `Client authentication = ON` (confidential — Auth.js v5 server-side flow uses client_secret), `Authorization = OFF`, `Standard flow = ON`, `Direct access grants = OFF`, `Service accounts = OFF`
4. Login settings:
   - Root URL: `http://localhost:3000`
   - Home URL: `http://localhost:3000`
   - Valid redirect URIs: `http://localhost:3000/api/auth/callback/keycloak`
   - Valid post logout redirect URIs: `http://localhost:3000`
   - Web origins: `http://localhost:3000` (or `+` to mirror redirect URIs)
5. Save → tab "Credentials" → copy "Client secret" → that's the value for `AUTH_KEYCLOAK_SECRET` in `.env.local`.

**Option B-β (reuse `cybertech-postman-client-id`)**
You can point the frontend at `cybertech-postman-client-id` instead — the realm already has it set up. But its current `redirectUris=/*` and `webOrigins=/*` are loose and you'd need to set `Root URL = http://localhost:3000` so the relative `/*` resolves correctly. Functionally OK for portfolio scope; less explicit than option α.

### B.4 Backend admin client secret

`application.properties` line 128 hardcodes:
```
keycloak.client.user.management.client.secret=rPKnibr1m14c2Oit4XybU1AhhIbuZVtt
```
After import the realm-side secret of `cybertech-user-management-client` will be regenerated by Keycloak. Two reconciliation paths:

- **Easy:** Admin Console → Clients → `cybertech-user-management-client` → Credentials tab → "Regenerate secret" → copy the new value → paste into `application.properties` (or override via env var `KEYCLOAK_CLIENT_USER_MANAGEMENT_CLIENT_SECRET`).
- **Or:** in the same Credentials tab, paste back `rPKnibr1m14c2Oit4XybU1AhhIbuZVtt` to match the property.

### B.5 Realm has NO human users

`cybertech-realm-export.json` only contains the two service-account users. Sign-up is done by the backend itself through `POST /api/v1/services/user/register`, which provisions the Keycloak user via the `cybertech-user-management-client` admin client. You'll need to register at least one user before any "logged-in" smoke test (see Section F).

---

## Section C — Config backend (overrides locaux)

The default `application.properties` is already wired for localhost, so launching from IntelliJ with no profile (`SPRING_PROFILES_ACTIVE` empty) is the right starting point.

### C.1 IntelliJ run config — minimum

- Main class: `com.novatech.cybertech.CybertechApplication` (Spring Boot run config)
- VM options: `--enable-preview` (Java 26 preview features are required by the build, see `pom.xml`)
- Active profiles: leave empty (DON'T activate `prod` — it points at in-cluster hostnames `mysql`, `keycloak`, etc.)

### C.2 Properties already correct out-of-the-box

| Key                                              | Value                                       | Status |
|--------------------------------------------------|---------------------------------------------|--------|
| `cybertech.cors.allowed-origins`                 | `http://localhost:3000`                     | OK — matches Next dev |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:8080/realms/cybertech` | OK    |
| `cybertech.security.hsts.enabled`                | `false`                                     | OK    |
| `cybertech.security.stripe-ip-allowlist.enabled` | `false`                                     | OK    |
| `spring.cloud.vault.enabled`                     | `false`                                     | OK    |

### C.3 Properties you SHOULD override locally

| Key                                                       | Default                                | Suggested local                                  |
|-----------------------------------------------------------|----------------------------------------|--------------------------------------------------|
| `keycloak.client.user.management.client.secret`           | `rPKnibr1m14c2Oit4XybU1AhhIbuZVtt`     | match what you regenerate in Keycloak (see B.4)  |
| `application.frontend.url`                                | `http://localhost:4200` (Angular leftover) | `http://localhost:3000` so order/registration emails point at the Next app |

> Override mechanism: either edit `application.properties`, OR add VM args `-Dapplication.frontend.url=http://localhost:3000 -Dkeycloak.client.user.management.client.secret=...`, OR use `application-local.properties` with `spring.profiles.active=local`.

### C.4 Properties you can IGNORE locally

`management.otlp.tracing.endpoint=http://tempo:4318/v1/traces` and `management.otlp.metrics.export.url=http://prometheus:9090/...` will fail to resolve `tempo` / `prometheus` DNS in local mode. Spring Boot logs a warn and trace export silently drops. Backend still works; just noisy. Set `MANAGEMENT_OTLP_TRACING_ENDPOINT=` (empty) or `management.tracing.sampling.probability=0.0` to silence.

---

## Section D — Config frontend (`.env.local`)

Create `front/app/.env.local` (gitignored — won't be committed). The example file `.env.local.example` already lists every variable the code reads.

### D.1 Variables actually read by the front

Sources of truth:
- `front/app/src/lib/api/client.ts` line 22: `process.env.NEXT_PUBLIC_API_BASE`
- `front/app/src/lib/auth.ts` lines 58-61, 90, 96, 101: `AUTH_KEYCLOAK_ID`, `AUTH_KEYCLOAK_SECRET`, `AUTH_KEYCLOAK_ISSUER`
- Auth.js v5 reads `AUTH_SECRET` and `AUTH_URL` natively (no NEXTAUTH_* prefix used).

### D.2 Concrete values for local dev

```bash
# front/app/.env.local

# Backend base URL (Spring Boot port 8081)
NEXT_PUBLIC_API_BASE=http://localhost:8081

# Auth.js v5 — JWE-encrypted session secret (32+ bytes base64).
# Generate with: openssl rand -base64 32
AUTH_SECRET=<paste-output-of-openssl-rand-base64-32>

# Auth.js v5 absolute origin used for the OIDC callback URL.
AUTH_URL=http://localhost:3000

# Keycloak — must match the realm + client created in Section B
AUTH_KEYCLOAK_ISSUER=http://localhost:8080/realms/cybertech
AUTH_KEYCLOAK_ID=cybertech-frontend
AUTH_KEYCLOAK_SECRET=<paste-secret-from-Keycloak-Credentials-tab>
```

### D.3 Contradiction signalled

`.env.production` only exposes `NEXT_PUBLIC_API_BASE` and `AUTH_URL` because the comment says other secrets come from the helm chart at runtime. That's consistent — nothing to fix in `.env.production`. But `.env.local.example` does ship all 5 keys, so use it as the source-of-truth for which variables exist.

---

## Section E — Run order

Follow strictly — Spring Boot won't start if Keycloak/MySQL/Redis/Mongo/ES aren't reachable.

1. **Bring up third-party services**
   ```bash
   cd src/main/resources/docker
   docker compose up -d mysql redis mongodb elasticsearch keycloak localstack mailpit
   docker compose ps   # wait for all "healthy" / "running"
   ```

2. **Import the Keycloak realm** (one-time)
   - Open <http://localhost:8080> → admin / admin → Create realm → Resource file → `cybertech-realm-export.json`.
   - Create the `cybertech-frontend` client (Section B.3 option α) → copy its secret.
   - Reconcile the `cybertech-user-management-client` secret with `application.properties` (Section B.4).

3. **Start backend from IntelliJ**
   - Spring Boot run config on `CybertechApplication`, no profile, `--enable-preview` in VM options.
   - Watch for `Started CybertechApplication in X.Xs` on port `8081`.
   - Verify: <http://localhost:8081/actuator/health> returns 200 with `"status":"UP"`.

4. **Create the frontend env file & start Next**
   ```bash
   cd front/app
   # write .env.local using the template in Section D.2
   npm install         # only on first run
   npm run dev         # listens on http://localhost:3000
   ```

5. **End-to-end check**
   - Open <http://localhost:3000> — products page should load (anonymous endpoints `/api/v1/services/product/**` are public).
   - Network tab: requests should go to `http://localhost:8081/api/v1/services/...` and return 200, no CORS error in the console.

---

## Section F — Smoke tests

### F.1 Anonymous catalogue
```bash
curl -i http://localhost:8081/api/v1/services/product/get/all
# Expect 200 + Spring Data Page<ProductResponseDto> envelope
```
- The browser version of this is what the homepage hits — a CORS preflight from `http://localhost:3000` should return `Access-Control-Allow-Origin: http://localhost:3000`.

### F.2 User registration (creates a Keycloak user via admin client)
```bash
curl -i -X POST http://localhost:8081/api/v1/services/user/register \
  -H "Content-Type: application/json" \
  -H "X-API-VERSION: 1.0" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "Passw0rd!",
    "firstName": "Alice",
    "lastName": "Tester",
    "bankCardCreationRequestDto": null
  }'
```
- 201 Created. If 500 with a Keycloak admin error → the `keycloak.client.user.management.client.secret` is out of sync (Section B.4).
- Bucket4j: 5 register calls / IP / minute → 6th returns 429.

### F.3 Login flow (front → Keycloak → back)
- Click "Sign in" on `localhost:3000` (or hit a `/account/*` route to be auto-redirected — see `proxy.ts`).
- Auth.js will redirect to Keycloak's hosted login form — log in as `alice` / `Passw0rd!` from F.2.
- Successful return: `proxy.ts` lets the request through, the page mounts, fetches via `apiFetch` send `Authorization: Bearer <jwt>`.

### F.4 Authenticated cart
- From the homepage product list, "Add to cart". Network: `POST /api/v1/services/cart/add` returns 200.

### F.5 Place order
- Frontend posts `POST /api/v1/services/management/order/place`. Backend creates a Stripe PaymentIntent server-side using the hardcoded test PaymentMethod (`stripe.payment-method`, e.g. `pm_card_visa`). Order shows up in `/account/orders`.

### F.6 Backend-only smoke
- Swagger UI: <http://localhost:8081/swagger-ui.html> — anonymous, public per `SecurityConfig.PUBLIC_URLS`.
- Mailpit UI: <http://localhost:8025> — verify the registration / order email arrives.

---

## Section G — Pièges identifiés

### G.1 BLOCKER — `cybertech-frontend` client missing from the realm export
`.env.local.example` references `AUTH_KEYCLOAK_ID=cybertech-frontend`, but that client does NOT exist in `cybertech-realm-export.json` (only `cybertech-postman-client-id` and `cybertech-user-management-client` exist). Without creating it (Section B.3), Auth.js will fail at the OIDC token exchange with "client not found".

### G.2 BLOCKER — `keycloak.client.user.management.client.secret` mismatch after import
Realm export ships `"secret": "**********"` (Keycloak masks secrets on export). On import, Keycloak generates a brand-new secret, which won't match the hardcoded `rPKnibr1m14c2Oit4XybU1AhhIbuZVtt` in `application.properties`. Backend boots fine but `POST /register` will 500. Section B.4 fix.

### G.3 RECOMMENDED — `moderation.api.url=http://moderation-api:5000/analyze`
This is a Docker / k8s in-cluster hostname, NOT reachable from a Spring Boot process running on the host. Either:
- Add a hosts entry `127.0.0.1 moderation-api`, OR
- Run the moderation container with `--network host` (Linux), OR
- Override locally: `-Dmoderation.api.url=http://localhost:5000/analyze` (and expose port 5000 on the moderation-api compose service — currently only `5000:5000` is published, so it's already reachable on `localhost:5000`).
- Only matters when posting reviews / comments. Skip if you're not testing moderation.

### G.4 RECOMMENDED — `application.frontend.url=http://localhost:4200`
Legacy Angular default port. Email links inside registration / order confirmation will go to a non-existent port until you override to `http://localhost:3000`. Cosmetic for happy path; matters if you click email links during smoke tests.

### G.5 NOTE — CORS allows credentials, headers `*`, methods explicit
`SecurityConfig.corsConfigurationSource()` sets `setAllowCredentials(true)` and reads origins from `cybertech.cors.allowed-origins`. With CSRF disabled and JWT in `Authorization` header (NOT cookies), this is fine — the Next frontend never sends cookies cross-origin to the API. But if you later add a cookie-based feature, the SameSite policy on the front must align.

### G.6 NOTE — Bucket4j rate limits in dev
- `POST /register`: 5/min per IP (will trip if you spam-test).
- `POST /management/order/place*`: 30/min per JWT-sub.
- `/cart/**`: 60/min per JWT-sub.
- 429 with `Retry-After` header. No way to disable without a code change — just slow down your loop.

### G.7 NOTE — CSRF disabled by design
`SecurityConfig.securityFilterChain` calls `.csrf(AbstractHttpConfigurer::disable)` and uses `SessionCreationPolicy.STATELESS`. The frontend therefore does NOT need to handle CSRF tokens. `proxy.ts` does NOT enforce same-site cookies either; auth state lives in the encrypted Auth.js session JWT cookie.

### G.8 NOTE — login order in `proxy.ts`
`/account/**` and `/admin/**` redirect to `/api/auth/signin` if no session. Visiting `/products/...` (public) anonymously is fine, but any clicks into the account area force login first. If you want to register WITHOUT being logged in, hit the registration endpoint via curl (F.2), or use whatever the front's `/register` page surface is — there's no `/register` route under `/account`, so it stays public.

### G.9 NOTE — Java 26 preview features
`pom.xml` builds with `--enable-preview`. IntelliJ run config MUST add `--enable-preview` to "VM options" or the JVM will refuse to load the compiled classes (`UnsupportedClassVersionError` or `Preview features are disabled`).

### G.10 NOTE — `next dev` is plain HTTP, HSTS off
HSTS is disabled in `application.properties` for exactly this reason. If you ever toggle `cybertech.security.hsts.enabled=true` on localhost, your browser will pin localhost:8081 to HTTPS forever and refuse plain HTTP — clear via `chrome://net-internals/#hsts`.

### G.11 NOTE — Stripe webhook signing
The default profile carries a hardcoded `stripe.webhook.secret` and `stripe.api.key`. The compose `stripe` service forwards CLI events to `host.docker.internal:8081/api/v1/webhooks/stripe`. Signature verification will succeed because the compose CLI is signing with the same key. Order placement does NOT need the webhook for the happy path — `setConfirm(true)` runs synchronously.

---

## Quick-reference — file paths

- `src/main/resources/docker/docker-compose.yml`
- `src/main/resources/docker/.env`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties` (do NOT activate locally)
- `src/main/java/com/novatech/cybertech/config/SecurityConfig.java`
- `src/main/java/com/novatech/cybertech/security/RateLimitFilter.java`
- `cybertech-realm-export.json`
- `front/app/.env.local.example`
- `front/app/.env.production` (do NOT use locally)
- `front/app/next.config.ts`
- `front/app/src/lib/auth.ts`
- `front/app/src/lib/api/client.ts`
- `front/app/src/proxy.ts`
- `front/app/src/app/api/auth/[...nextauth]/route.ts`

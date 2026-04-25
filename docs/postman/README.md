# Cybertech API - Postman Collection

This folder contains a ready-to-import Postman v2.1 collection and environment for the Cybertech Spring Boot 4 / Java 26 e-commerce backend.

## Files

- `Cybertech-API.postman_collection.json` - the request collection, grouped by domain folder.
- `Cybertech-API.postman_environment.json` - a local-dev environment with placeholders for your Keycloak credentials.
- `README.md` - this file.

## Quick import

1. Open Postman.
2. Click `Import` (top-left) and drop in BOTH JSON files (collection + environment).
3. In the top-right environment selector, pick `Cybertech (local)`.

## Configure the Keycloak credentials

The collection ships with a pre-request script that calls Keycloak's password-grant endpoint and caches the resulting access token until 30 seconds before expiry. Edit the environment and fill in:

| Variable                  | Example value                                                                                  | Notes                                                              |
|---------------------------|------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `base_url`                | `http://localhost:8081`                                                                        | Backend HTTP port (matches `server.port` in `application.properties`). |
| `keycloak_token_url`      | `http://localhost:8080/realms/cybertech/protocol/openid-connect/token`                          | Realm name `cybertech` is taken from `application.properties`.      |
| `keycloak_client_id`      | `cybertech-user-management-client`                                                              | From `application.properties`.                                      |
| `keycloak_client_secret`  | `<copy from Keycloak admin console>`                                                            | Marked secret. Leave blank if the client is public.                 |
| `keycloak_username`       | `you@example.com`                                                                               | The Keycloak user whose JWT will be used.                           |
| `keycloak_password`       | `<your password>`                                                                               | Marked secret.                                                      |

The fields `access_token` and `access_token_expires_at` are filled in automatically by the pre-request script. You can clear them at any time to force a refresh.

## How auth works

- Collection-level auth is `bearer` with `{{access_token}}`.
- Public requests (`/register`, `/products/**`, `/discounts/**`, `/webhooks/**`) override that to `noauth` so the bearer header is not sent.
- Before every request, the pre-request script checks whether `access_token` is missing or expired. If yes, it POSTs to `keycloak_token_url` with `grant_type=password` and stores the new token + expiry.

If something is misbehaving, open the Postman Console (`View > Show Postman Console`) - the script logs the failure reason.

## Quick-start workflow

A typical end-to-end smoke test:

1. **Register** - run `Auth > Register (public)` with a fresh email.
2. **Login** - configure your `keycloak_username` / `keycloak_password` in the environment. The first request you fire after that auto-fetches a JWT.
3. **Browse** - `Product > Search products (public)`, then `Product > Get product by UUID (public)`.
4. **Add to cart** - `Cart > Add items to my cart` with the product UUID from step 3.
5. **Place order** - `Order > Place order`. Capture the returned `uuid` and `clientSecret` for Stripe.
6. **Pay** - on the front-end (or with Stripe CLI) confirm the PaymentIntent. Status will flip to `PAID` via the webhook.
7. **Poll** - `Order > Get order status by UUID` until status is `PAID` / `SHIPPED`.
8. **Review** - once delivered, `Review > List reviewable products (USER)` then `Review > Create review (USER)`.
9. **Cancel (alt)** - `Order > Cancel order` if you want to abort before the order ships.

## Public vs ADMIN

Public (no token):

- `POST /api/v1/services/user/register`
- `GET  /api/v1/services/product/get/{uuid}`
- `POST /api/v1/services/product/search`
- `GET  /api/v1/services/product/best-sellers`
- `GET  /api/v1/services/discounts/active`
- `POST /api/v1/webhooks/stripe`
- Swagger: `/swagger-ui/**`, `/v3/api-docs/**`

ADMIN-only (caller must have `ROLE_ADMIN`):

- Everything under `/api/v1/services/admin/**` (user admin, product admin, discount admin)
- `POST /api/v1/services/user/register/auto/single`
- `POST /api/v1/services/management/order/place/auto`
- `DELETE /api/v1/services/management/order/delete/{uuid}`
- All admin routes in `Bank Card` (paged list, get-by-uuid without `/default`, raw POST/PUT/DELETE on the base path)

Everything else is `USER` or `ADMIN` and ownership-checked at the service layer (BUG-IDOR-D1, BUG-IDOR-D2, BUG-161).

## Notes on the Stripe webhook

The Stripe webhook entry is included for documentation only. Calling it directly from Postman returns `400 BAD_REQUEST` because the `Stripe-Signature` header is fake. To exercise the real flow locally, install the Stripe CLI and run:

```
stripe listen --forward-to http://localhost:8081/api/v1/webhooks/stripe
```

Then trigger a test event:

```
stripe trigger payment_intent.succeeded
```

See `StripeWebhookController` for the full retry / signature semantics (BUG-2501, BUG-2502).

## API versioning

`spring.mvc.apiversion.enabled=true` is set with `default=1.0`. The version is sent via the request header `X-API-VERSION: 1.0` (note the upper-case spelling - matches `spring.mvc.apiversion.use.header=X-API-VERSION`). Every request in the collection already carries this header.

# Cybertech Frontend SITREP

**Prepared:** 2026-04-25
**Branch:** `dev/develop`
**Backend HEAD:** `44a26b7 refactor(discount): DB-driven campaigns + algorithm-keyed strategies + admin endpoint`

This document is a self-contained brief. **Section 2** is the prompt to paste into a fresh Claude Code session that will build the frontend; everything else is the supporting context that prompt references.

---

## 1. Situation

You have a feature-complete Spring Boot e-commerce backend (1,621 unit tests green, 80%+ coverage gate enforced via JaCoCo). It exposes ~50 REST operations across product catalog, cart, order, payment (Stripe), wishlist, reviews, user, bank cards, and a discount-campaign admin surface. There is **no UI yet**. Google Stitch has produced an initial design pass — 13 screens of static Tailwind HTML + a PRD — already landed in the repo at `src/main/java/com/novatech/cybertech/front/assets/stitch_cybertech_e_commerce_ui_dashboard/`.

**Decision recommended (mono-repo):** keep frontend and backend in this same git repo as siblings. Solo-dev simplicity; one PR can change both ends; the Stitch assets are already here. Concretely:

```
cybertech-dev-develop_cybertech/      <- repo root (existing)
├── src/                              <- existing Spring Boot backend
├── pom.xml                           <- existing
├── docs/                             <- this SITREP lives here
├── front/                            <- NEW: move stitch assets here for cleanliness
│   ├── assets/stitch_cybertech_e_commerce_ui_dashboard/   <- moved from src/main/java/.../front/assets/
│   └── (next.js app initialized here)
└── ...
```

If you'd rather keep them split later (separate frontend repo, separate CI/CD), the move is `git mv`-cheap to do at any time.

**First housekeeping action for the frontend session:**
```bash
git mv src/main/java/com/novatech/cybertech/front/assets front/assets
```
(That folder is in a Java source path where it has no business being — it's not a class file. Moving it un-pollutes the backend classpath.)

---

## 2. Prompt — Paste this verbatim into a fresh Claude Code session

```
You are bootstrapping the frontend for the Cybertech e-commerce backend at this repo's root. Read docs/FRONTEND_SITREP.md before doing anything else — it has the full API contract, auth model, DTOs, error envelope, pagination shape, Stripe flow, and a Google Stitch design pass.

Constraints:
1. Use the **frontend-design** skill (frontend-design:frontend-design) for visual / component work. Invoke it when generating layouts, pages, or component code so the output avoids generic AI-shaped UI and matches the Stitch design language.
2. Framework: **Next.js 15 (App Router)** with TypeScript. Tailwind CSS for styling. The Stitch HTML at front/assets/stitch_cybertech_e_commerce_ui_dashboard/*/code.html already uses Tailwind utility classes + a Material 3 token palette + Inter/Space Grotesk fonts + Material Symbols icons — port those tokens into tailwind.config.ts as the design source of truth.
3. The Stitch PRD at front/assets/stitch_cybertech_e_commerce_ui_dashboard/cybertech_project_plan_prd.md lists 17 routes and the component architecture. Follow it as the page map; deviate only with a stated reason.
4. The backend is a sibling at the repo root (Spring Boot, port 8081 by default). Read docs/FRONTEND_SITREP.md for endpoints, DTO shapes, auth, error envelope, pagination, Stripe.
5. Auth is OAuth2/OIDC against Keycloak (realm `cybertech`, issuer `http://localhost:8080/realms/cybertech`). Use `next-auth` (Auth.js) with the Keycloak provider. Roles arrive in JWT `realm_access.roles[]` and the backend converts them to `ROLE_USER` / `ROLE_ADMIN`. Send the token as `Authorization: Bearer <token>` on every API call.
6. **Payments are server-confirmed** — do NOT wire Stripe Elements. The backend creates a Stripe `PaymentIntent` with `setConfirm(true)` using a hardcoded test PaymentMethod (config key `stripe.payment-method`, e.g. `pm_card_visa` for success, `pm_card_chargeDeclined` for failure). Stripe runs in a docker container locally. The frontend just calls `POST /api/v1/services/management/order/place` and reads `OrderResponseDto.status` from the response. To demo failure: change the config and restart the backend. No `clientSecret`, no PaymentElement.
7. Optional: send `X-API-VERSION: 1.0` on requests (the backend has spring.mvc.apiversion enabled, default 1.0).
8. Mono-repo: scaffold the Next.js app at `front/` (sibling of `src/`). First clean step: `git mv src/main/java/com/novatech/cybertech/front/assets front/assets` then `cd front && npx create-next-app@latest app --ts --tailwind --eslint --app --src-dir --import-alias "@/*"` (or your preferred layout — the SITREP doesn't prescribe).
9. Use the test stack appropriate to Next.js (Vitest + Testing Library for components, Playwright for E2E if scope permits). Don't aim for the same coverage gate as the backend; smoke-test critical flows (login, place order, view product list).

Your first deliverables (in this order):
a. Move the Stitch assets, scaffold the Next.js app, port the Tailwind theme tokens.
b. Build the API client layer (`lib/api.ts`) with typed request/response interfaces matching docs/FRONTEND_SITREP.md exactly. One function per endpoint group is fine.
c. Wire next-auth + Keycloak; gate routes by role.
d. Implement the customer-facing happy path first: Home → Product list → Product detail → Cart → Checkout → Order confirmation. Then My Orders, Wishlist, Profile.
e. Then the admin surface: Products, Users, Orders, Discounts.

Do NOT do: build a custom design system from scratch when the Stitch HTML already has one; mock data when you can hit the real backend (run it locally on 8081); skip ownership checks (the backend already enforces them, but the UI should never expose buttons that would 403).

When you finish a milestone, commit and report back.
```

---

## 3. Backend project context

| Item | Value |
|---|---|
| Stack | Java 26 (preview), Spring Boot 4.0.4, Spring Cloud 2025.1.1, Maven |
| Persistence | MySQL 8.4 (JPA), MongoDB (events), Elasticsearch 7.17 (search), Redis (cache + locks) |
| Auth | Keycloak 26.0.4 (OIDC), Spring Security OAuth2 resource server |
| Payments | Stripe Java SDK 31.4.0 (PaymentIntent + webhooks) |
| Storage | AWS S3 (product images) |
| Mail | Spring Mail + Thymeleaf, retry-on-failure listener |
| Async | Spring Kafka, Spring Batch, ApplicationEventPublisher + AFTER_COMMIT listeners |
| Docs | springdoc-openapi 3.0.1 — Swagger UI at `/swagger-ui/index.html` when running |
| Tests | 1,621 unit + 5 deferred Failsafe ITs (Testcontainers); JaCoCo gate 80% |

**Default ports** (running locally via `./mvnw spring-boot:run`): backend on **8081**, Keycloak on **8080**, MySQL on 3306, Redis on 6379, Elasticsearch on 9200.

**Recent commits:**
```
44a26b7 refactor(discount): DB-driven campaigns + algorithm-keyed strategies + admin endpoint
28d92e7 fix(security): close BUG-161 BankCard IDOR
6e3a3fb feat(notification,user): retry mechanism + email column length fix
56e1ff1 feat: add discount campaign data & service layer
```

---

## 4. Design source — Google Stitch assets

Located at: `src/main/java/com/novatech/cybertech/front/assets/stitch_cybertech_e_commerce_ui_dashboard/` (move to `front/assets/` as the first step — see §1).

**13 screens already designed** (each folder has `code.html` + `screen.png`):

| Folder | Maps to route | Persona |
|---|---|---|
| `cybertech_home_page/` | `/` | Public |
| `product_catalog/` + `product_catalog_refined/` | `/products` | Public |
| `product_detail/` | `/products/[uuid]` | Public |
| `shopping_cart/` | `/cart` | User |
| `checkout_page/` | `/checkout` | User |
| `order_confirmation/` | `/order-success/[uuid]` | User |
| `my_orders/` | `/account/orders` | User |
| `user_profile/` | `/account/profile` | User |
| `admin_dashboard/` | `/admin` | Admin |
| `admin_product_management/` | `/admin/products` | Admin |
| `admin_user_management/` | `/admin/users` | Admin |
| `cybernetic_precision/` | brand/about page | Public |

**Design language:**
- Tailwind CSS (via CDN in the HTML — port to `tailwind.config.ts` for the real app)
- Material 3 design tokens (`primary`, `surface`, `on-surface`, `surface-variant`, etc. — copy the `tailwind.config` block from any `code.html`)
- Fonts: Inter (body) + Space Grotesk (display)
- Icons: Material Symbols Outlined
- Light + dark mode (`darkMode: "class"`)

**PRD:** `cybertech_project_plan_prd.md` — full route list (17 pages), component architecture (`MainLayout`, `AdminLayout`, `AuthLayout`, shared UI, forms, overlays), and integration strategy (mock → real, X-API-VERSION header, DTO mapping).

---

## 5. Authentication & Authorization

**Scheme:** OAuth2 / OIDC against Keycloak. Backend is a JWT resource server.

**Application properties (relevant keys):**
```
spring.security.oauth2.resourceserver.jwt.issuer-uri = http://localhost:8080/realms/cybertech
spring.security.oauth2.resourceserver.jwt.jwk-set-uri = http://localhost:8080/realms/cybertech/protocol/openid-connect/certs
keycloak.client.user.management.realm = cybertech
keycloak.client.user.management.client.id = cybertech-user-management-client
```

**Frontend flow (recommended):**
- Use `next-auth` v5 (Auth.js) with the built-in **Keycloak** provider.
- Get a session in the browser, send `session.accessToken` as `Authorization: Bearer <token>` on every backend call.
- Refresh on expiry via the Keycloak refresh token.

**Role mapping (server side already implemented — `KeycloakRoleConverter`):**
- Keycloak realm role `user` → Spring authority `ROLE_USER`
- Keycloak realm role `admin` → Spring authority `ROLE_ADMIN`
- Endpoints declare `@PreAuthorize("hasRole('USER')")` / `hasRole('ADMIN')` — the role string in the annotation does NOT include the `ROLE_` prefix; Spring adds it.

**Public URLs (no token required):**
- `POST /api/v1/services/user/register` (signup, exact path)
- `GET  /api/v1/services/product/**` (catalog reads)
- `GET  /api/v1/services/review/get/**`
- `POST /api/v1/webhooks/**` (Stripe webhook, signature-verified)
- `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health/**`

**Error semantics:**
- Missing/invalid JWT → **401 UNAUTHORIZED** (`CustomAuthenticationEntryPoint`)
- Authenticated but role insufficient → **403 FORBIDDEN** (`CustomAccessDeniedHandler`)
- Authenticated, role OK, but not the resource owner → **404 NOT_FOUND** or domain-specific (`UnauthorizedCartAccessException` / `UnauthorizedBankCardAccessException`) — the backend favors NOT_FOUND-shaped responses on IDOR rather than leaking existence.

---

## 6. Endpoint inventory

All paths below are absolute. Default base is `http://localhost:8081`.

### 6.1 Product (public reads + admin writes)

| Method | Path | Auth | Request | Response | Notes |
|---|---|---|---|---|---|
| GET | `/api/v1/services/product/get/{productUuid}` | public | — | `ProductResponseDto` | One product |
| POST | `/api/v1/services/product/search` | public | `ProductSearchRequestDto` | `Page<ProductResponseDto>` | Faceted/filtered search |
| GET | `/api/v1/services/product/best-sellers` | public | — | `Page<ProductResponseDto>` | Default size 15 |
| GET | `/api/v1/services/admin/management/product/get/all` | ADMIN | — | `Page<ProductResponseDto>` | Default size 20 |
| POST | `/api/v1/services/admin/management/product/create` | ADMIN | `ProductCreateRequestDto` | `ProductResponseDto` | JSON only |
| POST | `/api/v1/services/admin/management/product/create-with-image` | ADMIN | multipart: `product` (json) + `image` (file) | `ProductResponseDto` | S3 upload |
| PATCH | `/api/v1/services/admin/management/product/update/{productUuid}` | ADMIN | `ProductUpdateRequestDto` | `ProductResponseDto` | |
| DELETE | `/api/v1/services/admin/management/product/delete/{productUuid}` | ADMIN | — | 204 | |

### 6.2 Cart (user)

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| GET | `/api/v1/services/cart/get` | USER | — | `CartResponseDto` (caller's cart) |
| GET | `/api/v1/services/cart/get/{cartUuid}` | USER | — | `CartResponseDto` (ownership-checked) |
| POST | `/api/v1/services/cart/create` | USER | `CartCreateRequestDto` | `CartResponseDto` |
| POST | `/api/v1/services/cart/add` | USER | `CartCreateRequestDto` | `CartResponseDto` |
| PATCH | `/api/v1/services/cart/update/{cartUuid}` | USER | `CartUpdateRequestDto` | `CartResponseDto` (ownership-checked) |
| PATCH | `/api/v1/services/cart/remove/{productUuid}` | USER | — | `CartResponseDto` |
| DELETE | `/api/v1/services/cart/decreaseQuantity` | USER | `CartItemRemoveRequestDto` | `CartResponseDto` |
| DELETE | `/api/v1/services/cart/clear` | USER | — | 204 |
| DELETE | `/api/v1/services/cart/delete/{cartUuid}` | USER | — | 204 (ownership-checked) |

### 6.3 Order

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| POST | `/api/v1/services/management/order/place` | USER | `OrderPlacingRequestDto` | `OrderResponseDto` |
| POST | `/api/v1/services/management/order/cancel` | USER | `OrderCancellationRequestDto` | `OrderResponseDto` |
| POST | `/api/v1/services/management/order/update` | USER | `OrderUpdateRequestDto` | `OrderResponseDto` |
| POST | `/api/v1/services/management/order/retry-payment/{uuid}` | USER | — | `OrderResponseDto` |
| GET | `/api/v1/services/management/order/get/{uuid}` | USER | — | `OrderResponseDto` |
| DELETE | `/api/v1/services/management/order/delete/{uuid}` | ADMIN | — | 204 |
| POST | `/api/v1/services/management/order/place/auto` | USER | — | `OrderResponseDto` (synthetic, dev-only) |

### 6.4 Bank cards

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/services/bank-card/add` | USER | `BankCardCreationRequestDto` → `BankCardResponseDto` |
| PUT | `/api/v1/services/bank-card/update` | USER | `BankCardUpdateRequestDto` → `BankCardResponseDto` |
| DELETE | `/api/v1/services/bank-card/delete` | USER | 204 |
| PATCH | `/api/v1/services/bank-card/set-default/{cardUuid}` | USER | 204 |
| GET | `/api/v1/services/bank-card/default` | USER | `BankCardResponseDto` |
| GET | `/api/v1/services/bank-card` | ADMIN | `Page<BankCardResponseDto>` (default size 10) |
| GET | `/api/v1/services/bank-card/{uuid}` | ADMIN | `BankCardResponseDto` |
| POST | `/api/v1/services/bank-card` | ADMIN | Create as admin |
| PUT | `/api/v1/services/bank-card` | ADMIN | Update as admin |
| DELETE | `/api/v1/services/bank-card/{uuid}` | ADMIN | 204 |

PCI: `BankCardResponseDto` exposes only `maskedNumber` (e.g. "•••• 4242"). Never display `cardNumber` even if non-null on legacy rows.

### 6.5 Wishlist

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/services/wishlist/add/{productUuid}` | USER | Returns 200 with `WishlistResponseDto` |
| DELETE | `/api/v1/services/wishlist/remove/{productUuid}` | USER | 204 |
| GET | `/api/v1/services/wishlist/my-wishlist` | USER | `Page<WishlistResponseDto>` (default size 20) |

### 6.6 Reviews

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/services/review/get/{reviewUuid}` | public | `ReviewResponseDto` |
| POST | `/api/v1/services/review/create` | USER | `ReviewCreateRequestDto` (rating 1–5, must own the order) |
| PATCH | `/api/v1/services/review/update/{reviewUuid}` | USER | |
| DELETE | `/api/v1/services/review/delete/{reviewUuid}` | USER | 204 |

### 6.7 User

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/services/user/register` | public | `UserCreateRequestDto` → `{id, keycloakId}` (201) |
| GET | `/api/v1/services/user/get/{userUuid}` | USER | `UserResponseDto` |
| GET | `/api/v1/services/admin/user/get/all` | ADMIN | `Page<UserResponseDto>` |
| POST | `/api/v1/services/admin/user/create` | ADMIN | |
| PATCH | `/api/v1/services/admin/user/update` | ADMIN | |
| DELETE | `/api/v1/services/admin/user/delete/{userUuid}` | ADMIN | 204 |

### 6.8 Discount admin (campaigns)

| Method | Path | Auth | Request | Response |
|---|---|---|---|---|
| GET | `/api/v1/services/admin/discounts` | ADMIN | — | `List<DiscountCampaignResponseDto>` |
| GET | `/api/v1/services/admin/discounts/{discountType}` | ADMIN | — | `DiscountCampaignResponseDto` |
| PATCH | `/api/v1/services/admin/discounts/{discountType}` | ADMIN | `DiscountCampaignUpdateRequestDto` | `DiscountCampaignResponseDto` |

`{discountType}` is a path enum: `NO_DISCOUNT`, `BLACK_FRIDAY`, `WINTER_SALES`, `SPRING_SALES`, `BUY_ONE_GET_ONE_FREE`. PATCH evicts the runtime cache so price calc picks up the new state on the next request.

### 6.9 Misc

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/events/consume-event` | USER | `UserEventDto` — behavioral analytics → MongoDB |
| POST | `/api/v1/webhooks/stripe` | public (signed) | Stripe webhook receiver |
| GET | `/api/v1/services/user/ok` | USER | Auth health probe |

---

## 7. Core DTOs

Field types are Java types; map to TypeScript as: `BigDecimal → string` (parse to `Number` only at display), `UUID → string`, `LocalDateTime → string` (ISO-8601), `LocalDate → string` (`YYYY-MM-DD`), enums → string-literal unions.

```ts
// Cart
type CartResponseDto = {
  cartUuid: string;
  userUuid: string;
  items: CartItemResponseDto[];
  totalPrice: string; // BigDecimal
};
type CartItemResponseDto = {
  cartItemUuid: string;
  productUuid: string;
  productName: string;
  quantity: number;
  unitPrice: string;
  lineItemTotalPrice: string;
};

// Order
type OrderResponseDto = {
  uuid: string;
  userUuid: string;
  orderDate: string;            // YYYY-MM-DD
  status: "CREATED" | "PAID" | "SHIPPED" | "DELIVERED" | "CANCELLED" | "PAYMENT_FAILED";
  totalAmount: string;
  shippingAddress: string;
  orderItems: OrderItemResponseDto[];
};
type OrderPlacingRequestDto = {
  userUuid: string;
  paymentType: "STRIPE" | "BANK_CARD";   // see backend enum
  shippingType: "STANDARD" | "EXPRESS";
  shippingProvider: "DHL" | "FEDEX" | string;
  shippingStreet: string;
  shippingCity: string;
  shippingZipCode: string;
  shippingCountry: string;
  discountType: "NO_DISCOUNT" | "BLACK_FRIDAY" | "WINTER_SALES" | "SPRING_SALES" | "BUY_ONE_GET_ONE_FREE";
};

// Product
type ProductResponseDto = {
  uuid: string;
  name: string;
  price: string;
  brand: string;
  category: string;
  photoUrl: string;
  description: string;
  attributes: Record<string, unknown>; // varies by category
};

// User
type UserResponseDto = {
  uuid: string;
  email: string;
  firstName: string;
  lastName: string;
  username: string;
  sex: "M" | "F" | "OTHER";
  address: { street: string; city: string; zipCode: string; country: string };
  birthDate: string;
  role: "USER" | "ADMIN";
  keycloakId: string;
};

// Bank card (display-safe)
type BankCardResponseDto = {
  uuid: string;
  cardHolderName: string;
  maskedNumber: string;          // e.g. "•••• 4242"
  expiryDate: string;            // MM/yyyy
  cardType: "VISA" | "MASTERCARD" | "AMEX";
  userUuid: string;
  isDefault: boolean;
};

// Discount campaign
type DiscountCampaignResponseDto = {
  uuid: string;
  discountType: "NO_DISCOUNT" | "BLACK_FRIDAY" | "WINTER_SALES" | "SPRING_SALES" | "BUY_ONE_GET_ONE_FREE";
  calculationType: "NONE" | "PERCENTAGE" | "FIXED_AMOUNT" | "BUY_ONE_GET_ONE_FREE";
  enabled: boolean;
  percentage: string | null;
  fixedAmount: string | null;
  minOrderAmount: string | null;
  maxDiscountAmount: string | null;
  startsAt: string | null;
  endsAt: string | null;
  priority: number | null;
};

// Wishlist
type WishlistResponseDto = {
  uuid: string;
  product: ProductResponseDto;
  addedAt: string;
};

// Review
type ReviewResponseDto = {
  uuid: string;
  userUuid: string;
  productUuid: string;
  productName: string;
  rating: number; // 1–5
  comment: string | null;
};

// Error envelope (any 4xx / 5xx)
type ErrorResponseDto = {
  message: string;
  httpStatusCode: number;
  errorCodeType: "FUNCTIONAL" | "TECHNICAL";
};
```

---

## 8. Pagination

Spring Data `Page<T>` JSON shape (the frontend always sees this for paginated GETs):

```json
{
  "content": [ /* T items */ ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,         // 0-based current page
  "size": 20,
  "numberOfElements": 20,
  "first": true,
  "last": false,
  "empty": false,
  "sort": { "sorted": true, "unsorted": false, "empty": false },
  "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, ... }
}
```

**Query params:** `?page=0&size=20&sort=createdAt,desc` (multiple `sort` params allowed).

**Default sizes:** admin=20, bank-card=10, best-sellers=15, wishlist=20. There's a global hard cap in `application.properties` (`spring.data.web.pageable.max-page-size`).

---

## 9. Errors

Every 4xx/5xx returns `ErrorResponseDto`. Status conventions:

| Code | When | `errorCodeType` |
|---|---|---|
| 400 | Validation failed (DTO bean validation, malformed JSON, expired card, type-mismatch path var) | `FUNCTIONAL` or `TECHNICAL` |
| 401 | No JWT or invalid JWT | `TECHNICAL` |
| 403 | JWT valid but missing role | `FUNCTIONAL` |
| 404 | UUID not found OR caller doesn't own the resource (IDOR) | `FUNCTIONAL` |
| 409 | Conflict (e.g. duplicate user) | `FUNCTIONAL` |
| 500 | Anything unexpected; body says only "An unexpected error occurred", no stack info leaked (BUG-140 fix) | `TECHNICAL` |

Frontend mapping suggestion: show `errorResponseDto.message` in toasts, branch on `httpStatusCode` for retry logic, branch on `errorCodeType` for FUNCTIONAL → user-fixable error vs TECHNICAL → "Something went wrong, try again".

---

## 10. Stripe payment flow (server-confirmed, portfolio mode)

**Decision:** Stripe runs as a docker container locally (stripe-mock or `stripe listen`). The backend confirms PaymentIntents **server-side** using a hardcoded test PaymentMethod ID. The frontend does NOT use Stripe Elements / `clientSecret` / `<PaymentElement>` — keeping the demo simple.

**Backend behavior** (`StripePaymentAttemptProcessor`):
- `PaymentIntent.create(params, options)` with `setConfirm(true)` and `setPaymentMethod(stripe.payment-method)`
- Default config: `stripe.payment-method=pm_card_visa` → succeeds
- For failure demo: change to `pm_card_chargeDeclined` and restart
- Returns `PaymentAttemptStatus` (SUCCESS / FAILED / PROCESSING) plus the Stripe intent id
- Webhooks at `POST /api/v1/webhooks/stripe` are still in place (HMAC-verified, idempotent via `ProcessedWebhookEventEntity`) and will move the order PAID async if the configured PM resolves later

**Frontend integration (simple):**
1. User confirms checkout → `POST /api/v1/services/management/order/place` with `paymentType: "VISA"` or `"MASTERCARD"`.
2. Read `OrderResponseDto.status` directly:
   - `PAID` → success page
   - `PAYMENT_FAILED` → show failure with retry CTA
   - `CREATED` / other → show "processing", optionally poll `GET /order/get/{uuid}` until terminal
3. Retry path: `POST /api/v1/services/management/order/retry-payment/{uuid}`.

No publishable key, no `clientSecret`, no Stripe.js needed in the browser. The "test card" the user sees in the UI is purely cosmetic; the backend uses the configured PM regardless.

---

## 11. API versioning

`spring.mvc.apiversion.enabled=true`, default `1.0`, header `X-API-VERSION`. Current routes already include `/api/v1/`, so the version header is advisory for now. Send it anyway:

```ts
const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE,   // http://localhost:8081
  headers: { "X-API-VERSION": "1.0" },
});
```

---

## 12. Bootstrap checklist for the frontend session

In order:

1. `git mv src/main/java/com/novatech/cybertech/front/assets front/assets`
2. `cd front && npx create-next-app@latest app --ts --tailwind --app --src-dir --import-alias "@/*"`
3. Port the Tailwind theme tokens from `front/assets/.../cybertech_home_page/code.html` (the `tailwind.config = {...}` block) into `front/app/tailwind.config.ts`. Wire Inter + Space Grotesk + Material Symbols.
4. Add deps: `next-auth`, `@stripe/stripe-js`, `@stripe/react-stripe-js`, `axios` (or `ky`), `zod` (DTO parsing/validation), `@tanstack/react-query` (data fetching).
5. Build `lib/api.ts` with one typed function per endpoint table row in §6. Use the DTO definitions from §7.
6. Wire next-auth Keycloak provider (env: `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`).
7. `MainLayout` + `AdminLayout` from the Stitch screens. Use the **frontend-design** skill for component generation so the output matches Stitch's design language, not generic AI defaults.
8. Customer happy path: Home → Catalog → Detail → Cart → Checkout (Stripe Elements) → Order success.
9. Account screens: Orders, Wishlist, Profile, Cards.
10. Admin screens: Dashboard, Products, Users, Orders, Discounts.
11. Smoke E2E (Playwright): login as test user, add product to cart, place order with Stripe test card `4242 4242 4242 4242`, verify order shows on `/account/orders`.

---

## 13. Open items the frontend will surface

Worth flagging because they may bite the frontend dev:

- ~~**`UserCreateRequestDto.bankCardCreationRequestDto`** is nested in registration.~~ **CLOSED** (2026-04-25): the field is now optional. Sign up with `bankCardCreationRequestDto: null` to skip the card; users add a card later via `POST /api/v1/services/bank-card/add`. When provided, registration delegates to `BankCardManagementService.addBankCard` so the same PCI rules apply (encryption + last4 masking, expiry guard).
- **Discount selection at checkout**: the backend takes a `discountType` enum. The frontend should call `GET /api/v1/services/admin/discounts` (admin-only) or expose enabled discounts via a new public read endpoint — currently no public "list active discounts" route. **Tell the user; this is a backend gap to close before Checkout works fully.**
- **Reviews need `orderUuid`** — user can only review a product they bought, so the "write a review" CTA on the product page needs to look up which of the user's orders contained this product first.

---

**End of SITREP.** Section 2 is the prompt to paste; sections 3–13 are what that prompt will read.

# Cybertech Frontend Bootstrap — PLAN

> **Note:** This file is the active plan for the frontend bootstrap session that kicked off 2026-04-25. The repo also has `PLAN.md` (bug-fix orchestration) — that is a separate workstream and stays untouched. Source-of-truth design brief: `docs/FRONTEND_SITREP.md`.

## Stack
- Next.js 15 (App Router) + TypeScript
- Tailwind CSS with the Material 3 token palette ported from the Stitch HTML
- next-auth v5 (Auth.js) + Keycloak provider (`http://localhost:8080/realms/cybertech`)
- TanStack Query for client data fetching, server components for catalog reads
- Zod for DTO parsing at API boundary
- Vitest + Testing Library (unit), Playwright (smoke E2E)
- Backend at `http://localhost:8081`, payments are server-confirmed (no Stripe.js)

## Repository layout (target)
```
cybertech-dev-develop_cybertech/
├── src/                 # Spring Boot backend (unchanged)
├── front/
│   ├── assets/          # moved Stitch HTML/PNG (design source of truth)
│   └── app/             # Next.js app
└── docs/FRONTEND_SITREP.md
```

## Routes (17, from PRD §2)

### Customer-facing
- [ ] `/` — Home (hero, categories, best-sellers, promo banner)
- [ ] `/products` — Catalog (filter sidebar + grid)
- [ ] `/products/[uuid]` — Product detail (gallery, specs, reviews, related)
- [ ] `/cart` — Cart management
- [ ] `/checkout` — Shipping + payment + discount selector
- [ ] `/order-success/[uuid]` — Confirmation, polls `/order/status/{uuid}`
- [ ] `/auth/login` — Keycloak login (delegated)
- [ ] `/auth/register` — Multi-step signup (no card by default; card optional)

### Account (USER)
- [ ] `/account/orders` — Order list with status badges
- [ ] `/account/orders/[uuid]` — Order detail + cancel/retry
- [ ] `/account/wishlist` — Saved products
- [ ] `/account/profile` — Personal info + address
- [ ] `/account/cards` — Bank cards (masked, set-default)

### Admin (ADMIN)
- [ ] `/admin` — Dashboard KPIs
- [ ] `/admin/products` — Product CRUD (incl. multipart create-with-image)
- [ ] `/admin/users` — User CRUD
- [ ] `/admin/orders` — Order management
- [ ] `/admin/discounts` — Discount campaigns CRUD (5 enum types)

## Milestones

### M1 — Foundation (this session)
- [x] Move `src/main/java/com/novatech/cybertech/front/` → `front/`
- [x] Scaffold Next.js **16.2.4** (15 was requested but 16 is what `npx create-next-app@latest` ships) at `front/app`
- [x] Port Material 3 theme tokens to `globals.css` via Tailwind v4 `@theme`
- [x] Wire Inter + Space Grotesk via `next/font/google`; Material Symbols via stylesheet `<link>`
- [x] Build `src/lib/api/types.ts` (every DTO from SITREP §7)
- [x] Build `src/lib/api/client.ts` (fetch wrapper, Auth.js token injection, X-API-VERSION, `ApiError` with envelope)
- [x] Build per-domain API modules: `product, cart, order, user, wishlist, review, bank-card, discount, events`
- [x] Wire Auth.js v5 (`next-auth@beta`) + Keycloak provider, refresh-token rotation, role mapping (`session.user.role`)
- [x] **`src/proxy.ts`** route gating (NOT `middleware.ts` — Next 16 renamed it): `/account/**` requires session, `/admin/**` requires ADMIN
- [x] `MainLayout` (Header auth-aware + Footer) + Home page (hero, bento grid, best-sellers fed by `/product/best-sellers`, promo banner fed by `/discounts/active`, brand strip)
- [x] `tsc --noEmit` clean, `eslint` clean, `next build` green (4 routes)
- [x] Commit and report

### M2 — Customer happy path
- [x] `/products` (server-side search via POST `/product/search`, GET-form filters with single-select brand/category to match backend contract, sort, pagination)
- [x] `/products/[uuid]` (Next 16 async params, hero + bento gallery placeholder, specs from `attributes`, qty stepper + add-to-cart server action, write-review CTA gated by `/review/reviewable`)
- [x] `/cart` (server component + `CartItemRow` client comp using server actions: add, decreaseQuantity, removeProduct, clear; revalidatePath after each)
- [x] `/checkout` (3-step layout — shipping address pre-filled from user profile, shipping method, payment type, discount selector wired to `/discounts/active`, server action calls `/order/place` and redirects to `/order-success/[uuid]`)
- [x] `/order-success/[uuid]` (status-aware copy, status badge, retry-payment button on failure, polls `/order/status/{uuid}` every 2s for ~10s when status is CREATED via server action + `router.refresh()`)
- [x] `tsc` clean, `eslint` clean (one ESLint react-hooks/purity catch fixed), `next build` green (7 routes)

### M3 — Account
- [ ] `/account/orders` + detail + cancel/retry
- [ ] `/account/wishlist`
- [ ] `/account/profile`
- [ ] `/account/cards`

### M4 — Admin
- [ ] `AdminLayout` (sidebar from Stitch admin screens)
- [ ] `/admin` dashboard
- [ ] `/admin/products` (incl. image upload)
- [ ] `/admin/users`
- [ ] `/admin/orders`
- [ ] `/admin/discounts`

### M5 — Tests + DX
- [ ] Vitest config + smoke component tests
- [ ] Playwright smoke flow: login → add to cart → place order → see in /account/orders

## Conventions
- Interface before impl (mirrors backend)
- One file per endpoint group in `lib/api/`
- `BigDecimal` stays a `string`; only parse when rendering
- All money formatted via `Intl.NumberFormat`
- Server components by default; `"use client"` only where interaction lives
- All forms use react-hook-form + zod resolvers
- Error UI maps `ErrorResponseDto.errorCodeType`: FUNCTIONAL → toast user-fixable, TECHNICAL → "something went wrong"

## Resume command for next session
> "Read FRONTEND_PLAN.md and docs/FRONTEND_SITREP.md. Pick up at M2 — implement the customer happy path: /products → /products/[uuid] → /cart → /checkout → /order-success/[uuid]. Use the frontend-design skill. Backend runs at :8081."

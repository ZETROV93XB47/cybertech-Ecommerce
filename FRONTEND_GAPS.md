# Cybertech Frontend — Pages manquantes et plan de complétude

> Audit du frontend Next.js 16 vs `FRONTEND_PLAN.md` et vs les endpoints backend disponibles.
> Date : 2026-04-28
> Source : `front/app/src/app/`, `front/app/src/lib/api/*`, `src/main/java/com/novatech/cybertech/api/controllers/implementation/`, `progress.md` Wave 6.

---

## 1. État actuel

### 1.1 Pages déjà implémentées (M1 + M2 — DONE)

| Route | Fichier | Notes |
|---|---|---|
| `/` | `front/app/src/app/page.tsx` | Home (hero MacBook, bento 4 catégories, best-sellers via `/product/best-sellers`, promo banner via `/discounts/active`, brand strip) |
| `/products` | `front/app/src/app/products/page.tsx` | Catalogue server-side (POST `/product/search`), `CatalogFilters`, `Pagination` |
| `/products/[uuid]` | `front/app/src/app/products/[uuid]/page.tsx` | Détail produit (gallery, specs, qty stepper, write-review CTA gated par `/review/reviewable`) |
| `/cart` | `front/app/src/app/cart/page.tsx` | Server component + `CartItemRow` client (server actions add/decrease/remove/clear) |
| `/checkout` | `front/app/src/app/checkout/page.tsx` + `CheckoutForm.tsx` | 3-step (shipping / method / payment + discount) — server action calls `/order/place` |
| `/order-success/[uuid]` | `front/app/src/app/order-success/[uuid]/page.tsx` | Status polling + `RetryPaymentButton` |
| `/api/auth/[...nextauth]` | `route.ts` | Auth.js v5 + Keycloak — handler routes |

Composants disponibles : `Header` (auth-aware), `Footer` (minimal), `MainLayout`, `ProductCard`, `ProductGrid`, `ProductActions`, `CatalogFilters`, `Pagination`, `RatingStars`, `StatusBadge`, `Icon`, `RetryPaymentButton`, `OrderStatusPoller`, `CartItemRow`.

### 1.2 Pages prévues mais pas encore (M3 + M4 — TODO du PLAN)

| Route | Bucket | Endpoint(s) back | Note |
|---|---|---|---|
| `/auth/login` | A | OAuth Keycloak (Auth.js redirect) | Aujourd'hui Header pointe vers `/api/auth/signin` géré par Auth.js — pas d'écran custom |
| `/auth/register` | A | `POST /api/v1/services/user/register` | Endpoint disponible, aucune page |
| `/account/orders` | A | **manque côté back** (cf §3) | Liste mes commandes — voir §5 bloquant |
| `/account/orders/[uuid]` | A | `GET /order/get/{uuid}` + `POST /order/cancel` + `POST /order/retry-payment/{uuid}` | OK back |
| `/account/wishlist` | A | `GET /wishlist/my-wishlist` + `DELETE /wishlist/remove/{p}` | OK back |
| `/account/profile` | A | `GET /user/get/{uuid}` (admin update DTO existe ; pas de self-update endpoint) | Lecture OK, edit limitée |
| `/account/cards` | A | `GET /bank-card/default`, `POST /bank-card/add`, `PUT /bank-card/update`, `DELETE /bank-card/delete`, `PATCH /bank-card/set-default/{u}` | OK back |
| `/admin` | A | KPI agrégés (pas d'endpoint dédié) | Devra composer plusieurs lectures admin |
| `/admin/products` | A | `GET /admin/management/product/get/all` + create / update / delete + create-with-image multipart | OK back |
| `/admin/users` | A | `GET /admin/user/get/all` + create / update / delete + register/auto | OK back |
| `/admin/orders` | A | **manque liste back** — uniquement get-by-uuid | Voir §5 bloquant |
| `/admin/discounts` | A | `GET/PATCH /admin/discounts/{type}` | OK back (5 enum types fixes — édition only, pas de CRUD vrai) |

---

## 2. Pages manquantes — recommandations

### 2.A — Indispensables (déjà sur la roadmap M3 + M4)

| # | Route | Description | Endpoint(s) | Effort | Pourquoi prioritaire |
|---|---|---|---|---|---|
| 1 | `/account/orders` | Liste commandes user avec status badges + filtres | **AUCUN** — il faut soit ajouter un endpoint back `GET /order/mine`, soit persister les UUIDs côté client après chaque checkout (localStorage / Mongo via `events`) | M+L (avec endpoint back) | Suite logique du checkout — sans ça, pas de retour visible sur ses commandes |
| 2 | `/account/orders/[uuid]` | Détail commande + cancel + retry-payment | `GET /order/get/{uuid}` + `POST /order/cancel` + `POST /order/retry-payment/{uuid}` | M | Permet d'agir sur une commande existante (annulation/retry) |
| 3 | `/account/wishlist` | Liste produits favoris paginée + bouton remove + add-to-cart | `GET /wishlist/my-wishlist`, `DELETE /wishlist/remove/{p}`, `POST /cart/add` | S | Module isolé — feature self-contained |
| 4 | `/account/profile` | Affichage info user + adresse | `GET /user/get/{uuid}` ; édition self-side limitée (pas d'endpoint user-self-update) | S | Pré-requis du checkout (pré-remplissage adresse) — déjà utilisé en lecture |
| 5 | `/account/cards` | Liste cartes (devrait exister mais back ne renvoie que la default) → list = `[default]` ; CRUD | Voir §1.2 ligne `/account/cards` | M | Indispensable au flux paiement avec carte sauvée |
| 6 | `/auth/register` | Form multi-step (perso + adresse + carte optionnelle) | `POST /user/register` | M | Sans page register, l'utilisateur peut seulement se loguer — pas de funnel acquisition |
| 7 | `/auth/login` | Bouton "Sign in with Keycloak" stylé (sinon c'est la page Keycloak brute) | Auth.js redirect | S | UX cohérente — actuellement bouton Header → `/api/auth/signin` Auth.js |
| 8 | `/admin` (layout + dashboard) | `AdminLayout` sidebar + KPIs (orders count, revenue, low-stock) | Composé : product/user/order admin reads | M+ | Bloque l'accès aux pages admin filles |
| 9 | `/admin/products` | Tableau + create + create-with-image (multipart) + update + delete | Voir §1.2 ligne `/admin/products` | L | Coeur du back-office (image upload = effort +) |
| 10 | `/admin/users` | Tableau + create + update + soft-delete | Voir §1.2 ligne `/admin/users` | M | Standard CRUD |
| 11 | `/admin/orders` | Tableau + filter status + drilldown | **Pas de listing — voir §5 bloquant** | M (avec endpoint back) | Sans liste, l'admin n'a pas de back-office orders |
| 12 | `/admin/discounts` | 5 cartes/lignes (1 par `DiscountType`) — toggle actif + édition `percentage`/`flat`/`window` | `GET/PATCH /admin/discounts/{type}` + `GET /admin/discounts/` (listAll) | S | Le back limite à un upsert par enum — UI très simple |

### 2.B — UX e-commerce standard manquantes (hors plan mais nécessaires)

| # | Route / fichier | Description | Effort | Justification |
|---|---|---|---|---|
| 1 | `app/not-found.tsx` | 404 stylé avec retour catalogue | S | Convention Next 16 ; aujourd'hui un 404 brut |
| 2 | `app/error.tsx` + `app/global-error.tsx` | Boundaries d'erreur globaux | S | Convention Next 16 — toute erreur server-side aujourd'hui crashe en blanc |
| 3 | `app/loading.tsx` (par segment) | Skeletons routés (catalog, account, admin) | M | Long TTFB sur `/products` server-side, aucun feedback visuel |
| 4 | `/search` ou search inline header | Recherche produit live (autocomplete + redirige `/products?search=...`) | M | L'input search Header existe déjà mais n'est **pas câblé** |
| 5 | `/about` | Présentation marque | S | Crédibilité / SEO / lien Footer absent |
| 6 | `/contact` | Form contact ou mailto stylé | S | Footer pointe `mailto:hello@cybertech.local` brut |
| 7 | `/legal/privacy` | Politique de confidentialité (RGPD) | S | Footer renvoie 404 — obligation EU |
| 8 | `/legal/terms` | CGU/CGV | S | Idem — 404 actuel |
| 9 | `/legal/cookies` | Notice cookies + bandeau consentement | S | RGPD — manquant entièrement (pas de cookie banner non plus) |
| 10 | `/support` | Hub support (lien depuis Header `Support` qui existe) | S | NAV_LINKS Header pointe vers `/support` → 404 |
| 11 | `/support/warranty` | Page garantie | S | Footer pointe ici → 404 |
| 12 | `/support/shipping` | Page livraison | S | Footer pointe ici → 404 |
| 13 | `/support/returns` ou `/account/returns` | Politique retours | S | Standard e-commerce |
| 14 | `/support/faq` | FAQ catégorisée | S | Réduit le support load |
| 15 | `/auth/forgot-password` + reset | Mot de passe oublié | M | Délégué Keycloak à la rigueur, mais pas de lien dans l'UI |
| 16 | `/checkout/cancel` ou page erreur paiement dédiée | Si Stripe retourne en échec hors flow polling | S | Aujourd'hui l'échec se voit seulement dans `/order-success/[uuid]` via badge |

### 2.C — Endpoints backend exposés sans écran front

| Endpoint | Description | UI proposée | Priorité |
|---|---|---|---|
| `GET /api/v1/services/discounts/active` | Liste publique des campagnes actives | Page `/promotions` ou `/deals` listant les campagnes (banner home les utilise déjà mais ne montre qu'une seule) | C |
| `GET /api/v1/services/review/reviewable` | Mes commandes éligibles à un avis | Section "À noter" dans `/account/orders` (CTA + form review modal) | B (déjà partiellement câblé sur `/products/[uuid]`) |
| `POST /api/v1/services/review/create` + update + delete | Écriture/édition d'avis | Modal "Write a review" depuis `/account/orders/[uuid]` ; édition depuis `/account/reviews` (page nouvelle) | B |
| `GET /api/v1/services/review/get/{uuid}` | Détail review | Pas besoin d'écran dédié — embedded sur product detail (déjà pré-câblé) | D |
| `POST /api/v1/services/wishlist/add/{p}` | Ajout wishlist | Bouton "heart" sur ProductCard + ProductActions (server action existe `actions/wishlist.ts`) | B (action côté lib mais bouton à câbler partout) |
| `GET /api/v1/services/wishlist/admin/all` + `DELETE admin/delete/{u}` | Modération wishlists | Onglet `/admin/wishlists` (utile pour ops, pas critique) | C |
| `POST /api/v1/services/order/place/auto` | Place une commande synthétique (USER) | Bouton dev-only dans `/admin` ou `/account/dev` (DEV banner) | D |
| `GET /api/v1/services/admin/discounts` (listAll) | Liste toutes les campagnes (5 enum) | `/admin/discounts` table avec toggle ON/OFF | A (déjà dans M4) |
| `POST /api/v1/events/consume-event` | Ingestion événement user (analytics) | À câbler dans les composants tracking (pas une page, mais un hook `useTrackEvent`) | C — instrumentation |
| `GET /api/v1/services/user/ok` | Auth probe | Healthcheck dev — pas d'UI | D |
| `POST /api/v1/services/admin/user/register/auto` | Génération de users seed | Bouton "Seed users" dans `/admin/dev` | D |
| `POST /api/v1/services/stripe/webhooks` | Webhook Stripe | Server-to-server, jamais exposé côté front | — |

### 2.D — Cohérence et finition

| Sujet | Description | Effort |
|---|---|---|
| Footer riche | Footer actuel est minimal (3 colonnes mal équilibrées). Lui donner 4 colonnes : Shop / Account / Help / Legal + newsletter capture + socials | S |
| Lien Support / Pre-buys / Builds Header | NAV_LINKS Header pointe `/support`, `/products?category=builds` (catégorie inexistante côté back — enums : COMPUTER, MONITOR, KEYBOARD, SMARTPHONE), `/products?promo=1` (pas implémenté) | S |
| Mobile drawer nav | Le burger n'a actuellement aucun handler — Header masque les liens en `md:flex`, mais aucune alternative mobile | M |
| Breadcrumbs | Sur `/products`, `/products/[uuid]`, `/checkout`, `/account/*` | S |
| Notification toasts (Sonner ou shadcn-toast) | Pour add-to-cart, errors API (server actions retournent void → silence) | S |
| États vides | Cart vide / wishlist vide / search no-results / orders vides — patterns illustrés | S/M |
| Skeletons loading | `ProductCard` skeleton, `OrderRow` skeleton | M |
| Cookie consent banner | RGPD — non présent | S |
| Page maintenance / 503 | Si backend down (le home tolère, pas le checkout) | S |
| Câbler search input Header | POST `/product/search` avec debounce + dropdown autocomplete + redirect `/products?search=` | M |
| Câbler bouton wishlist sur ProductCard / ProductActions | Action `lib/actions/wishlist.ts` existe mais n'est pas attachée à un bouton sur ProductCard | S |
| Theme dark/light toggle | Tokens M3 existent, pas de switch | S |
| i18n (FR / EN) | Footer a un bouton "language" inerte | M |
| Sitemap.xml + robots.ts | SEO de base | S |

---

## 3. Endpoints backend manquants pour rendre le front complet

Bloquants identifiés en cours d'audit (non bloquants techniques mais bloquants UX) :

1. **`GET /api/v1/services/order/mine`** (ou similaire) — listing des commandes du user authentifié avec filtres status + pagination. Aujourd'hui `OrderManagementController` n'expose **que** `getOrderByUuid`, `getOrderStatusByUuid`. Sans cet endpoint, `/account/orders` ne peut pas exister proprement (workaround : stocker les UUID en localStorage post-checkout — fragile).
2. **`GET /api/v1/services/management/order/get/all`** — listing admin des commandes (filter status + date + user). Nécessaire pour `/admin/orders`.
3. **Self-update profile** (`PATCH /api/v1/services/user/me` ou équivalent) — actuellement seul `UserManagementAdminController.update` existe (admin only). Sans cet endpoint, `/account/profile` est en lecture seule.
4. **`GET /api/v1/services/bank-card/all-mine`** — la liste de cartes du user. Le back n'expose que `/default` (1 carte default) — pas de listing user-side. Donc `/account/cards` ne peut afficher qu'**une** carte. Peut être OK selon le modèle métier (la migration BankCard a peut-être imposé "1 carte par user"), à confirmer avec le PO.

---

## 4. Plan de complétude — proposition d'ordre

### Phase A — Continuité M3 (account)
1. `/account/profile` (lecture seule pour commencer) + layout `/account` partagé
2. `/account/wishlist`
3. `/account/cards` (CRUD limité au modèle 1-card actuel)
4. `/account/orders` + `/account/orders/[uuid]` *(après ajout endpoint backend "mine")*

### Phase B — UX standard (quick wins)
5. `app/not-found.tsx` + `app/error.tsx` + `app/loading.tsx`
6. Câbler search input Header (autocomplete vers `/products?search=`)
7. Footer 4-colonnes + corriger les liens cassés (NAV_LINKS Header + Footer)
8. Toast system (Sonner) + câblage server-action errors
9. `/legal/{privacy,terms,cookies}` + `/about` + `/support` + `/support/{warranty,shipping,faq,returns}`
10. Cookie consent banner

### Phase C — Auth UX
11. `/auth/register` (multi-step)
12. `/auth/login` (custom Keycloak handoff)
13. `/auth/forgot-password` (renvoi Keycloak)

### Phase D — Admin M4
14. `AdminLayout` + `/admin` dashboard
15. `/admin/products` (incl. multipart create-with-image)
16. `/admin/users`
17. `/admin/discounts`
18. `/admin/orders` *(après ajout endpoint backend)*

### Phase E — Finition
19. Mobile drawer nav
20. Breadcrumbs sur catalog/product/checkout/account
21. Skeletons + états vides illustrés sur tout le site
22. Bouton wishlist câblé sur ProductCard / ProductActions
23. Sitemap + robots
24. (Optionnel) Theme toggle + i18n

---

## 5. Quick wins (< 30 min chacun)

- `app/not-found.tsx` (composant brand + bouton catalogue)
- `app/error.tsx` (reset boundary + lien retour)
- Footer multi-colonne (juste un refactor JSX)
- `/about` (page statique)
- `/legal/privacy` + `/legal/terms` (markdown imported)
- `/support` hub (3 cartes liens)
- États vides cart/wishlist (composant `EmptyState` réutilisable)
- Câbler search input Header → `/products?search=...` (form simple GET)
- Câbler bouton wishlist sur `ProductCard` (server action déjà écrite — `actions/wishlist.ts`)
- Corriger NAV_LINKS Header (`/products?category=builds` → catégorie inexistante côté back)

---

## 6. Synthèse

**Pages manquantes totales : 51**
- A. Indispensables (M3+M4) : **12** pages
- B. UX e-commerce standard : **16** pages
- C. Endpoints back sans front : **8** UI dérivées
- D. Cohérence / finition : **15** chantiers (pages + composants + UX)

**Effort cumulé estimé : ~80 à 100 heures de dev front** (en comptant tests Vitest/Playwright légers, hors design custom poussé) — sans les 4 endpoints backend manquants à ajouter.

**Bloquants (réels) :**
- `GET /order/mine` (user) — bloque `/account/orders` proprement
- `GET /admin/management/order/get/all` (admin) — bloque `/admin/orders`
- `PATCH /user/me` (user self-update) — limite `/account/profile` à read-only
- (Confirm avec PO) listing multi-cartes — limite `/account/cards`

**Bloquants (apparents — pas réels) :** aucun. Tout le reste s'appuie sur des endpoints exposés.

**Recommandation immédiate :** attaquer le bucket B (quick wins) pour fixer les liens cassés visibles (Footer / NAV_LINKS Header pointent vers 404) avant de pousser plus de fonctionnalités. Cohérence > complétude — un site avec 4 liens 404 paraît cassé même si le funnel d'achat fonctionne.

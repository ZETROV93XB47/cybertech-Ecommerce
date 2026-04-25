# Cybertech E-commerce Frontend Design Specification

## 1. Project Overview
**Brand Name:** Cybertech
**Focus:** High-end tech products (Computers, Monitors, MacBooks, Keyboards, Smartphones).
**Visual Style:** Modern, premium, clean, and responsive. Inspired by the provided Figma reference but optimized for a scalable Next.js production environment.
**Tech Stack Foundation:** Next.js, Component-based architecture, Mobile-first design, Mock data structured for future REST API (Spring Boot) integration.

---

## 2. Full Page List & Route Structure

### Customer Facing (Public & Auth Protected)
1.  **Home Page (`/`)**: Hero, featured categories, best sellers, brand highlights.
2.  **Product Catalog (`/products`)**: Grid view with advanced filtering (Sidebar) and sorting.
3.  **Product Detail (`/products/[uuid]`)**: Full specs, gallery, reviews, and related items.
4.  **Cart (`/cart`)**: Item management, quantity adjustment, and order summary.
5.  **Checkout (`/checkout`)**: Shipping info, payment selection, and final review.
6.  **Order Confirmation (`/order-success/[uuid]`)**: Success state and order overview.
7.  **Login (`/auth/login`)**: OIDC/JWT ready form.
8.  **Register (`/auth/register`)**: Multi-step registration (Personal info + Address).

### Customer Account (Protected)
9.  **My Orders (`/account/orders`)**: List of past and current orders with status badges.
10. **Order Detail (`/account/orders/[uuid]`)**: Detailed tracking and payment retry/cancel options.
11. **Wishlist (`/account/wishlist`)**: Saved items with 'Add to Cart' functionality.
12. **Profile / Account (`/account/profile`)**: Personal details, address management.
13. **Bank Cards (`/account/cards`)**: Masked card management.

### Admin Dashboard (Protected)
14. **Dashboard Overview (`/admin`)**: KPI cards and activity charts.
15. **Product Management (`/admin/products`)**: CRUD interface for the product catalog.
16. **User Management (`/admin/users`)**: User list and role management.
17. **Order Management (`/admin/orders`)**: Order fulfillment and status updates.

---

## 3. Component Architecture

### Layout Components
- **`MainLayout`**: Includes `Header`, `Footer`, and `Breadcrumb`.
- **`AdminLayout`**: Includes `AdminSidebar` and `AdminHeader`.
- **`AuthLayout`**: Focused layout for login/register.

### Shared UI Components
- **Navigation**: `Navbar`, `CategoryMenu`, `UserDropdown`, `Pagination`.
- **Product**: `ProductCard`, `ProductGrid`, `RatingStars`, `PriceRangeFilter`, `BrandFilter`.
- **Feedback**: `StatusBadge`, `EmptyState`, `LoadingSkeleton`, `ErrorState`, `Toast`.
- **Forms**: `Input`, `Select`, `AddressForm`, `PaymentSelector`.
- **Overlays**: `Modal`, `Drawer` (for mobile filters).

---

## 4. Integration Strategy (Mock -> Real)
- **API Versioning**: Every mock service is designed to include `X-API-VERSION: 1.0`.
- **DTO Mapping**: Mock data objects (e.g., `Product`, `Order`, `Cart`) match the provided backend schemas exactly.
- **Service Layer**: I will structure the components to use a clean service layer that Claude can later swap from local mocks to `fetch`/`axios` calls.

---

## 5. Next Steps
1. **Design System**: Establish typography, color palette (premium tech vibes), and base tokens.
2. **Core Components**: Predict and build the shared building blocks.
3. **Screen Generation**: Systematic creation of the pages listed above.

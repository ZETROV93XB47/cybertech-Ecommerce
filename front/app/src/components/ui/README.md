# UI primitives — feedback components

Reusable, dependency-free building blocks for client-side feedback. No Sonner, no shadcn — just React + Tailwind 4.

## Toasts

```tsx
// 1. Mount the provider ONCE in a client subtree (e.g. inside MainLayout)
// "use client";
import { ToastProvider } from "@/components/providers/ToastProvider";

export function MainLayout({ children }) {
  return <ToastProvider>{/* header, children, footer */}</ToastProvider>;
}

// 2. Fire toasts from any client component
"use client";
import { useToast } from "@/components/ui/use-toast";

const { toast } = useToast();
toast({ type: "success", title: "Added to cart" });
toast({ type: "error", title: "Payment failed", description: "Card declined." });
toast({ type: "info", title: "Heads up", duration: 0 }); // sticky
```

`app/layout.tsx` stays a Server Component — wrap inside `MainLayout` (or any other client-side layout) instead.

Max 4 visible at once, auto-dismiss 5 s, top-right desktop / top-center mobile.

## Skeletons (`./skeletons/`)

- `<ProductCardSkeleton />` — matches `ProductCard` exactly (no layout shift).
- `<TableRowSkeleton columns={N} />` — emits one `<tr>`; wrap N inside `<tbody>`.
- `<OrderRowSkeleton />` — order list row.
- `<TextSkeleton lines={3} width="40%" />` — generic text block.

## EmptyState

```tsx
<EmptyState icon="shopping_cart" title="Your cart is empty"
  description="Start adding products." cta={{ label: "Browse", href: "/products" }} />
```

## Spinner

```tsx
<Spinner size={16} className="text-on-primary" />
```

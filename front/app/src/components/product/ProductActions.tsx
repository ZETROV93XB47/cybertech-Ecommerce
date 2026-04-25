"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { addToCartAction } from "@/lib/actions/cart";
import { toggleWishlistAction } from "@/lib/actions/wishlist";

/**
 * Quantity stepper + Add to Cart + Wishlist toggle for the product detail page.
 * Both actions are server actions guarded by `auth()` — the action redirects
 * unauthenticated callers to the hosted Keycloak login.
 */
export function ProductActions({
  productUuid,
  initialWishlisted = false,
}: {
  productUuid: string;
  initialWishlisted?: boolean;
}) {
  const router = useRouter();
  const [qty, setQty] = useState(1);
  const [wishlisted, setWishlisted] = useState(initialWishlisted);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  const onAdd = () =>
    startTransition(async () => {
      setError(null);
      const res = await addToCartAction(productUuid, qty, `/products/${productUuid}`);
      if (!res.ok) setError(res.error);
      else router.push("/cart");
    });

  const onToggleWishlist = () =>
    startTransition(async () => {
      setError(null);
      const next = !wishlisted;
      const res = await toggleWishlistAction(productUuid, next);
      if (res.ok) setWishlisted(next);
      else setError(res.error);
    });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-4">
        <div className="flex items-center border border-slate-200 rounded-lg h-14 px-4 bg-white">
          <button
            type="button"
            onClick={() => setQty((q) => Math.max(1, q - 1))}
            disabled={pending || qty <= 1}
            aria-label="Decrease quantity"
            className="text-slate-500 hover:text-primary transition-colors disabled:opacity-40"
          >
            <Icon name="remove" />
          </button>
          <span className="px-6 font-display font-semibold text-headline-sm tabular-nums">
            {qty}
          </span>
          <button
            type="button"
            onClick={() => setQty((q) => q + 1)}
            disabled={pending}
            aria-label="Increase quantity"
            className="text-slate-500 hover:text-primary transition-colors disabled:opacity-40"
          >
            <Icon name="add" />
          </button>
        </div>
        <button
          type="button"
          onClick={onAdd}
          disabled={pending}
          className="flex-1 bg-secondary text-on-secondary h-14 rounded-lg font-display font-semibold text-base hover:opacity-90 active:scale-[0.98] transition-all flex items-center justify-center gap-2 disabled:opacity-60"
        >
          <Icon name="shopping_cart" />
          {pending ? "Adding…" : "Add to Cart"}
        </button>
      </div>

      <button
        type="button"
        onClick={onToggleWishlist}
        disabled={pending}
        className="w-full border border-slate-300 h-14 rounded-lg font-body hover:bg-slate-50 active:scale-[0.99] transition-all flex items-center justify-center gap-2 disabled:opacity-60"
      >
        <Icon name="favorite" filled={wishlisted} />
        {wishlisted ? "Saved to Wishlist" : "Add to Wishlist"}
      </button>

      {error && (
        <p
          role="alert"
          className="text-sm text-on-error-container bg-error-container/60 px-4 py-2 rounded-lg"
        >
          {error}
        </p>
      )}
    </div>
  );
}

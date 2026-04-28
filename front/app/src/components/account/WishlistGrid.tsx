"use client";

import Link from "next/link";
import Image from "next/image";
import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { formatMoney } from "@/lib/format";
import { toggleWishlistAction } from "@/lib/actions/wishlist";
import { addToCartAction } from "@/lib/actions/cart";
import type { WishlistResponseDto } from "@/lib/api";

/**
 * Full-page wishlist grid (in contrast with WishlistPreview, which is the
 * compact version on /account/profile).
 *
 * Each tile has two interactive controls:
 *   - "Add to cart" → Server Action against /cart/add
 *   - heart toggle  → Server Action against /wishlist/remove
 *
 * We track per-item pending state with Map-based state so two cards can
 * mutate independently without one disabling the other.
 */

interface WishlistGridProps {
  items: WishlistResponseDto[];
}

export function WishlistGrid({ items }: WishlistGridProps) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const onRemove = (productUuid: string) => {
    setError(null);
    setBusyKey(`remove-${productUuid}`);
    startTransition(async () => {
      const res = await toggleWishlistAction(productUuid, false);
      if (!res.ok) setError(res.error ?? "Could not remove the item.");
      else router.refresh();
      setBusyKey(null);
    });
  };

  const onAddToCart = (productUuid: string) => {
    setError(null);
    setBusyKey(`cart-${productUuid}`);
    startTransition(async () => {
      const res = await addToCartAction(productUuid, 1, "/account/wishlist");
      if (!res.ok) setError(res.error ?? "Could not add to cart.");
      setBusyKey(null);
    });
  };

  return (
    <div className="space-y-4">
      {error && (
        <p
          role="alert"
          className="text-sm text-on-error-container bg-error-container/60 px-4 py-3"
        >
          {error}
        </p>
      )}
      <ul className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {items.map((entry) => {
          const product = entry.product;
          const photo = product.photoUrl?.trim()
            ? product.photoUrl
            : "/product-placeholder.svg";
          const removeBusy = pending && busyKey === `remove-${product.uuid}`;
          const cartBusy = pending && busyKey === `cart-${product.uuid}`;
          return (
            <li
              key={entry.uuid}
              className="bg-white border border-slate-100 flex flex-col"
            >
              <Link
                href={`/products/${product.uuid}`}
                className="group flex flex-col flex-1"
              >
                <div className="aspect-square bg-surface-container-low overflow-hidden flex items-center justify-center p-6">
                  <Image
                    src={photo}
                    alt={product.name}
                    width={320}
                    height={320}
                    unoptimized
                    className="w-full h-full object-contain group-hover:scale-105 transition-transform duration-500"
                  />
                </div>
                <div className="px-5 pt-4 pb-3 space-y-1 flex-1">
                  {product.brand && (
                    <span className="font-label-caps uppercase text-secondary block tracking-wider text-xs">
                      {product.brand}
                    </span>
                  )}
                  <p className="font-display text-sm font-semibold line-clamp-2 min-h-[2.5rem]">
                    {product.name}
                  </p>
                  <span className="font-display text-base font-semibold text-primary">
                    {formatMoney(product.price)}
                  </span>
                </div>
              </Link>

              <div className="flex items-stretch border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => onAddToCart(product.uuid)}
                  disabled={cartBusy}
                  className="flex-1 inline-flex items-center justify-center gap-2 h-11 font-display text-xs font-semibold uppercase tracking-wider bg-primary text-on-primary hover:bg-slate-800 transition-colors disabled:opacity-60"
                >
                  <Icon name="shopping_cart" size={16} />
                  {cartBusy ? "Adding…" : "Add to cart"}
                </button>
                <button
                  type="button"
                  onClick={() => onRemove(product.uuid)}
                  disabled={removeBusy}
                  aria-label={`Remove ${product.name} from wishlist`}
                  className="inline-flex items-center justify-center w-12 h-11 border-l border-slate-100 hover:bg-slate-50 transition-colors disabled:opacity-60 text-on-surface-variant hover:text-error"
                >
                  <Icon
                    name={removeBusy ? "hourglass_top" : "delete"}
                    size={18}
                  />
                </button>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

"use client";

import { useState, useTransition } from "react";
import { Icon } from "@/components/ui/Icon";
import { toggleWishlistAction } from "@/lib/actions/wishlist";

/**
 * Heart-shaped wishlist toggle pinned top-right of the ProductCard image. The
 * server action `toggleWishlistAction(productUuid, add)` is the source of
 * truth — we flip optimistically and roll back on failure so the click feels
 * instant. The heart is filled when the product is saved.
 *
 * `stopPropagation` on the click matters: the surrounding card has a <Link>
 * that wraps the image, and we don't want a wishlist toggle to also navigate
 * to the product detail page.
 */
export function WishlistHeartButton({
  productUuid,
  initialWishlisted = false,
  className = "",
}: {
  productUuid: string;
  initialWishlisted?: boolean;
  className?: string;
}) {
  const [wishlisted, setWishlisted] = useState(initialWishlisted);
  const [pending, startTransition] = useTransition();

  const onToggle = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    e.stopPropagation();
    const next = !wishlisted;
    // optimistic flip
    setWishlisted(next);
    startTransition(async () => {
      const res = await toggleWishlistAction(productUuid, next);
      if (!res.ok) {
        // rollback
        setWishlisted(!next);
      }
    });
  };

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={pending}
      aria-label={wishlisted ? "Remove from wishlist" : "Add to wishlist"}
      aria-pressed={wishlisted}
      className={`absolute top-3 right-3 w-10 h-10 rounded-full bg-white/90 backdrop-blur-sm shadow-sm flex items-center justify-center transition-all duration-200 hover:bg-white hover:scale-110 active:scale-95 disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-secondary focus-visible:ring-offset-2 ${className}`}
    >
      <Icon
        name={wishlisted ? "favorite" : "favorite_border"}
        filled={wishlisted}
        size={20}
        className={wishlisted ? "text-error" : "text-on-surface-variant"}
      />
    </button>
  );
}

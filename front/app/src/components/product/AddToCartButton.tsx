"use client";

import { useState, useTransition } from "react";
import { Icon } from "@/components/ui/Icon";
import { addToCartAction } from "@/lib/actions/cart";

/**
 * Full-width "Add to cart" CTA used on the ProductCard tile. Server-action
 * driven (cart.addToCartAction); client-side here only to surface a pending
 * state and a transient inline error / success label without a full reload —
 * the action itself revalidates `/cart`.
 */
export function AddToCartButton({
  productUuid,
  productName,
  className = "",
  callbackUrl,
}: {
  productUuid: string;
  productName: string;
  className?: string;
  callbackUrl?: string;
}) {
  const [pending, startTransition] = useTransition();
  const [feedback, setFeedback] = useState<"added" | "error" | null>(null);

  const onClick = () =>
    startTransition(async () => {
      setFeedback(null);
      const res = await addToCartAction(
        productUuid,
        1,
        callbackUrl ?? `/products/${productUuid}`,
      );
      if (res.ok) {
        setFeedback("added");
        // brief confirmation, then return to default label
        setTimeout(() => setFeedback(null), 1500);
      } else {
        setFeedback("error");
      }
    });

  const label = pending
    ? "Adding…"
    : feedback === "added"
      ? "Added"
      : feedback === "error"
        ? "Try again"
        : "Add to cart";

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={pending}
      aria-label={`Add ${productName} to cart`}
      className={`w-full h-12 rounded-xl bg-primary text-on-primary font-display font-semibold text-sm tracking-wide flex items-center justify-center gap-2 hover:bg-secondary active:scale-[0.98] transition-all duration-200 disabled:opacity-60 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-secondary focus-visible:ring-offset-2 ${className}`}
    >
      <Icon
        name={feedback === "added" ? "check" : "add_shopping_cart"}
        size={18}
      />
      <span className="font-label-caps uppercase">{label}</span>
    </button>
  );
}

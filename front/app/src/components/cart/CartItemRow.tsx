"use client";

import Image from "next/image";
import Link from "next/link";
import { useTransition } from "react";
import { Icon } from "@/components/ui/Icon";
import { formatMoney } from "@/lib/format";
import {
  addToCartAction,
  decreaseCartItemAction,
  removeFromCartAction,
} from "@/lib/actions/cart";
import type { CartItemResponseDto } from "@/lib/api";

/**
 * A single cart row with qty stepper and remove. Pending state disables the
 * controls while the corresponding server action is in flight; the page is
 * revalidated server-side so the new totals show up after the transition.
 */
export function CartItemRow({ item }: { item: CartItemResponseDto }) {
  const [pending, startTransition] = useTransition();

  const inc = () =>
    startTransition(async () => {
      await addToCartAction(item.productUuid, 1);
    });
  const dec = () =>
    startTransition(async () => {
      await decreaseCartItemAction(item.productUuid);
    });
  const remove = () =>
    startTransition(async () => {
      await removeFromCartAction(item.productUuid);
    });

  const disabled = pending;

  return (
    <article
      className={`group flex flex-col sm:flex-row items-center gap-stack-md p-gutter bg-surface-container-lowest border border-outline-variant/30 rounded-xl transition-all duration-300 hover:shadow-[0_8px_30px_rgb(0,0,0,0.04)] ${
        disabled ? "opacity-60" : ""
      }`}
    >
      <Link
        href={`/products/${item.productUuid}`}
        className="w-full sm:w-40 h-40 bg-slate-100 rounded-lg overflow-hidden shrink-0 relative"
      >
        <Image
          src="/product-placeholder.svg"
          alt=""
          width={160}
          height={160}
          unoptimized
          className="w-full h-full object-cover"
        />
      </Link>

      <div className="flex-grow flex flex-col justify-between w-full">
        <div className="flex justify-between items-start gap-4">
          <div className="min-w-0">
            <Link href={`/products/${item.productUuid}`}>
              <h3 className="font-display font-semibold text-headline-sm text-primary mb-1 hover:text-secondary transition-colors line-clamp-2">
                {item.productName}
              </h3>
            </Link>
            <p className="font-label-caps text-on-surface-variant uppercase">
              {formatMoney(item.unitPrice)} each
            </p>
          </div>
          <p className="font-display font-semibold text-headline-sm text-secondary whitespace-nowrap">
            {formatMoney(item.lineItemTotalPrice)}
          </p>
        </div>

        <div className="mt-stack-lg flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center border border-outline-variant rounded-full p-1 bg-surface-bright">
            <button
              type="button"
              onClick={dec}
              disabled={disabled || item.quantity <= 1}
              aria-label="Decrease quantity"
              className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-primary transition-colors disabled:opacity-40"
            >
              <Icon name="remove" size={16} />
            </button>
            <span className="w-10 text-center font-display font-semibold text-sm tabular-nums">
              {item.quantity}
            </span>
            <button
              type="button"
              onClick={inc}
              disabled={disabled}
              aria-label="Increase quantity"
              className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-primary transition-colors disabled:opacity-40"
            >
              <Icon name="add" size={16} />
            </button>
          </div>

          <button
            type="button"
            onClick={remove}
            disabled={disabled}
            className="flex items-center gap-1 font-label-caps uppercase text-error hover:opacity-80 transition-opacity disabled:opacity-40"
          >
            <Icon name="delete" size={18} />
            Remove
          </button>
        </div>
      </div>
    </article>
  );
}

"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { cancelOrderAction, retryPaymentAction } from "@/lib/actions/order";
import type { OrderStatus } from "@/lib/api";

/**
 * Action bar shown on /account/orders/[uuid].
 *
 * Surfaced controls depend on the order's current status:
 *  - PAYMENT_FAILED → Retry payment
 *  - CREATED, AWAITING_PAYMENT, PAID, AWAITING_SHIPPING → Cancel
 *
 * Once the status moves past AWAITING_SHIPPING the order is in transit
 * (or terminal) and the user can no longer mutate it from this surface.
 */

const CANCELLABLE: OrderStatus[] = [
  "CREATED",
  "AWAITING_PAYMENT",
  "PAID",
  "AWAITING_SHIPPING",
];

interface OrderActionsProps {
  orderUuid: string;
  status: OrderStatus;
}

export function OrderActions({ orderUuid, status }: OrderActionsProps) {
  const router = useRouter();
  const [pendingCancel, startCancel] = useTransition();
  const [pendingRetry, startRetry] = useTransition();
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const canCancel = CANCELLABLE.includes(status);
  const canRetry = status === "PAYMENT_FAILED";

  if (!canCancel && !canRetry) return null;

  const onCancel = () =>
    startCancel(async () => {
      setError(null);
      const res = await cancelOrderAction(
        orderUuid,
        "Customer requested cancellation",
      );
      if (!res.ok) setError(res.error ?? "Could not cancel the order.");
      else {
        setConfirming(false);
        router.refresh();
      }
    });

  const onRetry = () =>
    startRetry(async () => {
      setError(null);
      const res = await retryPaymentAction(orderUuid);
      if (!res.ok) setError(res.error ?? "Retry failed.");
      else router.refresh();
    });

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-3">
        {canRetry && (
          <button
            type="button"
            onClick={onRetry}
            disabled={pendingRetry}
            className="inline-flex items-center justify-center gap-2 h-12 px-6 bg-error text-on-error font-display text-sm font-semibold uppercase tracking-wider hover:brightness-110 active:scale-[0.98] transition-all disabled:opacity-60"
          >
            <Icon name="autorenew" size={18} />
            {pendingRetry ? "Retrying…" : "Retry payment"}
          </button>
        )}

        {canCancel && !confirming && (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="inline-flex items-center justify-center gap-2 h-12 px-6 border border-outline text-on-surface font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors"
          >
            <Icon name="cancel" size={18} />
            Cancel order
          </button>
        )}

        {canCancel && confirming && (
          <div className="flex flex-wrap items-center gap-3 bg-error-container/40 border border-error/40 px-4 py-3">
            <span className="font-body text-sm text-on-error-container">
              Are you sure? This can&apos;t be undone.
            </span>
            <button
              type="button"
              onClick={onCancel}
              disabled={pendingCancel}
              className="inline-flex items-center justify-center gap-2 h-9 px-4 bg-error text-on-error font-display text-xs font-semibold uppercase tracking-wider hover:brightness-110 disabled:opacity-60"
            >
              {pendingCancel ? "Cancelling…" : "Confirm cancel"}
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              disabled={pendingCancel}
              className="inline-flex items-center justify-center h-9 px-4 font-display text-xs font-semibold uppercase tracking-wider text-on-surface hover:underline disabled:opacity-60"
            >
              Keep order
            </button>
          </div>
        )}
      </div>

      {error && (
        <p
          role="alert"
          className="text-sm text-on-error-container bg-error-container/60 px-4 py-3"
        >
          {error}
        </p>
      )}
    </div>
  );
}

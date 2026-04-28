import Link from "next/link";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Icon } from "@/components/ui/Icon";
import { formatMoney, formatDate } from "@/lib/format";
import type { OrderResponseDto } from "@/lib/api";

/**
 * Recent orders block. The backend doesn't yet expose a "list my orders"
 * endpoint (BUG-LISTORDERS — see Wave 6 backlog), so the page resolves what
 * it can — currently nothing — and we surface a graceful placeholder.
 *
 * When the endpoint lands, the page will pass an array of OrderResponseDto
 * here and the empty/placeholder branch becomes a real list.
 */

interface RecentOrdersProps {
  orders: OrderResponseDto[];
  /** True when the listing endpoint was attempted and failed. */
  errored: boolean;
  /** True when no listing endpoint exists yet (default for now). */
  unavailable?: boolean;
}

export function RecentOrders({ orders, errored, unavailable }: RecentOrdersProps) {
  return (
    <section
      aria-labelledby="recent-orders-heading"
      className="bg-white border border-slate-100"
    >
      <header className="flex items-end justify-between gap-4 p-6 border-b border-slate-100">
        <div>
          <h2
            id="recent-orders-heading"
            className="font-display text-headline-sm text-primary"
          >
            Recent orders
          </h2>
          <p className="font-body text-sm text-on-surface-variant mt-1">
            Your last five orders, with shipping and payment status.
          </p>
        </div>
        <Link
          href="/account/orders"
          className="hidden sm:inline-flex items-center gap-2 text-secondary font-label-caps uppercase tracking-wider hover:underline"
        >
          View all <Icon name="arrow_forward" size={14} />
        </Link>
      </header>

      <div className="p-6">
        {errored && (
          <p className="font-body text-sm text-on-surface-variant">
            We couldn&apos;t load your orders right now. Please try again later.
          </p>
        )}
        {!errored && unavailable && (
          <div className="flex flex-col items-center text-center gap-3 py-8">
            <div className="w-14 h-14 rounded-full bg-surface-container-low flex items-center justify-center">
              <Icon name="receipt_long" size={28} className="text-outline" />
            </div>
            <p className="font-body text-on-surface-variant max-w-md">
              Order history is moving in soon. Once we ship the dedicated
              listing endpoint, your purchases will surface here.
            </p>
            <Link
              href="/products"
              className="inline-flex items-center gap-2 text-secondary font-label-caps uppercase tracking-wider hover:underline"
            >
              Continue shopping <Icon name="arrow_forward" size={14} />
            </Link>
          </div>
        )}
        {!errored && !unavailable && orders.length === 0 && (
          <div className="flex flex-col items-center text-center gap-3 py-8">
            <div className="w-14 h-14 rounded-full bg-surface-container-low flex items-center justify-center">
              <Icon name="receipt_long" size={28} className="text-outline" />
            </div>
            <p className="font-body text-on-surface-variant">
              No orders yet — your future builds will live here.
            </p>
            <Link
              href="/products"
              className="inline-flex items-center gap-2 text-secondary font-label-caps uppercase tracking-wider hover:underline"
            >
              Shop hardware <Icon name="arrow_forward" size={14} />
            </Link>
          </div>
        )}
        {!errored && !unavailable && orders.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {orders.map((order) => {
              const itemsCount = order.orderItems.reduce(
                (sum, item) => sum + (item.quantity ?? 0),
                0,
              );
              return (
                <li key={order.uuid} className="py-4 first:pt-0 last:pb-0">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="space-y-1 min-w-0">
                      <p className="font-display text-sm font-semibold text-primary truncate">
                        Order #{order.uuid.slice(0, 8).toUpperCase()}
                      </p>
                      <p className="font-body text-xs text-on-surface-variant">
                        {formatDate(order.orderDate)} · {itemsCount} item
                        {itemsCount === 1 ? "" : "s"}
                      </p>
                    </div>
                    <div className="flex items-center gap-4">
                      <StatusBadge status={order.status} />
                      <span className="font-display text-base font-semibold text-primary">
                        {formatMoney(order.totalAmount)}
                      </span>
                      <Link
                        href={`/account/orders/${order.uuid}`}
                        className="text-secondary"
                        aria-label={`Open order ${order.uuid}`}
                      >
                        <Icon name="chevron_right" size={20} />
                      </Link>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}

import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatMoney, formatDate } from "@/lib/format";
import type { OrderResponseDto } from "@/lib/api";

/**
 * One row in /account/orders. Designed to read at-a-glance:
 *   - identifier on the left
 *   - status badge + total on the right
 *   - item summary as a one-liner ("3 items · CPU, GPU, …")
 */

interface OrderCardProps {
  order: OrderResponseDto;
}

export function OrderCard({ order }: OrderCardProps) {
  const itemsCount = order.orderItems.reduce(
    (sum, item) => sum + (item.quantity ?? 0),
    0,
  );
  const itemPreview = order.orderItems
    .slice(0, 3)
    .map((i) => i.productName)
    .join(", ");
  const more = order.orderItems.length > 3 ? ` +${order.orderItems.length - 3}` : "";

  return (
    <li className="bg-white border border-slate-100 hover:border-secondary/40 transition-colors">
      <Link
        href={`/account/orders/${order.uuid}`}
        className="block p-6 group"
        aria-label={`Open order ${order.uuid}`}
      >
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div className="space-y-2 min-w-0 flex-1">
            <div className="flex items-center gap-3 flex-wrap">
              <p className="font-display text-base font-semibold text-primary">
                Order #{order.uuid.slice(0, 8).toUpperCase()}
              </p>
              <StatusBadge status={order.status} />
            </div>
            <p className="font-body text-sm text-on-surface-variant">
              Placed {formatDate(order.orderDate)} · {itemsCount} item
              {itemsCount === 1 ? "" : "s"}
            </p>
            {itemPreview && (
              <p className="font-body text-sm text-on-surface line-clamp-1">
                {itemPreview}
                {more}
              </p>
            )}
          </div>
          <div className="flex items-center gap-4">
            <span className="font-display text-headline-sm font-semibold text-primary">
              {formatMoney(order.totalAmount)}
            </span>
            <Icon
              name="chevron_right"
              size={24}
              className="text-outline group-hover:text-secondary transition-colors"
            />
          </div>
        </div>
      </Link>
    </li>
  );
}

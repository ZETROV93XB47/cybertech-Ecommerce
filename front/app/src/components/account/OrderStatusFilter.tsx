import Link from "next/link";
import type { OrderStatus } from "@/lib/api";

/**
 * Status pill filter for /account/orders. Renders as a stack of links so it
 * stays SSR-friendly — the active filter is read off the current search
 * params rather than client-side state.
 */

interface OrderStatusFilterProps {
  current: OrderStatus | null;
  baseHref?: string;
}

const FILTERS: Array<{ label: string; status: OrderStatus | null }> = [
  { label: "All", status: null },
  { label: "Awaiting payment", status: "AWAITING_PAYMENT" },
  { label: "Paid", status: "PAID" },
  { label: "Shipped", status: "SHIPPED" },
  { label: "Delivered", status: "DELIVERED" },
  { label: "Cancelled", status: "CANCELED" },
];

export function OrderStatusFilter({
  current,
  baseHref = "/account/orders",
}: OrderStatusFilterProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {FILTERS.map((filter) => {
        const active = filter.status === current;
        const href = filter.status
          ? `${baseHref}?status=${filter.status}`
          : baseHref;
        return (
          <Link
            key={filter.label}
            href={href}
            aria-current={active ? "page" : undefined}
            className={`inline-flex items-center px-4 h-9 font-display text-xs font-semibold uppercase tracking-wider transition-colors border ${
              active
                ? "bg-primary text-on-primary border-primary"
                : "bg-white text-on-surface border-slate-200 hover:border-secondary hover:text-secondary"
            }`}
          >
            {filter.label}
          </Link>
        );
      })}
    </div>
  );
}

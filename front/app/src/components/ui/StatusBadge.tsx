import type { OrderStatus } from "@/lib/api";

const STATUS_STYLES: Record<OrderStatus, { label: string; className: string }> =
  {
    CREATED: {
      label: "Processing",
      className: "bg-surface-container text-on-primary-container",
    },
    PAID: {
      label: "Paid",
      className: "bg-secondary/10 text-secondary",
    },
    SHIPPED: {
      label: "Shipped",
      className: "bg-emerald-50 text-emerald-700",
    },
    DELIVERED: {
      label: "Delivered",
      className: "bg-emerald-100 text-emerald-800",
    },
    CANCELLED: {
      label: "Cancelled",
      className: "bg-slate-100 text-slate-600",
    },
    PAYMENT_FAILED: {
      label: "Payment failed",
      className: "bg-error-container text-on-error-container",
    },
  };

export function StatusBadge({ status }: { status: OrderStatus }) {
  const { label, className } = STATUS_STYLES[status];
  return (
    <span
      className={`inline-flex items-center px-3 py-1 rounded-full text-label-caps uppercase tracking-wider ${className}`}
    >
      {label}
    </span>
  );
}

import type { OrderStatus } from "@/lib/api";

const STATUS_STYLES: Record<OrderStatus, { label: string; className: string }> =
  {
    CREATED: {
      label: "Processing",
      className: "bg-surface-container text-on-primary-container",
    },
    AWAITING_PAYMENT: {
      label: "Awaiting payment",
      className: "bg-amber-50 text-amber-700",
    },
    PAID: {
      label: "Paid",
      className: "bg-secondary/10 text-secondary",
    },
    AWAITING_SHIPPING: {
      label: "Preparing",
      className: "bg-sky-50 text-sky-700",
    },
    SHIPPED: {
      label: "Shipped",
      className: "bg-emerald-50 text-emerald-700",
    },
    DELIVERED: {
      label: "Delivered",
      className: "bg-emerald-100 text-emerald-800",
    },
    RETURNED: {
      label: "Returned",
      className: "bg-slate-100 text-slate-700",
    },
    CANCELED: {
      label: "Cancelled",
      className: "bg-slate-100 text-slate-600",
    },
    REFUNDED: {
      label: "Refunded",
      className: "bg-slate-100 text-slate-700",
    },
    PAYMENT_FAILED: {
      label: "Payment failed",
      className: "bg-error-container text-on-error-container",
    },
  };

const FALLBACK_STYLE = {
  label: "Processing",
  className: "bg-surface-container text-on-primary-container",
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  const { label, className } = STATUS_STYLES[status] ?? FALLBACK_STYLE;
  return (
    <span
      className={`inline-flex items-center px-3 py-1 rounded-full text-label-caps uppercase tracking-wider ${className}`}
    >
      {label}
    </span>
  );
}

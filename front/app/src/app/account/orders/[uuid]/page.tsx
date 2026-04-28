import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, orderApi } from "@/lib/api";
import type { OrderResponseDto } from "@/lib/api";
import { AccountFrame } from "@/components/account/AccountFrame";
import { OrderActions } from "@/components/account/OrderActions";
import { Icon } from "@/components/ui/Icon";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatMoney, formatDate } from "@/lib/format";

export const metadata = { title: "Order detail — Cybertech" };
export const dynamic = "force-dynamic";

export default async function OrderDetailPage({
  params,
}: {
  params: Promise<{ uuid: string }>;
}) {
  const { uuid } = await params;

  let order: OrderResponseDto;
  try {
    order = await orderApi.get(uuid);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  const itemsCount = order.orderItems.reduce(
    (sum, item) => sum + (item.quantity ?? 0),
    0,
  );

  return (
    <AccountFrame
      eyebrow="Account"
      title={`Order #${order.uuid.slice(0, 8).toUpperCase()}`}
      description={`Placed ${formatDate(order.orderDate)} · ${itemsCount} item${itemsCount === 1 ? "" : "s"}`}
      actions={
        <Link
          href="/account/orders"
          className="inline-flex items-center gap-2 h-10 px-4 border border-outline font-display text-xs font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors"
        >
          <Icon name="arrow_back" size={14} />
          Back to orders
        </Link>
      }
    >
      <section className="bg-white border border-slate-100 p-8 flex flex-wrap items-center justify-between gap-6">
        <div className="space-y-2">
          <p className="font-label-caps uppercase tracking-[0.2em] text-on-surface-variant text-xs">
            Status
          </p>
          <StatusBadge status={order.status} />
        </div>
        <div className="space-y-2">
          <p className="font-label-caps uppercase tracking-[0.2em] text-on-surface-variant text-xs">
            Total
          </p>
          <p className="font-display text-display-lg text-primary leading-none">
            {formatMoney(order.totalAmount)}
          </p>
        </div>
        <OrderActions orderUuid={order.uuid} status={order.status} />
      </section>

      <section
        aria-labelledby="order-items-heading"
        className="bg-white border border-slate-100"
      >
        <header className="flex items-center justify-between gap-4 p-6 border-b border-slate-100">
          <h2
            id="order-items-heading"
            className="font-display text-headline-sm text-primary"
          >
            Items
          </h2>
          <span className="font-body text-sm text-on-surface-variant">
            {itemsCount} item{itemsCount === 1 ? "" : "s"}
          </span>
        </header>
        <ul className="divide-y divide-slate-100">
          {order.orderItems.map((item) => (
            <li
              key={item.orderItemUuid}
              className="px-6 py-5 flex items-center gap-5"
            >
              <div className="w-16 h-16 bg-slate-50 flex items-center justify-center flex-shrink-0">
                <Icon name="memory" size={28} className="text-outline" />
              </div>
              <div className="flex-grow min-w-0">
                <Link
                  href={`/products/${item.productUuid}`}
                  className="font-display text-base font-semibold text-primary hover:text-secondary transition-colors line-clamp-1"
                >
                  {item.productName}
                </Link>
                <p className="font-body text-sm text-on-surface-variant mt-1">
                  {formatMoney(item.unitPrice)} each · Qty {item.quantity}
                </p>
              </div>
              <div className="text-right">
                <p className="font-display text-base font-semibold text-primary">
                  {formatMoney(item.lineItemTotalPrice)}
                </p>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white border border-slate-100 p-6">
          <p className="font-label-caps uppercase tracking-[0.2em] text-on-surface-variant text-xs mb-3">
            Shipping address
          </p>
          <address className="not-italic font-body text-sm text-on-surface whitespace-pre-line leading-relaxed">
            {order.shippingAddress || "—"}
          </address>
        </div>
        <div className="bg-white border border-slate-100 p-6">
          <p className="font-label-caps uppercase tracking-[0.2em] text-on-surface-variant text-xs mb-3">
            Order timeline
          </p>
          <ul className="space-y-2 font-body text-sm text-on-surface">
            <li className="flex items-center gap-2">
              <Icon
                name="check_circle"
                size={16}
                className="text-secondary"
              />
              Placed {formatDate(order.orderDate)}
            </li>
            <li className="flex items-center gap-2 text-on-surface-variant">
              <Icon name="schedule" size={16} />
              Status: {order.status.replace(/_/g, " ").toLowerCase()}
            </li>
          </ul>
        </div>
      </section>
    </AccountFrame>
  );
}

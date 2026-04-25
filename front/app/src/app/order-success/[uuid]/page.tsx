import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { ApiError, orderApi } from "@/lib/api";
import { MainLayout } from "@/components/layout/MainLayout";
import { Icon } from "@/components/ui/Icon";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { OrderStatusPoller } from "@/components/order/OrderStatusPoller";
import { RetryPaymentButton } from "@/components/order/RetryPaymentButton";
import { formatMoney, formatDate } from "@/lib/format";
import type { OrderResponseDto } from "@/lib/api";

export const metadata = { title: "Order confirmation — Cybertech" };

const SUCCESS_COPY: Record<
  OrderResponseDto["status"],
  { headline: string; sub: string; tone: "ok" | "warn" | "fail" }
> = {
  CREATED: {
    headline: "We're confirming your payment",
    sub: "Hang tight — Stripe is finalizing the charge. This usually takes a few seconds.",
    tone: "warn",
  },
  AWAITING_PAYMENT: {
    headline: "Awaiting payment",
    sub: "Your order is awaiting payment confirmation.",
    tone: "warn",
  },
  PAID: {
    headline: "Order Confirmed",
    sub: "Thank you for your purchase. Our engineering team is preparing your hardware for shipment.",
    tone: "ok",
  },
  AWAITING_SHIPPING: {
    headline: "Preparing your shipment",
    sub: "Your order has been paid and is being prepared for shipping.",
    tone: "ok",
  },
  SHIPPED: {
    headline: "On its way",
    sub: "Your hardware has left our warehouse. A tracking link is on the way to your inbox.",
    tone: "ok",
  },
  DELIVERED: {
    headline: "Delivered",
    sub: "Enjoy your new gear. Reviews are open if you want to share notes.",
    tone: "ok",
  },
  RETURNED: {
    headline: "Order returned",
    sub: "Your order has been returned.",
    tone: "warn",
  },
  CANCELED: {
    headline: "Order cancelled",
    sub: "This order is cancelled and no charge will be captured.",
    tone: "warn",
  },
  REFUNDED: {
    headline: "Order refunded",
    sub: "Your order has been refunded.",
    tone: "warn",
  },
  PAYMENT_FAILED: {
    headline: "Payment didn't go through",
    sub: "Stripe declined the charge. You can retry below — your cart and shipping details are preserved.",
    tone: "fail",
  },
};

const FALLBACK_COPY = {
  headline: "Order received",
  sub: "Your order is being processed.",
  tone: "warn" as const,
};

export default async function OrderSuccessPage({
  params,
}: {
  params: Promise<{ uuid: string }>;
}) {
  const { uuid } = await params;
  const session = await auth();
  if (!session)
    redirect(`/api/auth/signin?callbackUrl=/order-success/${uuid}`);

  let order: OrderResponseDto;
  try {
    order = await orderApi.get(uuid);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  const copy = SUCCESS_COPY[order.status] ?? FALLBACK_COPY;
  const iconBg =
    copy.tone === "ok"
      ? "bg-secondary-container shadow-secondary/20"
      : copy.tone === "fail"
      ? "bg-error shadow-error/20"
      : "bg-tertiary-container shadow-primary/20";
  const iconName =
    copy.tone === "ok"
      ? "check_circle"
      : copy.tone === "fail"
      ? "error"
      : "hourglass_top";

  const isFailure = order.status === "PAYMENT_FAILED";

  return (
    <MainLayout>
      <OrderStatusPoller orderUuid={uuid} initialStatus={order.status} />

      <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop pt-12 pb-section-gap">
        <header className="flex flex-col items-center text-center mb-stack-lg max-w-2xl mx-auto">
          <div
            className={`w-20 h-20 rounded-full flex items-center justify-center mb-stack-md shadow-lg ${iconBg}`}
          >
            <Icon name={iconName} size={48} className="text-white" weight={600} />
          </div>
          <h1 className="font-display text-display-lg text-primary mb-stack-sm">
            {copy.headline}
          </h1>
          <p className="font-body text-body-lg text-on-surface-variant max-w-lg mb-stack-md">
            {copy.sub}
          </p>
          <div className="bg-surface-container-low px-6 py-3 rounded-lg border border-outline-variant inline-flex items-center gap-3">
            <span className="font-label-caps uppercase tracking-widest text-on-primary-container">
              Order ID
            </span>
            <code className="font-mono text-primary font-bold">{order.uuid}</code>
            <StatusBadge status={order.status} />
          </div>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-gutter items-start">
          {/* Items + addresses */}
          <div className="lg:col-span-8 space-y-gutter">
            <section className="bg-surface-container-lowest p-8 rounded-xl shadow-[0_4px_20px_-10px_rgba(0,0,0,0.05)] border border-slate-100">
              <h2 className="font-display font-semibold text-headline-sm text-primary mb-stack-lg flex items-center gap-2">
                <Icon name="inventory_2" className="text-secondary" />
                Hardware Configuration
              </h2>
              <ul className="space-y-6">
                {order.orderItems.map((item) => (
                  <li
                    key={item.orderItemUuid}
                    className="flex items-center gap-6 py-4 border-b border-outline-variant/30 last:border-b-0"
                  >
                    <div className="w-24 h-24 bg-slate-50 rounded-lg overflow-hidden flex-shrink-0 flex items-center justify-center">
                      <Icon name="memory" size={32} className="text-outline" />
                    </div>
                    <div className="flex-grow">
                      <div className="flex justify-between items-start gap-4">
                        <h3 className="font-display font-semibold text-lg text-primary">
                          {item.productName}
                        </h3>
                        <span className="font-body font-bold text-primary whitespace-nowrap">
                          {formatMoney(item.lineItemTotalPrice)}
                        </span>
                      </div>
                      <p className="text-on-surface-variant text-sm mt-1">
                        {formatMoney(item.unitPrice)} each
                      </p>
                      <span className="mt-2 inline-flex items-center px-2 py-1 bg-surface-container text-secondary text-xs font-semibold rounded">
                        QTY: {item.quantity}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            </section>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-gutter">
              <div className="bg-surface-container-lowest p-6 rounded-xl shadow-[0_4px_20px_-10px_rgba(0,0,0,0.05)] border border-slate-100">
                <span className="font-label-caps uppercase text-on-surface-variant mb-2 block">
                  Shipping to
                </span>
                <div className="text-primary font-medium whitespace-pre-line">
                  {order.shippingAddress}
                </div>
              </div>
              <div className="bg-secondary p-6 rounded-xl shadow-xl shadow-secondary/10 flex flex-col justify-between text-white">
                <div>
                  <span className="font-label-caps uppercase text-blue-100 mb-2 block">
                    Order placed
                  </span>
                  <div className="text-2xl font-display font-semibold">
                    {formatDate(order.orderDate)}
                  </div>
                </div>
                <div className="mt-4 flex items-center gap-2 text-blue-50 text-sm">
                  <Icon name="local_shipping" size={16} />
                  Standard white-glove delivery
                </div>
              </div>
            </div>
          </div>

          <aside className="lg:col-span-4 sticky top-28 space-y-gutter">
            <div className="bg-primary text-on-primary p-8 rounded-xl shadow-2xl">
              <h2 className="font-display font-semibold text-headline-sm mb-stack-lg">
                Order Total
              </h2>
              <div className="space-y-4 mb-stack-lg">
                <div className="flex justify-between text-on-primary-container font-body">
                  <span>Items</span>
                  <span className="font-semibold">
                    {order.orderItems.reduce((n, i) => n + i.quantity, 0)}
                  </span>
                </div>
                <div className="flex justify-between text-on-primary-container font-body pb-4 border-b border-white/10">
                  <span>Status</span>
                  <span className="font-semibold">
                    <StatusBadge status={order.status} />
                  </span>
                </div>
                <div className="flex justify-between text-2xl font-bold pt-2">
                  <span>Total</span>
                  <span>{formatMoney(order.totalAmount)}</span>
                </div>
              </div>

              {isFailure ? (
                <RetryPaymentButton orderUuid={uuid} />
              ) : (
                <Link
                  href="/products"
                  className="w-full bg-secondary hover:bg-secondary-container text-white py-5 rounded-lg font-bold text-lg transition-all duration-300 active:scale-95 shadow-lg shadow-secondary/20 flex items-center justify-center gap-3"
                >
                  Continue shopping
                  <Icon name="arrow_forward" />
                </Link>
              )}

              <p className="text-center text-xs text-blue-200/60 mt-6 px-4">
                A confirmation email is on its way.
              </p>
            </div>

            <Link
              href="/account/orders"
              className="bg-white p-6 rounded-xl border border-slate-100 flex items-center gap-4 group hover:shadow-lg transition-all"
            >
              <span className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-secondary group-hover:scale-110 transition-transform">
                <Icon name="receipt_long" />
              </span>
              <span>
                <span className="font-display font-bold text-primary block">
                  View all your orders
                </span>
                <span className="text-sm text-on-surface-variant">
                  Track shipments and rerun payments.
                </span>
              </span>
            </Link>
          </aside>
        </div>
      </div>
    </MainLayout>
  );
}

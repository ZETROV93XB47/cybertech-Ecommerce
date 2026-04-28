import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, orderApi } from "@/lib/api";
import { Breadcrumbs } from "@/components/ui/Breadcrumbs";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatDate, formatMoney } from "@/lib/format";

export const metadata = { title: "Admin — Order detail" };
export const dynamic = "force-dynamic";

export default async function AdminOrderDetailPage({
  params,
}: {
  params: Promise<{ uuid: string }>;
}) {
  const { uuid } = await params;

  let order;
  try {
    order = await orderApi.get(uuid);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  return (
    <div className="flex flex-col gap-6 max-w-4xl">
      <Breadcrumbs
        items={[
          { label: "Admin", href: "/admin" },
          { label: "Orders", href: "/admin/orders" },
          { label: order.uuid.slice(0, 8) + "…" },
        ]}
      />

      <header className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div className="min-w-0">
          <p className="font-label-caps uppercase tracking-[0.2em] text-xs text-slate-500">
            Order
          </p>
          <h1 className="font-display text-3xl font-bold text-slate-900 truncate">
            {order.uuid}
          </h1>
          <p className="text-slate-600 mt-1 text-sm">
            Placed {formatDate(order.orderDate)} · Customer{" "}
            <span className="font-mono text-xs">{order.userUuid}</span>
          </p>
        </div>
        <div className="flex flex-col items-start sm:items-end gap-2">
          <StatusBadge status={order.status} />
          <p className="font-display text-2xl font-bold tabular-nums text-slate-900">
            {formatMoney(order.totalAmount)}
          </p>
        </div>
      </header>

      <section className="rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="font-display text-lg font-semibold mb-3">
          Shipping address
        </h2>
        <p className="whitespace-pre-line text-sm text-slate-700">
          {order.shippingAddress || "—"}
        </p>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="font-display text-lg font-semibold mb-3">Items</h2>
        <ul className="divide-y divide-slate-100">
          {order.orderItems.map((item) => (
            <li
              key={item.orderItemUuid}
              className="py-3 flex items-center justify-between gap-4"
            >
              <div className="min-w-0">
                <p className="font-medium text-slate-900 truncate">
                  {item.productName}
                </p>
                <p className="text-xs text-slate-500 font-mono truncate">
                  {item.productUuid}
                </p>
              </div>
              <div className="flex items-center gap-6 text-sm tabular-nums">
                <span className="text-slate-500">×{item.quantity}</span>
                <span className="text-slate-700">
                  {formatMoney(item.unitPrice)}
                </span>
                <span className="font-semibold text-slate-900 min-w-[5rem] text-right">
                  {formatMoney(item.lineItemTotalPrice)}
                </span>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5">
        <h2 className="font-display text-lg font-semibold mb-3">
          Status transitions
        </h2>
        <p className="text-sm text-slate-600">
          Status mutation endpoints are not yet exposed at the admin level. As
          soon as the backend lands them they can be wired here as additional
          server-action buttons.
        </p>
      </section>

      <Link
        href="/admin/orders"
        className="text-sm text-primary hover:underline"
      >
        ← Back to orders
      </Link>
    </div>
  );
}

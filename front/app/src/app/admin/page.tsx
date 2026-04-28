import Link from "next/link";
import { ApiError, orderApi, productApi, userApi } from "@/lib/api";
import type {
  OrderResponseDto,
  Page,
  ProductResponseDto,
  UserResponseDto,
} from "@/lib/api";
import { KpiCard } from "@/components/admin/KpiCard";
import { AdminTable } from "@/components/admin/AdminTable";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Admin — Dashboard" };
export const dynamic = "force-dynamic";

/**
 * The dashboard composes its KPIs from several backend reads in parallel.
 * Every read is wrapped so a single 404/500 doesn't take the whole page
 * down — instead the corresponding card surfaces a soft "—" or a small
 * note. We don't cache: stale counts on a back-office surface are worse
 * than a fresh read on every visit.
 */
async function loadDashboard() {
  const [productsResult, usersResult, ordersResult] = await Promise.allSettled([
    productApi.listAll(0, 1),
    userApi.listAll(0, 1),
    orderApi.listAll(0, 50),
  ]);

  const products: Page<ProductResponseDto> | null =
    productsResult.status === "fulfilled" ? productsResult.value : null;

  const users: Page<UserResponseDto> | null =
    usersResult.status === "fulfilled" ? usersResult.value : null;

  let orders: Page<OrderResponseDto> | null = null;
  let ordersUnavailable = false;
  if (ordersResult.status === "fulfilled") {
    orders = ordersResult.value;
  } else {
    const reason = ordersResult.reason;
    if (reason instanceof ApiError && reason.status === 404) {
      ordersUnavailable = true;
    }
  }

  // Low-stock approximation: the public DTO doesn't expose stock, so we
  // surface "no inventory feed" instead of a misleading number. Wave 7
  // backend exposed an admin-only listAll though, so we still use that page
  // to render the "recently added" preview when available.
  const recentProducts =
    products?.content.slice(0, 5) ??
    (await safeRecentProducts());

  return {
    productsTotal: products?.totalElements ?? null,
    usersTotal: users?.totalElements ?? null,
    ordersTotal: orders?.totalElements ?? null,
    ordersUnavailable,
    statusBreakdown: countByStatus(orders?.content ?? []),
    recentOrders: (orders?.content ?? []).slice(0, 5),
    recentProducts,
  };
}

async function safeRecentProducts(): Promise<ProductResponseDto[]> {
  try {
    const page = await productApi.listAll(0, 5);
    return page.content;
  } catch {
    return [];
  }
}

function countByStatus(
  orders: OrderResponseDto[],
): Array<[OrderResponseDto["status"], number]> {
  const counts = new Map<OrderResponseDto["status"], number>();
  for (const o of orders) {
    counts.set(o.status, (counts.get(o.status) ?? 0) + 1);
  }
  return Array.from(counts.entries()).sort((a, b) => b[1] - a[1]);
}

export default async function AdminDashboardPage() {
  const data = await loadDashboard();

  return (
    <div className="flex flex-col gap-8">
      <header>
        <p className="font-label-caps uppercase tracking-[0.2em] text-xs text-slate-500">
          Overview
        </p>
        <h1 className="font-display text-3xl font-bold text-slate-900 mt-1">
          Dashboard
        </h1>
        <p className="text-slate-600 mt-2">
          Live KPIs across the catalog, customer base and order pipeline.
        </p>
      </header>

      <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label="Products"
          value={data.productsTotal ?? "—"}
          icon="inventory_2"
          hint={
            data.productsTotal === null ? "Could not load product count." : undefined
          }
        />
        <KpiCard
          label="Users"
          value={data.usersTotal ?? "—"}
          icon="group"
          hint={
            data.usersTotal === null ? "Could not load user count." : undefined
          }
        />
        <KpiCard
          label="Orders"
          value={data.ordersTotal ?? "—"}
          icon="receipt_long"
          hint={
            data.ordersUnavailable
              ? "Admin orders endpoint not yet available."
              : undefined
          }
        />
        <KpiCard
          label="Statuses tracked"
          value={data.statusBreakdown.length || "—"}
          icon="insights"
          hint="Distinct statuses in the latest 50 orders"
          tone={data.statusBreakdown.length > 0 ? "success" : "default"}
        />
      </section>

      <section className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <header className="flex items-center justify-between mb-4">
            <h2 className="font-display text-lg font-semibold">Status mix</h2>
            <Link
              href="/admin/orders"
              className="text-sm text-primary hover:underline"
            >
              View all
            </Link>
          </header>
          {data.ordersUnavailable ? (
            <p className="text-sm text-slate-500">
              Order list endpoint is not deployed yet — KPIs will populate as soon
              as the backend lands.
            </p>
          ) : data.statusBreakdown.length === 0 ? (
            <p className="text-sm text-slate-500">No orders yet.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {data.statusBreakdown.map(([status, count]) => (
                <li
                  key={status}
                  className="flex items-center justify-between text-sm"
                >
                  <StatusBadge status={status} />
                  <span className="font-semibold tabular-nums">{count}</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <header className="flex items-center justify-between mb-4">
            <h2 className="font-display text-lg font-semibold">
              Recent products
            </h2>
            <Link
              href="/admin/products"
              className="text-sm text-primary hover:underline"
            >
              Manage
            </Link>
          </header>
          {data.recentProducts.length === 0 ? (
            <p className="text-sm text-slate-500">No products yet.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-slate-100">
              {data.recentProducts.map((p) => (
                <li
                  key={p.uuid}
                  className="flex items-center justify-between py-2 text-sm"
                >
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900 truncate">
                      {p.name}
                    </p>
                    <p className="text-xs text-slate-500 truncate">
                      {p.brand} · {p.category}
                    </p>
                  </div>
                  <span className="font-semibold tabular-nums ml-4">
                    {formatMoney(p.price)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      {data.recentOrders.length > 0 && (
        <section>
          <header className="flex items-center justify-between mb-3">
            <h2 className="font-display text-lg font-semibold">
              Recent orders
            </h2>
            <Link
              href="/admin/orders"
              className="text-sm text-primary hover:underline"
            >
              View all
            </Link>
          </header>
          <AdminTable<OrderResponseDto>
            rowKey={(o) => o.uuid}
            columns={[
              {
                key: "uuid",
                header: "Order",
                cell: (o) => (
                  <Link
                    href={`/admin/orders/${o.uuid}`}
                    className="font-mono text-xs text-primary hover:underline"
                  >
                    {o.uuid.slice(0, 8)}…
                  </Link>
                ),
                nowrap: true,
              },
              { key: "orderDate", header: "Date", nowrap: true },
              {
                key: "status",
                header: "Status",
                cell: (o) => <StatusBadge status={o.status} />,
                nowrap: true,
              },
              {
                key: "totalAmount",
                header: "Total",
                cell: (o) => formatMoney(o.totalAmount),
                nowrap: true,
                className: "text-right",
                headerClassName: "text-right",
              },
            ]}
            rows={data.recentOrders}
          />
        </section>
      )}
    </div>
  );
}

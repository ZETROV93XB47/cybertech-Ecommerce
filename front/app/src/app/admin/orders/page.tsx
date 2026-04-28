import Link from "next/link";
import { ApiError, orderApi } from "@/lib/api";
import type { OrderResponseDto, OrderStatus, Page } from "@/lib/api";
import { AdminTable } from "@/components/admin/AdminTable";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatDate, formatMoney } from "@/lib/format";

export const metadata = { title: "Admin — Orders" };
export const dynamic = "force-dynamic";

const PAGE_SIZE = 20;

const STATUSES: OrderStatus[] = [
  "CREATED",
  "AWAITING_PAYMENT",
  "PAYMENT_FAILED",
  "PAID",
  "AWAITING_SHIPPING",
  "SHIPPED",
  "DELIVERED",
  "RETURNED",
  "CANCELED",
  "REFUNDED",
];

function readString(v: string | string[] | undefined): string | undefined {
  if (Array.isArray(v)) return v[0];
  return v;
}

function readNumber(v: string | string[] | undefined): number | undefined {
  const s = readString(v);
  if (s === undefined || s === "") return undefined;
  const n = Number(s);
  return Number.isFinite(n) ? n : undefined;
}

export default async function AdminOrdersPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const page = readNumber(params.page) ?? 0;
  const status = readString(params.status) as OrderStatus | undefined;

  let result: Page<OrderResponseDto> | null = null;
  let errorMessage: string | null = null;
  let unavailable = false;
  try {
    result = await orderApi.listAll(page, PAGE_SIZE, status);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      // The admin orders endpoint is delivered by another agent in parallel.
      // Until it lands, render a graceful empty state — don't error the
      // whole page.
      unavailable = true;
    } else if (err instanceof ApiError) {
      errorMessage = err.message;
    } else {
      errorMessage = "Could not load orders.";
    }
  }

  const orders = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const total = result?.totalElements ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <header>
        <p className="font-label-caps uppercase tracking-[0.2em] text-xs text-slate-500">
          Operations
        </p>
        <h1 className="font-display text-3xl font-bold text-slate-900 mt-1">
          Orders
        </h1>
        <p className="text-slate-600 mt-1">
          {unavailable
            ? "The admin order list endpoint isn't deployed yet."
            : `${total} ${total === 1 ? "order" : "orders"} on file.`}
        </p>
      </header>

      {!unavailable && (
        <form method="get" className="flex flex-wrap items-center gap-2">
          <label className="font-label-caps uppercase text-xs text-slate-500">
            Status
          </label>
          <select
            name="status"
            defaultValue={status ?? ""}
            className="bg-white border border-slate-200 rounded-lg text-sm font-medium py-1.5 px-2 focus:outline-none focus:ring-2 focus:ring-primary/30"
          >
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <button
            type="submit"
            className="px-3 py-1.5 text-sm font-medium rounded-lg border border-slate-200 hover:bg-slate-50"
          >
            Apply
          </button>
        </form>
      )}

      {unavailable ? (
        <div className="p-8 rounded-xl bg-slate-100 text-slate-600 text-sm border border-dashed border-slate-300">
          The admin order listing endpoint
          (<code>GET /api/v1/services/admin/management/order/get/all</code>)
          is not yet available. This view will populate as soon as the backend
          ships — no front-end change required.
        </div>
      ) : errorMessage ? (
        <div className="p-6 rounded-xl bg-rose-50 text-rose-700 text-sm">
          {errorMessage}
        </div>
      ) : (
        <>
          <AdminTable<OrderResponseDto>
            rowKey={(o) => o.uuid}
            emptyMessage="No orders match the current filter."
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
              { key: "userUuid", header: "Customer", nowrap: true,
                cell: (o) => (
                  <span className="font-mono text-xs text-slate-600">
                    {o.userUuid.slice(0, 8)}…
                  </span>
                ),
              },
              {
                key: "orderDate",
                header: "Date",
                cell: (o) => formatDate(o.orderDate),
                nowrap: true,
              },
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
                className: "text-right tabular-nums",
                headerClassName: "text-right",
              },
            ]}
            rows={orders}
          />
          <Pagination
            current={page}
            total={totalPages}
            baseHref="/admin/orders"
            searchParams={params}
          />
        </>
      )}
    </div>
  );
}

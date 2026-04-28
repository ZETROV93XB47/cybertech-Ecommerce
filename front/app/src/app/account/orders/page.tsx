import { ApiError, orderApi } from "@/lib/api";
import type { OrderResponseDto, OrderStatus, Page } from "@/lib/api";
import { AccountFrame } from "@/components/account/AccountFrame";
import { OrderCard } from "@/components/account/OrderCard";
import { OrderStatusFilter } from "@/components/account/OrderStatusFilter";
import { EmptyState } from "@/components/account/EmptyState";
import { Pagination } from "@/components/ui/Pagination";

export const metadata = { title: "My orders — Cybertech" };
export const dynamic = "force-dynamic";

const PAGE_SIZE = 10;

const VALID_STATUSES: OrderStatus[] = [
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

interface SearchParams {
  page?: string;
  status?: string;
}

interface LoadResult {
  page: Page<OrderResponseDto> | null;
  unavailable: boolean;
  errored: boolean;
}

async function loadOrders(
  page: number,
  status: OrderStatus | null,
): Promise<LoadResult> {
  try {
    const result = await orderApi.mine(page, PAGE_SIZE, status ?? undefined);
    return { page: result, unavailable: false, errored: false };
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      // /order/mine not yet merged on the backend — degrade gracefully.
      return { page: null, unavailable: true, errored: false };
    }
    if (err instanceof ApiError) {
      console.warn("[account/orders] orderApi.mine failed", err);
      return { page: null, unavailable: false, errored: true };
    }
    throw err;
  }
}

export default async function OrdersPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const params = await searchParams;
  const pageNumber = Math.max(0, Number(params.page ?? 0) || 0);
  const statusParam = (params.status ?? "").toUpperCase();
  const status: OrderStatus | null = VALID_STATUSES.includes(
    statusParam as OrderStatus,
  )
    ? (statusParam as OrderStatus)
    : null;

  const { page, unavailable, errored } = await loadOrders(pageNumber, status);

  return (
    <AccountFrame
      eyebrow="Account"
      title="My orders"
      description="Track shipments, retry failed payments and revisit your purchase history."
    >
      {!unavailable && !errored && <OrderStatusFilter current={status} />}

      {unavailable && (
        <EmptyState
          icon="receipt_long"
          title="Orders coming soon"
          description="We're shipping the orders listing endpoint shortly. In the meantime, your most recent purchase is still accessible from the order confirmation email."
          cta={{ href: "/products", label: "Continue shopping" }}
        />
      )}

      {errored && (
        <div className="bg-error-container/40 border border-error/40 px-6 py-5 text-on-error-container">
          <p className="font-display font-semibold mb-1">
            We couldn&apos;t load your orders
          </p>
          <p className="font-body text-sm">
            Please refresh the page or try again in a minute.
          </p>
        </div>
      )}

      {page && page.empty && (
        <EmptyState
          icon="receipt_long"
          title={status ? "No orders match this filter" : "No orders yet"}
          description={
            status
              ? "Try clearing the status filter or shopping the catalogue to add your first order."
              : "Your future builds will live here. Browse the catalogue to get started."
          }
          cta={{
            href: status ? "/account/orders" : "/products",
            label: status ? "Clear filter" : "Shop hardware",
          }}
        />
      )}

      {page && !page.empty && (
        <>
          <ul className="flex flex-col gap-4">
            {page.content.map((order) => (
              <OrderCard key={order.uuid} order={order} />
            ))}
          </ul>
          <Pagination
            current={page.number}
            total={page.totalPages}
            baseHref="/account/orders"
            searchParams={status ? { status } : {}}
          />
        </>
      )}
    </AccountFrame>
  );
}

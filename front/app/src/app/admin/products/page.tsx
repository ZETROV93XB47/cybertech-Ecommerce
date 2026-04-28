import Link from "next/link";
import { ApiError, productApi } from "@/lib/api";
import type { Page, ProductResponseDto } from "@/lib/api";
import { AdminTable } from "@/components/admin/AdminTable";
import { Pagination } from "@/components/ui/Pagination";
import { Icon } from "@/components/ui/Icon";
import { ProductRowActions } from "@/components/admin/ProductRowActions";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Admin — Products" };
export const dynamic = "force-dynamic";

const PAGE_SIZE = 20;

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

export default async function AdminProductsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const page = readNumber(params.page) ?? 0;
  const sort = readString(params.sort) ?? "name,asc";

  let result: Page<ProductResponseDto> | null = null;
  let errorMessage: string | null = null;
  try {
    result = await productApi.listAll(page, PAGE_SIZE, sort);
  } catch (err) {
    errorMessage =
      err instanceof ApiError
        ? err.message
        : "Could not load products.";
  }

  const products = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const total = result?.totalElements ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <p className="font-label-caps uppercase tracking-[0.2em] text-xs text-slate-500">
            Catalog
          </p>
          <h1 className="font-display text-3xl font-bold text-slate-900 mt-1">
            Products
          </h1>
          <p className="text-slate-600 mt-1">
            {total} {total === 1 ? "product" : "products"} in the catalog.
          </p>
        </div>
        <Link
          href="/admin/products/new"
          className="inline-flex items-center gap-2 rounded-lg bg-primary text-white px-4 py-2 text-sm font-semibold hover:opacity-90 transition-opacity self-start sm:self-auto"
        >
          <Icon name="add" size={18} />
          Add product
        </Link>
      </header>

      <form method="get" className="flex items-center gap-2 self-end">
        <label className="font-label-caps uppercase text-xs text-slate-500">
          Sort
        </label>
        <select
          name="sort"
          defaultValue={sort}
          className="bg-white border border-slate-200 rounded-lg text-sm font-medium py-1.5 px-2 focus:outline-none focus:ring-2 focus:ring-primary/30"
        >
          <option value="name,asc">Name (A–Z)</option>
          <option value="name,desc">Name (Z–A)</option>
          <option value="price,asc">Price (Low to High)</option>
          <option value="price,desc">Price (High to Low)</option>
        </select>
        <button
          type="submit"
          className="px-3 py-1.5 text-sm font-medium rounded-lg border border-slate-200 hover:bg-slate-50"
        >
          Apply
        </button>
      </form>

      {errorMessage ? (
        <div className="p-6 rounded-xl bg-rose-50 text-rose-700 text-sm">
          {errorMessage}
        </div>
      ) : (
        <>
          <AdminTable<ProductResponseDto>
            rowKey={(p) => p.uuid}
            emptyMessage="No products yet. Click 'Add product' to create the first one."
            columns={[
              {
                key: "name",
                header: "Name",
                cell: (p) => (
                  <div className="min-w-0">
                    <p className="font-semibold text-slate-900 truncate">
                      {p.name}
                    </p>
                    <p className="text-xs text-slate-500 font-mono truncate">
                      {p.uuid}
                    </p>
                  </div>
                ),
              },
              { key: "brand", header: "Brand", nowrap: true },
              { key: "category", header: "Category", nowrap: true },
              {
                key: "price",
                header: "Price",
                cell: (p) => formatMoney(p.price),
                nowrap: true,
                className: "text-right tabular-nums",
                headerClassName: "text-right",
              },
              {
                key: "actions",
                header: <span className="sr-only">Actions</span>,
                cell: (p) => <ProductRowActions uuid={p.uuid} name={p.name} />,
                nowrap: true,
                className: "text-right",
                headerClassName: "text-right",
              },
            ]}
            rows={products}
          />
          <Pagination
            current={page}
            total={totalPages}
            baseHref="/admin/products"
            searchParams={params}
          />
        </>
      )}
    </div>
  );
}

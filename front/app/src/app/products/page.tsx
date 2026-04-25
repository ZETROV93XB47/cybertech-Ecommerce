import { MainLayout } from "@/components/layout/MainLayout";
import { ProductGrid } from "@/components/product/ProductGrid";
import { CatalogFilters } from "@/components/product/CatalogFilters";
import { Pagination } from "@/components/ui/Pagination";
import { ApiError, productApi } from "@/lib/api";
import type { Page, ProductResponseDto } from "@/lib/api";

export const metadata = { title: "Products — Cybertech" };

const PAGE_SIZE = 12;

function readString(
  v: string | string[] | undefined,
): string | undefined {
  if (Array.isArray(v)) return v[0];
  return v;
}

function readNumber(v: string | string[] | undefined): number | undefined {
  const s = readString(v);
  if (s === undefined || s === "") return undefined;
  const n = Number(s);
  return Number.isFinite(n) ? n : undefined;
}

export default async function ProductsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const q = readString(params.q);
  const category = readString(params.category);
  const brand = readString(params.brand);
  const minPrice = readNumber(params.minPrice);
  const maxPrice = readNumber(params.maxPrice);
  const sort = readString(params.sort) ?? "name,asc";
  const page = readNumber(params.page) ?? 0;

  let result: Page<ProductResponseDto> | null = null;
  let errorMessage: string | null = null;
  try {
    result = await productApi.search({
      query: q,
      category: category || undefined,
      brand: brand || undefined,
      minPrice,
      maxPrice,
      page,
      size: PAGE_SIZE,
      sort,
    });
  } catch (err) {
    errorMessage =
      err instanceof ApiError
        ? err.message
        : "Could not reach the catalog right now. Please try again shortly.";
  }

  const products = result?.content ?? [];
  const total = result?.totalElements ?? 0;
  const totalPages = result?.totalPages ?? 0;
  const startIdx = result ? page * result.size + 1 : 0;
  const endIdx = result
    ? Math.min((page + 1) * result.size, total)
    : 0;

  return (
    <MainLayout>
      <div className="max-w-[1440px] mx-auto flex flex-col lg:flex-row">
        <CatalogFilters
          current={{
            q,
            category,
            brand,
            minPrice: minPrice?.toString(),
            maxPrice: maxPrice?.toString(),
          }}
        />

        <section className="flex-grow p-8">
          <header className="flex flex-col sm:flex-row justify-between sm:items-end gap-4 mb-8">
            <div>
              <h1 className="font-display text-display-lg text-primary">
                {category ?? "All Hardware"}
              </h1>
              <p className="font-body text-on-surface-variant">
                {result
                  ? total === 0
                    ? "No matching products."
                    : `Showing ${startIdx}-${endIdx} of ${total} ${
                        total === 1 ? "result" : "results"
                      }${q ? ` for "${q}"` : ""}.`
                  : "Loading catalog…"}
              </p>
            </div>

            <form method="get" className="flex items-center gap-3">
              {/* Preserve other filters when sort changes */}
              {q && <input type="hidden" name="q" value={q} />}
              {category && <input type="hidden" name="category" value={category} />}
              {brand && <input type="hidden" name="brand" value={brand} />}
              {minPrice !== undefined && (
                <input type="hidden" name="minPrice" value={String(minPrice)} />
              )}
              {maxPrice !== undefined && (
                <input type="hidden" name="maxPrice" value={String(maxPrice)} />
              )}
              <label className="font-label-caps uppercase text-on-surface-variant">
                Sort
              </label>
              <select
                name="sort"
                defaultValue={sort}
                className="bg-surface border border-outline-variant rounded-lg text-on-surface font-body font-semibold py-1 px-2 cursor-pointer focus:outline-none focus:ring-2 focus:ring-secondary/30"
              >
                <option value="name,asc">Name (A–Z)</option>
                <option value="price,asc">Price: Low to High</option>
                <option value="price,desc">Price: High to Low</option>
              </select>
              <button
                type="submit"
                className="font-label-caps uppercase text-secondary hover:underline"
              >
                Apply
              </button>
            </form>
          </header>

          {errorMessage ? (
            <div className="p-8 rounded-xl bg-error-container/40 text-on-error-container">
              {errorMessage}
            </div>
          ) : (
            <>
              <ProductGrid products={products} />
              <Pagination
                current={page}
                total={totalPages}
                baseHref="/products"
                searchParams={params}
              />
            </>
          )}
        </section>
      </div>
    </MainLayout>
  );
}

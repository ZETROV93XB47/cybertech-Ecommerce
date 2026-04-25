import Link from "next/link";
import { Icon } from "@/components/ui/Icon";

const CATEGORIES = ["COMPUTER", "MONITOR", "SMARTPHONE", "KEYBOARD"] as const;
const BRANDS = ["APPLE", "SAMSUNG", "ASUS", "LOGITECH", "RAZER", "SONY"] as const;

/**
 * Server-rendered filter form. Submits as GET so the URL reflects state and
 * server components re-fetch. Backend's ProductSearchRequestDto exposes a
 * single brand/category — multi-select would require a backend contract change.
 */
export function CatalogFilters({
  current,
}: {
  current: {
    q?: string;
    category?: string;
    brand?: string;
    minPrice?: string;
    maxPrice?: string;
  };
}) {
  const hasFilters = !!(
    current.q ||
    current.category ||
    current.brand ||
    current.minPrice ||
    current.maxPrice
  );

  return (
    <aside className="w-72 flex-shrink-0 p-8 border-r border-slate-100 lg:h-[calc(100vh-80px)] lg:sticky lg:top-20 lg:overflow-y-auto">
      <form method="get" className="space-y-8">
        <div>
          <h3 className="font-display font-semibold text-headline-sm mb-4">
            Filters
          </h3>
          {hasFilters ? (
            <Link
              href="/products"
              className="inline-flex items-center gap-1 text-label-caps uppercase text-secondary hover:underline"
            >
              <Icon name="close" size={14} />
              Reset all
            </Link>
          ) : (
            <p className="font-label-caps uppercase text-on-surface-variant">
              No filters active
            </p>
          )}
        </div>

        <div>
          <label className="block font-label-caps uppercase text-on-surface-variant mb-3">
            Search
          </label>
          <input
            name="q"
            type="search"
            defaultValue={current.q ?? ""}
            placeholder="MacBook, RTX 4080…"
            className="w-full bg-surface-container-low border border-outline-variant rounded-lg px-4 py-2 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
          />
        </div>

        <fieldset>
          <legend className="font-label-caps uppercase text-on-surface-variant mb-3">
            Category
          </legend>
          <div className="space-y-2">
            <label className="flex items-center gap-3 cursor-pointer group">
              <input
                type="radio"
                name="category"
                value=""
                defaultChecked={!current.category}
                className="text-secondary focus:ring-secondary"
              />
              <span className="font-body text-on-surface group-hover:text-secondary transition-colors">
                All
              </span>
            </label>
            {CATEGORIES.map((cat) => (
              <label
                key={cat}
                className="flex items-center gap-3 cursor-pointer group"
              >
                <input
                  type="radio"
                  name="category"
                  value={cat}
                  defaultChecked={current.category === cat}
                  className="text-secondary focus:ring-secondary"
                />
                <span className="font-body text-on-surface group-hover:text-secondary transition-colors">
                  {cat}
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="font-label-caps uppercase text-on-surface-variant mb-3">
            Brand
          </legend>
          <div className="space-y-2">
            <label className="flex items-center gap-3 cursor-pointer group">
              <input
                type="radio"
                name="brand"
                value=""
                defaultChecked={!current.brand}
                className="text-secondary focus:ring-secondary"
              />
              <span className="font-body text-on-surface group-hover:text-secondary transition-colors">
                Any
              </span>
            </label>
            {BRANDS.map((b) => (
              <label
                key={b}
                className="flex items-center gap-3 cursor-pointer group"
              >
                <input
                  type="radio"
                  name="brand"
                  value={b}
                  defaultChecked={
                    current.brand?.toUpperCase() === b
                  }
                  className="text-secondary focus:ring-secondary"
                />
                <span className="font-body text-on-surface group-hover:text-secondary transition-colors">
                  {b}
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="font-label-caps uppercase text-on-surface-variant mb-3">
            Price range (USD)
          </legend>
          <div className="flex items-center gap-2">
            <input
              name="minPrice"
              type="number"
              min="0"
              step="50"
              defaultValue={current.minPrice ?? ""}
              placeholder="Min"
              className="w-full bg-surface-container-low border border-outline-variant rounded-lg px-2 py-1 text-label-caps text-center focus:outline-none focus:ring-1 focus:ring-secondary"
            />
            <span className="text-outline">to</span>
            <input
              name="maxPrice"
              type="number"
              min="0"
              step="50"
              defaultValue={current.maxPrice ?? ""}
              placeholder="Max"
              className="w-full bg-surface-container-low border border-outline-variant rounded-lg px-2 py-1 text-label-caps text-center focus:outline-none focus:ring-1 focus:ring-secondary"
            />
          </div>
        </fieldset>

        <button
          type="submit"
          className="w-full h-11 bg-primary text-on-primary font-display font-semibold rounded-lg hover:bg-slate-800 active:scale-[0.98] transition-all"
        >
          Apply filters
        </button>
      </form>
    </aside>
  );
}

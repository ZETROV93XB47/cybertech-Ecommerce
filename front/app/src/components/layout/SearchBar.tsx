import { Icon } from "@/components/ui/Icon";

/**
 * Plain GET form posting to /products. The catalog page reads `q` from
 * `searchParams`, so the input is intentionally named `q` (not `search`).
 *
 * Kept as a server component — no JS needed for a form GET. The mobile
 * drawer reuses this same form so the search box is reachable on small
 * screens too.
 */
export function SearchBar({
  className,
  inputClassName,
  defaultValue,
  autoFocus,
}: {
  className?: string;
  inputClassName?: string;
  defaultValue?: string;
  autoFocus?: boolean;
}) {
  return (
    <form
      action="/products"
      method="get"
      role="search"
      className={"relative " + (className ?? "")}
    >
      <label htmlFor="header-search" className="sr-only">
        Search products
      </label>
      <input
        id="header-search"
        type="search"
        name="q"
        defaultValue={defaultValue}
        autoFocus={autoFocus}
        placeholder="Search tech…"
        aria-label="Search products"
        className={
          "pl-10 pr-4 py-2 rounded-full border-none bg-slate-100 focus:ring-2 focus:ring-secondary/20 text-sm font-body outline-none w-full " +
          (inputClassName ?? "")
        }
      />
      <Icon
        name="search"
        className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
        size={20}
      />
      <button type="submit" className="sr-only">
        Search
      </button>
    </form>
  );
}

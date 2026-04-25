import Link from "next/link";
import { Icon } from "./Icon";

/**
 * 0-based pagination matching Spring Data Page<T>.
 * Renders 1..N as Links that preserve existing search params and bump `page`.
 */
export function Pagination({
  current,
  total,
  baseHref,
  searchParams,
}: {
  /** 0-based current page */
  current: number;
  /** total number of pages (`Page.totalPages`) */
  total: number;
  /** path without query, e.g. "/products" */
  baseHref: string;
  /** existing query params to preserve (will be cloned) */
  searchParams: Record<string, string | string[] | undefined>;
}) {
  if (total <= 1) return null;

  const buildHref = (page: number) => {
    const params = new URLSearchParams();
    for (const [k, v] of Object.entries(searchParams)) {
      if (v === undefined) continue;
      if (Array.isArray(v)) v.forEach((x) => params.append(k, x));
      else params.set(k, v);
    }
    params.set("page", String(page));
    return `${baseHref}?${params.toString()}`;
  };

  const pages = paginationWindow(current, total);

  const prev = current > 0 ? buildHref(current - 1) : null;
  const next = current < total - 1 ? buildHref(current + 1) : null;

  return (
    <nav
      aria-label="Pagination"
      className="mt-section-gap flex justify-center"
    >
      <ul className="flex items-center gap-2">
        <li>
          {prev ? (
            <Link
              href={prev}
              aria-label="Previous page"
              className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors"
            >
              <Icon name="chevron_left" size={20} />
            </Link>
          ) : (
            <span
              aria-hidden
              className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-100 text-slate-300"
            >
              <Icon name="chevron_left" size={20} />
            </span>
          )}
        </li>
        {pages.map((p, i) =>
          p === "…" ? (
            <li key={`gap-${i}`} className="px-2 text-outline">
              …
            </li>
          ) : (
            <li key={p}>
              <Link
                href={buildHref(p)}
                aria-current={p === current ? "page" : undefined}
                className={
                  p === current
                    ? "w-10 h-10 flex items-center justify-center rounded-lg bg-primary text-white font-semibold"
                    : "w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors font-semibold"
                }
              >
                {p + 1}
              </Link>
            </li>
          ),
        )}
        <li>
          {next ? (
            <Link
              href={next}
              aria-label="Next page"
              className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors"
            >
              <Icon name="chevron_right" size={20} />
            </Link>
          ) : (
            <span
              aria-hidden
              className="w-10 h-10 flex items-center justify-center rounded-lg border border-slate-100 text-slate-300"
            >
              <Icon name="chevron_right" size={20} />
            </span>
          )}
        </li>
      </ul>
    </nav>
  );
}

function paginationWindow(current: number, total: number): Array<number | "…"> {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i);
  const window: Array<number | "…"> = [0];
  const start = Math.max(1, current - 1);
  const end = Math.min(total - 2, current + 1);
  if (start > 1) window.push("…");
  for (let p = start; p <= end; p++) window.push(p);
  if (end < total - 2) window.push("…");
  window.push(total - 1);
  return window;
}

import Link from "next/link";
import { Icon } from "./Icon";

export type BreadcrumbItem = {
  label: string;
  href?: string;
};

/**
 * Discreet inline breadcrumb trail. Server component — wires to plain anchors
 * (or `<Link>` when an href is provided). The last item is treated as the
 * current page and rendered without a link, regardless of its `href`.
 *
 *   <Breadcrumbs items={[
 *     { label: "Home", href: "/" },
 *     { label: "Products", href: "/products" },
 *     { label: product.name },
 *   ]} />
 */
export function Breadcrumbs({
  items,
  className,
}: {
  items: BreadcrumbItem[];
  className?: string;
}) {
  if (!items || items.length === 0) return null;
  const lastIdx = items.length - 1;

  return (
    <nav
      aria-label="Breadcrumb"
      className={
        "flex items-center flex-wrap gap-x-2 gap-y-1 text-sm text-on-surface-variant " +
        (className ?? "")
      }
    >
      <ol className="flex items-center flex-wrap gap-x-2 gap-y-1">
        {items.map((item, idx) => {
          const isLast = idx === lastIdx;
          return (
            <li key={`${idx}-${item.label}`} className="flex items-center gap-2">
              {!isLast && item.href ? (
                <Link
                  href={item.href}
                  className="hover:text-primary transition-colors"
                >
                  {item.label}
                </Link>
              ) : (
                <span
                  aria-current={isLast ? "page" : undefined}
                  className={
                    isLast
                      ? "text-primary font-semibold truncate max-w-[40ch]"
                      : "text-on-surface-variant"
                  }
                >
                  {item.label}
                </span>
              )}
              {!isLast && (
                <Icon
                  name="chevron_right"
                  size={14}
                  className="text-outline-variant"
                />
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

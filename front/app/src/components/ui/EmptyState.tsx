import Link from "next/link";
import { Icon } from "./Icon";

/**
 * Reusable empty-state for lists, search results, carts, wishlists, etc.
 * Sober Material 3 vibe: large circular icon chip on a low-emphasis surface,
 * tight headline, optional description, optional single CTA.
 *
 *   <EmptyState
 *     icon="shopping_cart"
 *     title="Your cart is empty"
 *     description="Start adding products and they'll show up here."
 *     cta={{ label: "Browse products", href: "/products" }}
 *   />
 */

type EmptyStateCta =
  | { label: string; href: string; onClick?: never }
  | { label: string; onClick: () => void; href?: never };

export type EmptyStateProps = {
  /** Material Symbols name. See https://fonts.google.com/icons. */
  icon: string;
  title: string;
  description?: string;
  cta?: EmptyStateCta;
  className?: string;
};

export function EmptyState({
  icon,
  title,
  description,
  cta,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={[
        "flex flex-col items-center justify-center text-center",
        "py-16 px-6",
        className ?? "",
      ]
        .join(" ")
        .trim()}
    >
      <div className="w-20 h-20 rounded-full bg-surface-container-low flex items-center justify-center mb-6 text-on-surface-variant">
        <Icon name={icon} size={36} />
      </div>
      <h3 className="font-display text-headline-sm text-primary mb-2">
        {title}
      </h3>
      {description && (
        <p className="font-body text-body-md text-on-surface-variant max-w-md mb-6">
          {description}
        </p>
      )}
      {cta && <EmptyStateCtaButton cta={cta} />}
    </div>
  );
}

function EmptyStateCtaButton({ cta }: { cta: EmptyStateCta }) {
  const className =
    "inline-flex items-center gap-2 bg-primary text-on-primary px-8 py-3 font-label-caps uppercase tracking-wider hover:bg-slate-800 transition-colors";

  if ("href" in cta && cta.href) {
    return (
      <Link href={cta.href} className={className}>
        {cta.label}
      </Link>
    );
  }
  return (
    <button type="button" onClick={cta.onClick} className={className}>
      {cta.label}
    </button>
  );
}

import Link from "next/link";
import { Icon } from "@/components/ui/Icon";

/**
 * Shared empty/zero-data state for the account surface.
 * Centralised so wishlist, orders and cards keep visual parity.
 */

interface EmptyStateProps {
  icon: string;
  title: string;
  description: string;
  cta?: { href: string; label: string };
  variant?: "soft" | "muted";
}

export function EmptyState({
  icon,
  title,
  description,
  cta,
  variant = "soft",
}: EmptyStateProps) {
  const wrapperBg =
    variant === "muted" ? "bg-surface-container-low" : "bg-white";

  return (
    <div
      className={`${wrapperBg} border border-slate-100 flex flex-col items-center text-center gap-4 px-8 py-16`}
    >
      <div className="w-16 h-16 rounded-full bg-surface-container-low flex items-center justify-center">
        <Icon name={icon} size={32} className="text-outline" />
      </div>
      <div className="space-y-2 max-w-md">
        <h2 className="font-display text-headline-sm text-primary">{title}</h2>
        <p className="font-body text-on-surface-variant">{description}</p>
      </div>
      {cta && (
        <Link
          href={cta.href}
          className="inline-flex items-center justify-center gap-2 h-11 px-6 mt-2 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors"
        >
          {cta.label}
          <Icon name="arrow_forward" size={16} />
        </Link>
      )}
    </div>
  );
}

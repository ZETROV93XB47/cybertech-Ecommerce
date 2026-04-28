"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { SignOutButton } from "./SignOutButton";

/**
 * Navigation sidebar shared across the /account/** surface.
 *
 * Layout strategy:
 *  - Desktop (lg+): sticky vertical rail anchored under the header.
 *  - Mobile: collapsible details/summary disclosure so the nav doesn't eat
 *    above-the-fold space on small screens. We use the native <details>
 *    element so it works with no JS and stays accessible by default.
 *
 * "Active" state matches the current pathname against each entry's prefix —
 * so `/account/orders/{uuid}` correctly highlights the Orders entry. The
 * profile entry is special-cased to avoid being highlighted on every
 * `/account/...` route (it would otherwise match too eagerly).
 */

interface NavEntry {
  href: string;
  label: string;
  icon: string;
  matchPrefix?: boolean;
}

const NAV_ENTRIES: NavEntry[] = [
  { href: "/account/profile", label: "Profile", icon: "person" },
  { href: "/account/orders", label: "Orders", icon: "receipt_long", matchPrefix: true },
  { href: "/account/wishlist", label: "Wishlist", icon: "favorite", matchPrefix: true },
  { href: "/account/cards", label: "Payment cards", icon: "credit_card", matchPrefix: true },
  { href: "/account/profile/edit", label: "Edit profile", icon: "edit" },
];

function isActive(pathname: string, entry: NavEntry): boolean {
  if (entry.href === "/account/profile") {
    // Exact match — /account/profile/edit is a different entry.
    return pathname === entry.href;
  }
  if (entry.matchPrefix) {
    return pathname === entry.href || pathname.startsWith(`${entry.href}/`);
  }
  return pathname === entry.href;
}

export function AccountSidebar() {
  const pathname = usePathname() ?? "";
  const activeEntry =
    NAV_ENTRIES.find((e) => isActive(pathname, e)) ?? NAV_ENTRIES[0];

  return (
    <>
      {/* Mobile disclosure */}
      <details className="lg:hidden bg-white border border-slate-100 group">
        <summary className="flex items-center justify-between gap-3 px-5 py-4 cursor-pointer list-none [&::-webkit-details-marker]:hidden">
          <span className="flex items-center gap-3">
            <Icon name={activeEntry.icon} size={20} className="text-secondary" />
            <span className="font-display font-semibold text-primary">
              {activeEntry.label}
            </span>
          </span>
          <Icon
            name="expand_more"
            size={20}
            className="text-on-surface-variant transition-transform group-open:rotate-180"
          />
        </summary>
        <nav aria-label="Account navigation" className="border-t border-slate-100">
          <ul className="flex flex-col">
            {NAV_ENTRIES.map((entry) => {
              const active = isActive(pathname, entry);
              return (
                <li key={entry.href}>
                  <Link
                    href={entry.href}
                    aria-current={active ? "page" : undefined}
                    className={`flex items-center gap-3 px-5 py-3 font-body transition-colors ${
                      active
                        ? "bg-secondary/10 text-secondary font-semibold"
                        : "text-on-surface hover:bg-slate-50"
                    }`}
                  >
                    <Icon name={entry.icon} size={18} />
                    <span>{entry.label}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
          <div className="px-5 py-4 border-t border-slate-100">
            <SignOutButton />
          </div>
        </nav>
      </details>

      {/* Desktop rail */}
      <nav
        aria-label="Account navigation"
        className="hidden lg:block lg:sticky lg:top-28 bg-white border border-slate-100"
      >
        <div className="px-6 pt-6 pb-3">
          <p className="font-label-caps uppercase tracking-[0.2em] text-on-surface-variant text-xs">
            Account
          </p>
        </div>
        <ul className="flex flex-col pb-3">
          {NAV_ENTRIES.map((entry) => {
            const active = isActive(pathname, entry);
            return (
              <li key={entry.href}>
                <Link
                  href={entry.href}
                  aria-current={active ? "page" : undefined}
                  className={`flex items-center gap-3 px-6 py-3 font-body border-l-2 transition-colors ${
                    active
                      ? "border-secondary bg-secondary/5 text-secondary font-semibold"
                      : "border-transparent text-on-surface hover:bg-slate-50"
                  }`}
                >
                  <Icon name={entry.icon} size={18} />
                  <span>{entry.label}</span>
                </Link>
              </li>
            );
          })}
        </ul>
        <div className="px-6 py-4 border-t border-slate-100">
          <SignOutButton />
        </div>
      </nav>
    </>
  );
}

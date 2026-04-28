"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/ui/Icon";

type NavItem = {
  href: string;
  label: string;
  icon: string;
  /** Active when pathname starts with `match` (defaults to `href`). */
  match?: string;
};

const ITEMS: NavItem[] = [
  { href: "/admin", label: "Dashboard", icon: "dashboard", match: "/admin" },
  {
    href: "/admin/products",
    label: "Products",
    icon: "inventory_2",
    match: "/admin/products",
  },
  {
    href: "/admin/users",
    label: "Users",
    icon: "group",
    match: "/admin/users",
  },
  {
    href: "/admin/orders",
    label: "Orders",
    icon: "receipt_long",
    match: "/admin/orders",
  },
  {
    href: "/admin/discounts",
    label: "Discounts",
    icon: "local_offer",
    match: "/admin/discounts",
  },
];

export function AdminSidebar() {
  const pathname = usePathname() ?? "";

  return (
    <aside className="hidden lg:flex w-[220px] flex-col gap-1 sticky top-16 self-start h-[calc(100vh-4rem)] border-r border-slate-200 bg-white px-3 py-6">
      <p className="px-3 mb-4 font-label-caps uppercase tracking-[0.2em] text-xs text-slate-500">
        Back office
      </p>
      <nav className="flex flex-col gap-1">
        {ITEMS.map((item) => {
          // /admin must match exactly so the dashboard link doesn't stay
          // highlighted when browsing /admin/products.
          const isActive =
            item.href === "/admin"
              ? pathname === "/admin"
              : pathname.startsWith(item.match ?? item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              className={
                "flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors " +
                (isActive
                  ? "bg-primary text-white"
                  : "text-slate-700 hover:bg-slate-100")
              }
            >
              <Icon name={item.icon} size={20} />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>
      <div className="mt-auto pt-6 border-t border-slate-100">
        <Link
          href="/"
          className="flex items-center gap-2 px-3 py-2 text-sm text-slate-500 hover:text-slate-900 transition-colors"
        >
          <Icon name="arrow_back" size={18} />
          Back to store
        </Link>
      </div>
    </aside>
  );
}

export function AdminSidebarMobile() {
  const pathname = usePathname() ?? "";
  return (
    <details className="lg:hidden border-b border-slate-200 bg-white">
      <summary className="cursor-pointer list-none flex items-center justify-between px-4 py-3 font-medium text-slate-900">
        <span className="flex items-center gap-2">
          <Icon name="menu" size={20} />
          Admin menu
        </span>
        <Icon name="expand_more" size={20} />
      </summary>
      <nav className="flex flex-col gap-1 px-3 pb-3">
        {ITEMS.map((item) => {
          const isActive =
            item.href === "/admin"
              ? pathname === "/admin"
              : pathname.startsWith(item.match ?? item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={
                "flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium " +
                (isActive
                  ? "bg-primary text-white"
                  : "text-slate-700 hover:bg-slate-100")
              }
            >
              <Icon name={item.icon} size={20} />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>
    </details>
  );
}

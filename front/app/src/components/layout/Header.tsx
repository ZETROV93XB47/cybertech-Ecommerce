import Link from "next/link";
import { auth } from "@/lib/auth";
import { Icon } from "@/components/ui/Icon";

type NavLink = {
  href: string;
  label: string;
  primary?: boolean;
};

const NAV_LINKS: NavLink[] = [
  { href: "/products", label: "Shop", primary: true },
  { href: "/products?category=builds", label: "Builds" },
  { href: "/products?promo=1", label: "Pre-buys" },
  { href: "/support", label: "Support" },
];

export async function Header() {
  const session = await auth();
  const role = session?.user?.role ?? null;

  return (
    <nav className="fixed top-0 left-1/2 -translate-x-1/2 z-50 w-full max-w-[1440px] flex justify-between items-center px-6 md:px-8 h-20 bg-white/80 backdrop-blur-md border-b border-slate-200 shadow-[0_4px_20px_-10px_rgba(0,0,0,0.05)] font-display tracking-tight">
      <div className="flex items-center gap-12">
        <Link
          href="/"
          className="text-2xl font-bold text-slate-900 tracking-tighter"
        >
          Cybertech
        </Link>
        <div className="hidden md:flex gap-8 items-center">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={
                link.primary
                  ? "text-secondary border-b-2 border-secondary pb-1"
                  : "text-slate-600 hover:text-slate-900 transition-colors"
              }
            >
              {link.label}
            </Link>
          ))}
          {role === "ADMIN" && (
            <Link
              href="/admin"
              className="text-slate-600 hover:text-slate-900 transition-colors"
            >
              Admin
            </Link>
          )}
        </div>
      </div>

      <div className="flex items-center gap-4 md:gap-6">
        <div className="relative hidden lg:block">
          <input
            type="search"
            placeholder="Search tech…"
            aria-label="Search products"
            className="pl-10 pr-4 py-2 rounded-full border-none bg-slate-100 focus:ring-2 focus:ring-secondary/20 w-64 text-sm font-body outline-none"
          />
          <Icon
            name="search"
            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            size={20}
          />
        </div>

        <div className="flex items-center gap-2">
          <Link
            href="/cart"
            aria-label="Shopping cart"
            className="p-2 hover:bg-slate-50 rounded-full transition-all duration-200"
          >
            <Icon name="shopping_cart" className="text-on-surface" />
          </Link>

          {session ? (
            <Link
              href="/account/profile"
              aria-label="Your account"
              className="p-2 hover:bg-slate-50 rounded-full transition-all duration-200"
            >
              <Icon name="person" className="text-on-surface" />
            </Link>
          ) : (
            <Link
              href="/api/auth/signin"
              className="px-4 py-2 text-sm font-label-caps uppercase tracking-wider text-slate-700 hover:text-slate-900 transition-colors"
            >
              Sign in
            </Link>
          )}
        </div>
      </div>
    </nav>
  );
}

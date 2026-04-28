import Link from "next/link";
import { auth } from "@/lib/auth";
import { Icon } from "@/components/ui/Icon";
import { SearchBar } from "./SearchBar";
import { MobileDrawer } from "./MobileDrawer";

type NavLink = {
  href: string;
  label: string;
  primary?: boolean;
};

/**
 * Primary navigation. Categories must match the backend ProductCategory
 * enum exactly (COMPUTER, MONITOR, KEYBOARD, SMARTPHONE) — anything else
 * 400s the catalog search. /promotions and /support are owned by other
 * agents but their routes are stable.
 */
const NAV_LINKS: NavLink[] = [
  { href: "/products", label: "Shop", primary: true },
  { href: "/products?category=COMPUTER", label: "Computers" },
  { href: "/products?category=MONITOR", label: "Monitors" },
  { href: "/promotions", label: "Promotions" },
  { href: "/support", label: "Support" },
];

export async function Header() {
  const session = await auth();
  const role = session?.user?.role ?? null;

  const authLink = session
    ? { href: "/account/profile", label: "My Account" }
    : { href: "/api/auth/signin", label: "Sign in" };

  return (
    <nav className="fixed top-0 z-50 w-full h-20 bg-white/80 backdrop-blur-md border-b border-slate-200 shadow-[0_4px_20px_-10px_rgba(0,0,0,0.05)] font-display tracking-tight">
      <div className="max-w-[1440px] mx-auto w-full flex justify-between items-center h-full px-6 md:px-8">
        <div className="flex items-center gap-12">
          <div className="flex items-center gap-2">
            <MobileDrawer
              navLinks={NAV_LINKS}
              authLink={authLink}
              isAdmin={role === "ADMIN"}
            />
            <Link
              href="/"
              className="text-2xl font-bold text-slate-900 tracking-tighter"
            >
              Cybertech
            </Link>
          </div>
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
          <SearchBar
            className="hidden lg:block w-64"
            inputClassName="w-full"
          />

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
                className="hidden sm:inline-flex px-4 py-2 text-sm font-label-caps uppercase tracking-wider text-slate-700 hover:text-slate-900 transition-colors"
              >
                Sign in
              </Link>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}

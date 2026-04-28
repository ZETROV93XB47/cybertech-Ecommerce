import Link from "next/link";
import { auth } from "@/lib/auth";
import { Icon } from "@/components/ui/Icon";

type FooterLink = {
  href: string;
  label: string;
  external?: boolean;
};

type FooterColumn = {
  title: string;
  links: FooterLink[];
};

/**
 * 4-column responsive footer. Stacks on mobile (1 col), 2 cols on tablet,
 * 4 cols on desktop. Uses backend-valid category values (COMPUTER, MONITOR,
 * KEYBOARD, SMARTPHONE) for shop links.
 *
 * The newsletter form is a UI placeholder — the backend has no /newsletter
 * endpoint yet, so the form posts nowhere (no action attribute, e.preventDefault
 * via type="button"). Wire to a real action when the endpoint lands.
 */
export async function Footer() {
  const session = await auth();
  const isSignedIn = Boolean(session);

  const accountLinks: FooterLink[] = isSignedIn
    ? [
        { href: "/account/profile", label: "My Profile" },
        { href: "/account/orders", label: "My Orders" },
        { href: "/account/wishlist", label: "Wishlist" },
        { href: "/account/cards", label: "My Cards" },
        { href: "/api/auth/signout", label: "Sign Out" },
      ]
    : [
        { href: "/api/auth/signin", label: "Sign In" },
        { href: "/account/orders", label: "My Orders" },
        { href: "/account/wishlist", label: "Wishlist" },
      ];

  const columns: FooterColumn[] = [
    {
      title: "Shop",
      links: [
        { href: "/products", label: "All Products" },
        { href: "/products?category=COMPUTER", label: "Computers" },
        { href: "/products?category=MONITOR", label: "Monitors" },
        { href: "/products?category=KEYBOARD", label: "Keyboards" },
        { href: "/products?category=SMARTPHONE", label: "Smartphones" },
        { href: "/promotions", label: "Promotions" },
      ],
    },
    {
      title: "Account",
      links: accountLinks,
    },
    {
      title: "Help",
      links: [
        { href: "/support", label: "Support Hub" },
        { href: "/support/faq", label: "FAQ" },
        { href: "/support/warranty", label: "Warranty" },
        { href: "/support/shipping", label: "Shipping" },
        { href: "/support/returns", label: "Returns" },
        { href: "/contact", label: "Contact" },
      ],
    },
    {
      title: "Legal",
      links: [
        { href: "/legal/privacy", label: "Privacy Policy" },
        { href: "/legal/terms", label: "Terms of Service" },
        { href: "/legal/cookies", label: "Cookie Policy" },
        { href: "/about", label: "About Cybertech" },
      ],
    },
  ];

  return (
    <footer className="w-full bg-slate-50 border-t border-slate-200 font-display text-sm">
      <div className="max-w-[1440px] mx-auto px-6 md:px-8 py-14">
        {/* Top brand + newsletter strip */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-10 pb-12 border-b border-slate-200">
          <div className="flex flex-col gap-3 max-w-md">
            <Link
              href="/"
              className="text-2xl font-bold text-slate-950 tracking-tighter"
            >
              Cybertech
            </Link>
            <p className="text-slate-500 font-body">
              Premium hardware for elite performance. Elevating your digital
              experience since 2024.
            </p>
          </div>

          <div className="flex flex-col gap-3 md:items-end">
            <div>
              <h3 className="font-label-caps uppercase tracking-widest text-slate-900 text-xs mb-1">
                Newsletter
              </h3>
              <p className="text-slate-500 font-body text-sm">
                Drop in for restocks, deep-dives and the occasional rare-spec
                drop.
              </p>
            </div>
            <form
              aria-label="Newsletter subscription"
              className="flex w-full max-w-sm items-center gap-2"
              // No backend endpoint yet; placeholder UI only.
            >
              <label htmlFor="footer-newsletter" className="sr-only">
                Email address
              </label>
              <input
                id="footer-newsletter"
                type="email"
                name="email"
                placeholder="you@cybertech.dev"
                className="flex-1 h-11 px-4 rounded-full bg-white border border-slate-200 focus:ring-2 focus:ring-secondary/20 focus:border-secondary text-sm font-body outline-none"
              />
              <button
                type="button"
                disabled
                title="Coming soon"
                className="h-11 px-5 rounded-full bg-primary text-on-primary font-label-caps uppercase tracking-wider text-xs hover:bg-slate-800 transition-colors disabled:opacity-70 disabled:cursor-not-allowed"
              >
                Subscribe
              </button>
            </form>
          </div>
        </div>

        {/* 4-column nav grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-10 py-12">
          {columns.map((col) => (
            <nav key={col.title} aria-label={col.title}>
              <h3 className="font-label-caps uppercase tracking-widest text-slate-900 text-xs mb-4">
                {col.title}
              </h3>
              <ul className="flex flex-col gap-2">
                {col.links.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="text-slate-500 hover:text-secondary underline-offset-4 hover:underline transition-all font-body"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        {/* Bottom bar */}
        <div className="pt-8 border-t border-slate-200 flex flex-col md:flex-row justify-between items-center gap-4">
          <span className="text-slate-500 font-body text-xs">
            © {new Date().getFullYear()} Cybertech Industrial. All rights
            reserved.
          </span>
          <div className="flex items-center gap-2">
            <a
              href="https://github.com/"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Cybertech on GitHub"
              className="p-2 rounded-full bg-slate-200 hover:bg-slate-300 transition-colors inline-flex items-center justify-center"
            >
              <Icon name="code" size={18} />
            </a>
            <a
              href="https://www.linkedin.com/"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="Cybertech on LinkedIn"
              className="p-2 rounded-full bg-slate-200 hover:bg-slate-300 transition-colors inline-flex items-center justify-center"
            >
              <Icon name="business_center" size={18} />
            </a>
            <a
              href="mailto:hello@cybertech.local"
              aria-label="Email Cybertech"
              className="p-2 rounded-full bg-slate-200 hover:bg-slate-300 transition-colors inline-flex items-center justify-center"
            >
              <Icon name="mail" size={18} />
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}

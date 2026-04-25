import { Icon } from "@/components/ui/Icon";

export function Footer() {
  return (
    <footer className="w-full py-12 px-8 flex flex-col md:flex-row justify-between items-center gap-8 bg-slate-50 border-t border-slate-200 font-display text-sm">
      <div className="flex flex-col gap-3 text-center md:text-left max-w-xs">
        <span className="font-bold text-lg text-slate-950">Cybertech</span>
        <p className="text-slate-500">
          Premium hardware for elite performance. Elevating your digital
          experience since 2024.
        </p>
        <span className="text-slate-500">
          © {new Date().getFullYear()} Cybertech Industrial. All rights
          reserved.
        </span>
      </div>

      <nav
        aria-label="Footer"
        className="flex gap-8 flex-wrap justify-center"
      >
        {[
          ["/legal/privacy", "Privacy Policy"],
          ["/legal/terms", "Terms of Service"],
          ["/support/warranty", "Hardware Warranty"],
          ["/support/shipping", "Global Shipping"],
        ].map(([href, label]) => (
          <a
            key={href}
            href={href}
            className="text-slate-500 hover:text-secondary underline-offset-4 hover:underline transition-all"
          >
            {label}
          </a>
        ))}
      </nav>

      <div className="flex gap-3">
        <button
          aria-label="Change language"
          className="p-2 bg-slate-200 rounded-full hover:bg-slate-300 transition-colors"
        >
          <Icon name="language" size={20} />
        </button>
        <a
          href="mailto:hello@cybertech.local"
          aria-label="Contact us"
          className="p-2 bg-slate-200 rounded-full hover:bg-slate-300 transition-colors inline-flex items-center justify-center"
        >
          <Icon name="mail" size={20} />
        </a>
      </div>
    </footer>
  );
}

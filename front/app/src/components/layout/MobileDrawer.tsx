"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import { SearchBar } from "./SearchBar";

export type DrawerNavLink = {
  href: string;
  label: string;
};

/**
 * Slide-in mobile navigation. Renders a burger button that opens an overlay
 * + left-anchored panel containing the primary nav, search, and an account
 * link. Closes on Escape, on overlay click, and on link clicks.
 *
 * a11y notes:
 *   - the trigger advertises `aria-expanded` + `aria-controls`
 *   - the panel is `role="dialog"` `aria-modal="true"` with `aria-label`
 *   - body scroll is locked while open
 *   - focus is moved to the close button on open and back to the trigger
 *     on close (lightweight focus management — no full focus trap, but
 *     Escape always works)
 */
export function MobileDrawer({
  navLinks,
  authLink,
  isAdmin = false,
}: {
  navLinks: DrawerNavLink[];
  authLink: { href: string; label: string };
  isAdmin?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const closeRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    // Focus the close button after mount.
    queueMicrotask(() => closeRef.current?.focus());
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [open]);

  const close = () => {
    setOpen(false);
    queueMicrotask(() => triggerRef.current?.focus());
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label="Open menu"
        aria-expanded={open}
        aria-controls="mobile-drawer"
        onClick={() => setOpen(true)}
        className="md:hidden p-2 hover:bg-slate-50 rounded-full transition-all duration-200"
      >
        <Icon name="menu" className="text-on-surface" />
      </button>

      {open && (
        <div
          id="mobile-drawer"
          role="dialog"
          aria-modal="true"
          aria-label="Site navigation"
          className="fixed inset-0 z-[60] md:hidden"
        >
          {/* Overlay */}
          <button
            type="button"
            aria-label="Close menu overlay"
            onClick={close}
            className="absolute inset-0 bg-black/50 backdrop-blur-[2px] cursor-default"
          />

          {/* Panel */}
          <aside
            className="absolute top-0 left-0 h-full w-80 max-w-[85vw] bg-white shadow-2xl flex flex-col animate-[slideIn_.2s_ease-out]"
            style={{
              animation: "slideIn 200ms ease-out",
            }}
          >
            <div className="flex items-center justify-between px-6 h-20 border-b border-slate-200">
              <Link
                href="/"
                onClick={close}
                className="text-2xl font-display font-bold text-slate-900 tracking-tighter"
              >
                Cybertech
              </Link>
              <button
                ref={closeRef}
                type="button"
                aria-label="Close menu"
                onClick={close}
                className="p-2 hover:bg-slate-50 rounded-full transition-all duration-200"
              >
                <Icon name="close" className="text-on-surface" />
              </button>
            </div>

            <div className="px-6 py-5 border-b border-slate-200">
              <SearchBar />
            </div>

            <nav
              aria-label="Mobile primary"
              className="flex-1 overflow-y-auto px-2 py-4"
            >
              <ul className="flex flex-col">
                {navLinks.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      onClick={close}
                      className="block px-4 py-3 rounded-lg text-base font-display font-medium text-slate-700 hover:bg-slate-50 hover:text-slate-900 transition-colors"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
                {isAdmin && (
                  <li>
                    <Link
                      href="/admin"
                      onClick={close}
                      className="block px-4 py-3 rounded-lg text-base font-display font-medium text-slate-700 hover:bg-slate-50 hover:text-slate-900 transition-colors"
                    >
                      Admin
                    </Link>
                  </li>
                )}
              </ul>
            </nav>

            <div className="px-6 py-5 border-t border-slate-200 flex flex-col gap-3">
              <Link
                href={authLink.href}
                onClick={close}
                className="inline-flex items-center justify-center gap-2 h-11 px-6 font-display text-sm font-semibold uppercase tracking-wider bg-primary text-on-primary hover:bg-slate-800 transition-colors rounded-lg"
              >
                <Icon name="person" size={18} />
                {authLink.label}
              </Link>
              <p className="text-xs text-slate-400 text-center">
                © {new Date().getFullYear()} Cybertech Industrial
              </p>
            </div>
          </aside>

          <style>{`
            @keyframes slideIn {
              from { transform: translateX(-100%); }
              to { transform: translateX(0); }
            }
          `}</style>
        </div>
      )}
    </>
  );
}

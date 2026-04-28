import Link from "next/link";
import type { Metadata } from "next";
import { MainLayout } from "@/components/layout/MainLayout";
import { Icon } from "@/components/ui/Icon";

/**
 * Root `not-found.tsx` — Next 16 convention.
 * Catches both `notFound()` calls from any segment and any URL that does
 * not match a known route. Server component by default.
 */

export const metadata: Metadata = {
  title: "Page not found — Cybertech",
  description:
    "We couldn't find the page you're looking for. Browse our catalog or head back home.",
  robots: { index: false, follow: false },
};

export default function NotFound() {
  return (
    <MainLayout>
      <section className="min-h-[calc(100vh-5rem)] flex items-center justify-center bg-surface-container-low">
        <div className="container mx-auto px-6 md:px-8 py-24">
          <div className="max-w-2xl mx-auto text-center space-y-8">
            <div className="flex justify-center">
              <span
                aria-hidden="true"
                className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-secondary/10 text-secondary"
              >
                <Icon name="search_off" size={40} />
              </span>
            </div>

            <h1
              className="font-display text-display-lg text-[clamp(72px,12vw,144px)] leading-none font-bold text-primary tracking-tight"
              aria-label="404"
            >
              404
            </h1>

            <div className="space-y-4">
              <h2 className="font-display text-headline-md text-primary">
                Page not found
              </h2>
              <p className="font-body text-body-lg text-on-surface-variant max-w-lg mx-auto">
                The page you&apos;re trying to reach has either moved, been
                retired, or never existed. Let&apos;s get you back to the gear.
              </p>
            </div>

            <div className="flex flex-wrap justify-center items-center gap-4 pt-4">
              <Link
                href="/products"
                className="bg-primary text-on-primary px-10 py-4 font-display text-sm font-semibold rounded-none hover:bg-slate-800 transition-all"
              >
                Browse catalog
              </Link>
              <Link
                href="/"
                className="border border-outline px-10 py-4 font-display text-sm font-semibold rounded-none hover:bg-slate-50 transition-all"
              >
                Back home
              </Link>
            </div>
          </div>
        </div>
      </section>
    </MainLayout>
  );
}

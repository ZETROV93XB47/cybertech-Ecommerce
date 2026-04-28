"use client";

import { useEffect } from "react";
import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { Icon } from "@/components/ui/Icon";

/**
 * Root segment error boundary — Next 16 convention.
 * Must be a Client Component. Wraps every route except the root layout itself
 * (for layout-level failures, see `global-error.tsx`).
 *
 * Next 16.2 adds `unstable_retry`, but `reset` remains supported and matches the
 * spec described in the task brief, so we keep `reset` for stability.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Surface to console only — wire to a real reporter (Sentry/Datadog) later.
    // Never display `error.message` to the user in production.
    console.error("[error.tsx] segment error", error);
  }, [error]);

  return (
    <MainLayout>
      <section className="min-h-[calc(100vh-5rem)] flex items-center justify-center bg-surface-container-low">
        <div className="container mx-auto px-6 md:px-8 py-24">
          <div className="max-w-xl mx-auto text-center space-y-8">
            <div className="flex justify-center">
              <span
                aria-hidden="true"
                className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-error/10 text-error"
              >
                <Icon name="error_outline" size={40} />
              </span>
            </div>

            <div className="space-y-4">
              <h1 className="font-display text-headline-md text-primary">
                Something went wrong
              </h1>
              <p className="font-body text-body-lg text-on-surface-variant">
                An unexpected error stopped this page from loading. You can try
                again, or head back to the catalog while we look into it.
              </p>
              {error.digest ? (
                <p className="font-mono text-body-sm text-on-surface-variant/70 pt-2">
                  Reference: <span className="select-all">{error.digest}</span>
                </p>
              ) : null}
            </div>

            <div className="flex flex-wrap justify-center items-center gap-4 pt-4">
              <button
                type="button"
                onClick={() => reset()}
                className="bg-primary text-on-primary px-10 py-4 font-display text-sm font-semibold rounded-none hover:bg-slate-800 transition-all"
              >
                Try again
              </button>
              <Link
                href="/products"
                className="border border-outline px-10 py-4 font-display text-sm font-semibold rounded-none hover:bg-slate-50 transition-all"
              >
                Back to catalog
              </Link>
            </div>
          </div>
        </div>
      </section>
    </MainLayout>
  );
}

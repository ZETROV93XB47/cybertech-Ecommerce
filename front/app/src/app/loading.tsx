import { MainLayout } from "@/components/layout/MainLayout";

/**
 * Root `loading.tsx` — Next 16 convention.
 * Server component. Renders a low-detail skeleton while a navigation streams in.
 * Per-segment loading states should live in their own `loading.tsx` files.
 */
export default function Loading() {
  return (
    <MainLayout>
      <div
        role="status"
        aria-live="polite"
        aria-label="Loading content"
        className="container mx-auto px-6 md:px-8 py-16"
      >
        <span className="sr-only">Loading…</span>

        {/* Hero placeholder */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-center mb-24">
          <div className="space-y-5">
            <div className="h-3 w-24 bg-slate-200 animate-pulse" />
            <div className="h-12 w-11/12 bg-slate-200 animate-pulse" />
            <div className="h-12 w-9/12 bg-slate-200 animate-pulse" />
            <div className="h-4 w-full bg-slate-200 animate-pulse" />
            <div className="h-4 w-10/12 bg-slate-200 animate-pulse" />
            <div className="flex gap-4 pt-2">
              <div className="h-12 w-32 bg-slate-200 animate-pulse" />
              <div className="h-12 w-32 bg-slate-200 animate-pulse" />
            </div>
          </div>
          <div className="h-[320px] lg:h-[480px] bg-slate-200 animate-pulse" />
        </div>

        {/* Card grid placeholder */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="space-y-4">
              <div className="aspect-square w-full bg-slate-200 animate-pulse" />
              <div className="h-4 w-3/4 bg-slate-200 animate-pulse" />
              <div className="h-4 w-1/2 bg-slate-200 animate-pulse" />
              <div className="h-5 w-1/3 bg-slate-200 animate-pulse" />
            </div>
          ))}
        </div>
      </div>
    </MainLayout>
  );
}

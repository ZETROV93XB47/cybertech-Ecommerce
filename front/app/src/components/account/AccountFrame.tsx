import { MainLayout } from "@/components/layout/MainLayout";
import { AccountSidebar } from "./AccountSidebar";

/**
 * Page chrome shared across the new /account/* pages (orders, wishlist,
 * cards, profile/edit).
 *
 * Wraps MainLayout + a 12-col grid with the AccountSidebar sticky on the
 * left and the page content on the right. The existing /account/profile
 * page still uses its own bespoke layout and is NOT routed through this
 * helper — keeping it untouched preserves its single-pane composition.
 */

interface AccountFrameProps {
  eyebrow: string;
  title: string;
  description?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}

export function AccountFrame({
  eyebrow,
  title,
  description,
  actions,
  children,
}: AccountFrameProps) {
  return (
    <MainLayout>
      <div className="max-w-[1280px] mx-auto px-6 md:px-8 py-12">
        <header className="mb-10 flex flex-wrap items-end justify-between gap-6">
          <div>
            <p className="font-label-caps uppercase tracking-[0.2em] text-secondary mb-2">
              {eyebrow}
            </p>
            <h1 className="font-display text-display-lg text-primary mb-3">
              {title}
            </h1>
            {description && (
              <p className="font-body text-body-lg text-on-surface-variant max-w-2xl">
                {description}
              </p>
            )}
          </div>
          {actions && <div className="flex items-center gap-3">{actions}</div>}
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <aside className="lg:col-span-3">
            <AccountSidebar />
          </aside>
          <div className="lg:col-span-9 flex flex-col gap-8 min-w-0">
            {children}
          </div>
        </div>
      </div>
    </MainLayout>
  );
}

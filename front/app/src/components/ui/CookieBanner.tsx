"use client";

import { useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { Icon } from "@/components/ui/Icon";

const STORAGE_KEY = "cookie-consent";

type Choice = "accepted" | "rejected" | "custom";

type CategorySelection = {
  essential: true; // always on
  analytics: boolean;
  marketing: boolean;
};

type StoredConsent = {
  choice: Choice;
  categories: CategorySelection;
  decidedAt: string;
};

const DEFAULT_SELECTION: CategorySelection = {
  essential: true,
  analytics: false,
  marketing: false,
};

/**
 * GDPR-style cookie banner. Persists the user's decision in localStorage under
 * the `cookie-consent` key. Three quick actions: Accept all, Reject all,
 * Customize. The customize panel exposes essential (locked on), analytics and
 * marketing toggles.
 *
 * The component is fixed bottom-0 and full-width. Render once, anywhere — it
 * gates itself on the stored decision and stays hidden until consent is
 * either missing or explicitly cleared (e.g. via the `/legal/cookies` page).
 *
 * Mounted client-side only; SSR returns null until hydration so no flash.
 */
/**
 * Read the persisted decision (if any). Returns true if the banner should
 * stay hidden — i.e. the user has already accepted, rejected, or saved a
 * customised choice.
 */
function readDecided(): boolean {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return false;
    // Backwards compat: previous versions stored the bare string
    // "accepted" / "rejected".
    if (raw === "accepted" || raw === "rejected") return true;
    const parsed = JSON.parse(raw) as Partial<StoredConsent>;
    return Boolean(parsed.choice);
  } catch {
    return false;
  }
}

/** Subscribe to cross-tab storage changes so the banner reacts instantly. */
function subscribeStorage(callback: () => void): () => void {
  const handler = (e: StorageEvent) => {
    if (e.key === STORAGE_KEY || e.key === null) callback();
  };
  window.addEventListener("storage", handler);
  return () => window.removeEventListener("storage", handler);
}

export function CookieBanner() {
  // useSyncExternalStore returns the SSR snapshot during SSR/initial render,
  // then swaps to the real client value post-hydration — no flash, no lint
  // setState-in-effect warning.
  const decided = useSyncExternalStore(
    subscribeStorage,
    readDecided,
    () => true, // SSR snapshot: assume decided so the banner doesn't flash.
  );

  const [showCustomize, setShowCustomize] = useState(false);
  const [selection, setSelection] = useState<CategorySelection>(
    DEFAULT_SELECTION,
  );

  function persist(choice: Choice, categories: CategorySelection) {
    const payload: StoredConsent = {
      choice,
      categories,
      decidedAt: new Date().toISOString(),
    };
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      // localStorage events fire on other tabs only — nudge the local store
      // so this tab updates too.
      window.dispatchEvent(new StorageEvent("storage", { key: STORAGE_KEY }));
    } catch {
      // localStorage may be unavailable (private mode, quota); fail silently
      // — we just won't persist beyond the session.
    }
    setShowCustomize(false);
  }

  function acceptAll() {
    persist("accepted", { essential: true, analytics: true, marketing: true });
  }
  function rejectAll() {
    persist("rejected", DEFAULT_SELECTION);
  }
  function saveCustom() {
    persist("custom", selection);
  }

  if (decided) return null;

  return (
    <div
      role="dialog"
      aria-label="Cookie consent"
      aria-live="polite"
      className="fixed inset-x-0 bottom-0 z-50 border-t border-slate-200 bg-white shadow-[0_-8px_24px_-12px_rgba(15,23,42,0.18)]"
    >
      <div className="container mx-auto px-6 md:px-8 py-5 md:py-6">
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between md:gap-8">
          <div className="flex items-start gap-3 max-w-2xl">
            <Icon
              name="cookie"
              size={22}
              className="text-secondary shrink-0 mt-1"
            />
            <div>
              <h2 className="font-display text-base font-semibold text-primary mb-1">
                We use cookies to make Cybertech better.
              </h2>
              <p className="font-body text-sm text-on-surface-variant leading-relaxed">
                Essential cookies keep the site running. With your consent we
                also use analytics and marketing cookies to understand
                traffic and tailor offers. You can change your mind any time
                from the{" "}
                <Link
                  href="/legal/cookies"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  cookie policy
                </Link>
                .
              </p>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-2 shrink-0">
            <button
              type="button"
              onClick={() => setShowCustomize((v) => !v)}
              className="h-11 px-5 border border-slate-300 text-primary font-label-caps uppercase tracking-wider text-xs hover:bg-slate-50 transition-colors"
              aria-expanded={showCustomize}
              aria-controls="cookie-customize"
            >
              Customize
            </button>
            <button
              type="button"
              onClick={rejectAll}
              className="h-11 px-5 border border-slate-300 text-primary font-label-caps uppercase tracking-wider text-xs hover:bg-slate-50 transition-colors"
            >
              Reject all
            </button>
            <button
              type="button"
              onClick={acceptAll}
              className="h-11 px-6 bg-primary text-on-primary font-label-caps uppercase tracking-wider text-xs hover:bg-slate-800 transition-colors"
            >
              Accept all
            </button>
          </div>
        </div>

        {showCustomize && (
          <div
            id="cookie-customize"
            className="mt-5 pt-5 border-t border-slate-200 grid grid-cols-1 md:grid-cols-3 gap-4"
          >
            <fieldset className="border border-slate-200 p-4">
              <legend className="font-label-caps uppercase tracking-widest text-xs text-primary px-2">
                Essential
              </legend>
              <label className="flex items-start gap-3 mt-2 cursor-not-allowed opacity-80">
                <input
                  type="checkbox"
                  checked
                  disabled
                  className="mt-1 accent-primary"
                />
                <span className="font-body text-sm text-on-surface-variant">
                  Required for core features (cart, sign-in, checkout). Always
                  on.
                </span>
              </label>
            </fieldset>

            <fieldset className="border border-slate-200 p-4">
              <legend className="font-label-caps uppercase tracking-widest text-xs text-primary px-2">
                Analytics
              </legend>
              <label className="flex items-start gap-3 mt-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={selection.analytics}
                  onChange={(e) =>
                    setSelection((s) => ({ ...s, analytics: e.target.checked }))
                  }
                  className="mt-1 accent-primary"
                />
                <span className="font-body text-sm text-on-surface-variant">
                  Anonymous traffic and usage metrics. Helps us improve the
                  experience.
                </span>
              </label>
            </fieldset>

            <fieldset className="border border-slate-200 p-4">
              <legend className="font-label-caps uppercase tracking-widest text-xs text-primary px-2">
                Marketing
              </legend>
              <label className="flex items-start gap-3 mt-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={selection.marketing}
                  onChange={(e) =>
                    setSelection((s) => ({ ...s, marketing: e.target.checked }))
                  }
                  className="mt-1 accent-primary"
                />
                <span className="font-body text-sm text-on-surface-variant">
                  Personalised promotions and ad attribution. Off by default.
                </span>
              </label>
            </fieldset>

            <div className="md:col-span-3 flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowCustomize(false)}
                className="h-11 px-5 border border-slate-300 text-primary font-label-caps uppercase tracking-wider text-xs hover:bg-slate-50 transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={saveCustom}
                className="h-11 px-6 bg-primary text-on-primary font-label-caps uppercase tracking-wider text-xs hover:bg-slate-800 transition-colors"
              >
                Save preferences
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

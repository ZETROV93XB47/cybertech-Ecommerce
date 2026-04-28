"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/ui/Icon";

interface Props {
  /**
   * Server action that performs the delete and revalidates the parent page.
   * Returns `{ ok }` so we can render an inline error if the call failed.
   */
  action: () => Promise<{ ok: boolean; error?: string }>;
  /** Plain-language description shown in the confirm dialog. */
  label: string;
  /** Optional extra confirmation copy. */
  description?: string;
}

/**
 * Inline destructive-action button with a click-to-confirm overlay.
 * Avoids a heavy modal library — purely Tailwind and a local state flag.
 */
export function DeleteRowButton({ action, label, description }: Props) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const router = useRouter();

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={`Delete ${label}`}
        className="p-1.5 rounded-md text-rose-600 hover:bg-rose-50 transition-colors"
      >
        <Icon name="delete" size={18} />
      </button>
      {open && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm"
          onClick={() => !isPending && setOpen(false)}
        >
          <div
            className="bg-white rounded-xl shadow-xl max-w-sm w-full p-6 mx-4"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="font-display text-lg font-bold text-slate-900">
              Delete {label}?
            </h3>
            <p className="text-sm text-slate-600 mt-2">
              {description ?? "This action cannot be undone."}
            </p>
            {error && (
              <p className="text-sm text-rose-600 mt-3" role="alert">
                {error}
              </p>
            )}
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setOpen(false)}
                disabled={isPending}
                className="px-4 py-2 text-sm font-medium rounded-lg border border-slate-200 hover:bg-slate-50 disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isPending}
                onClick={() => {
                  setError(null);
                  startTransition(async () => {
                    const result = await action();
                    if (!result.ok) {
                      setError(result.error ?? "Delete failed.");
                      return;
                    }
                    setOpen(false);
                    // Refresh the parent server component so the row disappears.
                    router.refresh();
                  });
                }}
                className="px-4 py-2 text-sm font-medium rounded-lg bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50"
              >
                {isPending ? "Deleting…" : "Delete"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

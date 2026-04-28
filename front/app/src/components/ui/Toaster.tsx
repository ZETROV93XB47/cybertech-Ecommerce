"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Icon } from "./Icon";
import {
  ToastContext,
  type Toast,
  type ToastInput,
  type ToastType,
} from "./use-toast";

/**
 * Maximum number of toasts visible at once. Older toasts beyond this cap are
 * dropped from the head of the queue when a new one comes in.
 */
const MAX_TOASTS = 4;
const DEFAULT_DURATION_MS = 5000;

/**
 * Per-type visual config. Tokens come from globals.css `@theme`.
 *
 *   success → primary  (positive confirmation)
 *   error   → error    (destructive / failure)
 *   info    → secondary (neutral info / progress)
 */
const TYPE_STYLES: Record<
  ToastType,
  { border: string; iconBg: string; iconText: string; icon: string }
> = {
  success: {
    border: "border-l-primary",
    iconBg: "bg-primary",
    iconText: "text-on-primary",
    icon: "check_circle",
  },
  error: {
    border: "border-l-error",
    iconBg: "bg-error",
    iconText: "text-on-error",
    icon: "error",
  },
  info: {
    border: "border-l-secondary",
    iconBg: "bg-secondary",
    iconText: "text-on-secondary",
    icon: "info",
  },
};

function genId(): string {
  // Crypto.randomUUID is fine in modern browsers; fall back to Math.random for
  // older targets / SSR safety. The id only needs to be unique within the queue.
  if (typeof globalThis.crypto !== "undefined" && globalThis.crypto.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `t_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

type ToastItemProps = {
  toast: Toast;
  onDismiss: (id: string) => void;
};

function ToastItem({ toast, onDismiss }: ToastItemProps) {
  const [visible, setVisible] = useState(false);

  // Mount → fade in next tick.
  useEffect(() => {
    const t = window.setTimeout(() => setVisible(true), 10);
    return () => window.clearTimeout(t);
  }, []);

  const handleClose = useCallback(() => {
    setVisible(false);
    // Wait for fade-out before removing from queue (matches duration-200 below).
    window.setTimeout(() => onDismiss(toast.id), 200);
  }, [onDismiss, toast.id]);

  const styles = TYPE_STYLES[toast.type];

  return (
    <div
      role={toast.type === "error" ? "alert" : "status"}
      aria-live={toast.type === "error" ? "assertive" : "polite"}
      className={[
        "pointer-events-auto w-full max-w-sm bg-surface-container-lowest",
        "border border-outline-variant/40 border-l-4",
        styles.border,
        "shadow-[0_8px_24px_rgb(0,0,0,0.08)] rounded-lg",
        "flex items-start gap-3 p-4",
        "transition-all duration-200 ease-out",
        visible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-2",
      ].join(" ")}
    >
      <span
        className={[
          "shrink-0 inline-flex items-center justify-center w-8 h-8 rounded-full",
          styles.iconBg,
          styles.iconText,
        ].join(" ")}
      >
        <Icon name={styles.icon} size={18} />
      </span>
      <div className="flex-1 min-w-0">
        <p className="font-display text-sm font-semibold text-on-surface leading-5">
          {toast.title}
        </p>
        {toast.description && (
          <p className="mt-1 text-sm text-on-surface-variant leading-5 break-words">
            {toast.description}
          </p>
        )}
      </div>
      <button
        type="button"
        onClick={handleClose}
        aria-label="Dismiss notification"
        className="shrink-0 -mr-1 -mt-1 p-1 text-on-surface-variant hover:text-on-surface transition-colors"
      >
        <Icon name="close" size={18} />
      </button>
    </div>
  );
}

/**
 * Top-level toast provider. Mount once near the root of the client tree.
 *
 *   <ToastProvider>
 *     {children}
 *   </ToastProvider>
 *
 * In a Next.js 16 app where `app/layout.tsx` stays a Server Component,
 * use the `<ToastProvider>` wrapper from `components/providers/ToastProvider.tsx`
 * (it re-exports this same component) inside any client subtree, e.g.
 * `MainLayout.tsx`.
 */
export function Toaster({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  // Track per-toast timers so manual dismiss doesn't leak them.
  const timersRef = useRef<Map<string, number>>(new Map());

  const dismiss = useCallback((id: string) => {
    setToasts((cur) => cur.filter((t) => t.id !== id));
    const handle = timersRef.current.get(id);
    if (handle !== undefined) {
      window.clearTimeout(handle);
      timersRef.current.delete(id);
    }
  }, []);

  const toast = useCallback(
    (input: ToastInput): string => {
      const id = genId();
      const next: Toast = { id, ...input };
      setToasts((cur) => {
        const merged = [...cur, next];
        // Trim oldest if we exceed MAX_TOASTS.
        return merged.length > MAX_TOASTS
          ? merged.slice(merged.length - MAX_TOASTS)
          : merged;
      });
      const duration = input.duration ?? DEFAULT_DURATION_MS;
      if (duration > 0) {
        const handle = window.setTimeout(() => dismiss(id), duration);
        timersRef.current.set(id, handle);
      }
      return id;
    },
    [dismiss],
  );

  // Cleanup all timers on unmount.
  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach((h) => window.clearTimeout(h));
      timers.clear();
    };
  }, []);

  const value = useMemo(() => ({ toasts, toast, dismiss }), [toasts, toast, dismiss]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {/* Viewport: top-right on desktop, top-center on mobile. Pointer-events
          gated so the empty area never blocks underlying UI. */}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="pointer-events-none fixed inset-x-0 top-4 z-[1000] flex flex-col items-center gap-3 px-4 sm:left-auto sm:right-4 sm:top-4 sm:items-end"
      >
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onDismiss={dismiss} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

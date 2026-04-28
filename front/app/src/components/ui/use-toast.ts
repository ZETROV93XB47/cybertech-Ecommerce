"use client";

import { createContext, useContext } from "react";

/**
 * Toast type — matches the design tokens in globals.css.
 * - success → bg-primary (deep ink)
 * - error   → bg-error (M3 error red)
 * - info    → bg-secondary (cybertech blue)
 */
export type ToastType = "success" | "error" | "info";

export type Toast = {
  id: string;
  type: ToastType;
  title: string;
  description?: string;
  /** Auto-dismiss after this many ms. Defaults to 5000. Pass 0 for sticky. */
  duration?: number;
};

export type ToastInput = Omit<Toast, "id">;

export type ToastContextValue = {
  toasts: Toast[];
  toast: (t: ToastInput) => string;
  dismiss: (id: string) => void;
};

/**
 * Context default is a no-op so calling `useToast()` outside a provider
 * during SSR / static rendering doesn't throw — it just silently swallows.
 * The real provider lives in `Toaster.tsx`.
 */
export const ToastContext = createContext<ToastContextValue>({
  toasts: [],
  toast: () => "",
  dismiss: () => {},
});

/**
 * Public hook. Use from any client component:
 *
 *   const { toast } = useToast();
 *   toast({ type: "success", title: "Added to cart" });
 */
export function useToast(): ToastContextValue {
  return useContext(ToastContext);
}

"use client";

import { Toaster } from "@/components/ui/Toaster";

/**
 * Thin client-component wrapper around `<Toaster>` so server components
 * (e.g. `app/layout.tsx`, individual server pages) can drop a single
 * `<ToastProvider>` into the tree without becoming client themselves.
 *
 * Pick ONE of the following mount points (whichever owns the broadest
 * subtree that needs toasts):
 *
 *   1. `MainLayout.tsx` — wraps every page that uses MainLayout (recommended).
 *   2. `app/layout.tsx`'s `<body>` children — global, including pages that
 *      bypass MainLayout. The root layout itself stays a Server Component.
 *
 * Inside any consumer:
 *
 *   "use client";
 *   import { useToast } from "@/components/ui/use-toast";
 *   const { toast } = useToast();
 *   toast({ type: "success", title: "Saved" });
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  return <Toaster>{children}</Toaster>;
}

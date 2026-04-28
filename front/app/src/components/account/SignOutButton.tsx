"use client";

import { signOut } from "next-auth/react";
import { useTransition } from "react";
import { Icon } from "@/components/ui/Icon";

/**
 * Client-side sign-out trigger. Posts to Auth.js's /api/auth/signout endpoint
 * and lands the user back on the home page. We use a transition to avoid the
 * button "flicker" while the redirect resolves.
 */
export function SignOutButton() {
  const [isPending, startTransition] = useTransition();

  return (
    <button
      type="button"
      onClick={() =>
        startTransition(() => {
          void signOut({ callbackUrl: "/" });
        })
      }
      disabled={isPending}
      className="inline-flex items-center justify-center gap-2 h-11 px-6 font-display text-sm font-semibold uppercase tracking-wider bg-primary text-on-primary hover:bg-slate-800 transition-colors disabled:opacity-60"
    >
      <Icon name="logout" size={16} />
      {isPending ? "Signing out…" : "Sign out"}
    </button>
  );
}

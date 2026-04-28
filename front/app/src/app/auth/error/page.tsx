"use client";

import { use } from "react";
import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { Icon } from "@/components/ui/Icon";

/**
 * Auth.js dispatches the user here whenever a callback fails — the error
 * code arrives via the `?error=...` query string. We translate the well-known
 * Auth.js error codes to operator-grade copy and offer a single retry path.
 *
 * Spec ref: https://authjs.dev/reference/core/errors
 */

interface ErrorEntry {
  title: string;
  blurb: string;
  hint?: string;
}

const ERRORS: Record<string, ErrorEntry> = {
  Configuration: {
    title: "Sign-in is misconfigured",
    blurb:
      "Auth.js couldn't load the Keycloak provider. This is on us — please try again in a moment or contact support.",
  },
  AccessDenied: {
    title: "Access denied",
    blurb:
      "Your account doesn't currently have access to Cybertech. If you think this is wrong, please get in touch with support.",
  },
  Verification: {
    title: "Verification link expired",
    blurb:
      "The link you used has either already been claimed or expired. Try signing in again to receive a fresh one.",
  },
  OAuthAccountNotLinked: {
    title: "Email already registered",
    blurb:
      "This email is already linked to a different sign-in method. Sign in with the original method, then link the new one from your account settings.",
  },
  OAuthCallbackError: {
    title: "Couldn't complete sign-in",
    blurb:
      "We didn't get a clean response from Keycloak. Please try again — if the issue persists, clear cookies for this site.",
  },
  OAuthSigninError: {
    title: "Sign-in failed before redirect",
    blurb:
      "Auth.js couldn't kick off the OIDC flow. This usually means our Keycloak realm is offline. Try again in a moment.",
  },
  CredentialsSignin: {
    title: "Invalid credentials",
    blurb: "The username or password didn't match. Double-check and try again.",
  },
  SessionRequired: {
    title: "Sign-in required",
    blurb: "This page needs an account. Please sign in to continue.",
  },
  Default: {
    title: "Something went wrong",
    blurb:
      "Sign-in failed for an unexpected reason. Try again — and if the problem keeps happening, send us the error code shown below.",
  },
};

interface ErrorPageProps {
  searchParams: Promise<{ error?: string }>;
}

export default function AuthErrorPage({ searchParams }: ErrorPageProps) {
  const params = use(searchParams);
  const code = typeof params.error === "string" && params.error ? params.error : "Default";
  const entry = ERRORS[code] ?? ERRORS.Default;

  return (
    <MainLayout>
      <div className="bg-surface-container-low min-h-[calc(100vh-160px)] flex items-center">
        <div className="max-w-xl w-full mx-auto px-6 md:px-8 py-12 md:py-20">
          <div className="bg-white border border-slate-100 rounded-2xl shadow-sm p-8 md:p-12">
            <span className="inline-flex w-12 h-12 items-center justify-center bg-error-container text-on-error-container rounded-full mb-6">
              <Icon name="error" size={22} />
            </span>

            <p className="font-label-caps uppercase tracking-[0.2em] text-error text-xs mb-2">
              Authentication error
            </p>
            <h1 className="font-display text-display-lg text-3xl md:text-4xl text-primary mb-3 font-bold">
              {entry.title}
            </h1>
            <p className="font-body text-body-md text-on-surface-variant mb-6">
              {entry.blurb}
            </p>

            <p className="font-mono text-xs text-on-surface-variant mb-8">
              Error code: <span className="text-primary">{code}</span>
            </p>

            <div className="flex flex-col sm:flex-row gap-3">
              <Link
                href="/auth/login"
                className="flex-1 inline-flex items-center justify-center gap-2 h-12 px-6 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors rounded-lg"
              >
                <Icon name="autorenew" size={16} />
                Try again
              </Link>
              <Link
                href="/"
                className="flex-1 inline-flex items-center justify-center gap-2 h-12 px-6 border border-outline font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors rounded-lg"
              >
                Back to store
              </Link>
            </div>

            <p className="mt-8 text-sm text-on-surface-variant">
              Still stuck?{" "}
              <a
                href="mailto:support@cybertech.example"
                className="text-secondary hover:underline"
              >
                support@cybertech.example
              </a>
            </p>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}

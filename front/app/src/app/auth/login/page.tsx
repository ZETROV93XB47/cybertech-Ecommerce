import Link from "next/link";
import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { MainLayout } from "@/components/layout/MainLayout";
import { Icon } from "@/components/ui/Icon";

export const metadata = {
  title: "Sign in — Cybertech",
  description: "Sign in to manage your orders, wishlist and saved payment methods.",
};

interface PageProps {
  searchParams?: Promise<{
    callbackUrl?: string;
    registered?: string;
    error?: string;
  }>;
}

/**
 * Branded sign-in screen. Auth.js's default `/api/auth/signin` works fine
 * for end-users but is a bare unstyled list — this page is what the rest of
 * the site links to. Clicking the CTA hands off to Keycloak via the
 * provider-specific signin handler at `/api/auth/signin/keycloak`.
 *
 * If the visitor is already signed in we redirect them to their account
 * (or the post-login `callbackUrl` if one was passed in, e.g. from a
 * "sign in to add to cart" flow).
 */
export default async function LoginPage({ searchParams }: PageProps) {
  const params = (await searchParams) ?? {};
  const callbackUrl = sanitizeCallback(params.callbackUrl);
  const session = await auth();
  if (session) redirect(callbackUrl);

  const signinHref = `/api/auth/signin/keycloak?callbackUrl=${encodeURIComponent(callbackUrl)}`;
  const justRegistered = params.registered === "1";
  const errorCode = typeof params.error === "string" ? params.error : null;

  return (
    <MainLayout>
      <div className="bg-surface-container-low min-h-[calc(100vh-160px)] flex items-center">
        <div className="max-w-xl w-full mx-auto px-6 md:px-8 py-12 md:py-20">
          <Link
            href="/"
            className="inline-flex items-center gap-2 text-on-surface-variant hover:text-primary transition-colors mb-8"
          >
            <Icon name="arrow_back" size={16} />
            <span className="font-label-caps uppercase tracking-wider text-xs">
              Back to store
            </span>
          </Link>

          <div className="bg-white border border-slate-100 rounded-2xl shadow-sm p-8 md:p-12">
            <div className="flex items-center gap-3 mb-6">
              <span className="inline-flex w-12 h-12 items-center justify-center bg-primary text-on-primary rounded-full">
                <Icon name="lock_open" size={22} />
              </span>
              <span className="font-label-caps uppercase tracking-[0.2em] text-secondary text-xs">
                Account access
              </span>
            </div>

            <h1 className="font-display text-display-lg text-3xl md:text-4xl text-primary mb-3 font-bold">
              Welcome back
            </h1>
            <p className="font-body text-body-md text-on-surface-variant mb-8">
              We use Keycloak to keep your credentials safe — you&apos;ll be
              redirected to a single secure prompt and bounced right back here
              once you&apos;re in.
            </p>

            {justRegistered && (
              <div
                role="status"
                className="mb-6 flex items-start gap-3 px-4 py-3 bg-secondary-container/40 text-on-secondary-container border border-secondary/30 rounded-lg"
              >
                <Icon name="check_circle" size={18} />
                <span className="text-sm">
                  Account created. Sign in to finish setting things up.
                </span>
              </div>
            )}

            {errorCode && (
              <div
                role="alert"
                className="mb-6 text-sm text-on-error-container bg-error-container/60 px-4 py-3 rounded-lg"
              >
                {humanize(errorCode)}
              </div>
            )}

            <Link
              href={signinHref}
              className="w-full inline-flex items-center justify-center gap-3 h-14 px-6 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors rounded-lg shadow-sm shadow-primary/10"
            >
              <Icon name="shield_person" size={18} />
              Sign in with Keycloak
            </Link>

            <div className="mt-8 flex flex-col gap-3 text-sm text-on-surface-variant">
              <Link
                href="/auth/forgot-password"
                className="inline-flex items-center gap-2 hover:text-primary"
              >
                <Icon name="help" size={14} />
                Forgot your password?
              </Link>
              <p>
                Don&apos;t have an account?{" "}
                <Link
                  href="/auth/register"
                  className="text-secondary hover:underline font-medium"
                >
                  Create one in under a minute
                </Link>
              </p>
            </div>
          </div>

          <p className="mt-6 flex items-center justify-center gap-2 text-on-surface-variant">
            <Icon name="lock" size={14} />
            <span className="font-label-caps uppercase tracking-widest text-[10px]">
              Encrypted secure session
            </span>
          </p>
        </div>
      </div>
    </MainLayout>
  );
}

function sanitizeCallback(raw: string | undefined): string {
  // Keep callbacks site-internal to avoid open-redirect via a doctored link.
  if (!raw || typeof raw !== "string") return "/account/profile";
  if (!raw.startsWith("/") || raw.startsWith("//")) return "/account/profile";
  return raw;
}

function humanize(code: string): string {
  switch (code) {
    case "OAuthAccountNotLinked":
      return "That email is already linked to another sign-in method.";
    case "AccessDenied":
      return "Access was denied. Please contact support if this is unexpected.";
    case "Verification":
      return "Your sign-in link is no longer valid. Try again.";
    case "Configuration":
      return "Authentication is misconfigured. Please contact support.";
    case "CredentialsSignin":
      return "Sign-in failed. Please check your credentials and try again.";
    default:
      return "Sign-in failed. Please try again.";
  }
}

import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { Icon } from "@/components/ui/Icon";

export const metadata = {
  title: "Reset password — Cybertech",
  description:
    "Reset your Cybertech password through our secure Keycloak identity provider.",
};

/**
 * Placeholder password-reset entry point.
 *
 * Cybertech delegates identity to Keycloak, which already exposes a
 * "Forgot password" flow on its login screen (`reset-credentials`). Rather
 * than try to mirror it inside Next, we link directly to Keycloak's hosted
 * page — same realm, same client, same redirect target as the Auth.js OIDC
 * flow.
 *
 * The URL is built from `AUTH_KEYCLOAK_ISSUER` + `AUTH_KEYCLOAK_ID`. If either
 * is missing (e.g. local dev with stub Keycloak), we degrade to instructions
 * so the link in the header/footer never 404s.
 */
function buildResetUrl(): string | null {
  const issuer = process.env.AUTH_KEYCLOAK_ISSUER;
  const clientId = process.env.AUTH_KEYCLOAK_ID;
  const appUrl = process.env.AUTH_URL ?? process.env.NEXTAUTH_URL;
  if (!issuer || !clientId) return null;

  // Keycloak's reset-credentials action expects the standard OIDC params.
  const redirectUri = appUrl
    ? `${appUrl.replace(/\/$/, "")}/api/auth/callback/keycloak`
    : null;

  const url = new URL(`${issuer.replace(/\/$/, "")}/protocol/openid-connect/auth`);
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", "openid");
  url.searchParams.set("kc_action", "reset-credentials");
  if (redirectUri) url.searchParams.set("redirect_uri", redirectUri);
  return url.toString();
}

export default function ForgotPasswordPage() {
  const resetUrl = buildResetUrl();

  return (
    <MainLayout>
      <div className="bg-surface-container-low min-h-[calc(100vh-160px)] flex items-center">
        <div className="max-w-xl w-full mx-auto px-6 md:px-8 py-12 md:py-20">
          <Link
            href="/auth/login"
            className="inline-flex items-center gap-2 text-on-surface-variant hover:text-primary transition-colors mb-8"
          >
            <Icon name="arrow_back" size={16} />
            <span className="font-label-caps uppercase tracking-wider text-xs">
              Back to sign in
            </span>
          </Link>

          <div className="bg-white border border-slate-100 rounded-2xl shadow-sm p-8 md:p-12">
            <span className="inline-flex w-12 h-12 items-center justify-center bg-secondary-container text-on-secondary-container rounded-full mb-6">
              <Icon name="lock_reset" size={22} />
            </span>

            <h1 className="font-display text-display-lg text-3xl md:text-4xl text-primary mb-3 font-bold">
              Reset your password
            </h1>
            <p className="font-body text-body-md text-on-surface-variant mb-8">
              Cybertech delegates password management to Keycloak, our secure
              identity provider. Click below to start the reset flow — you
              will receive an email with a link to choose a new password.
            </p>

            <ol className="space-y-4 mb-8">
              <Step
                index={1}
                title="Open the reset page"
                blurb="We'll redirect you to Keycloak's hosted reset form."
              />
              <Step
                index={2}
                title="Enter your email or username"
                blurb="Use the address you registered with."
              />
              <Step
                index={3}
                title="Check your inbox"
                blurb="Click the link to choose a new password, then come back to sign in."
              />
            </ol>

            {resetUrl ? (
              <a
                href={resetUrl}
                className="w-full inline-flex items-center justify-center gap-3 h-14 px-6 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors rounded-lg"
              >
                <Icon name="open_in_new" size={18} />
                Reset password on Keycloak
              </a>
            ) : (
              <div
                role="status"
                className="border border-outline-variant rounded-lg p-6 bg-surface-container-low text-sm text-on-surface-variant"
              >
                <p className="font-display text-primary font-semibold mb-2">
                  Reset link unavailable
                </p>
                <p>
                  Our identity provider is not currently reachable from this
                  environment. Please contact{" "}
                  <a
                    href="mailto:support@cybertech.example"
                    className="text-secondary hover:underline"
                  >
                    support@cybertech.example
                  </a>{" "}
                  and a team member will help you regain access.
                </p>
              </div>
            )}

            <p className="mt-6 text-sm text-on-surface-variant">
              Remembered it after all?{" "}
              <Link
                href="/auth/login"
                className="text-secondary hover:underline"
              >
                Back to sign in
              </Link>
              .
            </p>
          </div>
        </div>
      </div>
    </MainLayout>
  );
}

function Step({
  index,
  title,
  blurb,
}: {
  index: number;
  title: string;
  blurb: string;
}) {
  return (
    <li className="flex items-start gap-4">
      <span className="flex-shrink-0 w-8 h-8 rounded-full bg-primary text-on-primary font-display font-semibold text-sm flex items-center justify-center">
        {index}
      </span>
      <div>
        <p className="font-display font-semibold text-primary leading-tight">
          {title}
        </p>
        <p className="text-sm text-on-surface-variant mt-1">{blurb}</p>
      </div>
    </li>
  );
}

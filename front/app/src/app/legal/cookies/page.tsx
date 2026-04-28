import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";

export const metadata = {
  title: "Cookie Policy — Cybertech",
  description:
    "Detailed inventory of the cookies and similar storage Cybertech uses, their purpose, lifetime, and how to opt out.",
};

const COOKIE_TABLE = [
  {
    category: "Essential",
    name: "next-auth.session-token",
    purpose: "Keeps you signed in across pages.",
    lifetime: "Session / 30 days",
    party: "First-party",
  },
  {
    category: "Essential",
    name: "cart-id",
    purpose: "Maps your browser to its server-side cart.",
    lifetime: "30 days",
    party: "First-party",
  },
  {
    category: "Essential",
    name: "cookie-consent",
    purpose: "Stores your choice on this banner so we don't keep asking.",
    lifetime: "12 months",
    party: "First-party",
  },
  {
    category: "Analytics",
    name: "_cyt_anon",
    purpose:
      "Aggregated, anonymous traffic measurement (page views, search depth).",
    lifetime: "13 months",
    party: "First-party",
  },
  {
    category: "Marketing",
    name: "_cyt_promo",
    purpose: "Attribution of orders to a campaign — only set with consent.",
    lifetime: "90 days",
    party: "First-party",
  },
];

export default function CookiePolicyPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Legal"
        title="Cookie Policy"
        lead="Everything we store on your device, why, for how long, and how to take it back."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Legal" },
          { label: "Cookies" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 max-w-3xl space-y-10 font-body text-body-md text-on-surface-variant leading-relaxed">
          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              What is a cookie?
            </h2>
            <p>
              A cookie is a small text file written by a site (or a third
              party loaded by a site) into your browser&apos;s storage. We
              also use the term loosely to cover other storage mechanisms,
              like localStorage, that have the same legal regime under the
              GDPR / ePrivacy directive.
            </p>
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Categories we use
            </h2>
            <p>
              We split cookies into three categories. Essential cookies are
              always on — without them, the site can&apos;t work. The other
              two are off until you turn them on through the consent banner
              or this page.
            </p>
          </div>

          <div className="overflow-x-auto border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-container-low">
                <tr>
                  <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                    Category
                  </th>
                  <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                    Name
                  </th>
                  <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                    Purpose
                  </th>
                  <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                    Lifetime
                  </th>
                  <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                    Party
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {COOKIE_TABLE.map((row) => (
                  <tr key={row.name}>
                    <td className="py-3 px-4 align-top text-primary font-display font-semibold">
                      {row.category}
                    </td>
                    <td className="py-3 px-4 align-top font-mono text-xs text-primary">
                      {row.name}
                    </td>
                    <td className="py-3 px-4 align-top text-on-surface-variant">
                      {row.purpose}
                    </td>
                    <td className="py-3 px-4 align-top text-on-surface-variant">
                      {row.lifetime}
                    </td>
                    <td className="py-3 px-4 align-top text-on-surface-variant">
                      {row.party}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Changing your mind
            </h2>
            <p>
              The consent banner re-appears as soon as you clear the{" "}
              <code className="px-1 py-0.5 bg-slate-100 text-xs font-mono rounded">
                cookie-consent
              </code>{" "}
              storage entry. Most browsers expose a per-site &ldquo;clear
              storage&rdquo; option in their settings. You can also block
              all but essential cookies via your browser&apos;s privacy
              settings; the site stays functional but personalised promos
              will no longer follow you.
            </p>
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Questions
            </h2>
            <p>
              Email{" "}
              <a
                href="mailto:dpo@cybertech.local"
                className="text-secondary underline-offset-4 hover:underline"
              >
                dpo@cybertech.local
              </a>{" "}
              — our DPO answers within 30 days.
            </p>
          </div>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

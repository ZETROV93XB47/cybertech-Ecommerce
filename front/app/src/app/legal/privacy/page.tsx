import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";

export const metadata = {
  title: "Privacy Policy — Cybertech",
  description:
    "How Cybertech collects, processes, retains and shares your personal data, and the rights you can exercise under GDPR.",
};

export default function PrivacyPolicyPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Legal"
        title="Privacy Policy"
        lead="How we collect, store, share and delete your personal data — written in plain English, with the legal references where they matter."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Legal" },
          { label: "Privacy" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 max-w-3xl">
          <p className="font-body text-sm text-on-surface-variant mb-10">
            Last updated: 28 April 2026. This policy describes how Cybertech
            Industrial (&ldquo;we&rdquo;, &ldquo;us&rdquo;) processes personal
            data for visitors and customers of cybertech.local. It complies
            with the EU General Data Protection Regulation (GDPR) and the
            French Loi Informatique et Libertés.
          </p>

          <article className="prose-cybertech space-y-12">
            <Section title="1. Who we are">
              <p>
                Cybertech Industrial is a French simplified joint-stock
                company (SAS) registered at 14 quai Saint-Antoine, 69002
                Lyon, France. We are the data controller (&laquo; responsable
                de traitement &raquo;) for the personal data processed on
                this site.
              </p>
              <p>
                Our Data Protection Officer can be reached at{" "}
                <a
                  href="mailto:dpo@cybertech.local"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  dpo@cybertech.local
                </a>
                .
              </p>
            </Section>

            <Section title="2. Data we collect">
              <p>
                We only collect data we genuinely need. The categories of
                personal data we process are:
              </p>
              <ul>
                <li>
                  <strong>Account data</strong> — email, name, hashed
                  password, role, addresses, phone number.
                </li>
                <li>
                  <strong>Order data</strong> — order line items, totals,
                  shipping addresses, fulfillment status.
                </li>
                <li>
                  <strong>Payment metadata</strong> — last four digits and
                  expiry of saved cards. We never store full card numbers; the
                  Primary Account Number (PAN) lives only with our PCI-DSS
                  certified payment processor (Stripe).
                </li>
                <li>
                  <strong>Browsing events</strong> — anonymous product views,
                  search queries, cart events. Used to improve the catalog
                  and surface best-sellers.
                </li>
                <li>
                  <strong>Support correspondence</strong> — emails and form
                  submissions you send us.
                </li>
              </ul>
            </Section>

            <Section title="3. Why we process it (legal bases)">
              <ul>
                <li>
                  <strong>Contract</strong> (GDPR art. 6.1.b): account, order
                  and payment data — required to fulfill purchases.
                </li>
                <li>
                  <strong>Legitimate interest</strong> (art. 6.1.f):
                  fraud prevention, aggregated analytics, security logging.
                </li>
                <li>
                  <strong>Consent</strong> (art. 6.1.a): non-essential cookies
                  (analytics, marketing), email marketing.
                </li>
                <li>
                  <strong>Legal obligation</strong> (art. 6.1.c): invoicing,
                  accounting and tax records.
                </li>
              </ul>
            </Section>

            <Section title="4. Retention">
              <p>
                We keep data only as long as needed for the purpose it was
                collected for, then delete or anonymise it.
              </p>
              <ul>
                <li>Account data: lifetime of the account, then 12 months.</li>
                <li>
                  Orders &amp; invoices: 10 years (French commercial code,
                  art. L123-22).
                </li>
                <li>Browsing events: 13 months max.</li>
                <li>
                  Support correspondence: 3 years after the last contact.
                </li>
              </ul>
            </Section>

            <Section title="5. Sharing">
              <p>
                We share data only with the processors needed to operate the
                service. Each one is bound by a data-processing agreement.
              </p>
              <ul>
                <li>Stripe (payments, USA / EU)</li>
                <li>AWS S3 + CloudFront (asset storage, EU Frankfurt)</li>
                <li>Postmark (transactional email)</li>
                <li>OVHcloud (hosting, France)</li>
              </ul>
              <p>
                We do not sell personal data and we do not share it with
                advertisers without your explicit consent.
              </p>
            </Section>

            <Section title="6. Your rights">
              <p>Under GDPR you have the right to:</p>
              <ul>
                <li>access the data we hold about you,</li>
                <li>rectify inaccurate data,</li>
                <li>erase your data (&ldquo;right to be forgotten&rdquo;),</li>
                <li>restrict processing,</li>
                <li>port your data to another provider,</li>
                <li>object to processing based on legitimate interest,</li>
                <li>withdraw consent at any time without retroactive effect.</li>
              </ul>
              <p>
                Email{" "}
                <a
                  href="mailto:dpo@cybertech.local"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  dpo@cybertech.local
                </a>{" "}
                — we respond within 30 days. You can also lodge a complaint
                with the CNIL (
                <a
                  href="https://www.cnil.fr/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  cnil.fr
                </a>
                ).
              </p>
            </Section>

            <Section title="7. Cookies">
              <p>
                See the dedicated{" "}
                <Link
                  href="/legal/cookies"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  cookie policy
                </Link>{" "}
                for the full breakdown of categories and lifetimes. You can
                update your choices any time from that page.
              </p>
            </Section>

            <Section title="8. Security">
              <p>
                Passwords are hashed with bcrypt. Traffic is TLS-encrypted
                end-to-end. Access to production data is limited to engineers
                on a need-to-know basis and audited monthly. We notify
                impacted users within 72 hours of any qualifying breach, in
                line with art. 33 GDPR.
              </p>
            </Section>

            <Section title="9. Changes">
              <p>
                We may update this policy as the service evolves. Material
                changes will be flagged via email or banner at least 14 days
                before they take effect.
              </p>
            </Section>
          </article>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="font-body text-body-md text-on-surface-variant leading-relaxed [&_p]:my-3 [&_ul]:list-disc [&_ul]:pl-6 [&_ul]:my-3 [&_li]:my-1 [&_strong]:text-primary">
      <h2 className="font-display text-headline-sm text-primary mb-3">
        {title}
      </h2>
      {children}
    </section>
  );
}

import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";

export const metadata = {
  title: "Terms of Service — Cybertech",
  description:
    "Terms governing the use of cybertech.local, the placement of orders, payment, delivery and statutory guarantees.",
};

export default function TermsPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Legal"
        title="Terms of Service"
        lead="The contract between you and Cybertech Industrial when you browse the site, place an order or open an account. CGV / CGU combined."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Legal" },
          { label: "Terms" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 max-w-3xl">
          <p className="font-body text-sm text-on-surface-variant mb-10">
            Last updated: 28 April 2026. By accessing this site and placing
            an order you accept these terms in full. If you don&apos;t agree,
            please don&apos;t use the service.
          </p>

          <article className="space-y-12">
            <Section title="1. Identification">
              <p>
                Cybertech Industrial — SAS with €50,000 share capital,
                registered with the Lyon Trade Register under SIRET 902 321
                987 00012, head office at 14 quai Saint-Antoine, 69002 Lyon,
                France. VAT number FR 32 902321987.
              </p>
            </Section>

            <Section title="2. Scope">
              <p>
                These terms cover the catalog of computers, monitors,
                smartphones and peripherals offered on cybertech.local, plus
                associated services (account, support, returns, warranty).
                Specific promotional terms — when applicable — are
                published on the relevant campaign page and prevail over
                this document for that promotion only.
              </p>
            </Section>

            <Section title="3. Orders">
              <p>
                An order is formed when you click &ldquo;Place order&rdquo;
                on the checkout page after having validated cart, shipping
                address and payment method. We send a confirmation by email;
                the contract is binding from the moment that confirmation
                is dispatched.
              </p>
              <p>
                We may refuse or cancel an order in the following cases:
                stock issue, suspected fraud, manifest pricing error, or
                violation of these terms. In that case any debit is refunded
                in full within 14 days.
              </p>
            </Section>

            <Section title="4. Prices &amp; payment">
              <p>
                Prices are listed in euros, all taxes included (TTC), and
                exclude shipping fees which appear at checkout. Payment is
                taken at order placement via Stripe (Visa, Mastercard,
                Amex, Apple Pay, Google Pay). Card data is processed
                end-to-end by Stripe — we never see the full PAN.
              </p>
            </Section>

            <Section title="5. Delivery">
              <p>
                Standard delivery within metropolitan France ships in 2 to
                4 business days. EU shipping ranges from 3 to 7 business
                days depending on destination. Tracking links are emailed as
                soon as the parcel leaves our warehouse. Risk of loss
                transfers to the buyer on physical receipt of the goods,
                in line with art. L216-4 French Consumer Code.
              </p>
            </Section>

            <Section title="6. Right of withdrawal">
              <p>
                Consumers in the EU have 14 days from receipt to withdraw
                without giving any reason (art. L221-18 Consumer Code).
                Returned items must be unused, in their original packaging
                and complete. We refund the full order — including
                outbound standard shipping — within 14 days of receiving the
                return. See the{" "}
                <a
                  href="/support/returns"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  returns policy
                </a>{" "}
                for the procedure.
              </p>
            </Section>

            <Section title="7. Statutory &amp; commercial warranties">
              <p>
                All products benefit from the statutory conformity guarantee
                (art. L217-3 et seq. Consumer Code) and the latent defects
                warranty (art. 1641 Civil Code), in addition to any
                manufacturer warranty published on the product page. See{" "}
                <a
                  href="/support/warranty"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  /support/warranty
                </a>{" "}
                for the complete breakdown and how to file a claim.
              </p>
            </Section>

            <Section title="8. Account &amp; conduct">
              <p>
                You are responsible for keeping your credentials secret.
                Notify us immediately of any unauthorised access. We may
                suspend or close accounts that submit fraudulent payments,
                publish abusive reviews, scrape the catalog, or otherwise
                violate these terms.
              </p>
            </Section>

            <Section title="9. Liability">
              <p>
                We do everything reasonable to keep the site up and orders
                shipping on time, but we cannot be held liable for losses
                resulting from force majeure, third-party network outages,
                or actions by the buyer that breach these terms. Nothing
                in this clause limits liability that cannot legally be
                limited (gross negligence, willful misconduct, statutory
                consumer rights).
              </p>
            </Section>

            <Section title="10. Personal data">
              <p>
                Personal data collected through the order flow is processed
                in accordance with our{" "}
                <a
                  href="/legal/privacy"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  privacy policy
                </a>
                .
              </p>
            </Section>

            <Section title="11. Governing law &amp; disputes">
              <p>
                These terms are governed by French law. Before any legal
                action, you may contact our customer service at{" "}
                <a
                  href="mailto:support@cybertech.local"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  support@cybertech.local
                </a>
                . You can also access the EU Online Dispute Resolution
                platform at{" "}
                <a
                  href="https://ec.europa.eu/consumers/odr/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-secondary underline-offset-4 hover:underline"
                >
                  ec.europa.eu/consumers/odr
                </a>
                . Failing amicable resolution, the competent court is the
                Tribunal Judiciaire de Lyon.
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
    <section className="font-body text-body-md text-on-surface-variant leading-relaxed [&_p]:my-3">
      <h2 className="font-display text-headline-sm text-primary mb-3">
        {title}
      </h2>
      {children}
    </section>
  );
}

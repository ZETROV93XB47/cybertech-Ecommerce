import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";

export const metadata = {
  title: "Shipping — Cybertech",
  description:
    "Carriers, lead times, tracking, customs and our promise on lost or damaged parcels.",
};

const RATES = [
  {
    zone: "Metropolitan France",
    standard: "Free over €60 / €4.90",
    express: "€9.90 — next business day",
    eta: "2 – 4 business days",
  },
  {
    zone: "EU (zone 1)",
    standard: "€7.90",
    express: "€19.90",
    eta: "3 – 5 business days",
  },
  {
    zone: "EU (zone 2)",
    standard: "€12.90",
    express: "€24.90",
    eta: "4 – 7 business days",
  },
  {
    zone: "UK & Switzerland",
    standard: "€18.90 + duties",
    express: "€34.90 + duties",
    eta: "5 – 8 business days",
  },
];

export default function ShippingPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Support"
        title="Shipping"
        lead="How fast you'll get your order, who carries it, what it costs, and what we do when it doesn't show up."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Support", href: "/support" },
          { label: "Shipping" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 max-w-4xl space-y-12 font-body text-body-md text-on-surface-variant leading-relaxed">
          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Carriers we use
            </h2>
            <p>
              We pick the carrier with the best track record on each route.
              In France, that means Colissimo and Chronopost; across the EU,
              DPD and DHL Express for time-sensitive lanes; UPS Worldwide
              Saver for UK and Switzerland.
            </p>
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Rates &amp; lead times
            </h2>
            <div className="overflow-x-auto border border-slate-200">
              <table className="w-full text-left text-sm">
                <thead className="bg-surface-container-low">
                  <tr>
                    <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                      Zone
                    </th>
                    <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                      Standard
                    </th>
                    <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                      Express
                    </th>
                    <th className="py-3 px-4 font-label-caps uppercase tracking-widest text-xs text-primary">
                      ETA
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {RATES.map((r) => (
                    <tr key={r.zone}>
                      <td className="py-3 px-4 text-primary font-display font-semibold">
                        {r.zone}
                      </td>
                      <td className="py-3 px-4">{r.standard}</td>
                      <td className="py-3 px-4">{r.express}</td>
                      <td className="py-3 px-4">{r.eta}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-3 text-sm">
              Orders placed before 14:00 CET on a business day ship the
              same day. Anything later goes out the next morning.
            </p>
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Tracking
            </h2>
            <p>
              The moment your parcel leaves our warehouse, you receive an
              email with the tracking link. The same link is available on
              your{" "}
              <Link
                href="/account/orders"
                className="text-secondary underline-offset-4 hover:underline"
              >
                order history
              </Link>{" "}
              page. Most carriers update statuses every few hours; expect a
              quiet period of up to 24 h after dispatch before the first
              event.
            </p>
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Customs &amp; duties
            </h2>
            <p>
              Inside the EU, all-in prices apply: no extra fee on delivery.
              For UK and Switzerland, duties and import VAT are owed by the
              recipient and collected by the carrier. We mark every parcel
              with the correct HS code so processing is fast.
            </p>
          </div>

          <div className="bg-surface-container-low p-8">
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Lost or damaged parcel?
            </h2>
            <p>
              If a parcel hasn&apos;t moved for 5 business days, or arrives
              damaged, email{" "}
              <a
                href="mailto:support@cybertech.local"
                className="text-secondary underline-offset-4 hover:underline"
              >
                support@cybertech.local
              </a>{" "}
              with the tracking number and any photos. We open the carrier
              investigation, ship a replacement when stock allows, and
              refund the order in full if it doesn&apos;t. Risk transfer
              happens on physical receipt — not at handover to the carrier
              — so a missing parcel is on us, not on you.
            </p>
          </div>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

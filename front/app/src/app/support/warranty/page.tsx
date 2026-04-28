import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";
import { Icon } from "@/components/ui/Icon";

export const metadata = {
  title: "Warranty — Cybertech",
  description:
    "Statutory conformity, latent defects and manufacturer warranties on Cybertech products, and how to file a claim.",
};

const STEPS = [
  {
    icon: "mail",
    title: "Open a claim",
    detail:
      "Email support@cybertech.local with your order number and a short description of the issue. Photos or a short clip help.",
  },
  {
    icon: "fact_check",
    title: "Diagnostic",
    detail:
      "Our team confirms whether the issue is covered and which warranty applies — usually within 48 hours.",
  },
  {
    icon: "local_shipping",
    title: "Pickup",
    detail:
      "We email a prepaid return label. Drop the parcel at any partner point or schedule a home pickup.",
  },
  {
    icon: "verified",
    title: "Repair, replace, refund",
    detail:
      "We repair when possible, replace when the SKU is in stock, refund otherwise. Turnaround is 7 to 14 business days.",
  },
];

export default function WarrantyPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Support"
        title="Warranty"
        lead="Three layers of protection cover everything you buy from Cybertech. Here is what each one covers — and how to claim."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Support", href: "/support" },
          { label: "Warranty" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 max-w-4xl space-y-12">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card
              icon="gavel"
              title="Conformity (2 years)"
              body="French Consumer Code art. L217-3 et seq. Covers any defect of conformity present at delivery. We bear the cost of repair, replacement or refund."
            />
            <Card
              icon="search"
              title="Latent defects (2 years)"
              body="French Civil Code art. 1641. Covers hidden defects rendering the product unfit for use, claimable up to 2 years after discovery."
            />
            <Card
              icon="business"
              title="Manufacturer (varies)"
              body="Adds on top — typically 1 to 5 years depending on brand and category. The exact term is shown on every product page."
            />
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-6">
              How to file a claim
            </h2>
            <ol className="space-y-6">
              {STEPS.map((step, idx) => (
                <li key={step.title} className="flex gap-5">
                  <div className="shrink-0 w-12 h-12 flex items-center justify-center bg-primary text-on-primary">
                    <Icon name={step.icon} size={22} />
                  </div>
                  <div>
                    <h3 className="font-display text-base font-semibold text-primary mb-1">
                      {idx + 1}. {step.title}
                    </h3>
                    <p className="font-body text-body-md text-on-surface-variant leading-relaxed">
                      {step.detail}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </div>

          <div className="bg-surface-container-low p-8">
            <h2 className="font-display text-headline-sm text-primary mb-3">
              What&apos;s not covered
            </h2>
            <ul className="font-body text-body-md text-on-surface-variant leading-relaxed list-disc pl-6 space-y-1.5">
              <li>Cosmetic wear (scratches, scuffs) from normal use.</li>
              <li>
                Damage caused by drops, liquids, or unauthorised
                disassembly.
              </li>
              <li>Software incompatibilities with third-party tools.</li>
              <li>
                Consumables (batteries past their cycle limit, thermal
                paste, etc.).
              </li>
            </ul>
          </div>

          <div className="flex justify-center">
            <Link
              href="/contact"
              className="inline-flex items-center gap-2 bg-primary text-on-primary px-8 py-4 font-label-caps uppercase tracking-wider text-sm hover:bg-slate-800 transition-colors"
            >
              Start a warranty claim <Icon name="arrow_forward" size={16} />
            </Link>
          </div>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

function Card({
  icon,
  title,
  body,
}: {
  icon: string;
  title: string;
  body: string;
}) {
  return (
    <article className="bg-white border border-slate-200 p-6">
      <div className="w-10 h-10 flex items-center justify-center bg-primary text-on-primary mb-4">
        <Icon name={icon} size={20} />
      </div>
      <h3 className="font-display text-base font-semibold text-primary mb-2">
        {title}
      </h3>
      <p className="font-body text-sm text-on-surface-variant leading-relaxed">
        {body}
      </p>
    </article>
  );
}

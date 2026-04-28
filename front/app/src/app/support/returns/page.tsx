import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";
import { Icon } from "@/components/ui/Icon";

export const metadata = {
  title: "Returns — Cybertech",
  description:
    "14-day right of withdrawal, return procedure, refund timelines and exclusions for Cybertech orders.",
};

const STEPS = [
  {
    title: "Open a return request",
    detail:
      "Go to your order in /account/orders and click Return. You can also email support@cybertech.local — we'll set it up for you.",
  },
  {
    title: "Print the prepaid label",
    detail:
      "We email a Colissimo or DPD prepaid label, depending on your country. You don't pay for the return shipping inside the EU.",
  },
  {
    title: "Pack & drop off",
    detail:
      "Use the original box if you can. Include all accessories. Drop it at a partner relay point or schedule a home pickup.",
  },
  {
    title: "Refund within 14 days",
    detail:
      "We inspect the parcel on receipt. The refund — outbound shipping included — is issued on your original payment method within 14 days.",
  },
];

export default function ReturnsPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Support"
        title="Returns"
        lead="14 days to change your mind, no questions asked. Here's the procedure and the rare cases where it doesn't apply."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Support", href: "/support" },
          { label: "Returns" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 max-w-4xl space-y-12 font-body text-body-md text-on-surface-variant leading-relaxed">
          <div>
            <h2 className="font-display text-headline-sm text-primary mb-3">
              The 14-day rule
            </h2>
            <p>
              Under EU consumer law (art. L221-18 of the French Consumer
              Code), you can return any product purchased online within 14
              days of receipt — without giving any reason. The countdown
              starts the day the parcel is delivered, not the day you place
              the order.
            </p>
          </div>

          <div>
            <h2 className="font-display text-headline-sm text-primary mb-6">
              How it works
            </h2>
            <ol className="space-y-6">
              {STEPS.map((step, idx) => (
                <li key={step.title} className="flex gap-5">
                  <div className="shrink-0 w-12 h-12 flex items-center justify-center bg-primary text-on-primary">
                    <span className="font-display text-lg font-bold">
                      {idx + 1}
                    </span>
                  </div>
                  <div>
                    <h3 className="font-display text-base font-semibold text-primary mb-1">
                      {step.title}
                    </h3>
                    <p className="font-body text-body-md text-on-surface-variant leading-relaxed">
                      {step.detail}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-white border border-slate-200 p-6">
              <h3 className="font-display text-base font-semibold text-primary mb-3 flex items-center gap-2">
                <Icon name="check_circle" size={20} className="text-secondary" />
                Eligible
              </h3>
              <ul className="list-disc pl-6 space-y-1.5 text-sm">
                <li>Unused products in their original packaging.</li>
                <li>All accessories, manuals and seals intact.</li>
                <li>
                  Custom builds (subject to a 10% restocking fee on opened
                  cases).
                </li>
              </ul>
            </div>
            <div className="bg-white border border-slate-200 p-6">
              <h3 className="font-display text-base font-semibold text-primary mb-3 flex items-center gap-2">
                <Icon name="block" size={20} className="text-error" />
                Not eligible
              </h3>
              <ul className="list-disc pl-6 space-y-1.5 text-sm">
                <li>Software licenses with revealed activation key.</li>
                <li>
                  Hygiene-sensitive items (in-ear monitors with broken
                  seal).
                </li>
                <li>Items damaged after delivery by the customer.</li>
              </ul>
            </div>
          </div>

          <div className="bg-surface-container-low p-8">
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Defective on arrival?
            </h2>
            <p>
              If a product arrives broken or stops working within the
              warranty period, that&apos;s a warranty case, not a return.
              Head to{" "}
              <Link
                href="/support/warranty"
                className="text-secondary underline-offset-4 hover:underline"
              >
                /support/warranty
              </Link>{" "}
              — same prepaid label, same 14-day refund cap, but covered
              even after the 14-day withdrawal window has closed.
            </p>
          </div>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

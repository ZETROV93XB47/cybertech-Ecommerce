import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";
import { Icon } from "@/components/ui/Icon";

export const metadata = {
  title: "Support — Cybertech",
  description:
    "Find answers fast: warranty, shipping, returns and a categorised FAQ. Or reach out to our support team directly.",
};

const TILES = [
  {
    href: "/support/warranty",
    icon: "shield",
    title: "Warranty",
    blurb:
      "Statutory and manufacturer coverage explained, plus how to file a claim.",
  },
  {
    href: "/support/shipping",
    icon: "local_shipping",
    title: "Shipping",
    blurb:
      "Carriers, lead times, tracking, customs — and what we do when it goes wrong.",
  },
  {
    href: "/support/returns",
    icon: "undo",
    title: "Returns",
    blurb:
      "14-day right of withdrawal, refund timelines and the return procedure step by step.",
  },
  {
    href: "/support/faq",
    icon: "help",
    title: "FAQ",
    blurb:
      "Quick answers to the questions our support team hears most often.",
  },
];

export default function SupportHubPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Support"
        title="How can we help?"
        lead="Pick the topic closest to your question. Still stuck? Our team replies to every message within one business day."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Support" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {TILES.map((t) => (
              <Link
                key={t.href}
                href={t.href}
                className="group bg-white border border-slate-200 p-8 hover:border-secondary hover:-translate-y-1 transition-all flex flex-col"
              >
                <div className="w-12 h-12 flex items-center justify-center bg-primary text-on-primary mb-6 group-hover:bg-secondary transition-colors">
                  <Icon name={t.icon} size={24} />
                </div>
                <h2 className="font-display text-lg font-semibold text-primary mb-2">
                  {t.title}
                </h2>
                <p className="font-body text-sm text-on-surface-variant leading-relaxed flex-1">
                  {t.blurb}
                </p>
                <span className="inline-flex items-center gap-2 mt-6 text-secondary font-label-caps uppercase tracking-wider text-xs">
                  Read more <Icon name="arrow_forward" size={14} />
                </span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      <section className="pb-24">
        <div className="container mx-auto px-6 md:px-8">
          <div className="bg-surface-container-low p-10 md:p-14 flex flex-col md:flex-row items-center justify-between gap-8">
            <div className="max-w-xl">
              <h2 className="font-display text-headline-sm text-primary mb-3">
                Can&apos;t find what you&apos;re looking for?
              </h2>
              <p className="font-body text-body-md text-on-surface-variant">
                Drop us a line — we read everything and reply within one
                business day, weekdays and weekends covered.
              </p>
            </div>
            <Link
              href="/contact"
              className="bg-primary text-on-primary px-8 py-4 font-label-caps uppercase tracking-wider text-sm hover:bg-slate-800 transition-colors"
            >
              Contact support
            </Link>
          </div>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

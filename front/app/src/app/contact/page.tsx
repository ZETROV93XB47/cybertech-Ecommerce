import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { ContactForm } from "@/components/marketing/ContactForm";
import { CookieBanner } from "@/components/ui/CookieBanner";
import { Icon } from "@/components/ui/Icon";

export const metadata = {
  title: "Contact — Cybertech",
  description:
    "Get in touch with Cybertech support, sales or returns. We reply within one business day.",
};

const CHANNELS = [
  {
    icon: "mail",
    title: "Email",
    detail: "hello@cybertech.local",
    href: "mailto:hello@cybertech.local",
    note: "Best for everything except urgent order issues.",
  },
  {
    icon: "support_agent",
    title: "Support",
    detail: "support@cybertech.local",
    href: "mailto:support@cybertech.local",
    note: "Order, warranty and returns enquiries.",
  },
  {
    icon: "schedule",
    title: "Hours",
    detail: "Mon – Fri, 09:00 – 18:00 CET",
    note: "Replies queued outside hours go out the next morning.",
  },
];

export default function ContactPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Contact"
        title="Talk to a human."
        lead="Tell us what you need and we'll route it to whoever can actually help — usually within one business day."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Contact" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 grid grid-cols-1 lg:grid-cols-3 gap-12">
          <div className="lg:col-span-2">
            <h2 className="font-display text-headline-sm text-primary mb-2">
              Send us a message
            </h2>
            <p className="font-body text-body-md text-on-surface-variant mb-8 max-w-xl">
              We read every message. Include order numbers when relevant so
              we can dig in faster.
            </p>
            <ContactForm />
          </div>

          <aside className="space-y-6">
            <h2 className="font-display text-headline-sm text-primary mb-2">
              Other ways to reach us
            </h2>
            <ul className="flex flex-col gap-4">
              {CHANNELS.map((c) => (
                <li
                  key={c.title}
                  className="border border-slate-200 p-5 bg-white"
                >
                  <div className="flex items-center gap-3 mb-2">
                    <Icon
                      name={c.icon}
                      size={20}
                      className="text-secondary"
                    />
                    <h3 className="font-display text-sm font-semibold text-primary uppercase tracking-wider">
                      {c.title}
                    </h3>
                  </div>
                  {c.href ? (
                    <a
                      href={c.href}
                      className="block font-body text-body-md text-primary hover:text-secondary transition-colors mb-1"
                    >
                      {c.detail}
                    </a>
                  ) : (
                    <p className="font-body text-body-md text-primary mb-1">
                      {c.detail}
                    </p>
                  )}
                  <p className="font-body text-sm text-on-surface-variant">
                    {c.note}
                  </p>
                </li>
              ))}
            </ul>

            <div className="border border-slate-200 p-5 bg-surface-container-low">
              <h3 className="font-display text-sm font-semibold text-primary uppercase tracking-wider mb-2">
                Postal address
              </h3>
              <address className="not-italic font-body text-body-md text-on-surface-variant leading-relaxed">
                Cybertech Industrial
                <br />
                14 quai Saint-Antoine
                <br />
                69002 Lyon, France
              </address>
            </div>
          </aside>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

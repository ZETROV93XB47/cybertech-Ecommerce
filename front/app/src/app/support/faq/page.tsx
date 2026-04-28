import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";
import {
  FaqAccordion,
  type FaqCategory,
} from "@/components/marketing/FaqAccordion";

export const metadata = {
  title: "FAQ — Cybertech",
  description:
    "Quick answers to common questions about orders, shipping, returns, payment and accounts.",
};

const FAQ: FaqCategory[] = [
  {
    title: "Orders",
    items: [
      {
        question: "How do I place an order?",
        answer:
          "Add items to your cart, hit checkout, sign in or guest-checkout, enter your shipping address, pick a payment method, then confirm. You'll receive an order confirmation email immediately.",
      },
      {
        question: "Can I edit or cancel an order after placing it?",
        answer:
          "You can cancel an order from the order details page as long as it hasn't been picked. Once it shows status \"Picked\" or \"Shipped\", contact support@cybertech.local — we'll route you to the carrier or set up a return on arrival.",
      },
      {
        question: "Why was my order declined?",
        answer:
          "Most declines come from the issuing bank, not us. The most common reasons are 3-D Secure timing out and address verification mismatch. Try again with a fresh card or contact your bank for the exact decline code.",
      },
    ],
  },
  {
    title: "Shipping",
    items: [
      {
        question: "How fast will my order ship?",
        answer:
          "Orders placed before 14:00 CET on a business day ship the same day. France standard arrives in 2–4 business days; EU 3–7 days. See /support/shipping for the full breakdown by zone.",
      },
      {
        question: "Do you ship internationally?",
        answer:
          "Yes — across the EU, plus UK and Switzerland. Outside that, we don't ship today; reach out and we'll add you to the early-access list when we expand.",
      },
      {
        question: "My tracking hasn't updated in 48 h. What now?",
        answer:
          "Carriers occasionally batch-update; one quiet day is normal. Past 48 h, drop us a line at support@cybertech.local with your order number and we'll open a carrier investigation immediately.",
      },
    ],
  },
  {
    title: "Returns & Warranty",
    items: [
      {
        question: "How long do I have to return an item?",
        answer:
          "14 days from receipt under the EU right of withdrawal — no reason needed, full refund including outbound shipping. Defective products are covered separately by the 2-year statutory warranty plus the manufacturer warranty shown on each product page.",
      },
      {
        question: "Who pays for return shipping?",
        answer:
          "Inside the EU, we do — we email a prepaid label as soon as your return is approved. Outside the EU, return shipping is covered for warranty cases but on the customer for change-of-mind returns.",
      },
      {
        question: "When do I get my refund?",
        answer:
          "We issue the refund on the original payment method within 14 days of receiving the return. Banks typically process it in 2–5 business days after that.",
      },
    ],
  },
  {
    title: "Payment & Account",
    items: [
      {
        question: "What payment methods do you accept?",
        answer:
          "Visa, Mastercard, American Express, Apple Pay and Google Pay through Stripe. We never see or store the full card number — it's tokenised end-to-end.",
      },
      {
        question: "Can I save a card for next time?",
        answer:
          "Yes, from /account/cards. Saved cards keep only the last four digits and expiry on our side; the rest stays with Stripe. Delete a saved card any time from the same page.",
      },
      {
        question: "I forgot my password — what now?",
        answer:
          "Click \"Sign in\", then \"Forgot password\". You'll get a reset link by email; the link is valid for 60 minutes. Still stuck? Contact support and we'll unblock the account.",
      },
    ],
  },
];

export default function FaqPage() {
  return (
    <MainLayout>
      <PageHeader
        eyebrow="Support"
        title="Frequently Asked"
        lead="Quick answers, grouped by topic. If your question isn't here, we read every email at support@cybertech.local."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Support", href: "/support" },
          { label: "FAQ" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8 max-w-3xl">
          <FaqAccordion categories={FAQ} />

          <div className="mt-16 bg-surface-container-low p-8 text-center">
            <h2 className="font-display text-headline-sm text-primary mb-3">
              Still can&apos;t find an answer?
            </h2>
            <p className="font-body text-body-md text-on-surface-variant mb-6 max-w-xl mx-auto">
              Real humans, no scripted bots. We reply within one business
              day — usually faster.
            </p>
            <Link
              href="/contact"
              className="inline-block bg-primary text-on-primary px-8 py-4 font-label-caps uppercase tracking-wider text-sm hover:bg-slate-800 transition-colors"
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

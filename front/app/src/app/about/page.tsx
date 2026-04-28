import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { AboutHero } from "@/components/marketing/AboutHero";
import { ValueGrid } from "@/components/marketing/ValueGrid";
import { CookieBanner } from "@/components/ui/CookieBanner";

export const metadata = {
  title: "About — Cybertech",
  description:
    "Cybertech is a Lyon-based hardware curator for professionals, creators and enthusiasts who refuse to settle.",
};

const TEAM = [
  {
    name: "Léa Marchand",
    role: "Founder & CEO",
    bio: "Ex-systems engineer at a French aerospace lab. Started Cybertech after a decade of building rigs for friends.",
  },
  {
    name: "Tomás Vega",
    role: "Head of Sourcing",
    bio: "Tracks vendor roadmaps the way collectors track auctions. Personally validates every SKU on the catalog.",
  },
  {
    name: "Aïsha Rahman",
    role: "Customer Engineering",
    bio: "Runs the support desk. Replies before her coffee gets cold; refuses to outsource a single ticket.",
  },
  {
    name: "Jonas Keller",
    role: "Logistics & Returns",
    bio: "Hand-packs flagship orders. Believes the unboxing is part of the product.",
  },
];

export default function AboutPage() {
  return (
    <MainLayout>
      <AboutHero />

      <section className="py-24 bg-white">
        <div className="container mx-auto px-6 md:px-8 grid grid-cols-1 lg:grid-cols-3 gap-12">
          <div className="lg:col-span-1">
            <span className="font-label-caps text-secondary tracking-[0.2em] uppercase text-xs">
              Mission
            </span>
            <h2 className="font-display text-headline-md text-primary mt-3">
              Make great hardware easy to choose.
            </h2>
          </div>
          <div className="lg:col-span-2 font-body text-body-md text-on-surface-variant leading-relaxed space-y-4">
            <p>
              The market is flooded with hardware: dozens of laptops with the
              same chipset, monitors that look identical until you read the
              spec sheets twice, peripherals reviewed by influencers who
              never used them past a single video. We started Cybertech to
              cut through that noise.
            </p>
            <p>
              Every product on this site has been benchmarked, opened, used
              and shipped by us. We list fewer SKUs than the giants on
              purpose: only gear that earns a spot in our own daily setups
              makes the catalog. When something stops being best-in-class,
              we delist it — even if it sells.
            </p>
            <p>
              We are independent and self-funded. We don&apos;t take vendor
              kickbacks, we don&apos;t run paid placements, and we don&apos;t
              hide affiliate fees in product pages. If we&apos;re recommending
              something, it&apos;s because we&apos;d buy it ourselves.
            </p>
          </div>
        </div>
      </section>

      <ValueGrid />

      <section className="py-24 bg-surface-container-low">
        <div className="container mx-auto px-6 md:px-8">
          <div className="text-center mb-12">
            <span className="font-label-caps text-secondary tracking-[0.2em] uppercase text-xs">
              The Team
            </span>
            <h2 className="font-display text-headline-md text-primary mt-3">
              Six people, one shared shelf.
            </h2>
            <p className="text-on-surface-variant font-body text-body-md max-w-2xl mx-auto mt-4">
              We&apos;re a tight team and we like it that way. Here&apos;s who
              you&apos;ll be talking to.
            </p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {TEAM.map((member) => (
              <article
                key={member.name}
                className="bg-white border border-slate-200 p-6"
              >
                <div className="aspect-square bg-slate-100 mb-5" />
                <h3 className="font-display text-base font-semibold text-primary">
                  {member.name}
                </h3>
                <p className="font-label-caps uppercase tracking-widest text-secondary text-xs mt-1 mb-3">
                  {member.role}
                </p>
                <p className="font-body text-sm text-on-surface-variant leading-relaxed">
                  {member.bio}
                </p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="py-20">
        <div className="container mx-auto px-6 md:px-8">
          <div className="bg-primary text-on-primary p-12 flex flex-col md:flex-row items-center gap-8 justify-between">
            <div className="max-w-xl">
              <h2 className="font-display text-headline-sm font-bold mb-3">
                Looking for the right setup?
              </h2>
              <p className="font-body text-slate-300">
                Browse the catalog or reach out — we love putting together
                builds that fit specific workflows.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/products"
                className="bg-white text-primary px-8 py-3 font-label-caps uppercase tracking-wider text-sm hover:bg-slate-100 transition-colors"
              >
                Shop catalog
              </Link>
              <Link
                href="/contact"
                className="border border-white px-8 py-3 font-label-caps uppercase tracking-wider text-sm hover:bg-white/10 transition-colors"
              >
                Contact us
              </Link>
            </div>
          </div>
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

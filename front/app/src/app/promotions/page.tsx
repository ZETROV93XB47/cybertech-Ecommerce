import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { PageHeader } from "@/components/layout/PageHeader";
import { CookieBanner } from "@/components/ui/CookieBanner";
import { Icon } from "@/components/ui/Icon";
import { discountApi } from "@/lib/api";
import type { DiscountContext, DiscountType } from "@/lib/api";
import { formatDate, formatMoney } from "@/lib/format";

export const metadata = {
  title: "Promotions — Cybertech",
  description:
    "Live discount campaigns at Cybertech: limited-time savings on premium hardware and accessories.",
};

const TYPE_LABELS: Record<DiscountType, string> = {
  NO_DISCOUNT: "Standard pricing",
  BLACK_FRIDAY: "Black Friday",
  WINTER_SALES: "Winter Sales",
  SPRING_SALES: "Spring Sales",
  BUY_ONE_GET_ONE_FREE: "Buy One, Get One",
};

const TYPE_BLURB: Record<DiscountType, string> = {
  NO_DISCOUNT:
    "Standard pricing across the catalog — no campaign attached.",
  BLACK_FRIDAY:
    "Our biggest event of the year. Aggressive cuts across high-end builds, monitors and peripherals.",
  WINTER_SALES:
    "End-of-year savings on flagship gear — perfect timing for upgrades or gifts.",
  SPRING_SALES:
    "Seasonal refresh: discounts on new-generation hardware as the year picks up.",
  BUY_ONE_GET_ONE_FREE:
    "Pair up: add two eligible items to your cart and the cheaper one is on us.",
};

const TYPE_ICON: Record<DiscountType, string> = {
  NO_DISCOUNT: "sell",
  BLACK_FRIDAY: "bolt",
  WINTER_SALES: "ac_unit",
  SPRING_SALES: "local_florist",
  BUY_ONE_GET_ONE_FREE: "redeem",
};

function describeAmount(d: DiscountContext): string {
  if (d.calculationType === "PERCENTAGE" && d.percentage) {
    const pct = Math.round(Number(d.percentage));
    return `Up to ${pct}% off`;
  }
  if (d.calculationType === "FIXED_AMOUNT" && d.fixedAmount) {
    return `${formatMoney(d.fixedAmount)} off`;
  }
  if (d.calculationType === "BUY_ONE_GET_ONE_FREE") {
    return "Buy one, get one free";
  }
  return "Limited-time offer";
}

async function loadActive(): Promise<DiscountContext[]> {
  try {
    const all = await discountApi.active();
    return all.filter((d) => d.discountType !== "NO_DISCOUNT");
  } catch {
    return [];
  }
}

export default async function PromotionsPage() {
  const promotions = await loadActive();

  return (
    <MainLayout>
      <PageHeader
        eyebrow="Promotions"
        title="Live campaigns."
        lead="Limited-time savings hand-picked across the catalog. Click any campaign to jump straight into the eligible products."
        breadcrumbs={[
          { label: "Home", href: "/" },
          { label: "Promotions" },
        ]}
      />

      <section className="py-16">
        <div className="container mx-auto px-6 md:px-8">
          {promotions.length === 0 ? (
            <EmptyState />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {promotions.map((promo) => (
                <PromoCard key={promo.discountType} promo={promo} />
              ))}
            </div>
          )}
        </div>
      </section>
      <CookieBanner />
    </MainLayout>
  );
}

function PromoCard({ promo }: { promo: DiscountContext }) {
  const label = TYPE_LABELS[promo.discountType] ?? promo.discountType;
  const blurb = TYPE_BLURB[promo.discountType] ?? "Limited-time offer.";
  const iconName = TYPE_ICON[promo.discountType] ?? "sell";
  const headline = describeAmount(promo);
  const href = `/products?promo=${encodeURIComponent(promo.discountType)}`;

  return (
    <article className="group flex flex-col bg-white border border-slate-200 hover:border-secondary transition-colors">
      <div className="relative bg-primary text-on-primary p-8 overflow-hidden">
        <div className="absolute -top-12 -right-12 w-40 h-40 bg-secondary/30 rounded-full blur-3xl" />
        <Icon name={iconName} size={28} className="text-secondary mb-4" />
        <span className="font-label-caps uppercase tracking-widest text-xs text-slate-300">
          {label}
        </span>
        <h2 className="font-display text-3xl font-bold mt-2">{headline}</h2>
      </div>
      <div className="p-6 flex-1 flex flex-col">
        <p className="font-body text-body-md text-on-surface-variant leading-relaxed flex-1">
          {blurb}
        </p>

        <dl className="grid grid-cols-2 gap-3 mt-6 text-sm">
          {promo.startsAt && (
            <div>
              <dt className="font-label-caps uppercase tracking-widest text-xs text-on-surface-variant">
                Starts
              </dt>
              <dd className="font-display text-primary mt-0.5">
                {formatDate(promo.startsAt)}
              </dd>
            </div>
          )}
          {promo.endsAt && (
            <div>
              <dt className="font-label-caps uppercase tracking-widest text-xs text-on-surface-variant">
                Ends
              </dt>
              <dd className="font-display text-primary mt-0.5">
                {formatDate(promo.endsAt)}
              </dd>
            </div>
          )}
          {promo.minOrderAmount && (
            <div>
              <dt className="font-label-caps uppercase tracking-widest text-xs text-on-surface-variant">
                Min. order
              </dt>
              <dd className="font-display text-primary mt-0.5">
                {formatMoney(promo.minOrderAmount)}
              </dd>
            </div>
          )}
          {promo.maxDiscountAmount && (
            <div>
              <dt className="font-label-caps uppercase tracking-widest text-xs text-on-surface-variant">
                Capped at
              </dt>
              <dd className="font-display text-primary mt-0.5">
                {formatMoney(promo.maxDiscountAmount)}
              </dd>
            </div>
          )}
        </dl>

        <Link
          href={href}
          className="mt-6 inline-flex items-center justify-center gap-2 bg-primary text-on-primary py-3 px-6 font-label-caps uppercase tracking-wider text-xs hover:bg-slate-800 transition-colors"
        >
          Shop now <Icon name="arrow_forward" size={14} />
        </Link>
      </div>
    </article>
  );
}

function EmptyState() {
  return (
    <div className="border border-dashed border-slate-300 bg-white p-16 text-center max-w-2xl mx-auto">
      <div className="w-14 h-14 mx-auto mb-6 flex items-center justify-center bg-slate-100 text-on-surface-variant">
        <Icon name="sell" size={28} />
      </div>
      <h2 className="font-display text-headline-sm text-primary mb-3">
        No promotions running today.
      </h2>
      <p className="font-body text-body-md text-on-surface-variant max-w-md mx-auto mb-8">
        We run a handful of campaigns each year — Black Friday, Winter and
        Spring sales, plus the occasional flash drop. Check back soon, or
        browse the full catalog at full price.
      </p>
      <Link
        href="/products"
        className="inline-flex items-center gap-2 bg-primary text-on-primary px-8 py-4 font-label-caps uppercase tracking-wider text-sm hover:bg-slate-800 transition-colors"
      >
        Browse catalog <Icon name="arrow_forward" size={14} />
      </Link>
    </div>
  );
}

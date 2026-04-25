import Link from "next/link";
import { MainLayout } from "@/components/layout/MainLayout";
import { ProductCard } from "@/components/product/ProductCard";
import { Icon } from "@/components/ui/Icon";
import { productApi, discountApi } from "@/lib/api";
import type { ProductResponseDto, DiscountContext } from "@/lib/api";

/**
 * Home page — Server Component. Fetches best-sellers and active discount
 * campaigns in parallel. If the backend is down, both calls fail silently
 * and the page degrades to its static sections (hero, bento grid, brands).
 */

const CATEGORY_TILES = [
  {
    href: "/products?category=COMPUTER",
    title: "High-End Builds",
    blurb: "Ultimate performance for creators and gamers alike.",
    cta: "Shop Builds",
    span: "md:col-span-8",
    headlineSize: "headline-md",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuCmGt2DTuZYtQDwPGTbg_18BjJGe_IBQlgbh2BcQlchL2saFppmAmElSqRArGuPWlCRJRA1bkCIE62bp-lweP532UFemr11_ck1MI0ZiyTg160-_kyEsMbggnf-zOP0QiDHjzaiaKamBy4_0lyjzRASXhUrJBx2KvdDS3XG0WeoupolxC2bbUcU8v91iAD0N5MEqVKGGKK5zEHGk308Z4y1jz3TFbj6Tf_DUFFaw78878Pa9RBBd5oYuSAW-ouSIvBC5c4K1hzDhOog",
    alt: "High-performance custom gaming PC with glowing RGB",
  },
  {
    href: "/products?category=MONITOR",
    title: "Monitors",
    cta: "Explore",
    span: "md:col-span-4",
    headlineSize: "headline-sm",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuCYbJ9_PzqGF31f1xyhZdaI5qOTe9A9KKU5_MOum7lYpkB8wKIhWgq3jgFOyIJ2fLDAd0QW4WY2CaKPBI7bgxLvujkpC7M0duQ6NhdfXfgcRpT6ckkIco28--EpNnzYMl9ydYQpyawQ3QvDB5DzaQLDCd2cr6Hhh-aGs9ZPZe4IVHCMrzVuRp-eE27TeynGxqgAriVhu0WGXa9j41NlM2Ql8zoTRYn0mW1-r51cz0b-pjy5loZqny-LXdcElPW0h_naxNbd-OmCgnR4",
    alt: "Ultra-wide professional curved monitor",
  },
  {
    href: "/products?category=KEYBOARD",
    title: "Peripherals",
    cta: "Browse",
    span: "md:col-span-4",
    headlineSize: "headline-sm",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuCRTB592bmycFSvzi4ipbJfTFiPcLFBwP6XyIq1N6u3XvH__UKjCmLzxtChP-4v-My06DfZ3_NbP97kBT-V2YyxOBQ4_DgpwTGr1VQo7YCuTcyZzBUOI_EkoCIbZeZlpBV7BcZYkpkrcmb_85Joj8I3xM2eluD46UDAopZk7NYSoKcCW9oIfiHzGhOA_ivnoLiYiAkGOfcbU782amkdtBu6f8A8xeUUXUi6yx2jP8WsT6iqUnQem7Vmzkh5J6bNW1HmhFGsyB1wsizA",
    alt: "Mechanical keyboard with custom keycaps",
  },
  {
    href: "/products?category=SMARTPHONE",
    title: "Smartphones",
    cta: "Latest Models",
    span: "md:col-span-8",
    headlineSize: "headline-md",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuDMpPFrGYNktIQ3wkWhWnl0uRqZU6KeB8cPVW_o7iptwEp9l2dW0uXKk0MSkqZcQW0s1o5_JY4LKJrXE83p8sjMdZKXbQfYerznpyAojDnBnPl0fcyOmbZuZcOHyFI7aWl7VtNv7P1DMec5XRxrnr4ZbO6VZcpCyZ0OEmXqN3AS441HDOgtoBPfhnkpuwpf5ost_wKGzlCYIyt6gtVc2v1LnWupFcTWn5NfHqPdBA-RhnnBq_9loQPJW1RDj6njW4M2IBZBZMUechD2",
    alt: "Modern smartphone on a reflective surface",
  },
];

const BRAND_STRIP = ["APPLE", "SAMSUNG", "ASUS", "LOGITECH", "RAZER", "SONY"];

async function loadHomeData(): Promise<{
  bestSellers: ProductResponseDto[];
  promo: DiscountContext | null;
}> {
  const [bestSellersResult, discountsResult] = await Promise.allSettled([
    productApi.bestSellers(0, 4),
    discountApi.active(),
  ]);

  const bestSellers =
    bestSellersResult.status === "fulfilled"
      ? bestSellersResult.value.content
      : [];

  const promoSource =
    discountsResult.status === "fulfilled" ? discountsResult.value : [];
  const promo =
    promoSource.find(
      (d) =>
        d.calculationType === "PERCENTAGE" &&
        d.discountType !== "NO_DISCOUNT" &&
        d.percentage !== null,
    ) ??
    promoSource.find((d) => d.discountType !== "NO_DISCOUNT") ??
    null;

  return { bestSellers, promo };
}

function PromoCopy({ promo }: { promo: DiscountContext | null }) {
  if (!promo) {
    return {
      heading: "Winter Tech Solstice",
      copy: "Up to 30% off across all high-performance hardware and accessories.",
    };
  }
  const niceName = promo.discountType
    .toLowerCase()
    .split("_")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
  const heading =
    promo.discountType === "BLACK_FRIDAY"
      ? "Black Friday: hardware reimagined"
      : `${niceName} is live`;
  const pct = promo.percentage ? Math.round(Number(promo.percentage)) : null;
  const copy = pct
    ? `Up to ${pct}% off across our most demanded builds, monitors, and peripherals.`
    : "Curated savings on the gear our community can't stop talking about.";
  return { heading, copy };
}

export default async function HomePage() {
  const { bestSellers, promo } = await loadHomeData();
  const promoCopy = PromoCopy({ promo });

  return (
    <MainLayout>
      {/* Hero */}
      <section className="relative min-h-[640px] lg:min-h-[820px] flex items-center overflow-hidden hero-gradient">
        <div className="container mx-auto px-6 md:px-8 grid grid-cols-1 lg:grid-cols-2 gap-12 items-center">
          <div className="space-y-6 z-10">
            <span className="font-label-caps text-secondary tracking-[0.2em] uppercase">
              The New Standard
            </span>
            <h1 className="font-display text-[clamp(40px,6vw,72px)] leading-[1.05] font-bold text-primary mb-2">
              MacBook Pro.
              <br />
              Mind-blowing.
              <br />
              Head-turning.
            </h1>
            <p className="font-body text-body-lg text-on-surface-variant max-w-lg">
              Engineered for the most demanding workflows with M3 Max chips,
              now in a stunning Space Black finish.
            </p>
            <div className="flex flex-wrap items-center gap-4 pt-4">
              <Link
                href="/products"
                className="bg-primary text-on-primary px-10 py-4 font-display text-sm font-semibold rounded-none hover:bg-slate-800 transition-all"
              >
                Buy Now
              </Link>
              <Link
                href="/products"
                className="border border-outline px-10 py-4 font-display text-sm font-semibold rounded-none hover:bg-slate-50 transition-all"
              >
                Learn More
              </Link>
            </div>
          </div>
          <div className="relative h-[400px] lg:h-[600px] flex justify-center items-center">
            <div className="absolute w-[120%] h-[120%] bg-blue-50/50 rounded-full blur-3xl -z-10" />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              alt="MacBook Pro on a clean desk with studio lighting"
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuAQWSgj9eo1NWYIzGZYcGzE1A6KO94CqhIt5bfR6HsPnnliFklDl6bUEOC_8aoC75RHXpo6D7yA3jMxkofZWfXMoFvLfTAfraAHF5xS-9dIoUUcujlWyU-nRqYhO5alQtB7MT4bDAROm2Rqt_ViyCE1aDRYxLqYz7oSkM8Vx8EKUjaQ4NExrSWdzL7qcwq45ZDf5mkm8AjFjOx6XjemRbUgQFCDDtLqdllqQH_gfBHpLwUZo4hUS9dsYUj15bgHvReYebq9mj1Icfmm"
              className="w-full h-auto object-contain drop-shadow-2xl"
            />
          </div>
        </div>
      </section>

      {/* Category bento */}
      <section className="py-24 bg-white">
        <div className="container mx-auto px-6 md:px-8">
          <div className="flex justify-between items-end mb-12">
            <div>
              <h2 className="font-display text-headline-md text-primary mb-2">
                Featured Categories
              </h2>
              <p className="text-on-surface-variant font-body text-body-md">
                Explore high-performance hardware selected for professionals.
              </p>
            </div>
            <Link
              href="/products"
              className="hidden sm:inline-flex text-secondary font-label-caps uppercase items-center gap-2 hover:underline"
            >
              View All <Icon name="arrow_forward" size={16} />
            </Link>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-12 gap-6 h-auto md:h-[600px]">
            {CATEGORY_TILES.map((tile) => (
              <Link
                key={tile.href}
                href={tile.href}
                className={`${tile.span} group relative overflow-hidden bg-slate-100 block h-64 md:h-auto`}
              >
                <div className="absolute inset-0 z-0 opacity-80 group-hover:scale-105 transition-transform duration-700">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={tile.image}
                    alt={tile.alt}
                    className="w-full h-full object-cover"
                  />
                </div>
                <div className="relative z-10 p-8 flex flex-col justify-end h-full bg-gradient-to-t from-slate-900/60 to-transparent">
                  <h3
                    className={`text-white ${
                      tile.headlineSize === "headline-md"
                        ? "font-display text-headline-md"
                        : "font-display text-headline-sm"
                    }`}
                  >
                    {tile.title}
                  </h3>
                  {tile.blurb && (
                    <p className="text-slate-200 max-w-xs mb-4 mt-2">
                      {tile.blurb}
                    </p>
                  )}
                  <span
                    className={
                      tile.span.includes("col-span-8")
                        ? "bg-white text-primary w-fit px-6 py-2 mt-2 text-sm font-label-caps uppercase"
                        : "text-white inline-flex items-center gap-2 text-sm font-label-caps mt-2 uppercase"
                    }
                  >
                    {tile.cta}
                    {!tile.span.includes("col-span-8") && (
                      <Icon name="arrow_forward" size={14} />
                    )}
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Best sellers */}
      <section className="py-24 bg-surface-container-low">
        <div className="container mx-auto px-6 md:px-8">
          <div className="text-center mb-16">
            <h2 className="font-display text-headline-md text-primary mb-4">
              The Precision Collection
            </h2>
            <p className="text-on-surface-variant font-body text-body-md max-w-2xl mx-auto">
              Our most popular hardware, curated for those who settle for
              nothing but the best.
            </p>
          </div>

          {bestSellers.length === 0 ? (
            <div className="text-center text-on-surface-variant py-12">
              <p>
                Best-sellers will appear here as soon as the catalog is online.
              </p>
              <Link
                href="/products"
                className="inline-flex items-center gap-2 mt-6 text-secondary font-label-caps uppercase hover:underline"
              >
                Browse all products <Icon name="arrow_forward" size={14} />
              </Link>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8">
              {bestSellers.map((p) => (
                <ProductCard key={p.uuid} product={p} />
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Promo banner */}
      <section className="py-12">
        <div className="container mx-auto px-6 md:px-8">
          <div className="relative bg-primary text-on-primary p-8 md:p-12 overflow-hidden flex flex-col md:flex-row justify-between items-center gap-8">
            <div className="absolute top-0 right-0 w-64 h-64 bg-secondary/20 rounded-full blur-[100px]" />
            <div className="z-10 text-center md:text-left">
              <h2 className="font-display text-display-lg text-3xl md:text-4xl mb-3 font-bold">
                {promoCopy.heading}
              </h2>
              <p className="font-body text-body-lg text-slate-300">
                {promoCopy.copy}
              </p>
            </div>
            <Link
              href="/products"
              className="z-10 bg-white text-primary px-12 py-4 font-label-caps uppercase tracking-wider hover:bg-slate-100 transition-all whitespace-nowrap"
            >
              Explore Deals
            </Link>
          </div>
        </div>
      </section>

      {/* Brand strip */}
      <section className="py-24 border-t border-slate-100">
        <div className="container mx-auto px-6 md:px-8">
          <div className="flex flex-wrap justify-between items-center gap-12 opacity-40 grayscale hover:grayscale-0 transition-all duration-500">
            {BRAND_STRIP.map((brand) => (
              <span
                key={brand}
                className="text-3xl font-bold font-display tracking-wider"
              >
                {brand}
              </span>
            ))}
          </div>
        </div>
      </section>
    </MainLayout>
  );
}

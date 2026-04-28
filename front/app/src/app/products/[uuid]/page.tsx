import Link from "next/link";
import Image from "next/image";
import { notFound } from "next/navigation";
import { MainLayout } from "@/components/layout/MainLayout";
import { ProductActions } from "@/components/product/ProductActions";
import { Breadcrumbs } from "@/components/ui/Breadcrumbs";
import { Icon } from "@/components/ui/Icon";
import { auth } from "@/lib/auth";
import { ApiError, productApi, reviewApi } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import type { ReviewableProductDto } from "@/lib/api";

/**
 * Product detail page (Server Component). Next 16 makes `params` a Promise.
 * Fetches the product publicly; for signed-in users, it also fetches the
 * "reviewable" list to gate the Write-a-review CTA on whether the user has
 * actually purchased the product (PAID/SHIPPED/DELIVERED, not yet reviewed).
 */

export default async function ProductDetailPage({
  params,
}: {
  params: Promise<{ uuid: string }>;
}) {
  const { uuid } = await params;
  const [productResult, session] = await Promise.all([
    productApi.get(uuid).catch((err) => {
      if (err instanceof ApiError && err.status === 404) return null;
      throw err;
    }),
    auth(),
  ]);
  if (!productResult) notFound();
  const product = productResult;

  let reviewable: ReviewableProductDto | null = null;
  if (session) {
    try {
      const list = await reviewApi.reviewable();
      reviewable = list.find((r) => r.productUuid === uuid) ?? null;
    } catch {
      // 401/403 is fine — keeps CTA hidden.
    }
  }

  const photo = product.photoUrl?.trim() ? product.photoUrl : null;
  const attributeEntries = Object.entries(product.attributes ?? {})
    .filter(([, v]) => v !== null && v !== "" && v !== undefined)
    .slice(0, 8);

  return (
    <MainLayout>
      <div className="max-w-[1440px] mx-auto px-margin-mobile md:px-margin-desktop pt-12 pb-section-gap">
        <Breadcrumbs
          className="mb-8"
          items={[
            { label: "Home", href: "/" },
            { label: "Products", href: "/products" },
            {
              label: product.category,
              href: `/products?category=${encodeURIComponent(product.category)}`,
            },
            { label: product.name },
          ]}
        />

        <section className="grid grid-cols-1 lg:grid-cols-12 gap-12">
          {/* Gallery — backend gives us one photoUrl. We render the hero plus a
              ghost grid of secondary tiles to preserve the bento composition. */}
          <div className="lg:col-span-7 grid grid-cols-4 grid-rows-4 gap-4 aspect-square lg:aspect-auto lg:h-[700px]">
            <div className="col-span-4 row-span-3 rounded-xl overflow-hidden bg-surface-container-low relative">
              {photo ? (
                /* eslint-disable-next-line @next/next/no-img-element */
                <img
                  src={photo}
                  alt={product.name}
                  className="w-full h-full object-cover"
                />
              ) : (
                <Image
                  src="/product-placeholder.svg"
                  alt={product.name}
                  fill
                  unoptimized
                  className="object-contain p-12"
                />
              )}
            </div>
            {[0, 1, 2, 3].map((i) => (
              <div
                key={i}
                className="col-span-1 row-span-1 rounded-xl overflow-hidden border border-slate-200 bg-surface-container-lowest flex items-center justify-center"
              >
                <Icon name="photo_camera" size={24} className="text-outline-variant" />
              </div>
            ))}
          </div>

          {/* Info column */}
          <div className="lg:col-span-5 flex flex-col">
            <div className="mb-2">
              <span className="font-label-caps uppercase tracking-widest text-secondary bg-secondary/10 px-3 py-1 rounded-full">
                {product.brand}
              </span>
            </div>
            <h1 className="font-display text-[clamp(32px,4vw,48px)] leading-tight font-bold text-primary mb-2">
              {product.name}
            </h1>

            <div className="mb-8">
              <div className="font-display font-semibold text-headline-md text-primary">
                {formatMoney(product.price)}
              </div>
              {product.description && (
                <p className="text-on-surface-variant mt-4 font-body text-body-lg leading-relaxed">
                  {product.description}
                </p>
              )}
            </div>

            {attributeEntries.length > 0 && (
              <div className="border-t border-slate-200 pt-6 mb-8">
                <h2 className="font-label-caps uppercase text-on-surface-variant mb-4">
                  Technical Specifications
                </h2>
                <dl className="grid grid-cols-2 gap-y-4">
                  {attributeEntries.map(([key, value]) => (
                    <div key={key} className="flex flex-col">
                      <dt className="font-label-caps uppercase text-slate-400">
                        {humanise(key)}
                      </dt>
                      <dd className="font-body text-primary">
                        {String(value)}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
            )}

            <div className="mt-auto">
              <ProductActions productUuid={product.uuid} />
            </div>
          </div>
        </section>

        {/* Reviews — backend currently exposes only single-review GET, no
            list-by-product endpoint, so we render an empty state and gate the
            Write-a-review CTA on /review/reviewable. */}
        <section className="mt-section-gap">
          <div className="flex items-end justify-between mb-8 border-b border-slate-200 pb-6">
            <div>
              <h2 className="font-display font-semibold text-headline-md text-primary">
                Verified Reviews
              </h2>
              <p className="text-on-surface-variant">
                What our technical community thinks
              </p>
            </div>
            {reviewable ? (
              <Link
                href={`/account/orders/${reviewable.orderUuid}`}
                className="text-secondary font-display font-semibold flex items-center gap-2 hover:underline"
              >
                Write a review <Icon name="edit" size={18} />
              </Link>
            ) : null}
          </div>
          <div className="rounded-2xl bg-surface-container-low p-12 text-center text-on-surface-variant">
            <p className="font-body">
              Reviews for this product will appear here once they&apos;re available.
            </p>
            {reviewable ? (
              <p className="mt-2 text-label-caps uppercase tracking-widest text-secondary">
                You can review this product from your order history.
              </p>
            ) : null}
          </div>
        </section>
      </div>
    </MainLayout>
  );
}

function humanise(key: string): string {
  return key
    .replace(/[_-]/g, " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

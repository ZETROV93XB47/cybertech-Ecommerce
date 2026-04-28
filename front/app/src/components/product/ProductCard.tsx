import Link from "next/link";
import Image from "next/image";
import { formatMoney } from "@/lib/format";
import type { ProductResponseDto } from "@/lib/api";
import { AddToCartButton } from "./AddToCartButton";
import { WishlistHeartButton } from "./WishlistHeartButton";

/**
 * The product card pattern from cybertech_home_page/code.html and product_catalog/code.html:
 * white tile with rounded-2xl corners, square product photo on a slate-50
 * wash, brand label-caps in secondary blue, name in headline-sm, price on the
 * baseline and a generous full-width Add to cart CTA below. A wishlist heart
 * floats top-right over the image. Hover: shadow lift + image zoom.
 *
 * Stays a Server Component — interactive bits (heart toggle, add-to-cart
 * pending state) are isolated in `WishlistHeartButton` and `AddToCartButton`.
 */
export function ProductCard({
  product,
  initialWishlisted = false,
}: {
  product: ProductResponseDto;
  initialWishlisted?: boolean;
}) {
  const detailHref = `/products/${product.uuid}`;
  const photo = product.photoUrl?.trim()
    ? product.photoUrl
    : "/product-placeholder.svg";

  return (
    <article className="group bg-surface-container-lowest border border-outline-variant/30 rounded-2xl overflow-hidden hover:shadow-[0_12px_40px_rgb(0,0,0,0.08)] hover:-translate-y-0.5 transition-all duration-300 flex flex-col">
      <div className="relative">
        <Link
          href={detailHref}
          className="block aspect-square bg-slate-50 overflow-hidden p-8 flex items-center justify-center rounded-t-2xl"
          aria-label={`View ${product.name}`}
        >
          {/* unoptimized for now — backend uses S3 URLs we haven't whitelisted in next.config.ts */}
          <Image
            src={photo}
            alt={product.name}
            width={400}
            height={400}
            unoptimized
            className="w-full h-full object-contain group-hover:scale-110 transition-transform duration-500"
          />
        </Link>
        <WishlistHeartButton
          productUuid={product.uuid}
          initialWishlisted={initialWishlisted}
        />
      </div>

      <div className="p-6 flex flex-col flex-grow">
        {product.brand && (
          <span className="font-label-caps uppercase text-secondary mb-2 block">
            {product.brand}
          </span>
        )}
        <h3 className="font-display text-base font-semibold mb-3 line-clamp-2 min-h-[3rem]">
          <Link
            href={detailHref}
            className="hover:text-secondary transition-colors"
          >
            {product.name}
          </Link>
        </h3>
        <div className="mt-auto">
          <div className="flex justify-between items-baseline mb-4">
            <span className="font-display text-headline-sm">
              {formatMoney(product.price)}
            </span>
          </div>
          <AddToCartButton
            productUuid={product.uuid}
            productName={product.name}
          />
        </div>
      </div>
    </article>
  );
}

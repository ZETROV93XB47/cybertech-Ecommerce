import Link from "next/link";
import Image from "next/image";
import { Icon } from "@/components/ui/Icon";
import { formatMoney } from "@/lib/format";
import type { ProductResponseDto } from "@/lib/api";

/**
 * The product card pattern from cybertech_home_page/code.html and product_catalog/code.html:
 * white tile, square product photo on a slate-50 wash, brand label-caps in
 * secondary blue, name in headline-sm, price + dark circular CTA at the
 * baseline. Hover: shadow lift + image zoom.
 */
export function ProductCard({ product }: { product: ProductResponseDto }) {
  const detailHref = `/products/${product.uuid}`;
  const photo = product.photoUrl?.trim()
    ? product.photoUrl
    : "/product-placeholder.svg";

  return (
    <article className="group bg-white border border-slate-100 hover:shadow-xl transition-all duration-300">
      <Link
        href={detailHref}
        className="block aspect-square bg-slate-50 overflow-hidden p-8 flex items-center justify-center relative"
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

      <div className="p-6">
        {product.brand && (
          <span className="font-label-caps uppercase text-secondary mb-2 block">
            {product.brand}
          </span>
        )}
        <h3 className="font-display text-base font-semibold mb-4 line-clamp-2 min-h-[3rem]">
          <Link href={detailHref} className="hover:text-secondary transition-colors">
            {product.name}
          </Link>
        </h3>
        <div className="flex justify-between items-center">
          <span className="font-display text-headline-sm">
            {formatMoney(product.price)}
          </span>
          <button
            type="button"
            aria-label={`Add ${product.name} to cart`}
            className="p-2 bg-slate-900 text-white rounded-full hover:bg-secondary transition-colors"
          >
            <Icon name="add_shopping_cart" size={20} />
          </button>
        </div>
      </div>
    </article>
  );
}

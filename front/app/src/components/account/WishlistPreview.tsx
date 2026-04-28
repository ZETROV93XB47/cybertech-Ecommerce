import Link from "next/link";
import Image from "next/image";
import { Icon } from "@/components/ui/Icon";
import { formatMoney } from "@/lib/format";
import type { WishlistResponseDto } from "@/lib/api";

/**
 * Up to four wishlist entries, displayed as compact cards with the
 * associated product photo. Designed to live in a 2- or 4-column grid
 * inside /account/profile next to the orders block.
 */

interface WishlistPreviewProps {
  items: WishlistResponseDto[];
  totalElements: number;
  errored: boolean;
}

export function WishlistPreview({
  items,
  totalElements,
  errored,
}: WishlistPreviewProps) {
  return (
    <section
      aria-labelledby="wishlist-preview-heading"
      className="bg-white border border-slate-100"
    >
      <header className="flex items-end justify-between gap-4 p-6 border-b border-slate-100">
        <div>
          <h2
            id="wishlist-preview-heading"
            className="font-display text-headline-sm text-primary"
          >
            Wishlist
          </h2>
          <p className="font-body text-sm text-on-surface-variant mt-1">
            {totalElements > 0
              ? `${totalElements} item${totalElements === 1 ? "" : "s"} saved for later.`
              : "Save products to revisit them anytime."}
          </p>
        </div>
        <Link
          href="/account/wishlist"
          className="hidden sm:inline-flex items-center gap-2 text-secondary font-label-caps uppercase tracking-wider hover:underline"
        >
          View all <Icon name="arrow_forward" size={14} />
        </Link>
      </header>

      <div className="p-6">
        {errored ? (
          <p className="font-body text-sm text-on-surface-variant">
            We couldn&apos;t load your wishlist. Please try again later.
          </p>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center text-center gap-3 py-8">
            <div className="w-14 h-14 rounded-full bg-surface-container-low flex items-center justify-center">
              <Icon name="favorite" size={28} className="text-outline" />
            </div>
            <p className="font-body text-on-surface-variant max-w-md">
              You haven&apos;t favourited anything yet. Tap the heart on any
              product to drop it here.
            </p>
            <Link
              href="/products"
              className="inline-flex items-center gap-2 text-secondary font-label-caps uppercase tracking-wider hover:underline"
            >
              Browse catalogue <Icon name="arrow_forward" size={14} />
            </Link>
          </div>
        ) : (
          <ul className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {items.map((entry) => {
              const product = entry.product;
              const photo = product.photoUrl?.trim()
                ? product.photoUrl
                : "/product-placeholder.svg";
              return (
                <li key={entry.uuid}>
                  <Link
                    href={`/products/${product.uuid}`}
                    className="group flex flex-col gap-3 bg-surface-container-low border border-slate-100 hover:shadow-md transition-shadow"
                  >
                    <div className="aspect-square bg-white overflow-hidden flex items-center justify-center p-4">
                      <Image
                        src={photo}
                        alt={product.name}
                        width={240}
                        height={240}
                        unoptimized
                        className="w-full h-full object-contain group-hover:scale-105 transition-transform duration-500"
                      />
                    </div>
                    <div className="px-4 pb-4 space-y-1">
                      {product.brand && (
                        <span className="font-label-caps uppercase text-secondary block tracking-wider">
                          {product.brand}
                        </span>
                      )}
                      <p className="font-display text-sm font-semibold line-clamp-2 min-h-[2.5rem]">
                        {product.name}
                      </p>
                      <span className="font-display text-base font-semibold text-primary">
                        {formatMoney(product.price)}
                      </span>
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}

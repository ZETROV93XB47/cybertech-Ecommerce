/**
 * Skeleton matching `ProductCard` shape exactly so swap-in doesn't shift layout.
 *
 *   {loading
 *     ? Array.from({ length: 8 }).map((_, i) => <ProductCardSkeleton key={i} />)
 *     : products.map((p) => <ProductCard key={p.uuid} product={p} />)}
 */
export function ProductCardSkeleton() {
  return (
    <article
      aria-hidden="true"
      className="bg-surface-container-lowest border border-outline-variant/30 rounded-2xl overflow-hidden flex flex-col"
    >
      {/* Square image area, mirrors ProductCard's aspect-square + p-8 */}
      <div className="aspect-square bg-slate-100 animate-pulse" />

      <div className="p-6 flex flex-col flex-grow">
        {/* Brand label-caps */}
        <div className="h-3 w-16 bg-slate-200/80 rounded animate-pulse mb-3" />
        {/* Title — two lines, min-h-[3rem] like the real card */}
        <div className="space-y-2 mb-3 min-h-[3rem]">
          <div className="h-4 w-full bg-slate-200/80 rounded animate-pulse" />
          <div className="h-4 w-2/3 bg-slate-200/80 rounded animate-pulse" />
        </div>

        <div className="mt-auto">
          {/* Price row */}
          <div className="flex justify-between items-baseline mb-4">
            <div className="h-6 w-24 bg-slate-200/80 rounded animate-pulse" />
          </div>
          {/* Add to cart CTA */}
          <div className="h-11 w-full bg-slate-200/80 rounded animate-pulse" />
        </div>
      </div>
    </article>
  );
}

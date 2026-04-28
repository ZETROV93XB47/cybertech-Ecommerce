/**
 * Skeleton for a row in the customer "My Orders" list. Shape: small thumbnail
 * + order id + date + status pill + total. Render multiple in a column.
 *
 *   <ul>
 *     {Array.from({ length: 4 }).map((_, i) => (
 *       <OrderRowSkeleton key={i} />
 *     ))}
 *   </ul>
 */
export function OrderRowSkeleton() {
  return (
    <li
      aria-hidden="true"
      className="flex items-center gap-4 p-4 bg-surface-container-lowest border border-outline-variant/30 rounded-lg"
    >
      {/* Thumbnail placeholder */}
      <div className="shrink-0 w-16 h-16 rounded bg-slate-100 animate-pulse" />

      <div className="flex-1 min-w-0 space-y-2">
        {/* Order id */}
        <div className="h-4 w-40 bg-slate-200/80 rounded animate-pulse" />
        {/* Date / line count */}
        <div className="h-3 w-28 bg-slate-200/80 rounded animate-pulse" />
      </div>

      {/* Status pill */}
      <div className="hidden sm:block h-6 w-20 bg-slate-200/80 rounded-full animate-pulse" />

      {/* Total */}
      <div className="h-5 w-16 bg-slate-200/80 rounded animate-pulse" />
    </li>
  );
}

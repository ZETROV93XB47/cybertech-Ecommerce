/**
 * Generic admin-table row skeleton. Renders one `<tr>` of pulsing cells —
 * caller controls the number of columns. Wrap N of these in a `<tbody>`.
 *
 *   <tbody>
 *     {Array.from({ length: 5 }).map((_, i) => (
 *       <TableRowSkeleton key={i} columns={4} />
 *     ))}
 *   </tbody>
 */
type TableRowSkeletonProps = {
  columns?: number;
  className?: string;
};

export function TableRowSkeleton({
  columns = 4,
  className,
}: TableRowSkeletonProps) {
  return (
    <tr
      aria-hidden="true"
      className={["border-b border-outline-variant/30", className ?? ""]
        .join(" ")
        .trim()}
    >
      {Array.from({ length: columns }).map((_, i) => (
        <td key={i} className="px-4 py-4">
          <div
            className={[
              "h-4 bg-slate-200/80 rounded animate-pulse",
              // First column slightly wider, last narrower to look "real"
              i === 0 ? "w-3/4" : i === columns - 1 ? "w-1/3" : "w-1/2",
            ].join(" ")}
          />
        </td>
      ))}
    </tr>
  );
}

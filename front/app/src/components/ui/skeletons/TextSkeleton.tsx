/**
 * Generic text/line skeleton. Renders `lines` rows of `animate-pulse` blocks.
 * The last row uses 3/4 width to look like a wrapped paragraph.
 *
 *   <TextSkeleton />              // 1 line, full width
 *   <TextSkeleton lines={3} />    // 3 lines paragraph
 *   <TextSkeleton width="40%" />  // single fixed-width line (e.g. label)
 */
type TextSkeletonProps = {
  lines?: number;
  /** CSS width — applied to every line when `lines === 1`, otherwise ignored. */
  width?: string;
  /** Tailwind height class (default `h-4`). */
  height?: string;
  className?: string;
};

export function TextSkeleton({
  lines = 1,
  width,
  height = "h-4",
  className,
}: TextSkeletonProps) {
  if (lines <= 1) {
    return (
      <div
        className={[
          "animate-pulse rounded bg-slate-200/80",
          height,
          className ?? "",
        ]
          .join(" ")
          .trim()}
        style={width ? { width } : undefined}
      />
    );
  }
  return (
    <div className={`space-y-2 ${className ?? ""}`.trim()}>
      {Array.from({ length: lines }).map((_, i) => (
        <div
          key={i}
          className={[
            "animate-pulse rounded bg-slate-200/80",
            height,
            i === lines - 1 ? "w-3/4" : "w-full",
          ].join(" ")}
        />
      ))}
    </div>
  );
}

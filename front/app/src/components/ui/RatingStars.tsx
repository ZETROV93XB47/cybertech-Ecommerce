import { Icon } from "./Icon";

/**
 * Rating in 0–5. Backend doesn't expose aggregate ratings on ProductResponseDto,
 * so we accept `null` and render a "no ratings yet" placeholder.
 */
export function RatingStars({
  value,
  count,
  size = 18,
  showCount = true,
}: {
  value: number | null;
  count?: number;
  size?: number;
  showCount?: boolean;
}) {
  if (value === null || value === undefined) {
    return (
      <span className="text-on-surface-variant text-label-caps">No ratings yet</span>
    );
  }

  const full = Math.floor(value);
  const half = value - full >= 0.5 ? 1 : 0;
  const empty = 5 - full - half;

  return (
    <div className="flex items-center gap-2">
      <div className="flex text-amber-400" aria-label={`Rated ${value} out of 5`}>
        {Array.from({ length: full }).map((_, i) => (
          <Icon key={`f-${i}`} name="star" size={size} filled />
        ))}
        {half === 1 && <Icon name="star_half" size={size} filled />}
        {Array.from({ length: empty }).map((_, i) => (
          <Icon key={`e-${i}`} name="star" size={size} />
        ))}
      </div>
      {showCount && count !== undefined && (
        <span className="text-label-caps text-on-surface-variant">({count})</span>
      )}
    </div>
  );
}

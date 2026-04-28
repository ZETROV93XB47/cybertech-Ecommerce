/**
 * Minimal SVG spinner for inline pending states (button submit, async fetch).
 * Pure presentational — no client interactivity. Inherits `currentColor`,
 * so style with text-* utilities (e.g. `text-on-primary`).
 *
 *   <Spinner size={16} />
 *   <button disabled>{pending && <Spinner size={14} className="mr-2" />}Save</button>
 */
type SpinnerProps = {
  size?: number;
  className?: string;
  /** Accessible label. Set to empty string when paired with adjacent text. */
  label?: string;
};

export function Spinner({ size = 20, className, label = "Loading" }: SpinnerProps) {
  return (
    <svg
      role="status"
      aria-label={label || undefined}
      aria-hidden={label ? undefined : true}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`animate-spin ${className ?? ""}`.trim()}
    >
      <circle
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        strokeOpacity="0.25"
        strokeWidth="3"
      />
      <path
        d="M22 12a10 10 0 0 1-10 10"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  );
}

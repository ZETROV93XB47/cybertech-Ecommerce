/**
 * Material Symbols Outlined wrapper. The font is loaded once in the root layout
 * via a Google Fonts <link>; this component just lays down the right span class
 * and the icon name as text content.
 *
 *    <Icon name="shopping_cart" />
 *    <Icon name="arrow_forward" size={14} />
 */
type IconProps = {
  name: string;
  size?: number;
  className?: string;
  filled?: boolean;
  weight?: 100 | 200 | 300 | 400 | 500 | 600 | 700;
};

export function Icon({
  name,
  size,
  className,
  filled = false,
  weight,
}: IconProps) {
  const style: React.CSSProperties = {};
  if (size !== undefined) style.fontSize = `${size}px`;
  const settings: string[] = [];
  if (filled) settings.push('"FILL" 1');
  if (weight !== undefined) settings.push(`"wght" ${weight}`);
  if (settings.length) style.fontVariationSettings = settings.join(", ");

  return (
    <span
      aria-hidden="true"
      className={`material-symbols-outlined ${className ?? ""}`.trim()}
      style={style}
    >
      {name}
    </span>
  );
}

import { Icon } from "@/components/ui/Icon";

interface Props {
  label: string;
  value: string | number;
  /** Material icon name. */
  icon?: string;
  /** Compact secondary line (e.g. "12 in last 30 days"). */
  hint?: string;
  /** Visual accent. */
  tone?: "default" | "warning" | "success" | "danger";
}

const TONE: Record<NonNullable<Props["tone"]>, string> = {
  default: "bg-white",
  warning: "bg-amber-50",
  success: "bg-emerald-50",
  danger: "bg-rose-50",
};

const ICON_TONE: Record<NonNullable<Props["tone"]>, string> = {
  default: "bg-primary/10 text-primary",
  warning: "bg-amber-100 text-amber-700",
  success: "bg-emerald-100 text-emerald-700",
  danger: "bg-rose-100 text-rose-700",
};

export function KpiCard({
  label,
  value,
  icon = "insights",
  hint,
  tone = "default",
}: Props) {
  return (
    <div
      className={
        "rounded-xl border border-slate-200 p-5 flex items-start gap-4 shadow-sm " +
        TONE[tone]
      }
    >
      <div
        className={
          "w-10 h-10 rounded-lg flex items-center justify-center " +
          ICON_TONE[tone]
        }
      >
        <Icon name={icon} size={22} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="font-label-caps uppercase tracking-wider text-xs text-slate-500">
          {label}
        </p>
        <p className="font-display text-2xl font-bold text-slate-900 mt-1 truncate">
          {value}
        </p>
        {hint && <p className="text-xs text-slate-500 mt-1">{hint}</p>}
      </div>
    </div>
  );
}

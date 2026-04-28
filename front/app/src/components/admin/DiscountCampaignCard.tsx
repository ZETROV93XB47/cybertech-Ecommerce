"use client";

import { useActionState } from "react";
import { Icon } from "@/components/ui/Icon";
import type { DiscountCampaignResponseDto } from "@/lib/api";

type FormState = { ok: true } | { ok: false; error: string } | null;

interface Props {
  campaign: DiscountCampaignResponseDto;
  /** Server action bound to this campaign's `discountType`. */
  action: (state: FormState, formData: FormData) => Promise<FormState>;
}

const HUMAN_TYPE: Record<string, string> = {
  NO_DISCOUNT: "No discount",
  BLACK_FRIDAY: "Black Friday",
  WINTER_SALES: "Winter sales",
  SPRING_SALES: "Spring sales",
  BUY_ONE_GET_ONE_FREE: "Buy 1, get 1 free",
};

/** "2026-04-28T15:00:00" → "2026-04-28T15:00" for <input type=datetime-local>. */
function toLocalInput(iso: string | null): string {
  if (!iso) return "";
  // Trim seconds/timezone for datetime-local. The component accepts
  // `YYYY-MM-DDTHH:mm` only.
  return iso.length >= 16 ? iso.slice(0, 16) : iso;
}

export function DiscountCampaignCard({ campaign, action }: Props) {
  const [state, formAction, isPending] = useActionState<FormState, FormData>(
    action,
    null,
  );

  return (
    <form
      action={formAction}
      className="rounded-xl border border-slate-200 bg-white p-5 flex flex-col gap-4"
    >
      <header className="flex items-start justify-between gap-3">
        <div>
          <p className="font-label-caps uppercase tracking-wider text-xs text-slate-500">
            {campaign.discountType}
          </p>
          <h3 className="font-display text-lg font-semibold text-slate-900 mt-0.5">
            {HUMAN_TYPE[campaign.discountType] ?? campaign.discountType}
          </h3>
        </div>
        <label className="inline-flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            name="enabled"
            defaultChecked={campaign.enabled}
            className="w-4 h-4 accent-primary"
          />
          <span className="font-medium">
            {campaign.enabled ? "Active" : "Disabled"}
          </span>
        </label>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <Field
          label="Percentage"
          name="percentage"
          type="number"
          step="0.01"
          min="0"
          max="100"
          defaultValue={campaign.percentage ?? ""}
          placeholder="e.g. 25"
        />
        <Field
          label="Fixed amount"
          name="fixedAmount"
          type="number"
          step="0.01"
          min="0"
          defaultValue={campaign.fixedAmount ?? ""}
          placeholder="e.g. 10.00"
        />
        <Field
          label="Min order amount"
          name="minOrderAmount"
          type="number"
          step="0.01"
          min="0"
          defaultValue={campaign.minOrderAmount ?? ""}
        />
        <Field
          label="Max discount"
          name="maxDiscountAmount"
          type="number"
          step="0.01"
          min="0"
          defaultValue={campaign.maxDiscountAmount ?? ""}
        />
        <Field
          label="Starts at"
          name="startsAt"
          type="datetime-local"
          defaultValue={toLocalInput(campaign.startsAt)}
        />
        <Field
          label="Ends at"
          name="endsAt"
          type="datetime-local"
          defaultValue={toLocalInput(campaign.endsAt)}
        />
      </div>

      <footer className="flex items-center justify-between gap-3 pt-1">
        <p className="text-xs text-slate-500" aria-live="polite">
          {state?.ok && "Saved."}
          {state && !state.ok && (
            <span className="text-rose-600">{state.error}</span>
          )}
        </p>
        <button
          type="submit"
          disabled={isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-primary text-white px-4 py-2 text-sm font-semibold hover:opacity-90 disabled:opacity-50 transition-opacity"
        >
          {isPending && <Icon name="progress_activity" size={16} />}
          Save
        </button>
      </footer>
    </form>
  );
}

function Field({
  label,
  name,
  ...rest
}: {
  label: string;
  name: string;
} & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-xs font-medium text-slate-700">{label}</span>
      <input
        name={name}
        {...rest}
        className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
      />
    </label>
  );
}

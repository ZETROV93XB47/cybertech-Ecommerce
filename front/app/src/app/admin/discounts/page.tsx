import { ApiError, discountApi } from "@/lib/api";
import type {
  DiscountCampaignResponseDto,
  DiscountType,
} from "@/lib/api";
import { DiscountCampaignCard } from "@/components/admin/DiscountCampaignCard";
import { updateDiscountAction } from "@/lib/actions/admin";

export const metadata = { title: "Admin — Discounts" };
export const dynamic = "force-dynamic";

const ALL_TYPES: DiscountType[] = [
  "NO_DISCOUNT",
  "BLACK_FRIDAY",
  "WINTER_SALES",
  "SPRING_SALES",
  "BUY_ONE_GET_ONE_FREE",
];

/**
 * Build a stable list of campaign cards: one per enum value. If the backend
 * returns fewer rows than expected (e.g. NO_DISCOUNT not seeded yet) we still
 * render the card with neutral defaults so admins can configure it.
 */
function placeholderCampaign(
  type: DiscountType,
): DiscountCampaignResponseDto {
  return {
    uuid: "",
    enabled: false,
    discountType: type,
    calculationType: "NONE",
    percentage: null,
    fixedAmount: null,
    minOrderAmount: null,
    maxDiscountAmount: null,
    startsAt: null,
    endsAt: null,
    priority: null,
  };
}

export default async function AdminDiscountsPage() {
  let campaigns: DiscountCampaignResponseDto[] = [];
  let errorMessage: string | null = null;
  try {
    campaigns = await discountApi.listAll();
  } catch (err) {
    errorMessage =
      err instanceof ApiError ? err.message : "Could not load discount campaigns.";
  }

  const byType = new Map(campaigns.map((c) => [c.discountType, c]));
  const cards = ALL_TYPES.map(
    (t) => byType.get(t) ?? placeholderCampaign(t),
  );

  return (
    <div className="flex flex-col gap-6">
      <header>
        <p className="font-label-caps uppercase tracking-[0.2em] text-xs text-slate-500">
          Promotions
        </p>
        <h1 className="font-display text-3xl font-bold text-slate-900 mt-1">
          Discount campaigns
        </h1>
        <p className="text-slate-600 mt-1">
          Five engine-level campaigns map 1:1 to the backend{" "}
          <code>DiscountType</code> enum. Toggle activation, tune percentages or
          flat amounts, and adjust the active window.
        </p>
      </header>

      {errorMessage && (
        <div className="p-4 rounded-xl bg-rose-50 text-rose-700 text-sm">
          {errorMessage}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {cards.map((campaign) => {
          const action = makeAction(campaign.discountType);
          return (
            <DiscountCampaignCard
              key={campaign.discountType}
              campaign={campaign}
              action={action}
            />
          );
        })}
      </div>
    </div>
  );
}

function makeAction(type: DiscountType) {
  // Bound server action per type so the form can call it without a hidden
  // input. `useActionState` wants (state, formData) → state.
  async function action(
    _state: { ok: true } | { ok: false; error: string } | null,
    formData: FormData,
  ) {
    "use server";
    return updateDiscountAction(type, formData);
  }
  return action;
}

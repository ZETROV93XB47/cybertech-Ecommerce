import Link from "next/link";
import { ApiError, bankCardApi } from "@/lib/api";
import type { BankCardResponseDto } from "@/lib/api";
import { AccountFrame } from "@/components/account/AccountFrame";
import { BankCardForm } from "@/components/account/BankCardForm";
import { EmptyState } from "@/components/account/EmptyState";
import { Icon } from "@/components/ui/Icon";

export const metadata = { title: "Edit payment card — Cybertech" };
export const dynamic = "force-dynamic";

/**
 * Edit card page.
 *
 * Resolution:
 *  1. Try /bank-card/all-mine — pick the matching card by uuid.
 *  2. Fallback to /bank-card/default if all-mine is 404 (endpoint not
 *     merged yet). If the requested uuid matches the default card we
 *     can edit it; otherwise we surface a "this card isn't editable yet"
 *     placeholder rather than silently 404'ing.
 */
async function resolveCard(uuid: string): Promise<{
  card: BankCardResponseDto | null;
  unavailable: boolean;
}> {
  try {
    const list = await bankCardApi.allMine();
    const match = Array.isArray(list)
      ? list.find((c) => c.uuid === uuid)
      : null;
    return { card: match ?? null, unavailable: false };
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 404) {
      console.warn("[account/cards/[uuid]/edit] allMine failed", err);
    }
  }

  try {
    const def = await bankCardApi.getDefault();
    if (def && def.uuid === uuid) {
      return { card: def, unavailable: false };
    }
    // Multi-card listing isn't live yet AND the uuid doesn't match the
    // default card — we can't fetch arbitrary cards by id.
    return { card: null, unavailable: true };
  } catch (err) {
    if (
      err instanceof ApiError &&
      (err.status === 403 || err.status === 404)
    ) {
      return { card: null, unavailable: true };
    }
    throw err;
  }
}

export default async function EditBankCardPage({
  params,
}: {
  params: Promise<{ uuid: string }>;
}) {
  const { uuid } = await params;
  const { card, unavailable } = await resolveCard(uuid);

  return (
    <AccountFrame
      eyebrow="Account"
      title="Edit payment card"
      description="Update the cardholder name or expiry date. Changing the PAN requires adding a new card and deleting this one."
      actions={
        <Link
          href="/account/cards"
          className="inline-flex items-center gap-2 h-10 px-4 border border-outline font-display text-xs font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors"
        >
          <Icon name="arrow_back" size={14} />
          Back to cards
        </Link>
      }
    >
      {unavailable && (
        <EmptyState
          icon="credit_card"
          title="Editing this card isn't available yet"
          description="Multi-card editing is rolling out shortly. In the meantime you can edit your default card from the cards page."
          cta={{ href: "/account/cards", label: "Back to cards" }}
        />
      )}

      {!unavailable && !card && (
        <EmptyState
          icon="search_off"
          title="Card not found"
          description="That card isn't on your account anymore. It may have been removed."
          cta={{ href: "/account/cards", label: "Back to cards" }}
        />
      )}

      {!unavailable && card && <BankCardForm mode="edit" initial={card} />}
    </AccountFrame>
  );
}

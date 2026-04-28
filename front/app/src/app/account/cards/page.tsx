import Link from "next/link";
import { ApiError, bankCardApi } from "@/lib/api";
import type { BankCardResponseDto } from "@/lib/api";
import { AccountFrame } from "@/components/account/AccountFrame";
import { BankCardTile } from "@/components/account/BankCardTile";
import { EmptyState } from "@/components/account/EmptyState";
import { Icon } from "@/components/ui/Icon";

export const metadata = { title: "Payment cards — Cybertech" };
export const dynamic = "force-dynamic";

interface LoadResult {
  cards: BankCardResponseDto[];
  multiCard: boolean;
  errored: boolean;
}

/**
 * Resolution strategy:
 *  1. Try /bank-card/all-mine — when it lands we get the full list.
 *  2. On 404 (endpoint not yet merged) fall back to /bank-card/default.
 *     The user gets a single tile + the "set default" affordance is hidden
 *     (multiCard = false) since there's nothing to switch between.
 */
async function loadCards(): Promise<LoadResult> {
  // Attempt multi-card listing first.
  try {
    const list = await bankCardApi.allMine();
    return {
      cards: Array.isArray(list) ? list : [],
      multiCard: true,
      errored: false,
    };
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 404) {
      console.warn("[account/cards] bankCardApi.allMine failed", err);
      // Don't bail — fall back to default-card read.
    }
  }

  // Fallback: single default card.
  try {
    const card = await bankCardApi.getDefault();
    return {
      cards: card ? [card] : [],
      multiCard: false,
      errored: false,
    };
  } catch (err) {
    if (
      err instanceof ApiError &&
      (err.status === 403 || err.status === 404)
    ) {
      return { cards: [], multiCard: false, errored: false };
    }
    console.warn("[account/cards] bankCardApi.getDefault failed", err);
    return { cards: [], multiCard: false, errored: true };
  }
}

export default async function CardsPage() {
  const { cards, multiCard, errored } = await loadCards();

  return (
    <AccountFrame
      eyebrow="Account"
      title="Payment cards"
      description={
        multiCard
          ? "Cards used to settle your orders. Set a default, edit holder details or remove obsolete cards."
          : "Card on file for your orders. We tokenise — the full PAN is never stored."
      }
      actions={
        cards.length > 0 ? (
          <Link
            href="/account/cards/add"
            className="inline-flex items-center gap-2 h-10 px-5 bg-primary text-on-primary font-display text-xs font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors"
          >
            <Icon name="add" size={16} />
            Add card
          </Link>
        ) : null
      }
    >
      {errored && (
        <div className="bg-error-container/40 border border-error/40 px-6 py-5 text-on-error-container">
          <p className="font-display font-semibold mb-1">
            We couldn&apos;t load your cards
          </p>
          <p className="font-body text-sm">
            Please refresh the page or try again in a minute.
          </p>
        </div>
      )}

      {!errored && cards.length === 0 && (
        <EmptyState
          icon="credit_card"
          title="No payment method yet"
          description="Add a card to streamline checkout — we'll keep it tokenised and on file for future orders."
          cta={{ href: "/account/cards/add", label: "Add a card" }}
        />
      )}

      {!errored && cards.length > 0 && (
        <ul className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {cards.map((card) => (
            <BankCardTile key={card.uuid} card={card} multiCard={multiCard} />
          ))}
        </ul>
      )}
    </AccountFrame>
  );
}

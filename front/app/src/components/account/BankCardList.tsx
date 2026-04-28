import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import type { BankCardResponseDto, CardType } from "@/lib/api";

/**
 * Renders the user's saved bank cards. Today the backend enforces a
 * one-card-per-user policy (see BankCardControllerApiSpec) so we accept
 * either a single card or an array — whichever the page resolved.
 */

interface BankCardListProps {
  cards: BankCardResponseDto[];
  errored: boolean;
}

const CARD_GRADIENTS: Record<CardType, string> = {
  VISA: "from-[#1a1f71] via-[#0058be] to-[#3a8dff]",
  MASTERCARD: "from-[#0b1c30] via-[#243049] to-[#5a3aa8]",
  AMEX: "from-[#006fcf] via-[#0058be] to-[#1a1f71]",
};

const CARD_LABEL: Record<CardType, string> = {
  VISA: "Visa",
  MASTERCARD: "Mastercard",
  AMEX: "American Express",
};

function gradientFor(card: BankCardResponseDto): string {
  return CARD_GRADIENTS[card.cardType] ?? CARD_GRADIENTS.VISA;
}

function brandLabel(card: BankCardResponseDto): string {
  return CARD_LABEL[card.cardType] ?? card.cardType;
}

export function BankCardList({ cards, errored }: BankCardListProps) {
  return (
    <section
      aria-labelledby="bankcards-heading"
      className="bg-white border border-slate-100"
    >
      <header className="flex items-end justify-between gap-4 p-6 border-b border-slate-100">
        <div>
          <h2
            id="bankcards-heading"
            className="font-display text-headline-sm text-primary"
          >
            Payment methods
          </h2>
          <p className="font-body text-sm text-on-surface-variant mt-1">
            Cards used to settle your orders. Tokenised — we never store the
            full PAN.
          </p>
        </div>
        <Link
          href="/account/cards/add"
          className="hidden sm:inline-flex items-center gap-2 h-10 px-5 border border-outline font-display text-xs font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors"
        >
          <Icon name="add" size={16} />
          Add card
        </Link>
      </header>

      <div className="p-6">
        {errored ? (
          <p className="font-body text-sm text-on-surface-variant">
            We couldn&apos;t load your saved cards. Please try again later.
          </p>
        ) : cards.length === 0 ? (
          <div className="flex flex-col items-center text-center gap-3 py-8">
            <div className="w-14 h-14 rounded-full bg-surface-container-low flex items-center justify-center">
              <Icon name="credit_card" size={28} className="text-outline" />
            </div>
            <p className="font-body text-on-surface-variant">
              No payment method on file yet.
            </p>
            <Link
              href="/account/cards/add"
              className="inline-flex items-center gap-2 text-secondary font-label-caps uppercase tracking-wider hover:underline"
            >
              Add a card <Icon name="arrow_forward" size={14} />
            </Link>
          </div>
        ) : (
          <ul className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {cards.map((card) => (
              <li key={card.uuid}>
                <article
                  className={`relative overflow-hidden text-white aspect-[1.6/1] p-6 flex flex-col justify-between bg-gradient-to-br ${gradientFor(card)} shadow-sm`}
                >
                  <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-white/10 blur-2xl" />
                  <div className="flex items-start justify-between relative z-10">
                    <span className="font-display text-sm uppercase tracking-widest opacity-80">
                      {brandLabel(card)}
                    </span>
                    {card.isDefault && (
                      <span className="bg-white/20 text-white text-label-caps uppercase tracking-wider px-2 py-1">
                        Default
                      </span>
                    )}
                  </div>
                  <div className="relative z-10 space-y-3">
                    <p className="font-display text-xl tracking-[0.2em]">
                      {card.maskedNumber}
                    </p>
                    <div className="flex items-end justify-between gap-4 text-xs uppercase tracking-wider opacity-90">
                      <div>
                        <span className="block opacity-60">Holder</span>
                        <span className="font-display text-sm">
                          {card.cardHolderName}
                        </span>
                      </div>
                      <div className="text-right">
                        <span className="block opacity-60">Expires</span>
                        <span className="font-display text-sm">
                          {card.expiryDate}
                        </span>
                      </div>
                    </div>
                  </div>
                </article>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

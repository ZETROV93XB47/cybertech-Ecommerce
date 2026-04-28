import { BankCardActions } from "./BankCardActions";
import type { BankCardResponseDto, CardType } from "@/lib/api";

/**
 * Single card tile on /account/cards. Visual is intentionally close to
 * BankCardList's gradient face but with the action bar tacked on below
 * so the card itself stays "plastic-clean".
 */

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

export function BankCardTile({
  card,
  multiCard,
}: {
  card: BankCardResponseDto;
  multiCard: boolean;
}) {
  const gradient = CARD_GRADIENTS[card.cardType] ?? CARD_GRADIENTS.VISA;
  const label = CARD_LABEL[card.cardType] ?? card.cardType;

  return (
    <li className="flex flex-col gap-3">
      <article
        className={`relative overflow-hidden text-white aspect-[1.6/1] p-6 flex flex-col justify-between bg-gradient-to-br ${gradient} shadow-sm`}
      >
        <div className="absolute -top-12 -right-12 w-40 h-40 rounded-full bg-white/10 blur-2xl" />
        <div className="flex items-start justify-between relative z-10">
          <span className="font-display text-sm uppercase tracking-widest opacity-80">
            {label}
          </span>
          {card.isDefault && (
            <span className="bg-white/20 text-white text-xs uppercase tracking-wider px-2 py-1">
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
              <span className="font-display text-sm">{card.cardHolderName}</span>
            </div>
            <div className="text-right">
              <span className="block opacity-60">Expires</span>
              <span className="font-display text-sm">{card.expiryDate}</span>
            </div>
          </div>
        </div>
      </article>
      <BankCardActions card={card} multiCard={multiCard} />
    </li>
  );
}

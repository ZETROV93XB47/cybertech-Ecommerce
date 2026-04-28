import Link from "next/link";
import { AccountFrame } from "@/components/account/AccountFrame";
import { BankCardForm } from "@/components/account/BankCardForm";
import { Icon } from "@/components/ui/Icon";

export const metadata = { title: "Add payment card — Cybertech" };
export const dynamic = "force-dynamic";

export default function AddBankCardPage() {
  return (
    <AccountFrame
      eyebrow="Account"
      title="Add a payment card"
      description="Save a card to speed up your next checkout. We tokenise card data server-side and never store the PAN."
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
      <BankCardForm mode="create" />
    </AccountFrame>
  );
}

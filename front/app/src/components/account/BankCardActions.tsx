"use client";

import Link from "next/link";
import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import {
  deleteBankCardAction,
  setDefaultBankCardAction,
} from "@/lib/actions/bank-card";
import type { BankCardResponseDto } from "@/lib/api";

/**
 * Per-card action bar (set default, edit, delete) shown on /account/cards.
 *
 * The set-default Server Action is per-card (PATCH /set-default/{uuid})
 * but the delete endpoint is single-card today (DELETE /bank-card/delete)
 * — that lines up with the backend's one-card-per-user enforcement. When
 * multi-card lands the delete will need a uuid argument; the page hides
 * the button anyway when we're on a multi-card listing flagged unsupported.
 */

interface BankCardActionsProps {
  card: BankCardResponseDto;
  multiCard: boolean;
}

export function BankCardActions({ card, multiCard }: BankCardActionsProps) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const onSetDefault = () => {
    setError(null);
    setBusyAction("default");
    startTransition(async () => {
      const res = await setDefaultBankCardAction(card.uuid);
      if (!res.ok) setError(res.error ?? "Could not set the default.");
      else router.refresh();
      setBusyAction(null);
    });
  };

  const onDelete = () => {
    setError(null);
    setBusyAction("delete");
    startTransition(async () => {
      const res = await deleteBankCardAction();
      if (!res.ok) setError(res.error ?? "Could not remove the card.");
      else {
        setConfirming(false);
        router.refresh();
      }
      setBusyAction(null);
    });
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        {multiCard && !card.isDefault && (
          <button
            type="button"
            onClick={onSetDefault}
            disabled={pending}
            className="inline-flex items-center gap-2 h-9 px-4 border border-outline font-display text-xs font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors disabled:opacity-60"
          >
            <Icon name="star" size={14} />
            {busyAction === "default" ? "Setting…" : "Set default"}
          </button>
        )}
        <Link
          href={`/account/cards/${card.uuid}/edit`}
          className="inline-flex items-center gap-2 h-9 px-4 border border-outline font-display text-xs font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors"
        >
          <Icon name="edit" size={14} />
          Edit
        </Link>
        {!confirming && (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="inline-flex items-center gap-2 h-9 px-4 border border-error/40 text-error font-display text-xs font-semibold uppercase tracking-wider hover:bg-error-container/40 transition-colors"
          >
            <Icon name="delete" size={14} />
            Delete
          </button>
        )}
        {confirming && (
          <div className="flex flex-wrap items-center gap-2 bg-error-container/40 border border-error/40 px-3 py-2">
            <span className="font-body text-xs text-on-error-container">
              Confirm?
            </span>
            <button
              type="button"
              onClick={onDelete}
              disabled={pending}
              className="inline-flex items-center justify-center h-8 px-3 bg-error text-on-error font-display text-xs font-semibold uppercase tracking-wider hover:brightness-110 disabled:opacity-60"
            >
              {busyAction === "delete" ? "Removing…" : "Yes, delete"}
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              disabled={pending}
              className="inline-flex items-center justify-center h-8 px-3 font-display text-xs font-semibold uppercase tracking-wider hover:underline disabled:opacity-60"
            >
              Keep
            </button>
          </div>
        )}
      </div>
      {error && (
        <p
          role="alert"
          className="text-xs text-on-error-container bg-error-container/60 px-3 py-2"
        >
          {error}
        </p>
      )}
    </div>
  );
}

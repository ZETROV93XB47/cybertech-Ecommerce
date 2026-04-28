"use client";

import Link from "next/link";
import { useActionState } from "react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Icon } from "@/components/ui/Icon";
import {
  addBankCardAction,
  updateBankCardAction,
} from "@/lib/actions/bank-card";
import type { BankCardResponseDto, CardType } from "@/lib/api";

/**
 * Bank-card form, used both for /add and /[uuid]/edit.
 *
 * Mode is inferred from `mode` prop:
 *  - "create" → renders all fields (PAN, CVV, expiry, holder, type, default
 *    toggle) and POSTs through addBankCardAction.
 *  - "edit"   → renders only the fields the backend's update endpoint
 *    accepts (holder name + expiry) and PATCHes through updateBankCardAction.
 *
 * On success we route the user back to /account/cards.
 */

type FormState =
  | { ok: true }
  | { ok: false; error: string }
  | undefined;

interface BankCardFormProps {
  mode: "create" | "edit";
  initial?: BankCardResponseDto | null;
}

const CARD_TYPES: CardType[] = ["VISA", "MASTERCARD", "AMEX"];

export function BankCardForm({ mode, initial }: BankCardFormProps) {
  const router = useRouter();
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    async (_prev, formData) => {
      if (mode === "create") return await addBankCardAction(formData);
      if (initial?.uuid) formData.set("uuid", initial.uuid);
      return await updateBankCardAction(formData);
    },
    undefined,
  );

  // Bounce back to /account/cards once we've got a clean ok.
  useEffect(() => {
    if (state && state.ok) router.push("/account/cards");
  }, [state, router]);

  return (
    <form
      action={formAction}
      className="bg-white border border-slate-100 p-8 flex flex-col gap-6"
    >
      {mode === "edit" && initial && (
        <input type="hidden" name="uuid" value={initial.uuid} />
      )}

      {mode === "create" && (
        <>
          <Field
            name="cardNumber"
            label="Card number"
            placeholder="4242 4242 4242 4242"
            required
            autoComplete="cc-number"
            inputMode="numeric"
            maxLength={19}
          />
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Field
              name="expiryDate"
              label="Expiry (MM/YYYY)"
              placeholder="12/2030"
              required
              autoComplete="cc-exp"
              defaultValue={initial?.expiryDate}
            />
            <Field
              name="cvv"
              label="CVV"
              placeholder="123"
              required
              autoComplete="cc-csc"
              inputMode="numeric"
              maxLength={4}
            />
            <div>
              <label
                htmlFor="cardType"
                className="block font-label-caps uppercase text-on-surface-variant text-xs mb-2"
              >
                Card type
              </label>
              <select
                id="cardType"
                name="cardType"
                defaultValue=""
                className="w-full bg-white border border-outline-variant px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
              >
                <option value="">Auto-detect</option>
                {CARD_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </>
      )}

      <Field
        name="cardHolderName"
        label="Cardholder name"
        placeholder="JANE DOE"
        required
        autoComplete="cc-name"
        defaultValue={initial?.cardHolderName}
      />

      {mode === "edit" && (
        <Field
          name="expiryDate"
          label="Expiry (MM/YYYY)"
          placeholder="12/2030"
          autoComplete="cc-exp"
          defaultValue={initial?.expiryDate}
        />
      )}

      {mode === "create" && (
        <label className="inline-flex items-center gap-3 cursor-pointer select-none">
          <input
            type="checkbox"
            name="isDefault"
            className="h-5 w-5 accent-secondary"
            defaultChecked
          />
          <span className="font-body text-sm text-on-surface">
            Use this card as my default payment method
          </span>
        </label>
      )}

      {state && !state.ok && (
        <p
          role="alert"
          className="text-sm text-on-error-container bg-error-container/60 px-4 py-3"
        >
          {state.error}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={pending}
          className="inline-flex items-center justify-center gap-2 h-12 px-8 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors disabled:opacity-60"
        >
          <Icon name={mode === "create" ? "add_card" : "save"} size={18} />
          {pending
            ? "Saving…"
            : mode === "create"
              ? "Save card"
              : "Save changes"}
        </button>
        <Link
          href="/account/cards"
          className="inline-flex items-center justify-center h-12 px-6 font-display text-sm font-semibold uppercase tracking-wider text-on-surface hover:underline"
        >
          Cancel
        </Link>
      </div>

      <p className="font-body text-xs text-on-surface-variant flex items-center gap-2">
        <Icon name="lock" size={14} />
        Card data is sent server-side and tokenised. The full PAN is never
        stored locally.
      </p>
    </form>
  );
}

function Field({
  name,
  label,
  ...rest
}: React.InputHTMLAttributes<HTMLInputElement> & { name: string; label: string }) {
  return (
    <div>
      <label
        htmlFor={name}
        className="block font-label-caps uppercase text-on-surface-variant text-xs mb-2"
      >
        {label}
      </label>
      <input
        id={name}
        name={name}
        type="text"
        className="w-full bg-white border border-outline-variant px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
        {...rest}
      />
    </div>
  );
}

"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { auth } from "@/lib/auth";
import { ApiError, bankCardApi } from "@/lib/api";
import type { CardType } from "@/lib/api";

/**
 * Bank-card mutations as Server Actions. Each one verifies the session,
 * calls the backend with the access token, and revalidates the account
 * surfaces so the new state surfaces on next render.
 */

type Result = { ok: true } | { ok: false; error: string };

function s(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

function inferCardType(num: string): CardType {
  const digits = num.replace(/\D/g, "");
  if (digits.startsWith("4")) return "VISA";
  if (digits.startsWith("34") || digits.startsWith("37")) return "AMEX";
  return "MASTERCARD";
}

export async function addBankCardAction(formData: FormData): Promise<Result> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin?callbackUrl=/account/cards/add");

  const cardHolderName = s(formData.get("cardHolderName"));
  const cardNumber = s(formData.get("cardNumber")).replace(/\s+/g, "");
  const expiryDate = s(formData.get("expiryDate"));
  const cvv = s(formData.get("cvv"));
  const explicitType = s(formData.get("cardType")) as CardType | "";
  const cardType: CardType = explicitType || inferCardType(cardNumber);
  const isDefault = formData.get("isDefault") === "on";

  if (!cardHolderName || !cardNumber || !expiryDate || !cvv) {
    return { ok: false, error: "All card fields are required." };
  }

  try {
    const created = await bankCardApi.add({
      cardHolderName,
      cardNumber,
      expiryDate,
      cvv,
      cardType,
    });
    if (isDefault && created?.uuid) {
      try {
        await bankCardApi.setDefault(created.uuid);
      } catch {
        // Non-fatal — card was added, default toggle just didn't take.
      }
    }
    revalidatePath("/account/cards");
    revalidatePath("/account/profile");
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Could not add the card. Please try again." };
  }
}

export async function updateBankCardAction(formData: FormData): Promise<Result> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin?callbackUrl=/account/cards");

  const uuid = s(formData.get("uuid"));
  const cardHolderName = s(formData.get("cardHolderName")) || undefined;
  const expiryDate = s(formData.get("expiryDate")) || undefined;

  if (!uuid) return { ok: false, error: "Missing card identifier." };

  try {
    await bankCardApi.update({ uuid, cardHolderName, expiryDate });
    revalidatePath("/account/cards");
    revalidatePath(`/account/cards/${uuid}/edit`);
    revalidatePath("/account/profile");
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Could not update the card." };
  }
}

export async function deleteBankCardAction(): Promise<Result> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin?callbackUrl=/account/cards");

  try {
    await bankCardApi.remove();
    revalidatePath("/account/cards");
    revalidatePath("/account/profile");
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Could not remove the card." };
  }
}

export async function setDefaultBankCardAction(
  cardUuid: string,
): Promise<Result> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin?callbackUrl=/account/cards");

  try {
    await bankCardApi.setDefault(cardUuid);
    revalidatePath("/account/cards");
    revalidatePath("/account/profile");
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Could not set the default card." };
  }
}

"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { auth } from "@/lib/auth";
import { ApiError, cartApi } from "@/lib/api";

/**
 * Server actions for cart mutations. Each one:
 *  1) Verifies the caller has a session — if not, bounces them through the
 *     hosted Keycloak login with a callbackUrl back to where they came from.
 *  2) Calls the backend on the user's behalf (auth() picks up the access
 *     token via apiFetch).
 *  3) Revalidates `/cart` and `/products/[uuid]` so server-rendered totals
 *     reflect the change without a full reload.
 *
 * Returns `{ ok: true } | { ok: false, error: string }` so the calling
 * client component can surface failures via a toast.
 */

type Result = { ok: true } | { ok: false; error: string };

async function requireSession(callbackUrl: string) {
  const session = await auth();
  if (!session) {
    redirect(`/api/auth/signin?callbackUrl=${encodeURIComponent(callbackUrl)}`);
  }
}

function toMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return "Something went wrong. Please try again.";
}

export async function addToCartAction(
  productUuid: string,
  quantity = 1,
  callbackUrl = "/cart",
): Promise<Result> {
  await requireSession(callbackUrl);
  try {
    await cartApi.add({ items: [{ productUuid, quantity }] });
    revalidatePath("/cart");
    revalidatePath(`/products/${productUuid}`);
    return { ok: true };
  } catch (err) {
    return { ok: false, error: toMessage(err) };
  }
}

export async function decreaseCartItemAction(
  productUuid: string,
): Promise<Result> {
  await requireSession("/cart");
  try {
    await cartApi.decreaseQuantity({ productUuid, quantity: 1 });
    revalidatePath("/cart");
    return { ok: true };
  } catch (err) {
    return { ok: false, error: toMessage(err) };
  }
}

export async function removeFromCartAction(
  productUuid: string,
): Promise<Result> {
  await requireSession("/cart");
  try {
    await cartApi.removeProduct(productUuid);
    revalidatePath("/cart");
    return { ok: true };
  } catch (err) {
    return { ok: false, error: toMessage(err) };
  }
}

export async function clearCartAction(): Promise<Result> {
  await requireSession("/cart");
  try {
    await cartApi.clear();
    revalidatePath("/cart");
    return { ok: true };
  } catch (err) {
    return { ok: false, error: toMessage(err) };
  }
}

"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { auth } from "@/lib/auth";
import { ApiError, wishlistApi } from "@/lib/api";

type Result = { ok: true } | { ok: false; error: string };

export async function toggleWishlistAction(
  productUuid: string,
  add: boolean,
): Promise<Result> {
  const session = await auth();
  if (!session?.user) {
    redirect(
      `/api/auth/signin?callbackUrl=${encodeURIComponent(
        `/products/${productUuid}`,
      )}`,
    );
  }

  try {
    if (add) await wishlistApi.add(productUuid);
    else await wishlistApi.remove(productUuid);
    revalidatePath("/account/wishlist");
    revalidatePath(`/products/${productUuid}`);
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Wishlist update failed." };
  }
}

"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { auth } from "@/lib/auth";
import { ApiError, userApi } from "@/lib/api";
import type { Address, Sex } from "@/lib/api";

/**
 * Self-service profile update.
 *
 * The backend `PATCH /user/me` endpoint is being added in a parallel task.
 * If we get a 404 we surface a soft "feature not yet available" error so the
 * page can render a notice instead of a generic 500 — this lets the form ship
 * ahead of the backend without dead-ending the user.
 */

type Result =
  | { ok: true }
  | { ok: false; error: string; unavailable?: boolean };

function emptyToNull(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

export async function updateProfileAction(formData: FormData): Promise<Result> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin?callbackUrl=/account/profile/edit");

  const firstName = emptyToNull(formData.get("firstName"));
  const lastName = emptyToNull(formData.get("lastName"));
  const street = emptyToNull(formData.get("street"));
  const city = emptyToNull(formData.get("city"));
  const zipCode = emptyToNull(formData.get("zipCode"));
  const country = emptyToNull(formData.get("country"));
  const sexRaw = emptyToNull(formData.get("sex"));

  const address: Address | undefined =
    street || city || zipCode || country
      ? { street, city, zipCode, country }
      : undefined;

  const sex: Sex | undefined =
    sexRaw === "M" || sexRaw === "F" || sexRaw === "OTHER" ? sexRaw : undefined;

  try {
    await userApi.updateMe({
      firstName: firstName || undefined,
      lastName: lastName || undefined,
      sex,
      address,
    });
    revalidatePath("/account/profile");
    revalidatePath("/account/profile/edit");
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return {
        ok: false,
        unavailable: true,
        error:
          "Self-service profile editing is rolling out shortly. Please check back soon.",
      };
    }
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Could not update your profile. Please try again." };
  }
}

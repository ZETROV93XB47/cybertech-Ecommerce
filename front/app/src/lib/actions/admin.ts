"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { auth, signOut } from "@/lib/auth";
import {
  ApiError,
  API_BASE,
  discountApi,
  orderApi,
  productApi,
  userApi,
} from "@/lib/api";
import type {
  Address,
  DiscountCampaignUpdateRequestDto,
  DiscountType,
  ProductCreateRequestDto,
  ProductUpdateRequestDto,
  Sex,
  UserCreateRequestDto,
  UserUpdateRequestDto,
} from "@/lib/api";

type ActionResult = { ok: true } | { ok: false; error: string };

async function requireAdmin(): Promise<string> {
  const session = await auth();
  if (!session?.user || session.user.role !== "ADMIN") {
    redirect("/api/auth/signin?callbackUrl=/admin");
  }
  return session.accessToken ?? "";
}

function asString(fd: FormData, key: string, fallback = ""): string {
  const v = fd.get(key);
  return typeof v === "string" ? v : fallback;
}

function asOptionalString(fd: FormData, key: string): string | undefined {
  const v = fd.get(key);
  if (typeof v !== "string" || v.trim() === "") return undefined;
  return v;
}

function parseAttributes(raw: string): Record<string, unknown> {
  if (!raw.trim()) return {};
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
    throw new Error("Attributes must be a JSON object.");
  } catch (err) {
    throw new Error(
      err instanceof Error
        ? `Invalid attributes JSON: ${err.message}`
        : "Invalid attributes JSON.",
    );
  }
}

function failureMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return fallback;
}

// ---------- Products ----------

/**
 * Create a product. If a non-empty file is supplied under the `image` field
 * we hit the multipart endpoint; otherwise the JSON-only one. The multipart
 * call goes through plain `fetch` because we need the browser/Node fetch to
 * pick the boundary header itself — `apiFetch` already supports FormData
 * but has no way to forward the JWT in this server-action context without
 * exposing token plumbing in this file (see `requireAdmin`).
 */
export async function createProductAction(
  formData: FormData,
): Promise<ActionResult> {
  const token = await requireAdmin();

  let attributes: Record<string, unknown>;
  try {
    attributes = parseAttributes(asString(formData, "attributes", "{}"));
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Invalid attributes.") };
  }

  const req: ProductCreateRequestDto = {
    name: asString(formData, "name").trim(),
    price: asString(formData, "price").trim(),
    brand: asString(formData, "brand").trim(),
    category: asString(formData, "category").trim(),
    description: asString(formData, "description").trim(),
    photoUrl: asOptionalString(formData, "photoUrl"),
    attributes,
  };

  const imageFile = formData.get("image");
  const hasImage =
    imageFile instanceof File && imageFile.size > 0 && imageFile.name !== "";

  let createdUuid: string | null = null;
  try {
    if (hasImage) {
      const fd = new FormData();
      fd.append(
        "product",
        new Blob([JSON.stringify(req)], { type: "application/json" }),
      );
      fd.append("image", imageFile as File);
      const res = await fetch(
        `${API_BASE}/api/v1/services/admin/management/product/create-with-image`,
        {
          method: "POST",
          headers: {
            Accept: "application/json",
            "X-API-VERSION": "1.0",
            Authorization: `Bearer ${token}`,
            // NOTE: do NOT set Content-Type — fetch fills the boundary.
          },
          body: fd,
        },
      );
      if (!res.ok) {
        let msg = `HTTP ${res.status}`;
        try {
          const body = await res.json();
          if (body && typeof body.message === "string") msg = body.message;
        } catch {
          /* keep status code */
        }
        return { ok: false, error: msg };
      }
      const created = (await res.json()) as { uuid: string };
      createdUuid = created.uuid;
    } else {
      const created = await productApi.create(req);
      createdUuid = created.uuid;
    }
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Could not create product.") };
  }

  revalidatePath("/admin/products");
  revalidatePath("/admin");
  if (createdUuid) {
    redirect(`/admin/products/${createdUuid}/edit`);
  }
  redirect("/admin/products");
}

export async function updateProductAction(
  uuid: string,
  formData: FormData,
): Promise<ActionResult> {
  await requireAdmin();

  let attributes: Record<string, unknown> | undefined;
  const rawAttrs = asOptionalString(formData, "attributes");
  if (rawAttrs !== undefined) {
    try {
      attributes = parseAttributes(rawAttrs);
    } catch (err) {
      return { ok: false, error: failureMessage(err, "Invalid attributes.") };
    }
  }

  const req: ProductUpdateRequestDto = {
    name: asOptionalString(formData, "name"),
    price: asOptionalString(formData, "price"),
    brand: asOptionalString(formData, "brand"),
    category: asOptionalString(formData, "category"),
    description: asOptionalString(formData, "description"),
    photoUrl: asOptionalString(formData, "photoUrl"),
    attributes,
  };

  try {
    await productApi.update(uuid, req);
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Could not update product.") };
  }

  revalidatePath("/admin/products");
  revalidatePath(`/admin/products/${uuid}/edit`);
  redirect("/admin/products");
}

export async function deleteProductAction(uuid: string): Promise<ActionResult> {
  await requireAdmin();
  try {
    await productApi.remove(uuid);
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Could not delete product.") };
  }
  revalidatePath("/admin/products");
  revalidatePath("/admin");
  return { ok: true };
}

// ---------- Users ----------

function readAddress(formData: FormData): Address {
  return {
    street: asString(formData, "addressStreet").trim(),
    city: asString(formData, "addressCity").trim(),
    zipCode: asString(formData, "addressZipCode").trim(),
    country: asString(formData, "addressCountry").trim(),
  };
}

export async function createUserAction(
  formData: FormData,
): Promise<ActionResult> {
  await requireAdmin();
  const req: UserCreateRequestDto = {
    email: asString(formData, "email").trim(),
    password: asString(formData, "password"),
    firstName: asString(formData, "firstName").trim(),
    lastName: asString(formData, "lastName").trim(),
    username: asString(formData, "username").trim(),
    sex: (asString(formData, "sex") as Sex) || "OTHER",
    address: readAddress(formData),
    birthDate: asString(formData, "birthDate"),
    bankCardCreationRequestDto: null,
  };

  try {
    await userApi.create(req);
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Could not create user.") };
  }

  revalidatePath("/admin/users");
  revalidatePath("/admin");
  redirect("/admin/users");
}

export async function updateUserAction(
  uuid: string,
  formData: FormData,
): Promise<ActionResult> {
  await requireAdmin();

  // Only send fields that have actually been edited; the backend treats
  // missing values as "leave alone" while empty strings would clear them.
  const sex = asOptionalString(formData, "sex") as Sex | undefined;
  const street = asOptionalString(formData, "addressStreet");
  const city = asOptionalString(formData, "addressCity");
  const zipCode = asOptionalString(formData, "addressZipCode");
  const country = asOptionalString(formData, "addressCountry");

  const hasAddress =
    street !== undefined ||
    city !== undefined ||
    zipCode !== undefined ||
    country !== undefined;

  const req: UserUpdateRequestDto = {
    uuid,
    email: asOptionalString(formData, "email"),
    firstName: asOptionalString(formData, "firstName"),
    lastName: asOptionalString(formData, "lastName"),
    sex,
    address: hasAddress
      ? {
          street: street ?? "",
          city: city ?? "",
          zipCode: zipCode ?? "",
          country: country ?? "",
        }
      : undefined,
  };

  try {
    await userApi.update(req);
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Could not update user.") };
  }

  revalidatePath("/admin/users");
  revalidatePath(`/admin/users/${uuid}/edit`);
  redirect("/admin/users");
}

export async function deleteUserAction(uuid: string): Promise<ActionResult> {
  await requireAdmin();
  try {
    await userApi.remove(uuid);
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Could not delete user.") };
  }
  revalidatePath("/admin/users");
  revalidatePath("/admin");
  return { ok: true };
}

// ---------- Orders (admin) ----------

export async function deleteOrderAction(uuid: string): Promise<ActionResult> {
  await requireAdmin();
  try {
    await orderApi.remove(uuid);
  } catch (err) {
    return { ok: false, error: failureMessage(err, "Could not delete order.") };
  }
  revalidatePath("/admin/orders");
  return { ok: true };
}

// ---------- Discounts ----------

export async function updateDiscountAction(
  type: DiscountType,
  formData: FormData,
): Promise<ActionResult> {
  await requireAdmin();

  const enabledRaw = formData.get("enabled");
  const enabled =
    enabledRaw === "on" || enabledRaw === "true"
      ? true
      : enabledRaw === null
        ? undefined // checkbox unchecked & not present → leave alone
        : false;

  const req: DiscountCampaignUpdateRequestDto = {
    enabled,
    percentage: asOptionalString(formData, "percentage") ?? null,
    fixedAmount: asOptionalString(formData, "fixedAmount") ?? null,
    minOrderAmount: asOptionalString(formData, "minOrderAmount") ?? null,
    maxDiscountAmount: asOptionalString(formData, "maxDiscountAmount") ?? null,
    startsAt: asOptionalString(formData, "startsAt") ?? null,
    endsAt: asOptionalString(formData, "endsAt") ?? null,
  };

  try {
    await discountApi.update(type, req);
  } catch (err) {
    return {
      ok: false,
      error: failureMessage(err, "Could not update discount campaign."),
    };
  }

  revalidatePath("/admin/discounts");
  return { ok: true };
}

// ---------- Misc ----------

export async function adminSignOutAction(): Promise<void> {
  await signOut({ redirectTo: "/" });
}

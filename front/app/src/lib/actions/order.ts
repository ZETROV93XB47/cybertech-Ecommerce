"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { auth } from "@/lib/auth";
import { ApiError, orderApi } from "@/lib/api";
import type {
  DiscountType,
  OrderPlacingRequestDto,
  PaymentType,
  ShippingType,
} from "@/lib/api";

/**
 * Place an order from the checkout form. On a backend ApiError this returns
 * { ok: false, error } so the form can render the message. On success it
 * redirects to /order-success/{uuid} — the redirect throws, so callers don't
 * need to handle the success branch on the client.
 */
export async function placeOrderAction(formData: FormData): Promise<{
  ok: false;
  error: string;
} | void> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin?callbackUrl=/checkout");

  const userUuid = (formData.get("userUuid") as string) ?? "";
  const req: OrderPlacingRequestDto = {
    userUuid,
    paymentType: (formData.get("paymentType") as PaymentType) ?? "STRIPE",
    shippingType: (formData.get("shippingType") as ShippingType) ?? "STANDARD",
    shippingProvider: (formData.get("shippingProvider") as string) ?? "DHL",
    shippingStreet: (formData.get("shippingStreet") as string) ?? "",
    shippingCity: (formData.get("shippingCity") as string) ?? "",
    shippingZipCode: (formData.get("shippingZipCode") as string) ?? "",
    shippingCountry: (formData.get("shippingCountry") as string) ?? "",
    discountType:
      (formData.get("discountType") as DiscountType) ?? "NO_DISCOUNT",
  };

  let order;
  try {
    order = await orderApi.place(req);
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Could not place your order. Please try again." };
  }

  // Once placed, the cart is consumed by the backend; revalidate so the
  // header badge / cart page reflect the empty state immediately.
  revalidatePath("/cart");
  revalidatePath("/account/orders");
  redirect(`/order-success/${order.uuid}`);
}

/**
 * Lightweight status read for the post-checkout polling loop. Returns
 * `null` if the user lost session or the backend rejected the call — the
 * poller reads that as "stop polling".
 */
export async function getOrderStatusAction(
  orderUuid: string,
): Promise<{ status: string } | null> {
  const session = await auth();
  if (!session) return null;
  try {
    const dto = await orderApi.status(orderUuid);
    return { status: dto.status };
  } catch {
    return null;
  }
}

export async function retryPaymentAction(orderUuid: string): Promise<{
  ok: boolean;
  error?: string;
}> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin");
  try {
    await orderApi.retryPayment(orderUuid);
    revalidatePath(`/order-success/${orderUuid}`);
    revalidatePath("/account/orders");
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Retry failed. Please try again." };
  }
}

export async function cancelOrderAction(
  orderUuid: string,
  reason?: string,
): Promise<{ ok: boolean; error?: string }> {
  const session = await auth();
  if (!session?.user) redirect("/api/auth/signin");
  try {
    await orderApi.cancel({ orderUuid, reason });
    revalidatePath(`/account/orders/${orderUuid}`);
    revalidatePath("/account/orders");
    return { ok: true };
  } catch (err) {
    if (err instanceof ApiError) return { ok: false, error: err.message };
    return { ok: false, error: "Could not cancel the order." };
  }
}

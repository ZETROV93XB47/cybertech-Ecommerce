import Link from "next/link";
import { redirect } from "next/navigation";
import { MainLayout } from "@/components/layout/MainLayout";
import { Breadcrumbs } from "@/components/ui/Breadcrumbs";
import { Icon } from "@/components/ui/Icon";
import { auth } from "@/lib/auth";
import { ApiError, cartApi, discountApi, userApi } from "@/lib/api";
import type { CartResponseDto, DiscountContext, UserResponseDto } from "@/lib/api";
import { CheckoutForm } from "./CheckoutForm";

export const metadata = { title: "Checkout — Cybertech" };

/**
 * Checkout requires a session and a non-empty cart. We resolve the
 * caller's user UUID from the session sub claim (the backend stores
 * Keycloak's `sub` as the user's `keycloakId`); the user record is fetched
 * via /user/get/{uuid} where {uuid} is sourced from the session — but the
 * frontend doesn't know the internal user UUID without an API call. The
 * cleanest path: the cart already includes `userUuid`, so we use that.
 */
export default async function CheckoutPage() {
  const session = await auth();
  if (!session) redirect("/api/auth/signin?callbackUrl=/checkout");

  let cart: CartResponseDto | null = null;
  try {
    cart = await cartApi.getMine();
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) cart = null;
    else throw err;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <MainLayout>
        <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-section-gap">
          <div className="flex flex-col items-center text-center py-16">
            <div className="w-24 h-24 bg-surface-container-low rounded-full flex items-center justify-center mb-gutter">
              <Icon
                name="shopping_bag"
                size={48}
                className="text-outline-variant"
              />
            </div>
            <h1 className="font-display text-headline-md text-primary mb-stack-sm">
              Nothing to check out yet
            </h1>
            <p className="text-on-surface-variant max-w-md mb-gutter">
              Add a few items to your cart, then come back here to wrap up the
              order.
            </p>
            <Link
              href="/products"
              className="px-8 h-12 inline-flex items-center bg-primary text-on-primary font-display font-semibold rounded-lg hover:bg-slate-800 transition-all"
            >
              Shop Hardware
            </Link>
          </div>
        </div>
      </MainLayout>
    );
  }

  const [user, activeDiscounts] = await Promise.all([
    userApi.get(cart.userUuid).catch((): UserResponseDto | null => null),
    discountApi.active().catch((): DiscountContext[] => []),
  ]);

  if (!user) {
    return (
      <MainLayout>
        <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-section-gap">
          <div className="p-8 rounded-xl bg-error-container/40 text-on-error-container">
            We couldn&apos;t load your profile. Please refresh, or sign out
            and back in.
          </div>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop pt-12 pb-section-gap">
        <Breadcrumbs
          className="mb-12"
          items={[
            { label: "Home", href: "/" },
            { label: "Cart", href: "/cart" },
            { label: "Checkout" },
          ]}
        />

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12">
          <CheckoutForm
            user={user}
            cart={cart}
            activeDiscounts={activeDiscounts}
          />
        </div>
      </div>
    </MainLayout>
  );
}

import Link from "next/link";
import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { ApiError, cartApi } from "@/lib/api";
import { MainLayout } from "@/components/layout/MainLayout";
import { CartItemRow } from "@/components/cart/CartItemRow";
import { Icon } from "@/components/ui/Icon";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Your cart — Cybertech" };

export default async function CartPage() {
  const session = await auth();
  if (!session) redirect("/api/auth/signin?callbackUrl=/cart");

  let cart;
  try {
    cart = await cartApi.getMine();
  } catch (err) {
    // 404 from /cart/get can mean "no cart yet" — render an empty state instead
    // of erroring out the page.
    if (err instanceof ApiError && err.status === 404) cart = null;
    else throw err;
  }

  const items = cart?.items ?? [];

  if (items.length === 0) {
    return (
      <MainLayout>
        <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop py-section-gap">
          <div className="flex flex-col items-center text-center py-16">
            <div className="w-24 h-24 bg-surface-container-low rounded-full flex items-center justify-center mb-gutter">
              <Icon
                name="shopping_cart_off"
                size={48}
                className="text-outline-variant"
              />
            </div>
            <h1 className="font-display text-headline-md text-primary mb-stack-sm">
              Your cart is currently empty
            </h1>
            <p className="font-body text-on-surface-variant max-w-md mx-auto mb-gutter">
              Looks like you haven&apos;t added any high-performance gear yet.
              Explore our builds and upgrade your station.
            </p>
            <Link
              href="/products"
              className="px-8 h-12 inline-flex items-center bg-primary text-white font-display font-semibold rounded-lg hover:bg-slate-800 transition-all"
            >
              Shop Hardware
            </Link>
          </div>
        </div>
      </MainLayout>
    );
  }

  return (
    <MainLayout>
      <div className="max-w-[1280px] mx-auto px-margin-mobile md:px-margin-desktop pt-12 pb-section-gap">
        <header className="mb-stack-lg">
          <h1 className="font-display text-display-lg text-primary mb-2">
            Your Cart
          </h1>
          <p className="font-body text-on-surface-variant">
            Review your high-performance hardware selections.
          </p>
        </header>

        <div className="flex flex-col lg:flex-row gap-gutter">
          <div className="flex-grow space-y-gutter">
            {items.map((item) => (
              <CartItemRow key={item.cartItemUuid} item={item} />
            ))}

            <div className="pt-stack-md">
              <Link
                href="/products"
                className="inline-flex items-center gap-2 font-body text-secondary hover:underline underline-offset-4 transition-all"
              >
                <Icon name="arrow_back" size={20} />
                Continue Shopping
              </Link>
            </div>
          </div>

          <aside className="w-full lg:w-[400px] shrink-0">
            <div className="sticky top-28 p-8 bg-surface-container-high rounded-2xl shadow-[0_4px_20px_-10px_rgba(0,0,0,0.05)] border border-outline-variant/20">
              <h2 className="font-display font-semibold text-headline-sm text-primary mb-gutter">
                Order Summary
              </h2>
              <div className="space-y-4 mb-gutter">
                <div className="flex justify-between items-center">
                  <span className="font-body text-on-surface-variant">
                    Items ({items.reduce((n, i) => n + i.quantity, 0)})
                  </span>
                  <span className="font-body font-medium text-primary">
                    {formatMoney(cart?.totalPrice ?? "0")}
                  </span>
                </div>
                <p className="text-label-caps text-on-surface-variant">
                  Shipping &amp; taxes calculated at checkout.
                </p>
              </div>
              <div className="h-px bg-outline-variant/30 mb-gutter" />
              <div className="flex justify-between items-center mb-section-gap">
                <span className="font-display font-semibold text-headline-sm text-primary">
                  Subtotal
                </span>
                <span className="font-display font-semibold text-headline-sm text-secondary">
                  {formatMoney(cart?.totalPrice ?? "0")}
                </span>
              </div>
              <Link
                href="/checkout"
                className="w-full h-14 bg-secondary text-white font-display font-semibold rounded-lg hover:brightness-110 active:scale-[0.98] transition-all duration-200 flex items-center justify-center gap-2"
              >
                Proceed to Checkout
                <Icon name="payments" size={20} />
              </Link>

              <ul className="mt-gutter flex flex-col gap-3">
                <li className="flex items-center gap-3 p-3 rounded-lg bg-surface-bright/50">
                  <Icon name="verified_user" className="text-secondary" />
                  <span className="text-label-caps uppercase text-on-surface-variant tracking-wider">
                    Encrypted secure checkout
                  </span>
                </li>
                <li className="flex items-center gap-3 p-3 rounded-lg bg-surface-bright/50">
                  <Icon name="local_shipping" className="text-secondary" />
                  <span className="text-label-caps uppercase text-on-surface-variant tracking-wider">
                    Expedited worldwide delivery
                  </span>
                </li>
              </ul>
            </div>
          </aside>
        </div>
      </div>
    </MainLayout>
  );
}

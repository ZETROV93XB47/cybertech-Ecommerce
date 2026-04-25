"use client";

import { useActionState } from "react";
import { Icon } from "@/components/ui/Icon";
import { formatMoney } from "@/lib/format";
import { placeOrderAction } from "@/lib/actions/order";
import type {
  CartResponseDto,
  DiscountContext,
  UserResponseDto,
} from "@/lib/api";

type FormState = { ok: false; error: string } | undefined;

const SHIPPING_OPTIONS = [
  { value: "STANDARD", label: "Standard Delivery", sub: "3–5 business days", price: "Free" },
  { value: "EXPRESS", label: "Express Shipping", sub: "Next business day", price: "+$24.00" },
] as const;

const PAYMENT_OPTIONS = [
  {
    value: "STRIPE",
    label: "Stripe (test mode)",
    sub: "Server-confirmed via the configured test card.",
  },
  {
    value: "BANK_CARD",
    label: "Saved bank card",
    sub: "Uses the default card on your account.",
  },
] as const;

export function CheckoutForm({
  user,
  cart,
  activeDiscounts,
}: {
  user: UserResponseDto;
  cart: CartResponseDto;
  activeDiscounts: DiscountContext[];
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    async (_prev, formData) => {
      // placeOrderAction either redirects (success) or returns an error.
      const r = await placeOrderAction(formData);
      return r === undefined ? undefined : r;
    },
    undefined,
  );

  return (
    <form action={formAction} className="contents">
      <input type="hidden" name="userUuid" value={user.uuid} />

      <div className="lg:col-span-7 space-y-12">
        {/* Step 1 — Shipping address */}
        <section>
          <header className="flex items-center gap-3 mb-8">
            <span className="w-8 h-8 rounded-full bg-primary text-on-primary font-display font-semibold text-sm flex items-center justify-center">
              1
            </span>
            <h2 className="font-display font-semibold text-headline-sm text-on-surface">
              Shipping Address
            </h2>
          </header>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Field
              span="md:col-span-2"
              name="shippingStreet"
              label="Street address"
              defaultValue={user.address?.street}
              placeholder="123 Performance Way"
              required
            />
            <Field
              name="shippingCity"
              label="City"
              defaultValue={user.address?.city}
              placeholder="New York"
              required
            />
            <div className="grid grid-cols-2 gap-4">
              <Field
                name="shippingZipCode"
                label="Zip code"
                defaultValue={user.address?.zipCode}
                placeholder="10001"
                required
              />
              <div>
                <label
                  htmlFor="shippingCountry"
                  className="block font-label-caps uppercase text-on-surface-variant mb-2"
                >
                  Country
                </label>
                <select
                  id="shippingCountry"
                  name="shippingCountry"
                  defaultValue={user.address?.country ?? "United States"}
                  required
                  className="w-full bg-white border border-outline-variant rounded-lg px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
                >
                  <option>United States</option>
                  <option>Canada</option>
                  <option>Germany</option>
                  <option>France</option>
                  <option>United Kingdom</option>
                </select>
              </div>
            </div>
          </div>
        </section>

        <hr className="border-outline-variant" />

        {/* Step 2 — Shipping method */}
        <section>
          <header className="flex items-center gap-3 mb-8">
            <span className="w-8 h-8 rounded-full bg-primary text-on-primary font-display font-semibold text-sm flex items-center justify-center">
              2
            </span>
            <h2 className="font-display font-semibold text-headline-sm text-on-surface">
              Shipping Method
            </h2>
          </header>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {SHIPPING_OPTIONS.map((opt, idx) => (
              <RadioCard
                key={opt.value}
                name="shippingType"
                value={opt.value}
                label={opt.label}
                sub={opt.sub}
                trailing={opt.price}
                defaultChecked={idx === 0}
              />
            ))}
          </div>
          <input type="hidden" name="shippingProvider" value="DHL" />
        </section>

        <hr className="border-outline-variant" />

        {/* Step 3 — Payment */}
        <section>
          <header className="flex items-center gap-3 mb-8">
            <span className="w-8 h-8 rounded-full bg-primary text-on-primary font-display font-semibold text-sm flex items-center justify-center">
              3
            </span>
            <h2 className="font-display font-semibold text-headline-sm text-on-surface">
              Payment Method
            </h2>
          </header>
          <div className="space-y-4">
            {PAYMENT_OPTIONS.map((opt, idx) => (
              <RadioCard
                key={opt.value}
                name="paymentType"
                value={opt.value}
                label={opt.label}
                sub={opt.sub}
                defaultChecked={idx === 0}
              />
            ))}
          </div>
          <p className="mt-4 text-label-caps uppercase tracking-widest text-on-surface-variant flex items-center gap-2">
            <Icon name="lock" size={14} />
            Server-confirmed via Stripe test mode — no card data is captured here.
          </p>
        </section>

        {activeDiscounts.length > 0 && (
          <>
            <hr className="border-outline-variant" />
            <section>
              <header className="flex items-center gap-3 mb-8">
                <span className="w-8 h-8 rounded-full bg-primary text-on-primary font-display font-semibold text-sm flex items-center justify-center">
                  4
                </span>
                <h2 className="font-display font-semibold text-headline-sm text-on-surface">
                  Promotions
                </h2>
              </header>
              <div className="space-y-3">
                <RadioCard
                  name="discountType"
                  value="NO_DISCOUNT"
                  label="No promotion"
                  sub="Use the listed price without a campaign."
                  defaultChecked
                />
                {activeDiscounts.map((d) => (
                  <RadioCard
                    key={d.discountType}
                    name="discountType"
                    value={d.discountType}
                    label={prettyName(d.discountType)}
                    sub={discountSummary(d)}
                  />
                ))}
              </div>
            </section>
          </>
        )}
      </div>

      {/* Summary */}
      <aside className="lg:col-span-5">
        <div className="sticky top-28 bg-white border border-outline-variant rounded-2xl p-8 shadow-sm">
          <h2 className="font-display font-semibold text-headline-sm text-on-surface mb-8">
            Order Summary
          </h2>

          <ul className="space-y-6 mb-8">
            {cart.items.map((item) => (
              <li key={item.cartItemUuid} className="flex gap-4">
                <div className="w-20 h-20 bg-slate-50 rounded-lg overflow-hidden border border-slate-100 flex-shrink-0 flex items-center justify-center">
                  <Icon name="memory" size={28} className="text-outline" />
                </div>
                <div className="flex flex-col justify-center flex-grow">
                  <h4 className="font-display font-semibold text-base text-on-surface leading-tight">
                    {item.productName}
                  </h4>
                  <p className="text-on-surface-variant text-sm">
                    Qty {item.quantity} · {formatMoney(item.unitPrice)} each
                  </p>
                  <p className="font-display font-semibold text-sm text-secondary mt-1">
                    {formatMoney(item.lineItemTotalPrice)}
                  </p>
                </div>
              </li>
            ))}
          </ul>

          <div className="pt-8 border-t border-outline-variant space-y-4">
            <Row label="Subtotal" value={formatMoney(cart.totalPrice)} />
            <Row
              label="Shipping"
              value={
                <span className="text-secondary text-sm">
                  Calculated server-side
                </span>
              }
            />
            <Row label="Tax" value={<span className="text-on-surface-variant text-sm">Included where applicable</span>} />
            <div className="flex justify-between items-center pt-4 font-display font-semibold text-headline-sm">
              <span>Total</span>
              <span className="text-2xl">{formatMoney(cart.totalPrice)}</span>
            </div>
          </div>

          <button
            type="submit"
            disabled={pending}
            className="w-full mt-8 bg-primary text-on-primary font-display font-semibold py-4 rounded-xl hover:bg-slate-800 transition-all active:scale-[0.98] shadow-lg shadow-primary/10 disabled:opacity-60"
          >
            {pending ? "Placing order…" : "Place Order"}
          </button>

          {state && !state.ok && (
            <p
              role="alert"
              className="mt-4 text-sm text-on-error-container bg-error-container/60 px-4 py-3 rounded-lg"
            >
              {state.error}
            </p>
          )}

          <p className="mt-6 flex items-center justify-center gap-2 text-slate-400">
            <Icon name="lock" size={14} />
            <span className="font-label-caps uppercase tracking-widest text-[10px]">
              Encrypted secure transaction
            </span>
          </p>
        </div>
      </aside>
    </form>
  );
}

function Field({
  name,
  label,
  defaultValue,
  placeholder,
  required,
  span,
}: {
  name: string;
  label: string;
  defaultValue?: string;
  placeholder?: string;
  required?: boolean;
  span?: string;
}) {
  return (
    <div className={span}>
      <label
        htmlFor={name}
        className="block font-label-caps uppercase text-on-surface-variant mb-2"
      >
        {label}
      </label>
      <input
        id={name}
        name={name}
        type="text"
        required={required}
        defaultValue={defaultValue}
        placeholder={placeholder}
        className="w-full bg-white border border-outline-variant rounded-lg px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
      />
    </div>
  );
}

function RadioCard({
  name,
  value,
  label,
  sub,
  trailing,
  defaultChecked,
}: {
  name: string;
  value: string;
  label: string;
  sub: string;
  trailing?: string;
  defaultChecked?: boolean;
}) {
  return (
    <label className="relative flex items-center p-6 border border-outline-variant bg-white rounded-xl cursor-pointer hover:border-secondary transition-all has-checked:border-secondary has-checked:bg-surface-container-low">
      <input
        type="radio"
        name={name}
        value={value}
        defaultChecked={defaultChecked}
        className="absolute opacity-0 inset-0 w-full h-full cursor-pointer"
      />
      <div className="flex flex-col">
        <span className="font-display font-semibold text-on-surface">{label}</span>
        <span className="font-body text-body-md text-on-surface-variant text-sm">
          {sub}
        </span>
      </div>
      {trailing && (
        <span className="ml-auto font-display font-semibold text-secondary">
          {trailing}
        </span>
      )}
    </label>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between items-center text-body-md text-on-surface-variant">
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}

function prettyName(type: string): string {
  return type
    .toLowerCase()
    .split("_")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

function discountSummary(d: DiscountContext): string {
  switch (d.calculationType) {
    case "PERCENTAGE":
      return d.percentage
        ? `${Math.round(Number(d.percentage))}% off applicable line items.`
        : "Percentage discount on eligible items.";
    case "FIXED_AMOUNT":
      return d.fixedAmount
        ? `${formatMoney(d.fixedAmount)} off your order.`
        : "Fixed amount off your order.";
    case "BUY_ONE_GET_ONE_FREE":
      return "Buy one, get the second of equal or lesser value free.";
    default:
      return "Promotion currently active.";
  }
}

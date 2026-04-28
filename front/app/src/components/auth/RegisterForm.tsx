"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import {
  registerAction,
  type RegisterPayload,
  type RegisterResult,
} from "@/lib/actions/auth";
import type { CardType, Sex } from "@/lib/api";

/**
 * Three-step registration form.
 *
 * Why local state and not a URL `?step=`:
 *   - The form holds raw secrets (password, CVV, full PAN) that must never
 *     hit the server until the final submit. URL-driven state would survive
 *     across reloads and could leak via referrer headers.
 *   - The user expects "Back" to keep their typed values; URL navigation
 *     would force us to also persist to localStorage.
 *
 * Validation strategy:
 *   - Per-step `validateStep` short-circuits forward navigation.
 *   - The server action re-validates everything before forwarding to the
 *     backend (defence in depth — a tampered submit can't bypass us).
 */

type Step = 1 | 2 | 3;

const STEP_LABELS: Record<Step, string> = {
  1: "Identity",
  2: "Address",
  3: "Payment",
};

const SEX_OPTIONS: { value: Sex; label: string }[] = [
  { value: "M", label: "Male" },
  { value: "F", label: "Female" },
  { value: "OTHER", label: "Other / prefer not to say" },
];

const CARD_TYPES: CardType[] = ["VISA", "MASTERCARD", "AMEX"];

type FormState = Omit<RegisterPayload, "card"> & {
  passwordConfirm: string;
  withCard: boolean;
  cardHolderName: string;
  cardNumber: string;
  expiryDate: string;
  cvv: string;
  cardType: CardType;
};

const INITIAL: FormState = {
  firstName: "",
  lastName: "",
  email: "",
  username: "",
  password: "",
  passwordConfirm: "",
  birthDate: "",
  sex: "OTHER",
  street: "",
  city: "",
  zipCode: "",
  country: "",
  withCard: false,
  cardHolderName: "",
  cardNumber: "",
  expiryDate: "",
  cvv: "",
  cardType: "VISA",
};

const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validateStep(state: FormState, step: Step): string | null {
  if (step === 1) {
    if (!state.firstName.trim() || !state.lastName.trim()) return "Please enter your full name.";
    if (!EMAIL_RE.test(state.email)) return "Enter a valid email address.";
    if (state.username.trim().length < 3) return "Username must be at least 3 characters.";
    if (!PASSWORD_RE.test(state.password)) {
      return "Password must be 8+ chars with upper, lower and a digit.";
    }
    if (state.password !== state.passwordConfirm) return "Passwords don't match.";
    if (!/^\d{4}-\d{2}-\d{2}$/.test(state.birthDate)) return "Enter your birth date.";
  }
  if (step === 2) {
    if (!state.street.trim()) return "Street is required.";
    if (!state.city.trim()) return "City is required.";
    if (!state.zipCode.trim()) return "Zip code is required.";
    if (!state.country.trim()) return "Country is required.";
  }
  if (step === 3 && state.withCard) {
    if (!state.cardHolderName.trim()) return "Cardholder name is required.";
    if (!/^\d[\d\s]{12,22}$/.test(state.cardNumber)) return "Card number must be 13–19 digits.";
    if (!/^(0[1-9]|1[0-2])\/\d{4}$/.test(state.expiryDate)) return "Expiry must use MM/YYYY.";
    if (!/^\d{3,4}$/.test(state.cvv)) return "CVV must be 3 or 4 digits.";
  }
  return null;
}

export function RegisterForm() {
  const router = useRouter();
  const [step, setStep] = useState<Step>(1);
  const [state, setState] = useState<FormState>(INITIAL);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  function patch<K extends keyof FormState>(key: K, value: FormState[K]) {
    setState((prev) => ({ ...prev, [key]: value }));
  }

  function next() {
    const e = validateStep(state, step);
    if (e) {
      setError(e);
      return;
    }
    setError(null);
    setStep((s) => Math.min(3, s + 1) as Step);
  }
  function back() {
    setError(null);
    setStep((s) => Math.max(1, s - 1) as Step);
  }

  function submit() {
    const e = validateStep(state, 3);
    if (e) {
      setError(e);
      return;
    }
    setError(null);
    const payload: RegisterPayload = {
      firstName: state.firstName,
      lastName: state.lastName,
      email: state.email,
      username: state.username,
      password: state.password,
      birthDate: state.birthDate,
      sex: state.sex,
      street: state.street,
      city: state.city,
      zipCode: state.zipCode,
      country: state.country,
      card: state.withCard
        ? {
            cardHolderName: state.cardHolderName,
            cardNumber: state.cardNumber,
            expiryDate: state.expiryDate,
            cvv: state.cvv,
            cardType: state.cardType,
          }
        : null,
    };
    startTransition(async () => {
      const res: RegisterResult = await registerAction(payload);
      if (res.ok) {
        router.push("/auth/login?registered=1");
      } else {
        setError(res.error);
        // Bounce back to the relevant step if the server pinpointed a field.
        if (res.field && step === 3) {
          if (
            ["firstName", "lastName", "email", "username", "password", "birthDate", "sex"].includes(
              res.field as string,
            )
          ) {
            setStep(1);
          } else if (["street", "city", "zipCode", "country"].includes(res.field as string)) {
            setStep(2);
          }
        }
      }
    });
  }

  return (
    <div className="bg-white border border-slate-100 rounded-2xl shadow-sm p-8 md:p-12">
      <Stepper current={step} />

      {error && (
        <div
          role="alert"
          className="mt-6 mb-2 text-sm text-on-error-container bg-error-container/60 px-4 py-3 rounded-lg"
        >
          {error}
        </div>
      )}

      <div className="mt-8">
        {step === 1 && <StepIdentity state={state} patch={patch} />}
        {step === 2 && <StepAddress state={state} patch={patch} />}
        {step === 3 && <StepPayment state={state} patch={patch} />}
      </div>

      <div className="mt-10 flex flex-col-reverse sm:flex-row sm:justify-between gap-3 sm:items-center">
        <div className="text-sm text-on-surface-variant">
          Already have an account?{" "}
          <Link href="/auth/login" className="text-secondary hover:underline">
            Sign in
          </Link>
        </div>
        <div className="flex gap-3">
          {step > 1 && (
            <button
              type="button"
              onClick={back}
              disabled={pending}
              className="inline-flex items-center justify-center gap-2 h-12 px-6 border border-outline font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors disabled:opacity-60"
            >
              <Icon name="arrow_back" size={16} /> Back
            </button>
          )}
          {step < 3 ? (
            <button
              type="button"
              onClick={next}
              disabled={pending}
              className="inline-flex items-center justify-center gap-2 h-12 px-8 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors disabled:opacity-60"
            >
              Continue <Icon name="arrow_forward" size={16} />
            </button>
          ) : (
            <button
              type="button"
              onClick={submit}
              disabled={pending}
              className="inline-flex items-center justify-center gap-2 h-12 px-8 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors disabled:opacity-60"
            >
              {pending ? "Creating account…" : "Create account"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function Stepper({ current }: { current: Step }) {
  return (
    <ol className="flex items-center gap-3" aria-label="Registration progress">
      {([1, 2, 3] as Step[]).map((s, i) => {
        const active = s === current;
        const done = s < current;
        return (
          <li key={s} className="flex items-center gap-3 flex-1">
            <span
              className={[
                "w-8 h-8 rounded-full flex items-center justify-center font-display text-sm font-semibold flex-shrink-0",
                done
                  ? "bg-secondary text-on-secondary"
                  : active
                    ? "bg-primary text-on-primary"
                    : "bg-slate-100 text-on-surface-variant",
              ].join(" ")}
              aria-current={active ? "step" : undefined}
            >
              {done ? <Icon name="check" size={16} /> : s}
            </span>
            <span
              className={[
                "font-label-caps uppercase tracking-wider text-xs hidden sm:inline",
                active ? "text-primary" : "text-on-surface-variant",
              ].join(" ")}
            >
              {STEP_LABELS[s]}
            </span>
            {i < 2 && (
              <span
                aria-hidden="true"
                className={[
                  "h-px flex-1",
                  s < current ? "bg-secondary" : "bg-slate-200",
                ].join(" ")}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}

type StepProps = {
  state: FormState;
  patch: <K extends keyof FormState>(key: K, value: FormState[K]) => void;
};

function StepIdentity({ state, patch }: StepProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <Field
        label="First name"
        name="firstName"
        value={state.firstName}
        onChange={(v) => patch("firstName", v)}
        autoComplete="given-name"
        required
      />
      <Field
        label="Last name"
        name="lastName"
        value={state.lastName}
        onChange={(v) => patch("lastName", v)}
        autoComplete="family-name"
        required
      />
      <Field
        span="md:col-span-2"
        label="Email"
        name="email"
        type="email"
        value={state.email}
        onChange={(v) => patch("email", v)}
        autoComplete="email"
        required
      />
      <Field
        label="Username"
        name="username"
        value={state.username}
        onChange={(v) => patch("username", v)}
        autoComplete="username"
        required
      />
      <Field
        label="Birth date"
        name="birthDate"
        type="date"
        value={state.birthDate}
        onChange={(v) => patch("birthDate", v)}
        autoComplete="bday"
        required
      />
      <Field
        label="Password"
        name="password"
        type="password"
        value={state.password}
        onChange={(v) => patch("password", v)}
        autoComplete="new-password"
        hint="Min 8 chars with upper, lower and a digit."
        required
      />
      <Field
        label="Confirm password"
        name="passwordConfirm"
        type="password"
        value={state.passwordConfirm}
        onChange={(v) => patch("passwordConfirm", v)}
        autoComplete="new-password"
        required
      />
      <div className="md:col-span-2">
        <label
          htmlFor="sex"
          className="block font-label-caps uppercase text-on-surface-variant mb-2 text-xs"
        >
          Gender
        </label>
        <select
          id="sex"
          value={state.sex}
          onChange={(e) => patch("sex", e.target.value as Sex)}
          className="w-full bg-white border border-outline-variant rounded-lg px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
        >
          {SEX_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

function StepAddress({ state, patch }: StepProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <Field
        span="md:col-span-2"
        label="Street address"
        name="street"
        value={state.street}
        onChange={(v) => patch("street", v)}
        autoComplete="street-address"
        required
      />
      <Field
        label="City"
        name="city"
        value={state.city}
        onChange={(v) => patch("city", v)}
        autoComplete="address-level2"
        required
      />
      <Field
        label="Zip code"
        name="zipCode"
        value={state.zipCode}
        onChange={(v) => patch("zipCode", v)}
        autoComplete="postal-code"
        required
      />
      <Field
        span="md:col-span-2"
        label="Country"
        name="country"
        value={state.country}
        onChange={(v) => patch("country", v)}
        autoComplete="country-name"
        required
      />
    </div>
  );
}

function StepPayment({ state, patch }: StepProps) {
  return (
    <div className="space-y-6">
      <label className="flex items-start gap-3 cursor-pointer p-4 border border-outline-variant rounded-lg hover:bg-slate-50/60 transition-colors">
        <input
          type="checkbox"
          checked={state.withCard}
          onChange={(e) => patch("withCard", e.target.checked)}
          className="mt-1 h-4 w-4 accent-primary"
        />
        <span>
          <span className="block font-display text-sm font-semibold text-primary">
            Save a payment method now
          </span>
          <span className="block text-sm text-on-surface-variant">
            Optional — you can always add cards later from your account.
          </span>
        </span>
      </label>

      {state.withCard && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Field
            span="md:col-span-2"
            label="Cardholder name"
            name="cardHolderName"
            value={state.cardHolderName}
            onChange={(v) => patch("cardHolderName", v)}
            autoComplete="cc-name"
          />
          <Field
            span="md:col-span-2"
            label="Card number"
            name="cardNumber"
            value={state.cardNumber}
            onChange={(v) => patch("cardNumber", v)}
            placeholder="4242 4242 4242 4242"
            inputMode="numeric"
            autoComplete="cc-number"
          />
          <Field
            label="Expiry (MM/YYYY)"
            name="expiryDate"
            value={state.expiryDate}
            onChange={(v) => patch("expiryDate", v)}
            placeholder="08/2029"
            autoComplete="cc-exp"
          />
          <Field
            label="CVV"
            name="cvv"
            type="password"
            value={state.cvv}
            onChange={(v) => patch("cvv", v)}
            inputMode="numeric"
            autoComplete="cc-csc"
          />
          <div className="md:col-span-2">
            <label
              htmlFor="cardType"
              className="block font-label-caps uppercase text-on-surface-variant mb-2 text-xs"
            >
              Card type
            </label>
            <select
              id="cardType"
              value={state.cardType}
              onChange={(e) => patch("cardType", e.target.value as CardType)}
              className="w-full bg-white border border-outline-variant rounded-lg px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
            >
              {CARD_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}
    </div>
  );
}

function Field({
  span,
  label,
  name,
  value,
  onChange,
  type = "text",
  placeholder,
  required,
  autoComplete,
  inputMode,
  hint,
}: {
  span?: string;
  label: string;
  name: string;
  value: string;
  onChange: (v: string) => void;
  type?: "text" | "email" | "password" | "date";
  placeholder?: string;
  required?: boolean;
  autoComplete?: string;
  inputMode?: "numeric" | "text";
  hint?: string;
}) {
  return (
    <div className={span}>
      <label
        htmlFor={name}
        className="block font-label-caps uppercase text-on-surface-variant mb-2 text-xs"
      >
        {label}
      </label>
      <input
        id={name}
        name={name}
        type={type}
        required={required}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        autoComplete={autoComplete}
        inputMode={inputMode}
        className="w-full bg-white border border-outline-variant rounded-lg px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
      />
      {hint && (
        <p className="mt-1 text-xs text-on-surface-variant">{hint}</p>
      )}
    </div>
  );
}

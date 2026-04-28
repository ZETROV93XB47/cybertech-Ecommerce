"use client";

import Link from "next/link";
import { useActionState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { updateProfileAction } from "@/lib/actions/user";
import type { UserResponseDto } from "@/lib/api";

/**
 * Self-service profile edit form. Wires up to PATCH /user/me via a
 * Server Action — when the backend route isn't live yet the action
 * returns `unavailable: true` and we render a soft "rolling out shortly"
 * banner instead of a hard error.
 */

type FormState =
  | { ok: true }
  | { ok: false; error: string; unavailable?: boolean }
  | undefined;

interface ProfileEditFormProps {
  user: UserResponseDto | null;
  fallback: { firstName: string; lastName: string; email: string };
}

const SEX_OPTIONS = [
  { value: "", label: "Prefer not to say" },
  { value: "M", label: "Male" },
  { value: "F", label: "Female" },
  { value: "OTHER", label: "Other" },
] as const;

export function ProfileEditForm({ user, fallback }: ProfileEditFormProps) {
  const router = useRouter();
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    async (_prev, formData) => await updateProfileAction(formData),
    undefined,
  );

  useEffect(() => {
    if (state && state.ok) router.push("/account/profile");
  }, [state, router]);

  const firstName = user?.firstName ?? fallback.firstName;
  const lastName = user?.lastName ?? fallback.lastName;
  const street = user?.address?.street ?? "";
  const city = user?.address?.city ?? "";
  const zip = user?.address?.zipCode ?? "";
  const country = user?.address?.country ?? "";
  const sex = user?.sex ?? "";

  return (
    <form
      action={formAction}
      className="bg-white border border-slate-100 p-8 flex flex-col gap-8"
    >
      <fieldset className="flex flex-col gap-4">
        <legend className="font-display text-headline-sm text-primary mb-2">
          Identity
        </legend>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field
            name="firstName"
            label="First name"
            defaultValue={firstName}
            autoComplete="given-name"
            required
          />
          <Field
            name="lastName"
            label="Last name"
            defaultValue={lastName}
            autoComplete="family-name"
            required
          />
        </div>
        <div>
          <label
            htmlFor="sex"
            className="block font-label-caps uppercase text-on-surface-variant text-xs mb-2"
          >
            Salutation
          </label>
          <select
            id="sex"
            name="sex"
            defaultValue={sex}
            className="w-full md:w-1/2 bg-white border border-outline-variant px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
          >
            {SEX_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </fieldset>

      <fieldset className="flex flex-col gap-4">
        <legend className="font-display text-headline-sm text-primary mb-2">
          Default address
        </legend>
        <Field
          name="street"
          label="Street"
          defaultValue={street}
          autoComplete="street-address"
          placeholder="123 Performance Way"
        />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Field
            name="zipCode"
            label="Zip code"
            defaultValue={zip}
            autoComplete="postal-code"
          />
          <Field
            name="city"
            label="City"
            defaultValue={city}
            autoComplete="address-level2"
          />
          <Field
            name="country"
            label="Country"
            defaultValue={country}
            autoComplete="country-name"
          />
        </div>
      </fieldset>

      {state && !state.ok && (
        <p
          role="alert"
          className={`text-sm px-4 py-3 ${
            state.unavailable
              ? "bg-amber-50 text-amber-800 border border-amber-200"
              : "bg-error-container/60 text-on-error-container"
          }`}
        >
          {state.error}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={pending}
          className="inline-flex items-center justify-center gap-2 h-12 px-8 bg-primary text-on-primary font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-800 transition-colors disabled:opacity-60"
        >
          <Icon name="save" size={18} />
          {pending ? "Saving…" : "Save changes"}
        </button>
        <Link
          href="/account/profile"
          className="inline-flex items-center justify-center h-12 px-6 font-display text-sm font-semibold uppercase tracking-wider text-on-surface hover:underline"
        >
          Cancel
        </Link>
      </div>

      <p className="font-body text-xs text-on-surface-variant">
        Email and username changes go through Keycloak and aren&apos;t editable
        here yet.
      </p>
    </form>
  );
}

function Field({
  name,
  label,
  ...rest
}: React.InputHTMLAttributes<HTMLInputElement> & { name: string; label: string }) {
  return (
    <div>
      <label
        htmlFor={name}
        className="block font-label-caps uppercase text-on-surface-variant text-xs mb-2"
      >
        {label}
      </label>
      <input
        id={name}
        name={name}
        type="text"
        className="w-full bg-white border border-outline-variant px-4 py-3 text-body-md focus:outline-none focus:ring-2 focus:ring-secondary/30"
        {...rest}
      />
    </div>
  );
}

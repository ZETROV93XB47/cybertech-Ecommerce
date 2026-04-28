"use client";

import { useActionState } from "react";
import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import type { UserResponseDto } from "@/lib/api";

type Mode = "create" | "edit";

type FormState = { ok: false; error: string } | { ok: true } | null;

interface Props {
  mode: Mode;
  action: (state: FormState, formData: FormData) => Promise<FormState>;
  initial?: UserResponseDto;
}

export function UserForm({ mode, action, initial }: Props) {
  const [state, formAction, isPending] = useActionState<FormState, FormData>(
    action,
    null,
  );

  return (
    <form action={formAction} className="flex flex-col gap-6 max-w-3xl">
      {state && !state.ok && (
        <div
          role="alert"
          className="rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-sm px-4 py-3"
        >
          {state.error}
        </div>
      )}

      <Section title="Identity">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field
            label="First name"
            name="firstName"
            required
            defaultValue={initial?.firstName}
          />
          <Field
            label="Last name"
            name="lastName"
            required
            defaultValue={initial?.lastName}
          />
          {mode === "create" && (
            <Field
              label="Username"
              name="username"
              required
              defaultValue={initial?.username}
            />
          )}
          <Field
            label="Email"
            name="email"
            type="email"
            required
            defaultValue={initial?.email}
          />
          <Select
            label="Sex"
            name="sex"
            required
            defaultValue={initial?.sex}
            options={[
              { value: "M", label: "Male" },
              { value: "F", label: "Female" },
              { value: "OTHER", label: "Other" },
            ]}
          />
          {mode === "create" && (
            <Field
              label="Birth date"
              name="birthDate"
              type="date"
              required
              hint="Format: YYYY-MM-DD"
            />
          )}
        </div>
      </Section>

      {mode === "create" && (
        <Section title="Credentials">
          <Field
            label="Password"
            name="password"
            type="password"
            required
            minLength={8}
            hint="Minimum 8 characters. The user can change it after first sign-in."
          />
        </Section>
      )}

      <Section title="Address">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field
            label="Street"
            name="addressStreet"
            required={mode === "create"}
            defaultValue={initial?.address?.street}
          />
          <Field
            label="City"
            name="addressCity"
            required={mode === "create"}
            defaultValue={initial?.address?.city}
          />
          <Field
            label="Zip code"
            name="addressZipCode"
            required={mode === "create"}
            defaultValue={initial?.address?.zipCode}
          />
          <Field
            label="Country"
            name="addressCountry"
            required={mode === "create"}
            defaultValue={initial?.address?.country}
          />
        </div>
      </Section>

      <div className="flex items-center gap-3">
        <button
          type="submit"
          disabled={isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-primary text-white px-5 py-2.5 text-sm font-semibold hover:opacity-90 disabled:opacity-50 transition-opacity"
        >
          {isPending && <Icon name="progress_activity" size={16} />}
          {mode === "create" ? "Create user" : "Save changes"}
        </button>
        <Link
          href="/admin/users"
          className="text-sm text-slate-600 hover:text-slate-900 transition-colors"
        >
          Cancel
        </Link>
      </div>
    </form>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <fieldset className="rounded-xl border border-slate-200 bg-white p-5 flex flex-col gap-4">
      <legend className="px-2 -ml-2 font-label-caps uppercase tracking-wider text-xs text-slate-500">
        {title}
      </legend>
      {children}
    </fieldset>
  );
}

function Field({
  label,
  hint,
  name,
  ...rest
}: {
  label: string;
  hint?: string;
  name: string;
} & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm font-medium text-slate-700">
        {label}
        {rest.required && <span className="text-rose-500"> *</span>}
      </span>
      <input
        name={name}
        {...rest}
        className="border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
      />
      {hint && <span className="text-xs text-slate-500">{hint}</span>}
    </label>
  );
}

function Select({
  label,
  name,
  options,
  defaultValue,
  required,
}: {
  label: string;
  name: string;
  options: Array<{ value: string; label: string }>;
  defaultValue?: string;
  required?: boolean;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm font-medium text-slate-700">
        {label}
        {required && <span className="text-rose-500"> *</span>}
      </span>
      <select
        name={name}
        required={required}
        defaultValue={defaultValue ?? ""}
        className="border border-slate-200 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30"
      >
        <option value="" disabled>
          Choose…
        </option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

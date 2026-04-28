"use client";

import { useActionState } from "react";
import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import type { ProductResponseDto } from "@/lib/api";

type Mode = "create" | "edit";

type FormState = { ok: false; error: string } | { ok: true } | null;

interface Props {
  mode: Mode;
  /** Server action; for create it accepts FormData, for edit it's bound to the uuid. */
  action: (state: FormState, formData: FormData) => Promise<FormState>;
  initial?: ProductResponseDto;
  /** Backend allowlist (matches BUG-082/083 multipart filter). */
  acceptedImageTypes?: string;
}

const CATEGORIES = ["COMPUTER", "MONITOR", "SMARTPHONE", "KEYBOARD"];

export function ProductForm({
  mode,
  action,
  initial,
  acceptedImageTypes = "image/jpeg,image/png,image/webp,image/gif",
}: Props) {
  const [state, formAction, isPending] = useActionState<FormState, FormData>(
    action,
    null,
  );

  const initialAttrs = initial?.attributes
    ? JSON.stringify(initial.attributes, null, 2)
    : "{}";

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

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Field label="Name" name="name" required defaultValue={initial?.name} />
        <Field
          label="Brand"
          name="brand"
          required
          defaultValue={initial?.brand}
        />
        <SelectField
          label="Category"
          name="category"
          required
          defaultValue={initial?.category}
          options={CATEGORIES.map((c) => ({ value: c, label: c }))}
        />
        <Field
          label="Price (USD)"
          name="price"
          required
          type="number"
          step="0.01"
          min="0"
          defaultValue={initial?.price}
        />
      </div>

      <Field
        label="Photo URL"
        name="photoUrl"
        type="url"
        defaultValue={initial?.photoUrl}
        hint="Optional. Leave blank if you upload a file below."
      />

      {mode === "create" && (
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-slate-700">
            Product image
          </label>
          <input
            type="file"
            name="image"
            accept={acceptedImageTypes}
            className="text-sm text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-primary file:text-white file:px-3 file:py-1.5 file:text-sm file:font-medium hover:file:opacity-90 cursor-pointer"
          />
          <p className="text-xs text-slate-500">
            JPEG, PNG, WebP or GIF. Max 10 MB. When provided, this overrides
            the photo URL above.
          </p>
        </div>
      )}

      <TextareaField
        label="Description"
        name="description"
        required
        rows={4}
        defaultValue={initial?.description}
      />

      <TextareaField
        label="Attributes (JSON)"
        name="attributes"
        rows={6}
        defaultValue={initialAttrs}
        hint="Free-form JSON object: e.g. screen, battery, weight."
        className="font-mono text-xs"
      />

      <div className="flex items-center gap-3">
        <button
          type="submit"
          disabled={isPending}
          className="inline-flex items-center gap-2 rounded-lg bg-primary text-white px-5 py-2.5 text-sm font-semibold hover:opacity-90 disabled:opacity-50 transition-opacity"
        >
          {isPending && <Icon name="progress_activity" size={16} />}
          {mode === "create" ? "Create product" : "Save changes"}
        </button>
        <Link
          href="/admin/products"
          className="text-sm text-slate-600 hover:text-slate-900 transition-colors"
        >
          Cancel
        </Link>
      </div>
    </form>
  );
}

// ---------- Internal field primitives ----------

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

function SelectField({
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
        defaultValue={defaultValue}
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

function TextareaField({
  label,
  hint,
  name,
  className,
  ...rest
}: {
  label: string;
  hint?: string;
  name: string;
} & React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm font-medium text-slate-700">
        {label}
        {rest.required && <span className="text-rose-500"> *</span>}
      </span>
      <textarea
        name={name}
        {...rest}
        className={
          "border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 " +
          (className ?? "")
        }
      />
      {hint && <span className="text-xs text-slate-500">{hint}</span>}
    </label>
  );
}

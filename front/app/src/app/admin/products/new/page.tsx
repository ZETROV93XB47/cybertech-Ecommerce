import Link from "next/link";
import { ProductForm } from "@/components/admin/ProductForm";
import { Breadcrumbs } from "@/components/ui/Breadcrumbs";
import { createProductAction } from "@/lib/actions/admin";

export const metadata = { title: "Admin — New product" };

export default function AdminNewProductPage() {
  // useActionState expects (state, formData) => Promise<state>. Our action
  // returns ActionResult or redirects on success — wrap so the form can
  // surface errors inline. Successful redirects throw and never resolve.
  async function action(
    _state: { ok: false; error: string } | { ok: true } | null,
    formData: FormData,
  ) {
    "use server";
    const result = await createProductAction(formData);
    return result;
  }

  return (
    <div className="flex flex-col gap-6 max-w-3xl">
      <Breadcrumbs
        items={[
          { label: "Admin", href: "/admin" },
          { label: "Products", href: "/admin/products" },
          { label: "New product" },
        ]}
      />
      <header>
        <h1 className="font-display text-3xl font-bold text-slate-900">
          New product
        </h1>
        <p className="text-slate-600 mt-1">
          Create a product. Upload an image to use the multipart endpoint, or
          paste a URL to skip the upload.
        </p>
      </header>
      <ProductForm mode="create" action={action} />
      <p className="text-xs text-slate-500">
        Need to bulk import?{" "}
        <Link href="/admin/products" className="underline">
          Back to product list
        </Link>
        .
      </p>
    </div>
  );
}

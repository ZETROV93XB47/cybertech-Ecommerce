import { notFound } from "next/navigation";
import { ApiError, productApi } from "@/lib/api";
import { ProductForm } from "@/components/admin/ProductForm";
import { Breadcrumbs } from "@/components/ui/Breadcrumbs";
import { updateProductAction } from "@/lib/actions/admin";

export const metadata = { title: "Admin — Edit product" };
export const dynamic = "force-dynamic";

export default async function AdminEditProductPage({
  params,
}: {
  params: Promise<{ uuid: string }>;
}) {
  const { uuid } = await params;

  let product;
  try {
    product = await productApi.get(uuid);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  async function action(
    _state: { ok: false; error: string } | { ok: true } | null,
    formData: FormData,
  ) {
    "use server";
    return updateProductAction(uuid, formData);
  }

  return (
    <div className="flex flex-col gap-6 max-w-3xl">
      <Breadcrumbs
        items={[
          { label: "Admin", href: "/admin" },
          { label: "Products", href: "/admin/products" },
          { label: product.name },
        ]}
      />
      <header>
        <h1 className="font-display text-3xl font-bold text-slate-900 truncate">
          Edit “{product.name}”
        </h1>
        <p className="text-slate-600 mt-1 font-mono text-xs">{product.uuid}</p>
      </header>
      <ProductForm mode="edit" action={action} initial={product} />
    </div>
  );
}

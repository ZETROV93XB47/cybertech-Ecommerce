import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import { DeleteRowButton } from "./DeleteRowButton";
import { deleteProductAction } from "@/lib/actions/admin";

export function ProductRowActions({
  uuid,
  name,
}: {
  uuid: string;
  name: string;
}) {
  return (
    <div className="flex items-center justify-end gap-1">
      <Link
        href={`/admin/products/${uuid}/edit`}
        aria-label={`Edit ${name}`}
        className="p-1.5 rounded-md text-slate-600 hover:bg-slate-100 transition-colors"
      >
        <Icon name="edit" size={18} />
      </Link>
      <DeleteRowButton
        action={async () => {
          "use server";
          return deleteProductAction(uuid);
        }}
        label={`product "${name}"`}
        description="The product will be removed from the catalog. Existing orders are preserved."
      />
    </div>
  );
}

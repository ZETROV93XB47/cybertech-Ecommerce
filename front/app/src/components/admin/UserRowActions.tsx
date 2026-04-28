import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import { DeleteRowButton } from "./DeleteRowButton";
import { deleteUserAction } from "@/lib/actions/admin";

export function UserRowActions({
  uuid,
  label,
}: {
  uuid: string;
  label: string;
}) {
  return (
    <div className="flex items-center justify-end gap-1">
      <Link
        href={`/admin/users/${uuid}/edit`}
        aria-label={`Edit ${label}`}
        className="p-1.5 rounded-md text-slate-600 hover:bg-slate-100 transition-colors"
      >
        <Icon name="edit" size={18} />
      </Link>
      <DeleteRowButton
        action={async () => {
          "use server";
          return deleteUserAction(uuid);
        }}
        label={`user ${label}`}
        description="The user account and its Keycloak entry will be removed."
      />
    </div>
  );
}

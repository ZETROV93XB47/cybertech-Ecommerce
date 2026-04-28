import { notFound } from "next/navigation";
import { ApiError, userApi } from "@/lib/api";
import { UserForm } from "@/components/admin/UserForm";
import { Breadcrumbs } from "@/components/ui/Breadcrumbs";
import { updateUserAction } from "@/lib/actions/admin";

export const metadata = { title: "Admin — Edit user" };
export const dynamic = "force-dynamic";

export default async function AdminEditUserPage({
  params,
}: {
  params: Promise<{ uuid: string }>;
}) {
  const { uuid } = await params;

  let user;
  try {
    user = await userApi.get(uuid);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  async function action(
    _state: { ok: false; error: string } | { ok: true } | null,
    formData: FormData,
  ) {
    "use server";
    return updateUserAction(uuid, formData);
  }

  return (
    <div className="flex flex-col gap-6 max-w-3xl">
      <Breadcrumbs
        items={[
          { label: "Admin", href: "/admin" },
          { label: "Users", href: "/admin/users" },
          { label: `${user.firstName} ${user.lastName}` },
        ]}
      />
      <header>
        <h1 className="font-display text-3xl font-bold text-slate-900 truncate">
          {user.firstName} {user.lastName}
        </h1>
        <p className="text-slate-600 mt-1 font-mono text-xs">{user.uuid}</p>
      </header>
      <UserForm mode="edit" action={action} initial={user} />
    </div>
  );
}

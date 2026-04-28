import { UserForm } from "@/components/admin/UserForm";
import { Breadcrumbs } from "@/components/ui/Breadcrumbs";
import { createUserAction } from "@/lib/actions/admin";

export const metadata = { title: "Admin — New user" };

export default function AdminNewUserPage() {
  async function action(
    _state: { ok: false; error: string } | { ok: true } | null,
    formData: FormData,
  ) {
    "use server";
    return createUserAction(formData);
  }

  return (
    <div className="flex flex-col gap-6 max-w-3xl">
      <Breadcrumbs
        items={[
          { label: "Admin", href: "/admin" },
          { label: "Users", href: "/admin/users" },
          { label: "New user" },
        ]}
      />
      <header>
        <h1 className="font-display text-3xl font-bold text-slate-900">
          New user
        </h1>
        <p className="text-slate-600 mt-1">
          The account is created in Keycloak and synced into the application
          database.
        </p>
      </header>
      <UserForm mode="create" action={action} />
    </div>
  );
}

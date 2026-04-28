import Link from "next/link";
import { ApiError, userApi } from "@/lib/api";
import type { Page, UserResponseDto } from "@/lib/api";
import { AdminTable } from "@/components/admin/AdminTable";
import { Pagination } from "@/components/ui/Pagination";
import { Icon } from "@/components/ui/Icon";
import { UserRowActions } from "@/components/admin/UserRowActions";

export const metadata = { title: "Admin — Users" };
export const dynamic = "force-dynamic";

const PAGE_SIZE = 20;

function readString(v: string | string[] | undefined): string | undefined {
  if (Array.isArray(v)) return v[0];
  return v;
}

function readNumber(v: string | string[] | undefined): number | undefined {
  const s = readString(v);
  if (s === undefined || s === "") return undefined;
  const n = Number(s);
  return Number.isFinite(n) ? n : undefined;
}

export default async function AdminUsersPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const page = readNumber(params.page) ?? 0;
  const q = (readString(params.q) ?? "").trim().toLowerCase();

  let result: Page<UserResponseDto> | null = null;
  let errorMessage: string | null = null;
  try {
    result = await userApi.listAll(page, PAGE_SIZE);
  } catch (err) {
    errorMessage =
      err instanceof ApiError ? err.message : "Could not load users.";
  }

  const all = result?.content ?? [];
  // Backend has no full-text search on users — filter the current page
  // client-side so the search box still feels useful within the page window.
  const users = q
    ? all.filter((u) =>
        [u.email, u.firstName, u.lastName, u.username]
          .filter(Boolean)
          .some((s) => s.toLowerCase().includes(q)),
      )
    : all;

  const totalPages = result?.totalPages ?? 0;
  const total = result?.totalElements ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <p className="font-label-caps uppercase tracking-[0.2em] text-xs text-slate-500">
            Customers
          </p>
          <h1 className="font-display text-3xl font-bold text-slate-900 mt-1">
            Users
          </h1>
          <p className="text-slate-600 mt-1">
            {total} {total === 1 ? "user" : "users"} registered.
          </p>
        </div>
        <Link
          href="/admin/users/new"
          className="inline-flex items-center gap-2 rounded-lg bg-primary text-white px-4 py-2 text-sm font-semibold hover:opacity-90 transition-opacity self-start sm:self-auto"
        >
          <Icon name="add" size={18} />
          Add user
        </Link>
      </header>

      <form method="get" className="flex flex-wrap items-center gap-2">
        <input
          type="search"
          name="q"
          defaultValue={q}
          placeholder="Search this page (email, name, username)…"
          className="flex-1 min-w-[220px] bg-white border border-slate-200 rounded-lg text-sm py-1.5 px-3 focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
        <button
          type="submit"
          className="px-3 py-1.5 text-sm font-medium rounded-lg border border-slate-200 hover:bg-slate-50"
        >
          Search
        </button>
      </form>

      {errorMessage ? (
        <div className="p-6 rounded-xl bg-rose-50 text-rose-700 text-sm">
          {errorMessage}
        </div>
      ) : (
        <>
          <AdminTable<UserResponseDto>
            rowKey={(u) => u.uuid}
            emptyMessage={
              q
                ? "No users match your search on this page."
                : "No users yet."
            }
            columns={[
              {
                key: "name",
                header: "Name",
                cell: (u) => (
                  <div className="min-w-0">
                    <p className="font-semibold text-slate-900 truncate">
                      {u.firstName} {u.lastName}
                    </p>
                    <p className="text-xs text-slate-500 truncate">
                      @{u.username}
                    </p>
                  </div>
                ),
              },
              { key: "email", header: "Email", nowrap: true },
              {
                key: "role",
                header: "Role",
                cell: (u) => (
                  <span
                    className={
                      "inline-flex px-2 py-0.5 rounded-full text-xs font-semibold " +
                      (u.role === "ADMIN"
                        ? "bg-violet-100 text-violet-700"
                        : "bg-slate-100 text-slate-700")
                    }
                  >
                    {u.role}
                  </span>
                ),
                nowrap: true,
              },
              {
                key: "actions",
                header: <span className="sr-only">Actions</span>,
                cell: (u) => (
                  <UserRowActions
                    uuid={u.uuid}
                    label={`${u.firstName} ${u.lastName}`}
                  />
                ),
                className: "text-right",
                headerClassName: "text-right",
                nowrap: true,
              },
            ]}
            rows={users}
          />
          <Pagination
            current={page}
            total={totalPages}
            baseHref="/admin/users"
            searchParams={params}
          />
        </>
      )}
    </div>
  );
}

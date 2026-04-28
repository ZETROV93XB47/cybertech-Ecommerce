import Link from "next/link";
import { auth, signOut } from "@/lib/auth";
import { Icon } from "@/components/ui/Icon";

/**
 * Minimal admin top bar — distinct from the consumer-facing Header.
 * Brand on the left, "Back to store" + sign-out on the right. Sits sticky
 * so the surface beneath scrolls under it.
 */
export async function AdminHeader() {
  const session = await auth();
  const email = session?.user?.email ?? null;

  return (
    <header className="sticky top-0 z-40 h-16 bg-slate-900 text-white border-b border-slate-800">
      <div className="h-full flex items-center justify-between px-4 md:px-6">
        <Link href="/admin" className="flex items-center gap-2 font-bold text-lg">
          <Icon name="admin_panel_settings" size={22} />
          Cybertech Admin
        </Link>
        <div className="flex items-center gap-3 md:gap-5 text-sm">
          <Link
            href="/"
            className="hidden sm:inline-flex items-center gap-1 text-slate-300 hover:text-white transition-colors"
          >
            <Icon name="storefront" size={18} />
            Back to store
          </Link>
          {email && (
            <span className="hidden md:inline text-slate-400 truncate max-w-[18ch]">
              {email}
            </span>
          )}
          <form
            action={async () => {
              "use server";
              await signOut({ redirectTo: "/" });
            }}
          >
            <button
              type="submit"
              className="inline-flex items-center gap-1 rounded-lg bg-slate-800 hover:bg-slate-700 px-3 py-1.5 text-sm font-medium transition-colors"
            >
              <Icon name="logout" size={16} />
              Sign out
            </button>
          </form>
        </div>
      </div>
    </header>
  );
}

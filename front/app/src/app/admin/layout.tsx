import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { AdminHeader } from "@/components/admin/AdminHeader";
import {
  AdminSidebar,
  AdminSidebarMobile,
} from "@/components/admin/AdminSidebar";

export const metadata: Metadata = {
  title: "Admin — Cybertech",
  robots: { index: false, follow: false },
};

/**
 * `/admin/**` is already gated by `proxy.ts` (role === "ADMIN"). We re-check
 * here defensively so a request that somehow bypasses the proxy (e.g. proxy
 * disabled at the deploy edge, or a session that loses role mid-session) is
 * still rejected before any data fetch fires.
 */
export default async function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await auth();
  if (!session?.user) {
    redirect("/api/auth/signin?callbackUrl=/admin");
  }
  if (session.user.role !== "ADMIN") {
    redirect("/account/profile");
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <AdminHeader />
      <AdminSidebarMobile />
      <div className="flex">
        <AdminSidebar />
        <main className="flex-1 min-w-0 px-4 md:px-8 py-8">{children}</main>
      </div>
    </div>
  );
}

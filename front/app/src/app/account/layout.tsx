import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";

/**
 * Layout for /account/**.
 *
 * Acts as an auth gate for the entire account surface — every nested page
 * is gated behind a session check and bounced through Keycloak otherwise.
 *
 * We do NOT wrap children in MainLayout here because the existing
 * /account/profile page renders its own MainLayout. Instead, each child
 * page composes MainLayout via the shared `AccountFrame` component
 * (or its own bespoke layout in the case of profile).
 */

export const dynamic = "force-dynamic";

export default async function AccountLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await auth();
  if (!session) redirect("/api/auth/signin?callbackUrl=/account/profile");

  return <>{children}</>;
}

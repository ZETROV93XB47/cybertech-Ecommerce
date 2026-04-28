import Link from "next/link";
import { auth } from "@/lib/auth";
import { ApiError, cartApi, userApi } from "@/lib/api";
import type { CartResponseDto, UserResponseDto } from "@/lib/api";
import { AccountFrame } from "@/components/account/AccountFrame";
import { ProfileEditForm } from "@/components/account/ProfileEditForm";
import { Icon } from "@/components/ui/Icon";

export const metadata = { title: "Edit profile — Cybertech" };
export const dynamic = "force-dynamic";

/**
 * Profile edit page.
 *
 * Resolves the user's current details so we can prefill the form.
 * Without a /me endpoint we lean on /cart/get to recover the user uuid
 * and then call /user/get/{uuid} — exactly what /account/profile already
 * does. If either call fails we still render the form, just with empty
 * defaults pulled from the session.
 */
async function loadUser(): Promise<UserResponseDto | null> {
  let cart: CartResponseDto | null = null;
  try {
    cart = await cartApi.getMine();
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 404) {
      console.warn("[account/profile/edit] cart.getMine failed", err);
    }
    return null;
  }
  if (!cart?.userUuid) return null;
  try {
    return await userApi.get(cart.userUuid);
  } catch (err) {
    console.warn("[account/profile/edit] userApi.get failed", err);
    return null;
  }
}

export default async function ProfileEditPage() {
  const session = await auth();
  const user = await loadUser();
  const sessionName = session?.user?.name ?? "";
  const [firstFromSession = "", lastFromSession = ""] = sessionName.split(" ");

  return (
    <AccountFrame
      eyebrow="Account"
      title="Edit profile"
      description="Update your identity and default shipping address. Email changes go through Keycloak."
      actions={
        <Link
          href="/account/profile"
          className="inline-flex items-center gap-2 h-10 px-4 border border-outline font-display text-xs font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors"
        >
          <Icon name="arrow_back" size={14} />
          Back to profile
        </Link>
      }
    >
      <ProfileEditForm
        user={user}
        fallback={{
          firstName: firstFromSession,
          lastName: lastFromSession,
          email: session?.user?.email ?? "",
        }}
      />
    </AccountFrame>
  );
}

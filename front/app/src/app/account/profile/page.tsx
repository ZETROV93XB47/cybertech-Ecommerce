import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import { MainLayout } from "@/components/layout/MainLayout";
import { ProfileSummary } from "@/components/account/ProfileSummary";
import type { ProfileFallback } from "@/components/account/ProfileSummary";
import { BankCardList } from "@/components/account/BankCardList";
import { RecentOrders } from "@/components/account/RecentOrders";
import { WishlistPreview } from "@/components/account/WishlistPreview";
import {
  ApiError,
  bankCardApi,
  cartApi,
  userApi,
  wishlistApi,
} from "@/lib/api";
import type {
  BankCardResponseDto,
  CartResponseDto,
  OrderResponseDto,
  Page,
  UserResponseDto,
  WishlistResponseDto,
} from "@/lib/api";

export const metadata = { title: "My profile — Cybertech" };
export const dynamic = "force-dynamic";

/**
 * /account/profile — single-pane view consolidating identity, payment cards,
 * recent orders and wishlist preview for the authenticated caller.
 *
 * The route is gated by `proxy.ts` (auth middleware) but we still call
 * `auth()` here so we can short-circuit unauthenticated session edge cases
 * (cookie revoked between proxy hop and render) and surface the access
 * token to API calls.
 *
 * The backend currently has no `/me` endpoint nor a "list my orders" route.
 * To resolve the internal user UUID we lean on `cartApi.getMine()` (which
 * returns `userUuid` in its response). If the user has no cart yet, we still
 * render the page using the session-shaped name + email and surface a tiny
 * notice instead of crashing.
 */

const WISHLIST_PREVIEW_SIZE = 4;

interface ProfileData {
  user: UserResponseDto | null;
  cards: BankCardResponseDto[];
  cardsErrored: boolean;
  orders: OrderResponseDto[];
  ordersErrored: boolean;
  ordersUnavailable: boolean;
  wishlist: WishlistResponseDto[];
  wishlistTotal: number;
  wishlistErrored: boolean;
}

async function loadProfileData(): Promise<ProfileData> {
  // Resolve the user's internal UUID via the cart endpoint (the only
  // authenticated read that surfaces it without a /me route).
  let cart: CartResponseDto | null = null;
  try {
    cart = await cartApi.getMine();
  } catch (err) {
    if (!(err instanceof ApiError) || err.status !== 404) {
      console.warn("[account/profile] cart.getMine failed", err);
    }
    cart = null;
  }

  const [userResult, defaultCardResult, wishlistResult] = await Promise.allSettled([
    cart?.userUuid ? userApi.get(cart.userUuid) : Promise.resolve(null),
    bankCardApi.getDefault(),
    wishlistApi.myWishlist(0, WISHLIST_PREVIEW_SIZE),
  ]);

  const user: UserResponseDto | null =
    userResult.status === "fulfilled" ? userResult.value : null;
  if (userResult.status === "rejected") {
    console.warn("[account/profile] userApi.get failed", userResult.reason);
  }

  let cards: BankCardResponseDto[] = [];
  let cardsErrored = false;
  if (defaultCardResult.status === "fulfilled") {
    cards = defaultCardResult.value ? [defaultCardResult.value] : [];
  } else {
    const reason = defaultCardResult.reason;
    // 403 from /bank-card/default is the documented "no default set yet" — treat as empty.
    if (reason instanceof ApiError && (reason.status === 403 || reason.status === 404)) {
      cards = [];
    } else {
      cardsErrored = true;
      console.warn("[account/profile] bankCardApi.getDefault failed", reason);
    }
  }

  let wishlist: WishlistResponseDto[] = [];
  let wishlistTotal = 0;
  let wishlistErrored = false;
  if (wishlistResult.status === "fulfilled") {
    const page: Page<WishlistResponseDto> = wishlistResult.value;
    wishlist = page.content;
    wishlistTotal = page.totalElements;
  } else {
    wishlistErrored = true;
    console.warn("[account/profile] wishlistApi.myWishlist failed", wishlistResult.reason);
  }

  return {
    user,
    cards,
    cardsErrored,
    // Order listing endpoint not implemented yet (Wave 6 backlog); we always
    // render the "coming soon" placeholder.
    orders: [],
    ordersErrored: false,
    ordersUnavailable: true,
    wishlist,
    wishlistTotal,
    wishlistErrored,
  };
}

export default async function ProfilePage() {
  const session = await auth();
  if (!session) redirect("/api/auth/signin?callbackUrl=/account/profile");

  const data = await loadProfileData();

  // Compose a session-only fallback so the identity card still has something
  // to render when the user fetch failed (e.g. brand-new user with no cart).
  const sessionName = session.user?.name ?? "";
  const sessionEmail = session.user?.email ?? "";
  const fallback: ProfileFallback = {
    fullName: sessionName || sessionEmail.split("@")[0] || "Cybertech member",
    email: sessionEmail,
    username: null,
  };

  return (
    <MainLayout>
      <div className="max-w-[1280px] mx-auto px-6 md:px-8 py-12">
        <header className="mb-10">
          <p className="font-label-caps uppercase tracking-[0.2em] text-secondary mb-2">
            Account
          </p>
          <h1 className="font-display text-display-lg text-primary mb-3">
            My profile
          </h1>
          <p className="font-body text-body-lg text-on-surface-variant max-w-2xl">
            Identity, payment, orders and saved products — in one place.
          </p>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <aside className="lg:col-span-4 lg:sticky lg:top-28">
            <ProfileSummary user={data.user} fallback={fallback} />
          </aside>

          <div className="lg:col-span-8 flex flex-col gap-8">
            <BankCardList cards={data.cards} errored={data.cardsErrored} />
            <RecentOrders
              orders={data.orders}
              errored={data.ordersErrored}
              unavailable={data.ordersUnavailable}
            />
            <WishlistPreview
              items={data.wishlist}
              totalElements={data.wishlistTotal}
              errored={data.wishlistErrored}
            />
          </div>
        </div>
      </div>
    </MainLayout>
  );
}

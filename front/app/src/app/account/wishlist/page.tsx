import { ApiError, wishlistApi } from "@/lib/api";
import type { Page, WishlistResponseDto } from "@/lib/api";
import { AccountFrame } from "@/components/account/AccountFrame";
import { WishlistGrid } from "@/components/account/WishlistGrid";
import { EmptyState } from "@/components/account/EmptyState";
import { Pagination } from "@/components/ui/Pagination";

export const metadata = { title: "My wishlist — Cybertech" };
export const dynamic = "force-dynamic";

const PAGE_SIZE = 12;

interface SearchParams {
  page?: string;
}

interface LoadResult {
  page: Page<WishlistResponseDto> | null;
  errored: boolean;
}

async function loadWishlist(pageNumber: number): Promise<LoadResult> {
  try {
    const result = await wishlistApi.myWishlist(pageNumber, PAGE_SIZE);
    return { page: result, errored: false };
  } catch (err) {
    if (err instanceof ApiError) {
      console.warn("[account/wishlist] wishlistApi.myWishlist failed", err);
      return { page: null, errored: true };
    }
    throw err;
  }
}

export default async function WishlistPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const params = await searchParams;
  const pageNumber = Math.max(0, Number(params.page ?? 0) || 0);
  const { page, errored } = await loadWishlist(pageNumber);

  const description = page
    ? `${page.totalElements} item${page.totalElements === 1 ? "" : "s"} saved for later.`
    : "Save products to revisit them anytime.";

  return (
    <AccountFrame
      eyebrow="Account"
      title="My wishlist"
      description={description}
    >
      {errored && (
        <div className="bg-error-container/40 border border-error/40 px-6 py-5 text-on-error-container">
          <p className="font-display font-semibold mb-1">
            We couldn&apos;t load your wishlist
          </p>
          <p className="font-body text-sm">
            Please refresh the page or try again in a minute.
          </p>
        </div>
      )}

      {page && page.empty && (
        <EmptyState
          icon="favorite"
          title="No favourites yet"
          description="Tap the heart on any product card to save it here for later."
          cta={{ href: "/products", label: "Browse catalogue" }}
        />
      )}

      {page && !page.empty && (
        <>
          <WishlistGrid items={page.content} />
          <Pagination
            current={page.number}
            total={page.totalPages}
            baseHref="/account/wishlist"
            searchParams={{}}
          />
        </>
      )}
    </AccountFrame>
  );
}

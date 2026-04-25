import { apiFetch } from "./client";
import type { Page, WishlistResponseDto } from "./types";

const ROOT = "/api/v1/services/wishlist";

export const wishlistApi = {
  add: (productUuid: string) =>
    apiFetch<WishlistResponseDto>(`${ROOT}/add/${productUuid}`, {
      method: "POST",
    }),

  remove: (productUuid: string) =>
    apiFetch<void>(`${ROOT}/remove/${productUuid}`, {
      method: "DELETE",
    }),

  myWishlist: (page = 0, size = 20) =>
    apiFetch<Page<WishlistResponseDto>>(`${ROOT}/my-wishlist`, {
      query: { page, size },
    }),
};

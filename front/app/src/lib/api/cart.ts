import { apiFetch } from "./client";
import type {
  CartCreateRequestDto,
  CartItemRemoveRequestDto,
  CartResponseDto,
  CartUpdateRequestDto,
} from "./types";

const ROOT = "/api/v1/services/cart";

export const cartApi = {
  getMine: () => apiFetch<CartResponseDto>(`${ROOT}/get`),

  getOne: (cartUuid: string) =>
    apiFetch<CartResponseDto>(`${ROOT}/get/${cartUuid}`),

  create: (req: CartCreateRequestDto) =>
    apiFetch<CartResponseDto>(`${ROOT}/create`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  add: (req: CartCreateRequestDto) =>
    apiFetch<CartResponseDto>(`${ROOT}/add`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  update: (cartUuid: string, req: CartUpdateRequestDto) =>
    apiFetch<CartResponseDto>(`${ROOT}/update/${cartUuid}`, {
      method: "PATCH",
      body: req as unknown as Record<string, unknown>,
    }),

  removeProduct: (productUuid: string) =>
    apiFetch<CartResponseDto>(`${ROOT}/remove/${productUuid}`, {
      method: "PATCH",
    }),

  decreaseQuantity: (req: CartItemRemoveRequestDto) =>
    apiFetch<CartResponseDto>(`${ROOT}/decreaseQuantity`, {
      method: "DELETE",
      body: req as unknown as Record<string, unknown>,
    }),

  clear: () =>
    apiFetch<void>(`${ROOT}/clear`, {
      method: "DELETE",
    }),

  remove: (cartUuid: string) =>
    apiFetch<void>(`${ROOT}/delete/${cartUuid}`, {
      method: "DELETE",
    }),
};

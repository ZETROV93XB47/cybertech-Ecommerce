import { apiFetch } from "./client";
import type {
  Page,
  ProductCreateRequestDto,
  ProductResponseDto,
  ProductSearchRequestDto,
  ProductUpdateRequestDto,
} from "./types";

const PUBLIC = "/api/v1/services/product";
const ADMIN = "/api/v1/services/admin/management/product";

export const productApi = {
  // Public reads
  get: (uuid: string) =>
    apiFetch<ProductResponseDto>(`${PUBLIC}/get/${uuid}`, { anonymous: true }),

  search: (req: ProductSearchRequestDto) =>
    apiFetch<Page<ProductResponseDto>>(`${PUBLIC}/search`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
      anonymous: true,
    }),

  bestSellers: (page = 0, size = 15) =>
    apiFetch<Page<ProductResponseDto>>(`${PUBLIC}/best-sellers`, {
      query: { page, size },
      anonymous: true,
    }),

  // Admin
  listAll: (page = 0, size = 20, sort?: string) =>
    apiFetch<Page<ProductResponseDto>>(`${ADMIN}/get/all`, {
      query: { page, size, sort },
    }),

  create: (req: ProductCreateRequestDto) =>
    apiFetch<ProductResponseDto>(`${ADMIN}/create`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  createWithImage: (req: ProductCreateRequestDto, image: File) => {
    const fd = new FormData();
    fd.append(
      "product",
      new Blob([JSON.stringify(req)], { type: "application/json" }),
    );
    fd.append("image", image);
    return apiFetch<ProductResponseDto>(`${ADMIN}/create-with-image`, {
      method: "POST",
      body: fd,
    });
  },

  update: (uuid: string, req: ProductUpdateRequestDto) =>
    apiFetch<ProductResponseDto>(`${ADMIN}/update/${uuid}`, {
      method: "PATCH",
      body: req as unknown as Record<string, unknown>,
    }),

  remove: (uuid: string) =>
    apiFetch<void>(`${ADMIN}/delete/${uuid}`, { method: "DELETE" }),
};

import { apiFetch } from "./client";
import type {
  DiscountCampaignResponseDto,
  DiscountCampaignUpdateRequestDto,
  DiscountContext,
  DiscountType,
} from "./types";

const PUBLIC = "/api/v1/services/discounts";
const ADMIN = "/api/v1/services/admin/discounts";

export const discountApi = {
  active: () =>
    apiFetch<DiscountContext[]>(`${PUBLIC}/active`, { anonymous: true }),

  // Admin
  listAll: () => apiFetch<DiscountCampaignResponseDto[]>(ADMIN),

  get: (type: DiscountType) =>
    apiFetch<DiscountCampaignResponseDto>(`${ADMIN}/${type}`),

  update: (type: DiscountType, req: DiscountCampaignUpdateRequestDto) =>
    apiFetch<DiscountCampaignResponseDto>(`${ADMIN}/${type}`, {
      method: "PATCH",
      body: req as unknown as Record<string, unknown>,
    }),
};

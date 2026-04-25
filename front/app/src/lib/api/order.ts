import { apiFetch } from "./client";
import type {
  OrderCancellationRequestDto,
  OrderPlacingRequestDto,
  OrderResponseDto,
  OrderStatusDto,
  OrderUpdateRequestDto,
} from "./types";

const ROOT = "/api/v1/services/management/order";

export const orderApi = {
  place: (req: OrderPlacingRequestDto) =>
    apiFetch<OrderResponseDto>(`${ROOT}/place`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  cancel: (req: OrderCancellationRequestDto) =>
    apiFetch<OrderResponseDto>(`${ROOT}/cancel`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  update: (req: OrderUpdateRequestDto) =>
    apiFetch<OrderResponseDto>(`${ROOT}/update`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  retryPayment: (uuid: string) =>
    apiFetch<OrderResponseDto>(`${ROOT}/retry-payment/${uuid}`, {
      method: "POST",
    }),

  get: (uuid: string) => apiFetch<OrderResponseDto>(`${ROOT}/get/${uuid}`),

  status: (uuid: string) => apiFetch<OrderStatusDto>(`${ROOT}/status/${uuid}`),

  // Dev-only synthetic order placement (USER role)
  placeAuto: () =>
    apiFetch<OrderResponseDto>(`${ROOT}/place/auto`, { method: "POST" }),

  // Admin only
  remove: (uuid: string) =>
    apiFetch<void>(`${ROOT}/delete/${uuid}`, { method: "DELETE" }),
};

import { apiFetch } from "./client";
import type {
  OrderCancellationRequestDto,
  OrderPlacingRequestDto,
  OrderResponseDto,
  OrderStatus,
  OrderStatusDto,
  OrderUpdateRequestDto,
  Page,
} from "./types";

const ROOT = "/api/v1/services/management/order";
const ADMIN = "/api/v1/services/admin/management/order";

export const orderApi = {
  place: (req: OrderPlacingRequestDto) =>
    apiFetch<OrderResponseDto>(`${ROOT}/place`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  /**
   * List the caller's own orders (paginated, optionally filtered by status).
   * The endpoint is being added in a parallel task — until it lands the call
   * will surface a 404 ApiError that the page can swallow into a graceful
   * "orders coming soon" placeholder.
   */
  mine: (page = 0, size = 10, status?: OrderStatus) =>
    apiFetch<Page<OrderResponseDto>>(`${ROOT}/mine`, {
      query: { page, size, status },
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

  /**
   * Admin: list every order in the system, paginated and optionally filtered
   * by status. The endpoint is being added in a parallel task; until it lands,
   * callers should swallow a 404 ApiError into an empty state.
   */
  listAll: (page = 0, size = 20, status?: OrderStatus) =>
    apiFetch<Page<OrderResponseDto>>(`${ADMIN}/get/all`, {
      query: { page, size, status },
    }),
};

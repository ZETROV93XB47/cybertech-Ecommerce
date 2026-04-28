import { apiFetch } from "./client";
import type {
  BankCardCreationRequestDto,
  BankCardResponseDto,
  BankCardUpdateRequestDto,
  Page,
} from "./types";

const ROOT = "/api/v1/services/bank-card";

export const bankCardApi = {
  // User
  add: (req: BankCardCreationRequestDto) =>
    apiFetch<BankCardResponseDto>(`${ROOT}/add`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  update: (req: BankCardUpdateRequestDto) =>
    apiFetch<BankCardResponseDto>(`${ROOT}/update`, {
      method: "PUT",
      body: req as unknown as Record<string, unknown>,
    }),

  remove: () =>
    apiFetch<void>(`${ROOT}/delete`, {
      method: "DELETE",
    }),

  setDefault: (cardUuid: string) =>
    apiFetch<void>(`${ROOT}/set-default/${cardUuid}`, {
      method: "PATCH",
    }),

  getDefault: () => apiFetch<BankCardResponseDto>(`${ROOT}/default`),

  /**
   * Multi-card listing for the authenticated user. Endpoint being added in a
   * parallel task — a 404 means we should fall back to the single-default
   * read so the page still has something to render.
   */
  allMine: () => apiFetch<BankCardResponseDto[]>(`${ROOT}/all-mine`),

  // Admin
  listAll: (page = 0, size = 10) =>
    apiFetch<Page<BankCardResponseDto>>(ROOT, {
      query: { page, size },
    }),

  getOne: (uuid: string) =>
    apiFetch<BankCardResponseDto>(`${ROOT}/${uuid}`),

  adminCreate: (req: BankCardCreationRequestDto) =>
    apiFetch<BankCardResponseDto>(ROOT, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  adminUpdate: (req: BankCardUpdateRequestDto) =>
    apiFetch<BankCardResponseDto>(ROOT, {
      method: "PUT",
      body: req as unknown as Record<string, unknown>,
    }),

  adminRemove: (uuid: string) =>
    apiFetch<void>(`${ROOT}/${uuid}`, { method: "DELETE" }),
};

import { apiFetch } from "./client";
import type {
  ReviewCreateRequestDto,
  ReviewResponseDto,
  ReviewUpdateRequestDto,
  ReviewableProductDto,
} from "./types";

const ROOT = "/api/v1/services/review";

export const reviewApi = {
  get: (uuid: string) =>
    apiFetch<ReviewResponseDto>(`${ROOT}/get/${uuid}`, { anonymous: true }),

  reviewable: () =>
    apiFetch<ReviewableProductDto[]>(`${ROOT}/reviewable`),

  create: (req: ReviewCreateRequestDto) =>
    apiFetch<ReviewResponseDto>(`${ROOT}/create`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  update: (reviewUuid: string, req: ReviewUpdateRequestDto) =>
    apiFetch<ReviewResponseDto>(`${ROOT}/update/${reviewUuid}`, {
      method: "PATCH",
      body: req as unknown as Record<string, unknown>,
    }),

  remove: (reviewUuid: string) =>
    apiFetch<void>(`${ROOT}/delete/${reviewUuid}`, { method: "DELETE" }),
};

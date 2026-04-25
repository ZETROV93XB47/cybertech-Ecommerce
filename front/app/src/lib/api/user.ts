import { apiFetch } from "./client";
import type {
  Page,
  UserCreateRequestDto,
  UserRegistrationResponse,
  UserResponseDto,
  UserUpdateRequestDto,
} from "./types";

const PUBLIC = "/api/v1/services/user";
const ADMIN = "/api/v1/services/admin/user";

export const userApi = {
  register: (req: UserCreateRequestDto) =>
    apiFetch<UserRegistrationResponse>(`${PUBLIC}/register`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
      anonymous: true,
    }),

  get: (uuid: string) => apiFetch<UserResponseDto>(`${PUBLIC}/get/${uuid}`),

  // Admin
  listAll: (page = 0, size = 20) =>
    apiFetch<Page<UserResponseDto>>(`${ADMIN}/get/all`, {
      query: { page, size },
    }),

  create: (req: UserCreateRequestDto) =>
    apiFetch<UserRegistrationResponse>(`${ADMIN}/create`, {
      method: "POST",
      body: req as unknown as Record<string, unknown>,
    }),

  update: (req: UserUpdateRequestDto) =>
    apiFetch<UserResponseDto>(`${ADMIN}/update`, {
      method: "PATCH",
      body: req as unknown as Record<string, unknown>,
    }),

  remove: (uuid: string) =>
    apiFetch<void>(`${ADMIN}/delete/${uuid}`, { method: "DELETE" }),
};

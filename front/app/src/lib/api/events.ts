import { apiFetch } from "./client";
import type { UserEventDto } from "./types";

export const eventsApi = {
  consume: (event: UserEventDto) =>
    apiFetch<void>("/api/v1/events/consume-event", {
      method: "POST",
      body: event as unknown as Record<string, unknown>,
    }),

  authProbe: () => apiFetch<unknown>("/api/v1/services/user/ok"),
};

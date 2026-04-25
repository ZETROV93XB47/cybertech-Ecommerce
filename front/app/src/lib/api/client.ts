import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";
import type { ErrorResponseDto } from "./types";

/**
 * Thin fetch wrapper for the Cybertech backend.
 *
 * Behavior:
 *  - Reads NEXT_PUBLIC_API_BASE (default: http://localhost:8081).
 *  - Injects `X-API-VERSION: 1.0` (advisory; backend defaults to 1.0).
 *  - Pulls the OAuth2 access token from the Auth.js session and sends
 *    `Authorization: Bearer <token>` on every authenticated request.
 *  - Surfaces backend `ErrorResponseDto` as an `ApiError` so callers can
 *    branch on `httpStatusCode` and `errorCodeType`.
 *
 * Server components import this directly. Client components also import it,
 * but go through a route handler or server action when they need the token —
 * never expose the access token to the browser.
 */

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8081";

export class ApiError extends Error {
  readonly status: number;
  readonly body: ErrorResponseDto | null;

  constructor(status: number, body: ErrorResponseDto | null, message?: string) {
    super(message ?? body?.message ?? `HTTP ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }

  isFunctional(): boolean {
    return this.body?.errorCodeType === "FUNCTIONAL";
  }
  isTechnical(): boolean {
    return this.body?.errorCodeType === "TECHNICAL";
  }
}

type Json = Record<string, unknown> | unknown[];

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: Json | FormData | null;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Override token (e.g. server-side scenarios); defaults to Auth.js session token. */
  token?: string | null;
  /** Skip pulling the session — useful for public endpoints. */
  anonymous?: boolean;
  /** Pass-through to fetch() for caching directives. */
  cache?: RequestCache;
  /** Pass-through to fetch() for revalidation. */
  next?: { revalidate?: number | false; tags?: string[] };
}

function buildUrl(
  path: string,
  query?: RequestOptions["query"],
): string {
  const url = new URL(path.startsWith("http") ? path : `${API_BASE}${path}`);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null) continue;
      url.searchParams.append(k, String(v));
    }
  }
  return url.toString();
}

async function resolveToken(options: RequestOptions): Promise<string | null> {
  if (options.anonymous) return null;
  if (options.token !== undefined) return options.token;
  // auth() is async in v5; safe to call from server components / route handlers.
  const session = await auth();
  // If the Keycloak refresh-token call failed earlier, the session cookie is
  // still valid client-side but the backend will 401. Bounce through sign-in
  // before we waste a network round-trip.
  if (session?.error === "RefreshTokenError") {
    redirect("/api/auth/signin");
  }
  return session?.accessToken ?? null;
}

export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { method = "GET", body, query, cache, next: nextOpts } = options;
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-API-VERSION": "1.0",
  };

  let serializedBody: BodyInit | undefined;
  if (body instanceof FormData) {
    serializedBody = body;
  } else if (body !== undefined && body !== null) {
    headers["Content-Type"] = "application/json";
    serializedBody = JSON.stringify(body);
  }

  const token = await resolveToken(options);
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(buildUrl(path, query), {
    method,
    headers,
    body: serializedBody,
    cache,
    next: nextOpts,
  });

  if (res.status === 204 || res.status === 205) {
    return undefined as T;
  }

  const contentType = res.headers.get("content-type") ?? "";
  const isJson = contentType.includes("application/json");

  if (!res.ok) {
    let errorBody: ErrorResponseDto | null = null;
    if (isJson) {
      try {
        errorBody = (await res.json()) as ErrorResponseDto;
      } catch {
        errorBody = null;
      }
    }
    // 401 from the backend on an authenticated request means our access token
    // is no longer valid (refresh likely failed). Send the user through
    // sign-in instead of letting a vague error bubble up to the page.
    if (res.status === 401 && !options.anonymous) {
      redirect("/api/auth/signin");
    }
    throw new ApiError(res.status, errorBody);
  }

  if (!isJson) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

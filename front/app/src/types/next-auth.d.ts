import type { DefaultSession } from "next-auth";

/**
 * Module augmentation for Auth.js v5 (next-auth@beta).
 *
 * - `next-auth` is augmented to extend Session.
 * - `@auth/core/jwt` is the actual home of the JWT interface; `next-auth/jwt`
 *   is a re-export shim that doesn't accept augmentation, so we target the
 *   underlying module here.
 */
declare module "next-auth" {
  interface Session {
    accessToken?: string;
    error?: "RefreshTokenError";
    user: {
      roles: string[];
      role: "ADMIN" | "USER" | null;
    } & DefaultSession["user"];
  }
}

declare module "@auth/core/jwt" {
  interface JWT {
    access_token?: string;
    refresh_token?: string;
    expires_at?: number;
    id_token?: string;
    roles?: string[];
    error?: "RefreshTokenError";
  }
}

export {};

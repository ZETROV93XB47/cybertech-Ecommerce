import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";

/**
 * Auth.js v5 (next-auth@beta) configured for the Cybertech backend.
 *
 *  - OIDC against Keycloak at AUTH_KEYCLOAK_ISSUER (default: cybertech realm).
 *  - JWT strategy: we keep the OAuth2 access token (and its expiry) in the
 *    encrypted Auth.js session JWT and forward it as Bearer to the backend.
 *  - Refresh token rotation: when the access token is past expiry, hit
 *    `${issuer}/protocol/openid-connect/token` with grant_type=refresh_token.
 *  - Role mapping: Keycloak realm roles arrive in the access token under
 *    `realm_access.roles[]` — we surface them on `session.user.roles` and
 *    expose a derived `session.user.role` of "ADMIN" | "USER" matching the
 *    backend's `KeycloakRoleConverter` (admin > user).
 */

const ROLE_PRIORITY: Record<string, number> = { ADMIN: 2, USER: 1 };

function pickPrimaryRole(roles: readonly string[]): "ADMIN" | "USER" | null {
  let best: "ADMIN" | "USER" | null = null;
  for (const r of roles) {
    const upper = r.toUpperCase();
    if (upper === "ADMIN" || upper === "USER") {
      if (!best || ROLE_PRIORITY[upper] > ROLE_PRIORITY[best]) {
        best = upper;
      }
    }
  }
  return best;
}

interface KeycloakAccessTokenPayload {
  realm_access?: { roles?: string[] };
  [k: string]: unknown;
}

function decodeJwtPayload(jwt: string): KeycloakAccessTokenPayload | null {
  const parts = jwt.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload + "=".repeat((4 - (payload.length % 4)) % 4);
    const json = Buffer.from(padded, "base64").toString("utf8");
    return JSON.parse(json) as KeycloakAccessTokenPayload;
  } catch {
    return null;
  }
}

function rolesFromAccessToken(accessToken: string): string[] {
  return decodeJwtPayload(accessToken)?.realm_access?.roles ?? [];
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.AUTH_KEYCLOAK_ID,
      clientSecret: process.env.AUTH_KEYCLOAK_SECRET,
      issuer: process.env.AUTH_KEYCLOAK_ISSUER,
    }),
  ],
  session: { strategy: "jwt" },
  callbacks: {
    async jwt({ token, account }) {
      // First sign-in: persist the OIDC tokens + decoded roles.
      if (account && account.access_token) {
        const expiresAt =
          account.expires_at ??
          Math.floor(Date.now() / 1000) + 300; // fallback 5 min
        return {
          ...token,
          access_token: account.access_token,
          refresh_token: account.refresh_token,
          expires_at: expiresAt,
          id_token: account.id_token,
          roles: rolesFromAccessToken(account.access_token),
        };
      }

      // Still valid — short-circuit.
      if (
        typeof token.expires_at === "number" &&
        Date.now() < token.expires_at * 1000
      ) {
        return token;
      }

      // Refresh.
      if (!token.refresh_token || !process.env.AUTH_KEYCLOAK_ISSUER) {
        return { ...token, error: "RefreshTokenError" as const };
      }

      try {
        const res = await fetch(
          `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/token`,
          {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({
              client_id: process.env.AUTH_KEYCLOAK_ID ?? "",
              client_secret: process.env.AUTH_KEYCLOAK_SECRET ?? "",
              grant_type: "refresh_token",
              refresh_token: token.refresh_token,
            }),
          },
        );
        const refreshed = (await res.json()) as {
          access_token?: string;
          expires_in?: number;
          refresh_token?: string;
          error?: string;
        };
        if (!res.ok || !refreshed.access_token) throw refreshed;

        return {
          ...token,
          access_token: refreshed.access_token,
          expires_at:
            Math.floor(Date.now() / 1000) + (refreshed.expires_in ?? 300),
          refresh_token: refreshed.refresh_token ?? token.refresh_token,
          roles: rolesFromAccessToken(refreshed.access_token),
          error: undefined,
        };
      } catch (error) {
        console.error("Keycloak token refresh failed", error);
        return { ...token, error: "RefreshTokenError" as const };
      }
    },

    async session({ session, token }) {
      session.accessToken = token.access_token;
      session.error = token.error;
      const roles = token.roles ?? [];
      session.user.roles = roles;
      session.user.role = pickPrimaryRole(roles);
      return session;
    },
  },
  pages: {
    // Send unauthenticated visitors through Keycloak's hosted login.
    // Auth.js's default `/api/auth/signin` will redirect to the only
    // configured provider, so we leave this empty for now.
  },
});

// Module augmentation for Session/JWT lives in src/types/next-auth.d.ts.

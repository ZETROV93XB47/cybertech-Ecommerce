import { NextResponse } from "next/server";
import { auth } from "@/lib/auth";

/**
 * Next.js 16 `proxy.ts` — runs in nodejs runtime ahead of every matched route.
 * Replaces the legacy `middleware.ts` convention.
 *
 *   /account/**  → requires an authenticated USER (or ADMIN)
 *   /admin/**    → requires ADMIN
 *
 * Anything else falls through. Backend ownership checks remain authoritative;
 * this layer only avoids exposing controls that would 401/403/404.
 */
export default auth((req) => {
  const { pathname } = req.nextUrl;
  const session = req.auth;
  const role = session?.user?.role ?? null;

  const isAccount = pathname.startsWith("/account");
  const isAdmin = pathname.startsWith("/admin");

  if (!isAccount && !isAdmin) return undefined;

  if (!session) {
    const signin = new URL("/api/auth/signin", req.nextUrl);
    signin.searchParams.set("callbackUrl", req.nextUrl.pathname);
    return NextResponse.redirect(signin);
  }

  if (isAdmin && role !== "ADMIN") {
    return NextResponse.redirect(new URL("/", req.nextUrl));
  }

  return undefined;
});

export const config = {
  // Match everything except Next.js internals, static files, API auth routes,
  // and the catch-all auth handler at /api/auth/*.
  matcher: ["/((?!_next/static|_next/image|favicon\\.ico|api/auth).*)"],
};

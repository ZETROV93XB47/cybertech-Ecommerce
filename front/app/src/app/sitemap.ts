import type { MetadataRoute } from "next";

/**
 * Programmatic sitemap — Next 16 convention.
 *
 * We list the static, indexable routes here. Product detail pages
 * (`/products/[uuid]`) are intentionally NOT enumerated at build time:
 * the catalog API requires a running backend and may not be reachable
 * during `next build`. A fallback strategy (e.g. `generateSitemaps` over
 * a paginated `productApi.search`) can be added once the build pipeline
 * has a guaranteed backend, without changing this file's contract.
 *
 * Account-only and admin routes are intentionally omitted — `robots.ts`
 * disallows them anyway.
 */

const baseUrl = (
  process.env.AUTH_URL ?? "http://localhost:3000"
).replace(/\/+$/, "");

type Entry = MetadataRoute.Sitemap[number];

const staticRoutes: ReadonlyArray<{
  path: string;
  changeFrequency: NonNullable<Entry["changeFrequency"]>;
  priority: number;
}> = [
  // Discovery
  { path: "/", changeFrequency: "daily", priority: 1.0 },
  { path: "/products", changeFrequency: "daily", priority: 0.9 },

  // Marketing
  { path: "/about", changeFrequency: "monthly", priority: 0.5 },
  { path: "/contact", changeFrequency: "monthly", priority: 0.5 },
  { path: "/support", changeFrequency: "monthly", priority: 0.5 },

  // Auth entry points (public and indexable; SEO is fine here)
  { path: "/auth/login", changeFrequency: "yearly", priority: 0.3 },
  { path: "/auth/register", changeFrequency: "yearly", priority: 0.3 },

  // Legal
  { path: "/legal/privacy", changeFrequency: "yearly", priority: 0.2 },
  { path: "/legal/terms", changeFrequency: "yearly", priority: 0.2 },
  { path: "/legal/cookies", changeFrequency: "yearly", priority: 0.2 },
];

export default function sitemap(): MetadataRoute.Sitemap {
  const lastModified = new Date();
  return staticRoutes.map(({ path, changeFrequency, priority }) => ({
    url: `${baseUrl}${path}`,
    lastModified,
    changeFrequency,
    priority,
  }));
}

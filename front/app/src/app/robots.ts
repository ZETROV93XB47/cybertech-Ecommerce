import type { MetadataRoute } from "next";

/**
 * Programmatic robots.txt — Next 16 convention.
 * Mirrors the historical `robots.txt`, plus the dynamic sitemap location.
 */

const baseUrl = (
  process.env.AUTH_URL ?? "http://localhost:3000"
).replace(/\/+$/, "");

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/account/", "/admin/", "/checkout", "/cart"],
    },
    sitemap: `${baseUrl}/sitemap.xml`,
    host: baseUrl,
  };
}

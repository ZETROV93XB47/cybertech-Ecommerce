import type { NextConfig } from "next";

/**
 * Production-rigor Next.js config for the minikube self-hosted deployment.
 *
 *  - `output: 'standalone'` builds a tiny `.next/standalone/server.js` bundle
 *    that the helm chart runs in the container (no `node_modules` to ship).
 *  - `reactStrictMode: true` keeps React's dev-time double-invocation warnings
 *    visible in dev so we catch effect/state foot-guns before they hit prod.
 *  - `typescript.ignoreBuildErrors: false` makes `tsc` block the production
 *    build on any type error. Belt-and-suspenders — this is also the default
 *    in Next 16, but pinning it documents the intent.
 *  - In Next 16 the legacy `eslint` config key has been removed and
 *    `next build` no longer runs linting. The `prebuild` npm script runs
 *    `eslint` directly so a lint failure still gates the production build.
 *  - `images.remotePatterns` whitelists the hostnames `<Image>` is allowed
 *    to fetch. In prod, product photos are served by localstack S3 behind
 *    the api ingress (`https://api.cybertech.local`). The dev port-forward
 *    host (`localhost`) is also permitted so cards/galleries don't 400 in
 *    mixed envs.
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  output: "standalone",
  typescript: {
    ignoreBuildErrors: false,
  },
  images: {
    remotePatterns: [
      // Production: product images are proxied through the api ingress,
      // which fronts the in-cluster localstack S3.
      {
        protocol: "https",
        hostname: "api.cybertech.local",
      },
      // Local dev: backend usually runs at http://localhost:8081.
      {
        protocol: "http",
        hostname: "localhost",
      },
      // Local dev: localstack edge port when accessed directly.
      {
        protocol: "http",
        hostname: "127.0.0.1",
      },
    ],
  },
};

export default nextConfig;

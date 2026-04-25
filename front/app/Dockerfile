# syntax=docker/dockerfile:1.7
#
# Cybertech Next.js frontend — multi-stage production image.
# Built from `front/app/` against `next.config.ts`'s `output: 'standalone'`,
# which produces `.next/standalone/server.js` + a minimal node_modules tree.
#
# Build:
#   eval $(minikube docker-env)
#   docker build -t cybertech-front:dev front/app
#
# Run (helm chart does this with `command: ["node", "/app/server.js"]`):
#   docker run --rm -p 3000:3000 cybertech-front:dev

# ---------- Stage 1: install deps with full dev set ----------
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

# ---------- Stage 2: build (uses dev deps for tsc/eslint) ----------
FROM node:22-alpine AS builder
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---------- Stage 3: minimal runtime ----------
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# non-root for defense in depth
RUN addgroup --system --gid 1001 nodejs \
 && adduser --system --uid 1001 nextjs

# Static + standalone bundle. The standalone bundle ships its own
# trimmed node_modules tree at /app/node_modules, so we don't copy
# the full dev tree.
COPY --from=builder --chown=nextjs:nodejs /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]

# ── Stage 1: Install dependencies ──────────────
FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

# ── Stage 2: Runtime (slim) ────────────────────
FROM node:20-alpine
WORKDIR /app

# Don't run as root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=deps /app/node_modules ./node_modules
COPY src/ ./src/
COPY openapi.yaml ./openapi.yaml 

EXPOSE 3001
HEALTHCHECK --interval=30s --timeout=5s   CMD wget -qO- http://localhost:3001/health || exit 1

CMD ["node", "src/index.js"]
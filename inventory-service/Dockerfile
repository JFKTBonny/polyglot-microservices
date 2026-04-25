# ── Stage 1: Build binary ───────────────────────────────
FROM golang:1.25-alpine AS builder
WORKDIR /app

# Download dependencies first (cached layer)
COPY go.mod go.sum ./
RUN go mod download

# Copy source and build
COPY . .
RUN go mod tidy && CGO_ENABLED=0 GOOS=linux go build -o inventory-service .

# ── Stage 2: Minimal runtime ────────────────────────────
# scratch is an empty image — just the binary, nothing else
FROM alpine:3.19
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/inventory-service .

EXPOSE 3003
HEALTHCHECK --interval=30s --timeout=5s \
    CMD wget -qO- http://localhost:3003/health || exit 1

CMD ["./inventory-service"]
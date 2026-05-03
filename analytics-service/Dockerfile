# ── Stage 1: Install dependencies ──────────────
FROM composer:2.7 AS deps
WORKDIR /app

COPY composer.json ./
RUN composer install \
    --no-dev \
    --no-scripts \
    --no-autoloader \
    --ignore-platform-reqs \
    --prefer-dist

COPY . .

# Create required Laravel cache directories before autoload dump
RUN mkdir -p bootstrap/cache storage/framework/sessions \
    storage/framework/views storage/framework/cache \
    storage/logs \
    && chmod -R 777 bootstrap/cache storage

RUN composer dump-autoload --optimize --no-dev --ignore-platform-reqs

# ── Stage 2: Runtime ────────────────────────────
FROM php:8.4-cli-alpine
WORKDIR /app

RUN apk add --no-cache libpq-dev \
    && docker-php-ext-install pdo pdo_pgsql

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=deps --chown=appuser:appgroup /app .
USER appuser

EXPOSE 3005

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s \
    CMD php -r "file_get_contents('http://localhost:3005/health');" || exit 1

CMD sh -c "php artisan migrate --force --skip-next-id && \
    php -S 0.0.0.0:3005 -t public public/index.php & \
    php artisan kafka:consume"
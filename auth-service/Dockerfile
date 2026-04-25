# ── Stage 1: Install dependencies ──────────────
FROM python:3.12-slim AS deps
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# ── Stage 2: Runtime ───────────────────────────
FROM python:3.12-slim
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=deps /usr/local/lib/python3.12/site-packages /usr/local/lib/python3.12/site-packages
COPY --from=deps /usr/local/bin/uvicorn /usr/local/bin/uvicorn
COPY . .

USER appuser

EXPOSE 3006
HEALTHCHECK --interval=30s --timeout=5s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:3006/health')" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "3006"]
FROM python:3.12-slim
WORKDIR /app

# Install dependencies as root FIRST
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Then create and switch to non-root user
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
COPY --chown=appuser:appgroup . .
USER appuser

CMD ["python", "-u", "main.py"]
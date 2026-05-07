import os
import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
from dotenv import load_dotenv

from db.client import get_pool, close_pool
from routes.auth import router as auth_router

load_dotenv()

# ── Lifespan ───────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    print("[auth-service] starting up...")
    await get_pool()
    print("[auth-service] database pool ready")
    print(f"[auth-service] listening on port {os.getenv('PORT', 3006)}")

    yield

    # Shutdown
    print("[auth-service] shutting down...")
    await close_pool()
    print("[auth-service] closed")


# ── App instance ───────────────────────────────────────────────
app = FastAPI(
    title="Auth Service",
    description="JWT authentication — register, login, refresh, validate",
    version="1.0.0",
    lifespan=lifespan,
    redirect_slashes=False
)


# ── Global exception handler ───────────────────────────────────
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    print(f"[error] {exc}")
    return JSONResponse(
        status_code=500,
        content={"error": "Internal server error"}
    )


# ── Health check ───────────────────────────────────────────────
@app.get("/health")
async def health():
    return {
        "status":  "ok",
        "service": "auth-service",
    }


# ── Routes ─────────────────────────────────────────────────────
app.include_router(auth_router, prefix="/auth")


# ── Entry point ────────────────────────────────────────────────
if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", 3006)),
        reload=True
    )
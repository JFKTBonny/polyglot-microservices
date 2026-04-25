import os
import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
from dotenv import load_dotenv

from db.client import get_pool, close_pool
from routes.orders import router as orders_router

from events.producer import get_producer, close_producer

load_dotenv()

# ── Lifespan — runs on startup and shutdown ────────────────────────────────
# This is the FastAPI equivalent of app.listen() in Node.js
# but also handles graceful startup and shutdown of the DB pool

@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup ──
    print("[order-service] starting up...")
    await get_pool()   # initialise DB pool — fail fast if DB is unreachable
    print("[order-service] database pool ready")
    print(f"[order-service] docs at http://localhost:{os.getenv('PORT', 3002)}/docs")

    yield  # app is running and serving requests here

    # ── Shutdown ──
    print("[order-service] shutting down...")
    await close_producer()  # ← add this
    await close_pool()
    print("[order-service] closed")



# ── App instance ───────────────────────────────────────────────────────────
app = FastAPI(
    title="Order Service",
    description="Manages orders. Owns its own MySQL database.",
    version="1.0.0",
    lifespan=lifespan,
    redirect_slashes=False   
)


# ── Global exception handler ───────────────────────────────────────────────
# Catches any unhandled exception — equivalent of Express error handler
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    print(f"[error] {exc}")
    return JSONResponse(
        status_code=500,
        content={"error": "Internal server error"}
    )


# ── Health check ───────────────────────────────────────────────────────────
@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "order-service",
    }


# ── Routes ─────────────────────────────────────────────────────────────────
app.include_router(orders_router, prefix="/orders")


# ── Entry point ────────────────────────────────────────────────────────────
if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", 3002)),
        reload=True   # equivalent of nodemon — auto-restarts on file change
    )
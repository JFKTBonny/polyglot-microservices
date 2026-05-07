from fastapi import APIRouter, HTTPException, Header
from fastapi.responses import JSONResponse
from typing import Optional
from datetime import datetime, timezone
import uuid

from db.client import fetch_one, execute, fetch_val
from core.security import hash_password, verify_password
from core.jwt import (
    create_access_token,
    create_refresh_token,
    decode_token,
    get_token_expiry,
    JWT_EXPIRY_MINUTES
)
from models.user import (
    RegisterRequest,
    LoginRequest,
    RefreshRequest,
    TokenResponse,
    RegisterResponse,
    UserResponse,
    ValidateResponse
)

router = APIRouter()


# ── POST /auth/register ────────────────────────────────────────
@router.post("/register", response_model=RegisterResponse, status_code=201)
async def register(body: RegisterRequest):
    """
    Register a new user.
    Creates credentials in auth DB — separate from User Service profile.
    """
    # Check if email already exists
    existing = await fetch_one(
        "SELECT id FROM auth_users WHERE email = $1",
        body.email.lower().strip()
    )
    if existing:
        raise HTTPException(status_code=409, detail="Email already registered")

    # Hash password — never store plain text
    hashed = hash_password(body.password)

    # Insert user
    user = await fetch_one(
        """
        INSERT INTO auth_users (email, name, password, role)
        VALUES ($1, $2, $3, $4)
        RETURNING id, email, name, role, is_active, created_at
        """,
        body.email.lower().strip(),
        body.name.strip(),
        hashed,
        body.role
    )

    # Generate tokens
    access_token  = create_access_token(
        str(user["id"]), user["email"], user["role"]
    )
    refresh_token = create_refresh_token(str(user["id"]))

    # Store refresh token in DB
    await execute(
        """
        INSERT INTO refresh_tokens (user_id, token, expires_at)
        VALUES ($1, $2, $3)
        """,
        user["id"],
        refresh_token,
        get_token_expiry(days=7)
    )

    return RegisterResponse(
        user=UserResponse(**user),
        tokens=TokenResponse(
            access_token=access_token,
            refresh_token=refresh_token,
            expires_in=JWT_EXPIRY_MINUTES * 60
        )
    )


# ── POST /auth/login ───────────────────────────────────────────
@router.post("/login", response_model=TokenResponse)
async def login(body: LoginRequest):
    """
    Login with email and password.
    Returns access token and refresh token.
    """
    # Find user by email
    user = await fetch_one(
        """
        SELECT id, email, name, password, role, is_active
        FROM auth_users WHERE email = $1
        """,
        body.email.lower().strip()
    )

    # Generic error — don't reveal whether email exists
    if not user or not verify_password(body.password, user["password"]):
        raise HTTPException(
            status_code=401,
            detail="Invalid email or password"
        )

    if not user["is_active"]:
        raise HTTPException(status_code=403, detail="Account is disabled")

    # Generate tokens
    access_token  = create_access_token(
        str(user["id"]), user["email"], user["role"]
    )
    refresh_token = create_refresh_token(str(user["id"]))

    # Store refresh token
    await execute(
        """
        INSERT INTO refresh_tokens (user_id, token, expires_at)
        VALUES ($1, $2, $3)
        """,
        user["id"],
        refresh_token,
        get_token_expiry(days=7)
    )

    return TokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        expires_in=JWT_EXPIRY_MINUTES * 60
    )


# ── POST /auth/refresh ─────────────────────────────────────────
@router.post("/refresh", response_model=TokenResponse)
async def refresh(body: RefreshRequest):
    """
    Exchange a valid refresh token for a new access token.
    Rotates the refresh token — old one is deleted, new one issued.
    """
    # Decode refresh token
    payload = decode_token(body.refresh_token)
    if not payload or payload.get("type") != "refresh":
        raise HTTPException(status_code=401, detail="Invalid refresh token")

    # Check token exists in DB and not expired
    stored = await fetch_one(
        """
        SELECT id, user_id, expires_at
        FROM refresh_tokens
        WHERE token = $1
        """,
        body.refresh_token
    )

    if not stored:
        raise HTTPException(status_code=401, detail="Refresh token not found or already used")

    # Check expiry
    if stored["expires_at"].replace(tzinfo=timezone.utc) < datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Refresh token expired")

    # Get user
    user = await fetch_one(
        "SELECT id, email, role, is_active FROM auth_users WHERE id = $1",
        stored["user_id"]
    )

    if not user or not user["is_active"]:
        raise HTTPException(status_code=401, detail="User not found or disabled")

    # Rotate — delete old refresh token
    await execute(
        "DELETE FROM refresh_tokens WHERE id = $1",
        stored["id"]
    )

    # Issue new tokens
    access_token  = create_access_token(
        str(user["id"]), user["email"], user["role"]
    )
    refresh_token = create_refresh_token(str(user["id"]))

    # Store new refresh token
    await execute(
        """
        INSERT INTO refresh_tokens (user_id, token, expires_at)
        VALUES ($1, $2, $3)
        """,
        user["id"],
        refresh_token,
        get_token_expiry(days=7)
    )

    return TokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        expires_in=JWT_EXPIRY_MINUTES * 60
    )


# ── GET /auth/validate ─────────────────────────────────────────
@router.get("/validate")
async def validate(
    authorization: Optional[str] = Header(None)
):
    if not authorization or not authorization.startswith("Bearer "):
        return JSONResponse(
            status_code=401,
            content={"valid": False, "message": "No token provided"}
        )

    token = authorization.replace("Bearer ", "")
    payload = decode_token(token)

    if not payload or payload.get("type") != "access":
        return JSONResponse(
            status_code=401,
            content={"valid": False, "message": "Invalid or expired token"}
        )

    # Return 200 with user context in headers
    response = JSONResponse(
        status_code=200,
        content={
            "valid":   True,
            "user_id": payload.get("sub"),
            "email":   payload.get("email"),
            "role":    payload.get("role")
        }
    )
    response.headers["X-User-ID"]    = payload.get("sub", "")
    response.headers["X-User-Email"] = payload.get("email", "")
    response.headers["X-User-Role"]  = payload.get("role", "")
    return response


# ── POST /auth/logout ──────────────────────────────────────────
@router.post("/logout", status_code=204)
async def logout(body: RefreshRequest):
    """
    Logout — invalidate refresh token.
    Access token expires naturally — can't be revoked without a blocklist.
    """
    await execute(
        "DELETE FROM refresh_tokens WHERE token = $1",
        body.refresh_token
    )
    # Always return 204 — don't reveal if token existed
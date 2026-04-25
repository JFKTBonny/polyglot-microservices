from pydantic import BaseModel, EmailStr, field_validator
from typing import Optional
from uuid import UUID
from datetime import datetime

# ── Request Models ─────────────────────────────────────────────

class RegisterRequest(BaseModel):
    name:     str
    email:    EmailStr
    password: str
    role:     Optional[str] = "user"

    @field_validator("password")
    def password_strength(cls, v):
        if len(v) < 8:
            raise ValueError("password must be at least 8 characters")
        if not any(c.isupper() for c in v):
            raise ValueError("password must contain at least one uppercase letter")
        if not any(c.isdigit() for c in v):
            raise ValueError("password must contain at least one digit")
        return v

    @field_validator("role")
    def role_must_be_valid(cls, v):
        if v not in ["user", "admin"]:
            raise ValueError("role must be 'user' or 'admin'")
        return v

    @field_validator("name")
    def name_must_not_be_empty(cls, v):
        if not v.strip():
            raise ValueError("name must not be empty")
        return v.strip()


class LoginRequest(BaseModel):
    email:    EmailStr
    password: str


class RefreshRequest(BaseModel):
    refresh_token: str


class ValidateRequest(BaseModel):
    token: str


# ── Response Models ────────────────────────────────────────────

class TokenResponse(BaseModel):
    access_token:  str
    refresh_token: str
    token_type:    str = "bearer"
    expires_in:    int


class UserResponse(BaseModel):
    id:         UUID
    email:      str
    name:       str
    role:       str
    is_active:  bool
    created_at: datetime


class RegisterResponse(BaseModel):
    user:   UserResponse
    tokens: TokenResponse


class ValidateResponse(BaseModel):
    valid:   bool
    user_id: Optional[str] = None
    email:   Optional[str] = None
    role:    Optional[str] = None
    message: Optional[str] = None
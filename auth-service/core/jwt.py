import os
from datetime import datetime, timedelta, timezone
from typing import Optional
from jose import JWTError, jwt
from dotenv import load_dotenv

load_dotenv()

JWT_SECRET          = os.getenv("JWT_SECRET", "change-me-in-production")
JWT_ALGORITHM       = os.getenv("JWT_ALGORITHM", "HS256")
JWT_EXPIRY_MINUTES  = int(os.getenv("JWT_EXPIRY_MINUTES", 30))
JWT_REFRESH_EXPIRY_DAYS = int(os.getenv("JWT_REFRESH_EXPIRY_DAYS", 7))

def create_access_token(user_id: str, email: str, role: str) -> str:
    """
    Create a JWT access token.
    Expires in JWT_EXPIRY_MINUTES (default 30 minutes).
    """
    payload = {
        "sub":   user_id,     # subject — who the token is for
        "email": email,
        "role":  role,
        "type":  "access",
        "iat":   datetime.now(timezone.utc),                           # issued at
        "exp":   datetime.now(timezone.utc) + timedelta(minutes=JWT_EXPIRY_MINUTES),  # expires at
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

def create_refresh_token(user_id: str) -> str:
    """
    Create a JWT refresh token.
    Expires in JWT_REFRESH_EXPIRY_DAYS (default 7 days).
    Stored in DB so it can be revoked.
    """
    payload = {
        "sub":  user_id,
        "type": "refresh",
        "iat":  datetime.now(timezone.utc),
        "exp":  datetime.now(timezone.utc) + timedelta(days=JWT_REFRESH_EXPIRY_DAYS),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

def decode_token(token: str) -> Optional[dict]:
    """
    Decode and validate a JWT token.
    Returns the payload dict if valid, None if invalid or expired.
    """
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        return payload
    except JWTError:
        return None

def get_token_expiry(minutes: int = None, days: int = None) -> datetime:
    """
    Calculate token expiry datetime.
    Used when storing refresh tokens in the database.
    """
    if days:
        return datetime.now(timezone.utc) + timedelta(days=days)
    return datetime.now(timezone.utc) + timedelta(minutes=minutes or JWT_EXPIRY_MINUTES)
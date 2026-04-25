import httpx
import os
from dotenv import load_dotenv
from core.circuit_breaker import get_breaker

load_dotenv()

USER_SERVICE_URL = os.getenv("USER_SERVICE_URL", "http://localhost:3001")

# Create circuit breaker for User Service
# Opens after 5 failures, recovers after 30 seconds
_breaker = get_breaker(
    "user-service",
    failure_threshold=5,
    recovery_timeout=30.0,
    timeout=3.0
)

# Fallback — return cached/default user data when circuit is open
@_breaker.fallback
async def _fallback(user_id: str):
    return {
        "id":          user_id,
        "name":        "Unknown User",
        "email":       "unknown@example.com",
        "_from_cache": True,
        "_circuit":    "open"
    }

async def _fetch_user(user_id: str) -> dict | None:
    """Raw HTTP call to User Service — wrapped by circuit breaker."""
    async with httpx.AsyncClient(timeout=3.0) as client:
        response = await client.get(f"{USER_SERVICE_URL}/users/{user_id}")
        if response.status_code == 404:
            return None
        if response.status_code == 200:
            return response.json()
        raise Exception(f"User service returned {response.status_code}")

async def get_user(user_id: str) -> dict | None:
    """
    Get a user by ID through the circuit breaker.
    Returns None if user not found.
    Returns fallback data if circuit is open.
    """
    try:
        return await _breaker.call(_fetch_user, user_id)
    except Exception as err:
        raise Exception(f"User service unavailable: {err}")
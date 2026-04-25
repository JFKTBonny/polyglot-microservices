import httpx
import os
from dotenv import load_dotenv

load_dotenv()

USER_SERVICE_URL = os.getenv("USER_SERVICE_URL", "http://localhost:3001")

async def get_user(user_id: str) -> dict | None:
    """
    Fetch a user from User Service by ID.
    Returns the user dict if found, None if 404, raises exception if service is down.
    """
    async with httpx.AsyncClient(timeout=3.0) as client:
        try:
            response = await client.get(f"{USER_SERVICE_URL}/users/{user_id}")

            if response.status_code == 404:
                return None

            if response.status_code == 200:
                return response.json()

            # Any other status code is unexpected
            raise Exception(
                f"User service returned unexpected status {response.status_code}"
            )

        except httpx.TimeoutException:
            raise Exception("User service timed out after 3 seconds")

        except httpx.ConnectError:
            raise Exception("User service is unreachable")
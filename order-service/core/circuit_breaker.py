import time
import asyncio
import logging
from enum import Enum
from typing import Callable, Any, Optional

logger = logging.getLogger(__name__)

class CircuitState(Enum):
    CLOSED    = "CLOSED"      # normal — calls pass through
    OPEN      = "OPEN"        # failing — calls blocked
    HALF_OPEN = "HALF_OPEN"   # testing — one call allowed

class CircuitBreaker:
    """
    Circuit Breaker implementation for protecting synchronous HTTP calls.

    States:
      CLOSED    → calls pass through normally
      OPEN      → calls fail immediately without hitting the service
      HALF_OPEN → one test call allowed to check if service recovered

    Transitions:
      CLOSED    → OPEN      after failure_threshold consecutive failures
      OPEN      → HALF_OPEN after recovery_timeout seconds
      HALF_OPEN → CLOSED    after one successful call
      HALF_OPEN → OPEN      after one failed call
    """

    def __init__(
        self,
        name:              str,
        failure_threshold: int   = 5,     # failures before opening
        recovery_timeout:  float = 30.0,  # seconds to wait before half-open
        success_threshold: int   = 1,     # successes to close from half-open
        timeout:           float = 3.0,   # call timeout in seconds
    ):
        self.name              = name
        self.failure_threshold = failure_threshold
        self.recovery_timeout  = recovery_timeout
        self.success_threshold = success_threshold
        self.timeout           = timeout

        # State
        self._state            = CircuitState.CLOSED
        self._failure_count    = 0
        self._success_count    = 0
        self._last_failure_time: Optional[float] = None

        # Fallback function — called when circuit is open
        self._fallback: Optional[Callable] = None

        logger.info(f"[circuit-breaker] '{name}' initialised — CLOSED")

    # ── Public API ─────────────────────────────────────────────

    def fallback(self, fn: Callable):
        """Register a fallback function called when circuit is open."""
        self._fallback = fn
        return fn

    async def call(self, fn: Callable, *args, **kwargs) -> Any:
        """
        Execute a function through the circuit breaker.
        Raises CircuitOpenError if circuit is open and no fallback registered.
        """
        # Check if we should transition from OPEN to HALF_OPEN
        self._check_recovery()

        if self._state == CircuitState.OPEN:
            logger.warning(f"[circuit-breaker] '{self.name}' OPEN — fast failing")
            if self._fallback:
                return await self._execute_fallback(*args, **kwargs)
            raise CircuitOpenError(f"Circuit '{self.name}' is OPEN")

        # CLOSED or HALF_OPEN — attempt the call
        try:
            result = await asyncio.wait_for(
                fn(*args, **kwargs),
                timeout=self.timeout
            )
            self._on_success()
            return result

        except asyncio.TimeoutError:
            logger.error(f"[circuit-breaker] '{self.name}' call timed out after {self.timeout}s")
            self._on_failure()
            if self._fallback:
                return await self._execute_fallback(*args, **kwargs)
            raise

        except Exception as err:
            logger.error(f"[circuit-breaker] '{self.name}' call failed: {err}")
            self._on_failure()
            if self._fallback:
                return await self._execute_fallback(*args, **kwargs)
            raise

    @property
    def state(self) -> str:
        return self._state.value

    @property
    def failure_count(self) -> int:
        return self._failure_count

    def stats(self) -> dict:
        return {
            "name":           self.name,
            "state":          self._state.value,
            "failure_count":  self._failure_count,
            "success_count":  self._success_count,
            "last_failure":   self._last_failure_time,
        }

    # ── State transitions ──────────────────────────────────────

    def _on_success(self):
        if self._state == CircuitState.HALF_OPEN:
            self._success_count += 1
            if self._success_count >= self.success_threshold:
                self._close()
        else:
            # Reset failure count on success in CLOSED state
            self._failure_count = 0

    def _on_failure(self):
        self._failure_count    += 1
        self._last_failure_time = time.time()

        if self._state == CircuitState.HALF_OPEN:
            # Test call failed — go back to OPEN
            self._open()
        elif self._failure_count >= self.failure_threshold:
            self._open()

    def _check_recovery(self):
        """Check if enough time has passed to try recovery."""
        if (self._state == CircuitState.OPEN and
            self._last_failure_time and
            time.time() - self._last_failure_time >= self.recovery_timeout):
            self._half_open()

    def _open(self):
        self._state         = CircuitState.OPEN
        self._success_count = 0
        logger.warning(
            f"[circuit-breaker] '{self.name}' → OPEN "
            f"(failures: {self._failure_count}/{self.failure_threshold})"
        )

    def _close(self):
        self._state         = CircuitState.CLOSED
        self._failure_count = 0
        self._success_count = 0
        logger.info(f"[circuit-breaker] '{self.name}' → CLOSED (recovered)")

    def _half_open(self):
        self._state         = CircuitState.HALF_OPEN
        self._success_count = 0
        logger.info(
            f"[circuit-breaker] '{self.name}' → HALF-OPEN "
            f"(testing after {self.recovery_timeout}s)"
        )

    async def _execute_fallback(self, *args, **kwargs) -> Any:
        logger.info(f"[circuit-breaker] '{self.name}' executing fallback")
        if asyncio.iscoroutinefunction(self._fallback):
            return await self._fallback(*args, **kwargs)
        return self._fallback(*args, **kwargs)


class CircuitOpenError(Exception):
    """Raised when a circuit breaker is open and no fallback is registered."""
    pass


# ── Registry — one breaker per service ────────────────────────
# Shared across all requests — state persists for the lifetime
# of the Order Service process

_breakers: dict[str, CircuitBreaker] = {}

def get_breaker(name: str, **kwargs) -> CircuitBreaker:
    """
    Get or create a circuit breaker by name.
    Breakers are singletons — same instance reused across all requests.
    """
    if name not in _breakers:
        _breakers[name] = CircuitBreaker(name, **kwargs)
    return _breakers[name]

def get_all_stats() -> list[dict]:
    """Get stats for all registered circuit breakers."""
    return [b.stats() for b in _breakers.values()]
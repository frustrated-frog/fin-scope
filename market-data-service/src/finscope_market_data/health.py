from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from finscope_market_data.models import DataCapability, ProviderHealth
from finscope_market_data.providers.base import MarketDataProvider


@dataclass
class _HealthState:
    consecutive_failures: int = 0
    open_until: datetime | None = None
    last_success_at: datetime | None = None
    last_failure_at: datetime | None = None
    last_error: str | None = None


class ProviderHealthRegistry:
    def __init__(self, failure_threshold: int = 3, open_seconds: int = 60) -> None:
        self.failure_threshold = failure_threshold
        self.open_seconds = open_seconds
        self._states: dict[tuple[str, DataCapability], _HealthState] = {}

    def is_available(self, provider: MarketDataProvider, capability: DataCapability) -> bool:
        state = self._state(provider, capability)
        if state.open_until is None:
            return True
        now = datetime.now(UTC)
        if now >= state.open_until:
            state.open_until = None
            state.consecutive_failures = 0
            return True
        return False

    def record_success(self, provider: MarketDataProvider, capability: DataCapability) -> None:
        state = self._state(provider, capability)
        state.consecutive_failures = 0
        state.open_until = None
        state.last_success_at = datetime.now(UTC)
        state.last_error = None

    def record_failure(
        self,
        provider: MarketDataProvider,
        capability: DataCapability,
        error: Exception,
    ) -> None:
        state = self._state(provider, capability)
        state.consecutive_failures += 1
        state.last_failure_at = datetime.now(UTC)
        state.last_error = str(error)
        if state.consecutive_failures >= self.failure_threshold:
            state.open_until = datetime.now(UTC) + timedelta(seconds=self.open_seconds)

    def list(self, providers: list[MarketDataProvider]) -> list[ProviderHealth]:
        result: list[ProviderHealth] = []
        now = datetime.now(UTC)
        for provider in sorted(providers, key=lambda item: item.priority):
            states = [self._state(provider, capability) for capability in provider.capabilities]
            opened = [state for state in states if state.open_until is not None and state.open_until > now]
            last_failure = _latest(state.last_failure_at for state in states)
            failed_state = max(
                states,
                key=lambda state: state.last_failure_at or datetime.min.replace(tzinfo=UTC),
            ) if states else _HealthState()
            result.append(
                ProviderHealth(
                    provider_code=provider.provider_code,
                    provider_family=provider.provider_family,
                    capabilities=provider.capabilities,
                    state=(
                        "OPEN" if states and len(opened) == len(states)
                        else "DEGRADED" if opened
                        else "CLOSED"
                    ),
                    consecutive_failures=max((state.consecutive_failures for state in states), default=0),
                    open_until=_latest(state.open_until for state in states),
                    last_success_at=_latest(state.last_success_at for state in states),
                    last_failure_at=last_failure,
                    last_error=failed_state.last_error if last_failure is not None else None,
                )
            )
        return result

    def _state(self, provider: MarketDataProvider, capability: DataCapability) -> _HealthState:
        return self._states.setdefault((provider.provider_code, capability), _HealthState())


def _latest(values):
    present = [value for value in values if value is not None]
    return max(present) if present else None

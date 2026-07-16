from __future__ import annotations

from typing import Any, Protocol

from finscope_market_data.models import DataCapability, StockSymbol


class ProviderError(RuntimeError):
    def __init__(self, error_type: str, message: str, retryable: bool = True) -> None:
        super().__init__(message)
        self.error_type = error_type
        self.retryable = retryable


class MarketDataProvider(Protocol):
    provider_code: str
    provider_family: str
    priority: int
    capabilities: set[DataCapability]

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool: ...

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> Any: ...


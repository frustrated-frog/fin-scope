from __future__ import annotations

from datetime import date

import pandas as pd
import pytest

from finscope_market_data.models import DataCapability, StockSymbol
from finscope_market_data.providers.index_daily import (
    EastmoneyIndexDailyProvider,
    SinaIndexDailyProvider,
)
from finscope_market_data.providers.akshare_provider import AkshareProvider
from finscope_market_data.providers.eastmoney import EastmoneyProvider
from finscope_market_data.providers.pytdx_provider import PytdxDailyProvider


@pytest.mark.asyncio
async def test_eastmoney_index_provider_uses_index_endpoint_and_maps_daily_bars() -> None:
    requested: list[tuple[str, str, str]] = []

    def loader(symbol: str, start_date: str, end_date: str) -> pd.DataFrame:
        requested.append((symbol, start_date, end_date))
        return pd.DataFrame(
            [
                {
                    "date": date(2026, 8, 21),
                    "open": 3880.0,
                    "high": 3920.0,
                    "low": 3860.0,
                    "close": 3905.2,
                    "volume": 500_000_000,
                    "amount": 800_000_000_000,
                }
            ]
        )

    provider = EastmoneyIndexDailyProvider(loader=loader)
    symbol = StockSymbol(market="SH", code="000001")

    result = await provider.fetch(DataCapability.DAILY_BARS, symbol, limit=30)

    assert requested == [("sh000001", "19900101", "20500101")]
    assert result[0].trade_date == "2026-08-21"
    assert result[0].close == 3905.2
    assert result[0].amount == 800_000_000_000
    assert result[0].adjustment == "QFQ"


@pytest.mark.asyncio
async def test_sina_index_provider_is_limited_to_market_pulse_indices() -> None:
    provider = SinaIndexDailyProvider(
        loader=lambda symbol: pd.DataFrame(
            [
                {
                    "date": "2026-08-21",
                    "open": 4800,
                    "high": 4820,
                    "low": 4780,
                    "close": 4810,
                    "volume": 10,
                }
            ]
        )
    )

    index = StockSymbol(market="SZ", code="399001")
    stock = StockSymbol(market="SH", code="600519")

    assert provider.supports(DataCapability.DAILY_BARS, index)
    assert not provider.supports(DataCapability.DAILY_BARS, stock)
    result = await provider.fetch(DataCapability.DAILY_BARS, index, limit=30)
    assert result[0].amount == 0


def test_stock_daily_providers_do_not_claim_market_pulse_indices() -> None:
    index = StockSymbol(market="SH", code="000300")

    assert not AkshareProvider().supports(DataCapability.DAILY_BARS, index)
    assert not EastmoneyProvider().supports(DataCapability.DAILY_BARS, index)
    assert not PytdxDailyProvider(api_factory=lambda: None).supports(
        DataCapability.DAILY_BARS, index
    )

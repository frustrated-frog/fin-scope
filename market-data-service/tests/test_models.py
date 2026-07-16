from datetime import UTC, datetime

import pytest
from pydantic import ValidationError

from finscope_market_data.models import (
    DataCapability,
    DataEnvelope,
    Market,
    ProviderAttempt,
    QualityStatus,
    StockQuote,
    StockSymbol,
)


def test_stock_symbol_normalizes_market_and_vendor_codes() -> None:
    symbol = StockSymbol(market="sh", code="600519")

    assert symbol.market is Market.SH
    assert symbol.code == "600519"
    assert symbol.eastmoney_secid == "1.600519"
    assert symbol.prefixed_code == "sh600519"


def test_stock_symbol_rejects_invalid_a_share_code() -> None:
    with pytest.raises(ValidationError):
        StockSymbol(market="SZ", code="123")


def test_data_envelope_keeps_quality_provenance_and_attempts() -> None:
    now = datetime(2026, 7, 16, 10, 30, tzinfo=UTC)
    symbol = StockSymbol(market="SH", code="600519")
    quote = StockQuote(symbol=symbol, name="贵州茅台", price=1480.5, observed_at=now)
    attempt = ProviderAttempt(
        provider_code="TENCENT_QUOTE",
        provider_family="TENCENT",
        success=True,
        duration_ms=21,
    )

    envelope = DataEnvelope[StockQuote](
        capability=DataCapability.QUOTE,
        symbol=symbol,
        quality_status=QualityStatus.FRESH_PRIMARY,
        source_code="TENCENT_QUOTE",
        source_family="TENCENT",
        as_of=now,
        retrieved_at=now,
        attempts=[attempt],
        data=quote,
    )

    payload = envelope.model_dump(mode="json")
    assert payload["quality_status"] == "FRESH_PRIMARY"
    assert payload["source_code"] == "TENCENT_QUOTE"
    assert payload["attempts"][0]["success"] is True
    assert payload["data"]["price"] == 1480.5


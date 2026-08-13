from __future__ import annotations

from datetime import date, datetime, timedelta

import pytest

from finscope_market_data.discovery.schemas import DiscoveryRequest, DiscoverySector
from finscope_market_data.discovery.service import StockDiscoveryService
from finscope_market_data.models import DailyBar, StockSymbol


class FakeProvider:
    source_code = "FAKE_HOT_SECTORS"
    source_family = "FAKE"

    def sectors(self, limit: int):
        return [
            DiscoverySector(
                code="BK0001",
                name="先进制造",
                category="INDUSTRY",
                source_code=self.source_code,
                source_family=self.source_family,
                source_rank=1,
                main_net_inflow=1000,
                retrieved_at=datetime.now().isoformat(),
            )
        ]

    def constituents(self, sector: DiscoverySector):
        return [("000001", "SZ", "优质制造"), ("000002", "SZ", "*ST样本")]


class BrokenProvider(FakeProvider):
    def sectors(self, limit: int):
        raise RuntimeError("upstream unavailable")


class BrokenConstituentProvider(FakeProvider):
    source_family = "BROKEN_MEMBERS"

    def constituents(self, sector: DiscoverySector):
        raise RuntimeError("constituent contract drift")


class FakeMarket:
    async def bars(self, market: str, code: str):
        symbol = StockSymbol(market=market, code=code)
        start = date(2023, 1, 1)
        return [
            DailyBar(
                symbol=symbol,
                trade_date=(start + timedelta(days=index)).isoformat(),
                open=10 + index * 0.01,
                high=10.3 + index * 0.01,
                low=9.8 + index * 0.01,
                close=10.1 + index * 0.01,
                volume=1_000_000,
                amount=10_000_000,
                adjustment="QFQ",
            )
            for index in range(800)
        ]


@pytest.mark.asyncio
async def test_service_falls_back_and_rejects_st_candidate() -> None:
    service = StockDiscoveryService(
        providers=[BrokenProvider(), FakeProvider()],
        market=FakeMarket(),
        forecast_builder=lambda candidate, bars, request: {
            "qualified": True,
            "conclusion": "ROBUST",
            "calibrated_probability": 0.64,
            "probability_lower_bound": 0.55,
            "brier_skill_score": 0.1,
            "locked_accuracy": 0.58,
            "locked_log_loss": 0.64,
            "risk_adjusted_return": 0.45,
            "max_drawdown": -0.12,
            "stability_score": 0.8,
            "health_status": "HEALTHY",
        },
    )

    report = await service.discover(DiscoveryRequest(budget=6000))

    assert report.source_family == "FAKE"
    assert report.funnel.constituent_count == 2
    assert report.funnel.admitted_count == 1
    assert report.final_candidates[0].code == "000001"
    rejected = next(item for item in report.candidates if item.code == "000002")
    assert "SPECIAL_TREATMENT" in rejected.rejection_reasons
    assert any("upstream unavailable" in warning for warning in report.warnings)


@pytest.mark.asyncio
async def test_service_falls_back_when_primary_sector_members_are_unavailable() -> None:
    service = StockDiscoveryService(
        providers=[BrokenConstituentProvider(), FakeProvider()],
        market=FakeMarket(),
        forecast_builder=lambda candidate, bars, request: {
            "qualified": True,
            "conclusion": "ROBUST",
            "calibrated_probability": 0.64,
            "probability_lower_bound": 0.55,
            "brier_skill_score": 0.1,
            "locked_accuracy": 0.58,
            "locked_log_loss": 0.64,
            "risk_adjusted_return": 0.45,
            "max_drawdown": -0.12,
            "stability_score": 0.8,
            "health_status": "HEALTHY",
        },
    )

    report = await service.discover(DiscoveryRequest(budget=6000))

    assert report.source_family == "FAKE"
    assert report.funnel.constituent_count == 2
    assert any("constituent contract drift" in warning for warning in report.warnings)


@pytest.mark.asyncio
async def test_service_never_uses_bars_after_requested_business_date() -> None:
    service = StockDiscoveryService(
        providers=[FakeProvider()],
        market=FakeMarket(),
        forecast_builder=lambda candidate, bars, request: {
            "qualified": False,
            "conclusion": "NO_CLEAR_ADVANTAGE",
            "calibrated_probability": 0.5,
            "probability_lower_bound": 0.4,
            "brier_skill_score": 0,
            "locked_accuracy": 0.5,
            "locked_log_loss": 0.7,
            "risk_adjusted_return": 0,
            "max_drawdown": -0.1,
            "stability_score": 0.5,
            "health_status": "DEGRADED",
        },
    )

    report = await service.discover(
        DiscoveryRequest(business_date="2025-02-28", budget=6000)
    )

    assert report.as_of_date == "2025-02-28"
    assert report.funnel.admitted_count == 1

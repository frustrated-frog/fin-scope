from __future__ import annotations

import json
from datetime import date, datetime, timedelta

import pytest

from finscope_market_data.discovery.schemas import (
    DiscoveryCandidate,
    DiscoveryRequest,
    DiscoverySector,
)
from finscope_market_data.discovery.service import (
    DiscoveryBarsSnapshot,
    StockDiscoveryService,
    _forecast,
)
from finscope_market_data.models import DailyBar, StockSymbol
from finscope_market_data.forecast.panel import PanelArtifactStore


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


class FlakyProvider(FakeProvider):
    def __init__(self) -> None:
        self.calls = 0

    def sectors(self, limit: int):
        self.calls += 1
        if self.calls == 1:
            raise RuntimeError("temporary disconnect")
        return super().sectors(limit)


class BrokenSecondaryProvider(BrokenProvider):
    source_code = "SECONDARY_HOT_SECTORS"
    source_family = "SECONDARY"


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
async def test_discovery_publishes_bounded_panel_artifact_from_cached_histories(
    tmp_path,
) -> None:
    store = PanelArtifactStore(tmp_path / "quant")
    service = StockDiscoveryService(
        providers=[FakeProvider()],
        market=FakeMarket(),
        panel_store=store,
    )
    base = await FakeMarket().bars("SH", "600000")
    histories = {
        f"{600000 + index}": [
            item.model_copy(update={
                "symbol": StockSymbol(market="SH", code=f"{600000 + index}")
            })
            for item in base
        ]
        for index in range(20)
    }
    warnings: list[str] = []

    artifact = service._train_panel_artifact(histories, 5, warnings)

    assert artifact is not None
    assert artifact.universe_size == 20
    assert store.load(5) is not None
    assert warnings == []


class ShortHistoryMarket:
    async def bars(self, market: str, code: str):
        symbol = StockSymbol(market=market, code=code)
        return [
            DailyBar(
                symbol=symbol,
                trade_date="2026-08-14",
                open=10,
                high=10.2,
                low=9.8,
                close=10,
                volume=100_000,
                amount=1_000_000,
                adjustment="QFQ",
            )
        ]


class EmptyMarket:
    async def bars(self, market: str, code: str):
        return []


class StaleMarket(FakeMarket):
    async def bars(self, market: str, code: str):
        bars = await super().bars(market, code)
        return DiscoveryBarsSnapshot(
            bars=bars,
            quality_status="STALE_FALLBACK",
            stale_age_seconds=5 * 24 * 60 * 60,
            warnings=("在线源失败，命中旧快照",),
        )


@pytest.mark.asyncio
async def test_universe_retries_the_same_provider_before_switching_source() -> None:
    provider = FlakyProvider()
    service = StockDiscoveryService(
        providers=[provider],
        market=FakeMarket(),
        provider_attempts=2,
        provider_retry_delay_seconds=0,
    )

    sectors, selected, members, warnings = await service._universe(5)

    assert provider.calls == 2
    assert selected.source_family == "FAKE"
    assert len(sectors) == 1
    assert len(members) == 2
    assert any("第1/2次" in warning for warning in warnings)


@pytest.mark.asyncio
async def test_universe_failure_reports_each_provider_and_attempt() -> None:
    service = StockDiscoveryService(
        providers=[BrokenProvider(), BrokenSecondaryProvider()],
        market=FakeMarket(),
        provider_attempts=2,
        provider_retry_delay_seconds=0,
    )

    with pytest.raises(RuntimeError) as captured:
        await service._universe(5)

    message = str(captured.value)
    assert "FAKE[2/2]" in message
    assert "SECONDARY[2/2]" in message
    assert "upstream unavailable" in message


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
async def test_service_uses_last_successful_universe_snapshot_when_sources_are_down(
    tmp_path,
) -> None:
    snapshot = tmp_path / "stock-discovery-universe.json"
    builder = lambda candidate, bars, request: {
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
    }
    await StockDiscoveryService(
        providers=[FakeProvider()],
        market=FakeMarket(),
        forecast_builder=builder,
        universe_snapshot_path=snapshot,
    ).discover(DiscoveryRequest(budget=6000))

    report = await StockDiscoveryService(
        providers=[BrokenProvider()],
        market=FakeMarket(),
        forecast_builder=builder,
        universe_snapshot_path=snapshot,
    ).discover(DiscoveryRequest(budget=6000))

    assert report.source_family == "FAKE"
    assert report.funnel.constituent_count == 2
    assert any("本地热门板块快照" in warning for warning in report.warnings)


@pytest.mark.asyncio
async def test_service_rejects_expired_universe_snapshot(tmp_path) -> None:
    snapshot = tmp_path / "stock-discovery-universe.json"
    first = StockDiscoveryService(
        providers=[FakeProvider()],
        market=FakeMarket(),
        universe_snapshot_path=snapshot,
    )
    await first._universe(5)
    payload = json.loads(snapshot.read_text(encoding="utf-8"))
    payload["snapshot_at"] = (datetime.now() - timedelta(days=5)).isoformat()
    snapshot.write_text(json.dumps(payload), encoding="utf-8")

    service = StockDiscoveryService(
        providers=[BrokenProvider()],
        market=FakeMarket(),
        universe_snapshot_path=snapshot,
    )

    with pytest.raises(RuntimeError, match="所有热门板块"):
        await service._universe(5)


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


@pytest.mark.asyncio
async def test_short_history_is_rejected_without_aborting_the_batch() -> None:
    service = StockDiscoveryService(
        providers=[FakeProvider()],
        market=ShortHistoryMarket(),
    )

    report = await service.discover(
        DiscoveryRequest(business_date="2026-08-14", budget=6000)
    )

    candidate = next(item for item in report.candidates if item.code == "000001")
    assert candidate.admitted is False
    assert candidate.factors == {}
    assert "INSUFFICIENT_QFQ_HISTORY" in candidate.rejection_reasons
    assert report.funnel.final_count == 0


@pytest.mark.asyncio
async def test_empty_market_data_is_rejected_instead_of_faking_a_low_price() -> None:
    service = StockDiscoveryService(
        providers=[FakeProvider()],
        market=EmptyMarket(),
    )

    report = await service.discover(
        DiscoveryRequest(business_date="2026-08-14", budget=6000)
    )

    candidate = next(item for item in report.candidates if item.code == "000001")
    assert candidate.admitted is False
    assert candidate.budget_eligible is False
    assert "MARKET_DATA_UNAVAILABLE" in candidate.rejection_reasons
    assert report.funnel.admitted_count == 0


@pytest.mark.asyncio
async def test_excessively_stale_snapshot_is_rejected_and_traced() -> None:
    service = StockDiscoveryService(
        providers=[FakeProvider()],
        market=StaleMarket(),
    )

    report = await service.discover(DiscoveryRequest(budget=6000))

    candidate = next(item for item in report.candidates if item.code == "000001")
    assert candidate.admitted is False
    assert "STALE_MARKET_DATA" in candidate.rejection_reasons
    assert any("在线源失败" in warning for warning in report.warnings)


@pytest.mark.parametrize("decision", ["DOWN", "ABSTAIN"])
def test_deep_forecast_rejects_non_up_decisions(monkeypatch, decision: str) -> None:
    class Value:
        status = "ROBUST"
        up_probability = 0.72
        probability_interval = None
        performance = None
        parameter_stability = None
        warnings = []
        decision_reason = "当前方向不满足上涨门禁"

        class Qualification:
            status = "QUALIFIED"

            class LockedTest:
                calibrated_metrics = None

            locked_test = LockedTest()

        qualification = Qualification()

        def __init__(self, current_decision: str):
            self.decision = current_decision

        def model_dump(self, **kwargs):
            return {"decision": self.decision}

    monkeypatch.setattr(
        "finscope_market_data.discovery.service.build_forecast",
        lambda *args, **kwargs: Value(decision),
    )
    candidate = DiscoveryCandidate(
        code="000001",
        market="SZ",
        name="样本",
        price=10,
        lot_cost=1005,
        budget_eligible=True,
        admitted=True,
    )

    result = _forecast(candidate, [], DiscoveryRequest())

    assert result["qualified"] is False
    assert result["health_status"] == "DEGRADED"


def test_deep_forecast_rejects_up_signal_without_cost_adjusted_advantage(
    monkeypatch,
) -> None:
    class Summary:
        sharpe_ratio = 0.2
        max_drawdown = -0.1

    class Benchmark:
        sharpe_ratio = 0.3

    class Performance:
        strategy = Summary()
        benchmark = Benchmark()
        excess_return = -0.01

    class Qualification:
        status = "QUALIFIED"

        class LockedTest:
            calibrated_metrics = None

        locked_test = LockedTest()

    class Value:
        status = "CONDITIONAL"
        decision = "UP"
        up_probability = 0.65
        probability_interval = None
        performance = Performance()
        parameter_stability = None
        warnings = []
        decision_reason = "概率向上但成本后没有相对优势"
        qualification = Qualification()

        def model_dump(self, **kwargs):
            return {"decision": self.decision}

    monkeypatch.setattr(
        "finscope_market_data.discovery.service.build_forecast",
        lambda *args, **kwargs: Value(),
    )
    candidate = DiscoveryCandidate(
        code="000001",
        market="SZ",
        name="样本",
        price=10,
        lot_cost=1005,
        budget_eligible=True,
        admitted=True,
    )

    result = _forecast(candidate, [], DiscoveryRequest())

    assert result["qualified"] is False
    assert result["health_status"] == "DEGRADED"


def test_deep_forecast_reuses_shared_backtest_audit_summary(monkeypatch) -> None:
    class Audit:
        status = "PASS"
        entry_date_agreement_rate = 1.0
        return_delta = 0.0002

    class Value:
        status = "NO_CLEAR_EDGE"
        decision = "ABSTAIN"
        up_probability = 0.55
        probability_interval = None
        performance = None
        parameter_stability = None
        backtest_audit = Audit()
        warnings = []
        decision_reason = "样本外优势不足"
        qualification = None

        def model_dump(self, **kwargs):
            return {"backtestAudit": {"status": "PASS"}}

    monkeypatch.setattr(
        "finscope_market_data.discovery.service.build_forecast",
        lambda *args, **kwargs: Value(),
    )
    candidate = DiscoveryCandidate(
        code="000001",
        market="SZ",
        name="样本",
        price=10,
        lot_cost=1005,
        budget_eligible=True,
        admitted=True,
    )

    result = _forecast(candidate, [], DiscoveryRequest())

    assert result["backtest_audit_status"] == "PASS"
    assert result["backtest_entry_date_agreement_rate"] == 1.0
    assert result["backtest_return_delta"] == 0.0002

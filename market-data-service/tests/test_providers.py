from __future__ import annotations

import asyncio
import json
from typing import Any

import pytest

from finscope_market_data.models import DataCapability, FinancialStatementType, StockSymbol
from finscope_market_data.providers.akshare_provider import AkshareProvider
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.eastmoney import EastmoneyProvider
from finscope_market_data.providers.pytdx_provider import PytdxDailyProvider
from finscope_market_data.providers.sina import SinaQuoteProvider
from finscope_market_data.providers.tencent import TencentQuoteProvider


def test_tencent_parser_maps_rich_quote_fields() -> None:
    raw = (
        'v_sh600519="51~贵州茅台~600519~1480.00~1471.20~1475.00~123456'
        '~~~~~~~~~~~~~~~~~~~~~~~~20260714100000~8.80~0.60~1488.00~1468.10~~'
        '123456~187040.00~~~~~~1.35~~~~~~~~~CN";'
    )

    quote = TencentQuoteProvider.parse(raw, StockSymbol(market="SH", code="600519"))

    assert quote.name == "贵州茅台"
    assert quote.price == 1480.0
    assert quote.previous_close == 1471.2
    assert quote.volume == 12_345_600
    assert quote.amount == 1_870_400_000
    assert quote.change_pct == 0.6


def test_sina_parser_maps_quote_fields() -> None:
    raw = (
        'var hq_str_sz000001="平安银行,11.10,11.00,11.23,11.30,10.98,11.22,11.23,'
        '12345600,138000000.00,100,11.22,200,11.21,300,11.20,400,11.19,500,11.18,'
        '100,11.23,200,11.24,300,11.25,400,11.26,500,11.27,2026-07-16,10:30:00,00";'
    )

    quote = SinaQuoteProvider.parse(raw, StockSymbol(market="SZ", code="000001"))

    assert quote.name == "平安银行"
    assert quote.price == 11.23
    assert quote.volume == 12_345_600
    assert quote.amount == 138_000_000
    assert round(quote.change_pct, 2) == 2.09


def test_eastmoney_parser_maps_daily_bars_and_capital_flow() -> None:
    symbol = StockSymbol(market="SH", code="600519")
    bars_payload = json.loads(
        '{"data":{"klines":["2026-07-14,1480.00,1481.50,1495.00,1475.00,'
        '1210000,1800000000,1.35,0.10,1.50,3.21"]}}'
    )
    flow_payload = json.loads(
        '{"data":{"klines":["2026-07-14,180000000,-30000000,20000000,'
        '50000000,140000000,12.5,-2.1,1.4,3.5,9.7"]}}'
    )

    bars = EastmoneyProvider.parse_daily_bars(bars_payload, symbol)
    flows = EastmoneyProvider.parse_flow(flow_payload, symbol, daily=True)

    assert bars[0].close == 1481.5
    assert bars[0].amount == 1_800_000_000
    assert bars[0].turnover_rate == 3.21
    assert bars[0].adjustment == "QFQ"
    assert flows[0].main_net_inflow == 180_000_000
    assert flows[0].super_large_net_inflow == 140_000_000
    assert flows[0].main_net_inflow_ratio == 12.5


def test_akshare_mapping_accepts_chinese_dataframe_columns() -> None:
    symbol = StockSymbol(market="SZ", code="000001")
    daily_records = [
        {
            "日期": "2026-07-16",
            "开盘": 11.1,
            "收盘": 11.23,
            "最高": 11.3,
            "最低": 10.98,
            "成交量": 123456,
            "成交额": 138000000,
            "振幅": 2.91,
            "涨跌幅": 2.09,
            "涨跌额": 0.23,
            "换手率": 1.53,
        }
    ]
    flow_records = [
        {
            "日期": "2026-07-16",
            "收盘价": 11.23,
            "涨跌幅": 2.09,
            "主力净流入-净额": 1000000,
            "超大单净流入-净额": 500000,
            "大单净流入-净额": 500000,
            "中单净流入-净额": -200000,
            "小单净流入-净额": -800000,
        }
    ]

    bars = AkshareProvider.map_daily_records(daily_records, symbol)
    flows = AkshareProvider.map_flow_records(flow_records, symbol)

    assert bars[0].close == 11.23
    assert bars[0].volume == 123456
    assert bars[0].adjustment == "QFQ"
    assert flows[0].main_net_inflow == 1_000_000
    assert flows[0].small_net_inflow == -800_000


def test_akshare_maps_three_financial_statements_with_stable_concepts() -> None:
    symbol = StockSymbol(market="SH", code="600519")
    common = {
        "REPORT_DATE": "2026-06-30 00:00:00",
        "REPORT_TYPE": "中报",
        "NOTICE_DATE": "2026-08-20 00:00:00",
        "CURRENCY": "CNY",
    }

    result = AkshareProvider.map_financial_records(
        symbol=symbol,
        period_end="2026-06-30",
        report_type="HALF_YEAR",
        scope="CONSOLIDATED",
        income_records=[
            {
                **common,
                "TOTAL_OPERATE_INCOME": 1_200_000_000.12,
                "PARENT_NETPROFIT": 210_000_000,
                "SALE_EXPENSE": 80_000_000,
            }
        ],
        balance_records=[
            {
                **common,
                "TOTAL_ASSETS": 3_400_000_000,
                "TOTAL_LIABILITIES": 1_100_000_000,
                "TOTAL_CURRENT_ASSETS": 1_500_000_000,
                "TOTAL_CURRENT_LIAB": 620_000_000,
                "ACCOUNTS_RECE": 220_000_000,
                "INVENTORY": 180_000_000,
            }
        ],
        cash_flow_records=[
            {
                **common,
                "NETCASH_OPERATE": 180_000_000,
                "CONSTRUCT_LONG_ASSET": 90_000_000,
                "END_CCE": 520_000_000,
            }
        ],
    )

    assert result.report.period_end == "2026-06-30"
    assert result.report.report_type == "HALF_YEAR"
    assert result.report.published_at is not None
    assert {item.statement_type for item in result.statements} == {
        FinancialStatementType.INCOME,
        FinancialStatementType.BALANCE_SHEET,
        FinancialStatementType.CASH_FLOW,
    }
    values = {
        value.concept_code: value.value
        for statement in result.statements
        for value in statement.values
    }
    assert values["REVENUE"] == "1200000000.12"
    assert values["NET_PROFIT_PARENT"] == "210000000"
    assert values["ACCOUNTS_RECEIVABLE"] == "220000000"
    assert values["INVENTORY"] == "180000000"
    assert values["TOTAL_CURRENT_ASSETS"] == "1500000000"
    assert values["TOTAL_CURRENT_LIABILITIES"] == "620000000"
    assert values["OPERATING_CASH_FLOW"] == "180000000"
    assert values["CAPITAL_EXPENDITURE"] == "90000000"


@pytest.mark.asyncio
async def test_akshare_reports_minute_flow_as_non_retryable_capability_gap() -> None:
    provider = AkshareProvider()

    with pytest.raises(ProviderError) as captured:
        await provider.fetch(
            capability=DataCapability.CAPITAL_FLOW,
            symbol=StockSymbol(market="SH", code="600519"),
            require_minute=True,
        )

    assert captured.value.error_type == "UNSUPPORTED_GRANULARITY"
    assert captured.value.retryable is False


@pytest.mark.asyncio
async def test_akshare_daily_bars_honors_requested_limit(monkeypatch: pytest.MonkeyPatch) -> None:
    import akshare as ak

    class Frame:
        def to_dict(self, orient: str) -> list[dict[str, Any]]:
            assert orient == "records"
            return [
                {
                    "日期": f"2026-07-{day:02d}",
                    "开盘": 10 + day,
                    "收盘": 10 + day,
                    "最高": 10 + day,
                    "最低": 10 + day,
                    "成交量": 100,
                }
                for day in range(1, 6)
            ]

    monkeypatch.setattr(ak, "stock_zh_a_hist", lambda **kwargs: Frame())
    provider = AkshareProvider()

    bars = await provider.fetch(
        capability=DataCapability.DAILY_BARS,
        symbol=StockSymbol(market="SH", code="600519"),
        limit=2,
    )

    assert [bar.trade_date for bar in bars] == ["2026-07-04", "2026-07-05"]


def test_pytdx_mapping_provides_independent_daily_bar_fallback() -> None:
    symbol = StockSymbol(market="SH", code="600519")
    rows = [
        {
            "datetime": "2026-07-16 15:00",
            "open": 1252.0,
            "high": 1264.62,
            "low": 1245.05,
            "close": 1257.72,
            "vol": 28422,
            "amount": 3565850000,
        }
    ]

    bars = PytdxDailyProvider.map_rows(rows, symbol)

    assert bars[0].trade_date == "2026-07-16"
    assert bars[0].close == 1257.72
    assert bars[0].amount == 3_565_850_000


@pytest.mark.asyncio
async def test_pytdx_uses_bounded_server_fallback_without_global_probe() -> None:
    class FakeApi:
        def __init__(self) -> None:
            self.connect_calls: list[tuple[str, int, int]] = []
            self.disconnect_calls = 0

        def connect(self, host: str, port: int, time_out: int) -> bool:
            self.connect_calls.append((host, port, time_out))
            return host == "working.example"

        def get_security_bars(
            self,
            category: int,
            market: int,
            code: str,
            start: int,
            count: int,
        ) -> list[dict[str, Any]]:
            assert (category, market, code, start, count) == (9, 1, "600519", 0, 2)
            return [
                {
                    "datetime": "2026-07-16 15:00",
                    "open": 1252.0,
                    "high": 1264.62,
                    "low": 1245.05,
                    "close": 1257.72,
                    "vol": 28422,
                    "amount": 3565850000,
                }
            ]

        def disconnect(self) -> None:
            self.disconnect_calls += 1

    api = FakeApi()
    provider = PytdxDailyProvider(
        api_factory=lambda: api,
        servers=(("unreachable.example", 7709), ("working.example", 7709)),
    )

    bars = await provider.fetch(
        capability=DataCapability.DAILY_BARS,
        symbol=StockSymbol(market="SH", code="600519"),
        limit=2,
    )

    assert bars[0].close == 1257.72
    assert api.connect_calls == [
        ("unreachable.example", 7709, 2),
        ("working.example", 7709, 2),
    ]
    assert provider._server == ("working.example", 7709)


@pytest.mark.asyncio
async def test_eastmoney_capital_flow_serializes_its_upstream_requests() -> None:
    class TrackingHttpClient:
        def __init__(self) -> None:
            self.active = 0
            self.max_active = 0

        async def get_json(self, provider_code: str, url: str, **kwargs: Any) -> dict[str, Any]:
            self.active += 1
            self.max_active = max(self.max_active, self.active)
            try:
                await asyncio.sleep(0.01)
                if "fflow/kline/get" in url:
                    return {"data": {"klines": ["2026-07-16 10:00,1,2,3,4,5"]}}
                if "fflow/daykline/get" in url:
                    return {"data": {"klines": ["2026-07-16,1,2,3,4,5"]}}
                if "kline/get" in url:
                    return {
                        "data": {
                            "klines": [
                                "2026-07-16,11.10,11.23,11.30,10.98,123456,138000000,2.91,2.09,0.23,1.53"
                            ]
                        }
                    }
                return {
                    "data": {
                        "f43": 1123,
                        "f47": 123456,
                        "f48": 138000000,
                        "f57": "000001",
                        "f58": "平安银行",
                        "f60": 1100,
                        "f168": 153,
                    }
                }
            finally:
                self.active -= 1

    http = TrackingHttpClient()
    provider = EastmoneyProvider(http=http)  # type: ignore[arg-type]

    result = await provider.fetch(
        capability=DataCapability.CAPITAL_FLOW,
        symbol=StockSymbol(market="SZ", code="000001"),
    )

    assert result.minute_points
    assert result.daily_points
    assert http.max_active == 1

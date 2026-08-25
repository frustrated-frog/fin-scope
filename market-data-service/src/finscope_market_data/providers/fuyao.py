from __future__ import annotations

import asyncio
from datetime import datetime, timedelta
from decimal import Decimal, InvalidOperation
import threading
import time
from typing import Any
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

import httpx

from finscope_market_data.discovery.constituents import ConstituentBatch
from finscope_market_data.discovery.schemas import DiscoverySector
from finscope_market_data.models import (
    DailyBar,
    DataCapability,
    FinancialReportMeta,
    FinancialStatement,
    FinancialStatementType,
    FinancialStatementValue,
    FinancialStatementsData,
    StockSymbol,
)
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient


SHANGHAI = ZoneInfo("Asia/Shanghai")


def unwrap_fuyao_response(payload: dict[str, Any]) -> dict[str, Any]:
    code = payload.get("code")
    if code != 0:
        message = str(payload.get("message") or "扶摇数据接口调用失败")
        if code == 4001:
            raise ProviderError("FUYAO_RATE_LIMITED", message, True)
        if code in {3002, 5001, 5002, 5003}:
            raise ProviderError(f"FUYAO_{code}", message, True)
        raise ProviderError(f"FUYAO_{code}", message, False)
    data = payload.get("data")
    if not isinstance(data, dict):
        raise ProviderError("SCHEMA_DRIFT", "扶摇响应缺少对象 data", False)
    return data


class FuyaoAsyncApiClient:
    def __init__(
        self,
        api_key: str,
        base_url: str = "https://fuyao.aicubes.cn",
        http: ProviderHttpClient | None = None,
    ) -> None:
        self.api_key = api_key.strip()
        self.base_url = base_url.rstrip("/")
        self.http = http or ProviderHttpClient(
            timeout_seconds=15,
            minimum_interval_seconds=0.1,
        )

    async def get_data(self, path: str, params: dict[str, Any]) -> dict[str, Any]:
        if not self.api_key:
            raise ProviderError("PROVIDER_DISABLED", "扶摇 API Key 未配置", False)
        payload = await self.http.get_json(
            "FUYAO",
            self.base_url + path,
            headers={"X-api-key": self.api_key},
            params=params,
        )
        return unwrap_fuyao_response(payload)

    async def aclose(self) -> None:
        await self.http.aclose()


class FuyaoSyncApiClient:
    def __init__(
        self,
        api_key: str,
        base_url: str = "https://fuyao.aicubes.cn",
        timeout_seconds: float = 15,
        minimum_interval_seconds: float = 0.1,
        client: httpx.Client | None = None,
    ) -> None:
        self.api_key = api_key.strip()
        self.base_url = base_url.rstrip("/")
        self.minimum_interval_seconds = max(0.0, minimum_interval_seconds)
        self.client = client or httpx.Client(
            timeout=httpx.Timeout(timeout_seconds, connect=min(timeout_seconds, 5)),
            headers={"User-Agent": "FinScope-Market-Data/0.1"},
        )
        self.lock = threading.Lock()
        self.last_request_started_at: float | None = None

    def get_data(self, path: str, params: dict[str, Any]) -> dict[str, Any]:
        if not self.api_key:
            raise ProviderError("PROVIDER_DISABLED", "扶摇 API Key 未配置", False)
        try:
            with self.lock:
                if self.last_request_started_at is not None:
                    remaining = self.minimum_interval_seconds - (
                        time.monotonic() - self.last_request_started_at
                    )
                    if remaining > 0:
                        time.sleep(remaining)
                self.last_request_started_at = time.monotonic()
                response = self.client.get(
                    self.base_url + path,
                    headers={"X-api-key": self.api_key},
                    params=params,
                )
        except httpx.TimeoutException as error:
            raise ProviderError("TIMEOUT", "扶摇数据接口请求超时", True) from error
        except httpx.HTTPError as error:
            raise ProviderError("CONNECTION_ERROR", "扶摇数据接口连接失败", True) from error
        if response.status_code < 200 or response.status_code >= 300:
            retryable = response.status_code in {429, 500, 502, 503, 504}
            raise ProviderError(
                f"HTTP_{response.status_code}",
                f"扶摇数据接口返回 HTTP {response.status_code}",
                retryable,
            )
        try:
            payload = response.json()
        except ValueError as error:
            raise ProviderError("SCHEMA_DRIFT", "扶摇数据接口返回无效 JSON", False) from error
        if not isinstance(payload, dict):
            raise ProviderError("SCHEMA_DRIFT", "扶摇数据接口返回非对象响应", False)
        return unwrap_fuyao_response(payload)

    def close(self) -> None:
        self.client.close()


class FuyaoMarketDumpClient:
    _PATHS = {
        "daily-k": "/dump/market-dumps/daily-k/download-url",
        "daily-k-10d": "/dump/market-dumps/daily-k-10d/download-url",
        "adjustment-factors": (
            "/dump/market-dumps/adjustment-factors/download-url"
        ),
    }

    def __init__(self, api: FuyaoAsyncApiClient) -> None:
        self.api = api

    async def download_url(self, kind: str) -> dict[str, Any]:
        path = self._PATHS.get(kind)
        if path is None:
            raise ProviderError(
                "INVALID_DUMP_KIND",
                f"不支持的扶摇全市场导出类型：{kind}",
                False,
            )
        data = await self.api.get_data(path, {})
        download_url = data.get("download_url") or data.get("url")
        if not isinstance(download_url, str):
            raise ProviderError(
                "SCHEMA_DRIFT", "扶摇全市场导出响应缺少下载链接", False
            )
        parsed = urlsplit(download_url)
        if parsed.scheme != "https" or not parsed.netloc:
            raise ProviderError(
                "SCHEMA_DRIFT", "扶摇全市场导出下载链接无效", False
            )
        result: dict[str, Any] = {
            "kind": kind,
            "download_url": download_url,
        }
        for field in ("expires_in", "expires_at", "expires_at_ms"):
            if data.get(field) is not None:
                result[field] = data[field]
        return result

    async def aclose(self) -> None:
        await self.api.aclose()


class FuyaoMarketDataProvider:
    provider_code = "FUYAO_TONGHUASHUN_API"
    provider_family = "TONGHUASHUN"
    priority = 5
    capabilities = {
        DataCapability.DAILY_BARS,
        DataCapability.FINANCIAL_STATEMENTS,
    }

    _INCOME_FIELDS = {
        "operating_income": ("REVENUE", "营业收入"),
        "operating_costs": ("OPERATING_COST", "营业成本"),
        "operating_expenses": ("TOTAL_OPERATING_COST", "营业总成本"),
        "sales_fee": ("SELLING_EXPENSE", "销售费用"),
        "manage_fee": ("ADMIN_EXPENSE", "管理费用"),
        "research_and_development_expenses": ("RND_EXPENSE", "研发费用"),
        "operating_profit": ("OPERATING_PROFIT", "营业利润"),
        "interest_expenses": ("INTEREST_EXPENSE", "利息费用"),
        "profit_total": ("TOTAL_PROFIT", "利润总额"),
        "income_tax_expense": ("INCOME_TAX", "所得税费用"),
        "net_profit": ("NET_PROFIT", "净利润"),
        "parent_holder_net_profit": ("NET_PROFIT_PARENT", "归母净利润"),
        "basic_eps": ("BASIC_EPS", "基本每股收益"),
    }
    _BALANCE_FIELDS = {
        "assets_total": ("TOTAL_ASSETS", "资产总计"),
        "total_current_assets": ("TOTAL_CURRENT_ASSETS", "流动资产合计"),
        "non_current_nets_total": ("TOTAL_NON_CURRENT_ASSETS", "非流动资产合计"),
        "cash": ("CASH", "货币资金"),
        "accounts_receivable": ("ACCOUNTS_RECEIVABLE", "应收账款"),
        "total_debt": ("TOTAL_LIABILITIES", "负债合计"),
        "holder_equity_total": ("TOTAL_EQUITY", "所有者权益合计"),
    }
    _CASH_FLOW_FIELDS = {
        "act_cash_flow_net": ("OPERATING_CASH_FLOW", "经营活动现金流量净额"),
        "invest_cash_flow_net": ("INVESTING_CASH_FLOW", "投资活动现金流量净额"),
        "financing_cash_flow_net": ("FINANCING_CASH_FLOW", "筹资活动现金流量净额"),
        "pay_fixed_assets_etc_cash": ("CAPITAL_EXPENDITURE", "购建长期资产支付的现金"),
        "pay_dividends_profits_interest_cash": (
            "DIVIDENDS_INTEREST_PAID",
            "分配股利、利润或偿付利息支付的现金",
        ),
        "cash_equivalents_net_addition": ("NET_INCREASE_CASH", "现金净增加额"),
    }

    def __init__(self, api: FuyaoAsyncApiClient) -> None:
        self.api = api
        self.http = api

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return (
            capability in self.capabilities
            and not (
                capability is DataCapability.DAILY_BARS
                and symbol.is_market_pulse_index
            )
        )

    async def fetch(
        self,
        capability: DataCapability,
        symbol: StockSymbol,
        **kwargs: Any,
    ) -> Any:
        if capability is DataCapability.DAILY_BARS:
            return await self._daily_bars(symbol, int(kwargs.get("limit", 250)))
        if capability is DataCapability.FINANCIAL_STATEMENTS:
            return await self._financial_statements(symbol, **kwargs)
        raise ProviderError("UNSUPPORTED_CAPABILITY", capability.value, False)

    async def _daily_bars(self, symbol: StockSymbol, limit: int) -> list[DailyBar]:
        end = datetime.now(SHANGHAI)
        start = end - timedelta(days=3652)
        data = await self.api.get_data(
            "/api/a-share/prices/historical",
            {
                "thscode": _thscode(symbol),
                "interval": "1d",
                "start": int(start.timestamp() * 1000),
                "end": int(end.timestamp() * 1000),
                "adjust": "forward",
                "offset": 0,
            },
        )
        items = _items(data, "扶摇历史 K 线")
        bars = [self._daily_bar(symbol, item) for item in items]
        if not bars:
            raise ProviderError("EMPTY_DATA", "扶摇历史 K 线为空", True)
        return bars[-min(max(limit, 1), 5000):]

    async def _financial_statements(
        self,
        symbol: StockSymbol,
        **kwargs: Any,
    ) -> FinancialStatementsData:
        period_end = str(kwargs["period_end"])
        report_type = str(kwargs["report_type"])
        scope = str(kwargs.get("scope", "CONSOLIDATED"))
        period = "annual" if report_type == "ANNUAL" else "quarterly"
        boundary = datetime.fromisoformat(period_end).replace(tzinfo=SHANGHAI)
        params = {
            "thscode": _thscode(symbol),
            "period": period,
            "start": int(boundary.timestamp() * 1000),
            "end": int(boundary.timestamp() * 1000),
        }
        paths = (
            "/api/a-share/financials/income-statements",
            "/api/a-share/financials/balance-sheets",
            "/api/a-share/financials/cash-flow-statements",
        )
        income_data, balance_data, cash_flow_data = await asyncio.gather(
            *(self.api.get_data(path, dict(params)) for path in paths)
        )
        income = _single_item(income_data, "扶摇利润表")
        balance = _single_item(balance_data, "扶摇资产负债表")
        cash_flow = _single_item(cash_flow_data, "扶摇现金流量表")
        representative = income or balance or cash_flow
        if not representative:
            raise ProviderError("REPORT_NOT_FOUND", f"未找到报告期 {period_end} 的财务报表", False)
        return FinancialStatementsData(
            report=FinancialReportMeta(
                symbol=symbol,
                period_end=period_end,
                report_type=report_type,
                scope=scope,
                published_at=_date_time(representative.get("report_date_ms")),
                audited=report_type == "ANNUAL",
                currency=str(representative.get("currency") or "CNY"),
            ),
            statements=[
                self._statement(
                    FinancialStatementType.INCOME,
                    income,
                    self._INCOME_FIELDS,
                    "CURRENT_YTD",
                ),
                self._statement(
                    FinancialStatementType.BALANCE_SHEET,
                    balance,
                    self._BALANCE_FIELDS,
                    "CURRENT_PERIOD_END",
                ),
                self._statement(
                    FinancialStatementType.CASH_FLOW,
                    cash_flow,
                    self._CASH_FLOW_FIELDS,
                    "CURRENT_YTD",
                ),
            ],
        )

    @staticmethod
    def _daily_bar(symbol: StockSymbol, item: dict[str, Any]) -> DailyBar:
        return DailyBar(
            symbol=symbol,
            trade_date=_date_time(_required(item, "date_ms")).date().isoformat(),
            open=float(_required(item, "open_price")),
            high=float(_required(item, "high_price")),
            low=float(_required(item, "low_price")),
            close=float(_required(item, "close_price")),
            volume=float(_required(item, "volume")),
            amount=_float(item.get("turnover")),
            adjustment="QFQ",
        )

    @staticmethod
    def _statement(
        statement_type: FinancialStatementType,
        item: dict[str, Any] | None,
        mappings: dict[str, tuple[str, str]],
        period_role: str,
    ) -> FinancialStatement:
        values: list[FinancialStatementValue] = []
        source = item or {}
        for field, (concept, label) in mappings.items():
            value = _number_text(source.get(field))
            if value is None:
                continue
            values.append(
                FinancialStatementValue(
                    source_label=label,
                    concept_code=concept,
                    period_role=period_role,
                    value=value,
                    source_field=field,
                )
            )
        return FinancialStatement(statement_type=statement_type, values=values)


class FuyaoConstituentProvider:
    source_family = "FUYAO_TONGHUASHUN"

    def __init__(self, api: FuyaoSyncApiClient) -> None:
        self.api = api

    def constituents(self, sector: DiscoverySector) -> ConstituentBatch:
        suffix = sector.code if "." in sector.code else f"{sector.code}.TI"
        data = self.api.get_data(
            "/api/a-share-index/constituents/ths-stock-list",
            {"thscode": suffix},
        )
        values = tuple(
            (
                str(_required(item, "ticker")),
                str(_required(item, "thscode")).rsplit(".", 1)[-1],
                str(_required(item, "name")),
            )
            for item in _items(data, "扶摇同花顺指数成分")
        )
        expected = max(0, sector.expected_constituent_count)
        coverage = min(1.0, len(values) / expected) if expected else (1.0 if values else 0.0)
        complete = bool(values) and (not expected or coverage >= 0.95)
        warning = "" if complete else f"扶摇成分覆盖不足：{len(values)}/{expected or '未知'}"
        return ConstituentBatch(
            sector_code=sector.code,
            sector_name=sector.name,
            source_family=self.source_family,
            values=values,
            expected_count=expected or len(values),
            retrieved_count=len(values),
            quality_status="COMPLETE" if complete else "PARTIAL",
            coverage=coverage,
            retrieved_at=datetime.now(SHANGHAI).isoformat(),
            warning=warning,
            acquisition_mode="REST_API",
        )

    def close(self) -> None:
        self.api.close()


def _thscode(symbol: StockSymbol) -> str:
    return f"{symbol.code}.{symbol.market.value}"


def _items(data: dict[str, Any], label: str) -> list[dict[str, Any]]:
    values = data.get("item")
    if not isinstance(values, list):
        raise ProviderError("SCHEMA_DRIFT", f"{label}缺少 item 数组", False)
    if any(not isinstance(item, dict) for item in values):
        raise ProviderError("SCHEMA_DRIFT", f"{label}包含非对象记录", False)
    return values


def _single_item(data: dict[str, Any], label: str) -> dict[str, Any] | None:
    values = _items(data, label)
    return values[0] if values else None


def _required(item: dict[str, Any], field: str) -> Any:
    value = item.get(field)
    if value is None or value == "":
        raise ProviderError("SCHEMA_DRIFT", f"扶摇响应缺少字段 {field}", False)
    return value


def _date_time(value: Any) -> datetime:
    try:
        return datetime.fromtimestamp(float(value) / 1000, tz=SHANGHAI)
    except (TypeError, ValueError, OSError) as error:
        raise ProviderError("SCHEMA_DRIFT", "扶摇响应包含无效时间戳", False) from error


def _float(value: Any) -> float | None:
    try:
        return None if value is None else float(value)
    except (TypeError, ValueError):
        return None


def _number_text(value: Any) -> str | None:
    if value is None:
        return None
    try:
        number = Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None
    if not number.is_finite():
        return None
    text = format(number, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text

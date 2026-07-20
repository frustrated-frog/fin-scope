from __future__ import annotations

import asyncio
import importlib.util
import math
from datetime import datetime
from decimal import Decimal, InvalidOperation
from typing import Any
from zoneinfo import ZoneInfo

from finscope_market_data.models import (
    CapitalFlowData,
    CapitalFlowPoint,
    DailyBar,
    DataCapability,
    FinancialReportMeta,
    FinancialStatement,
    FinancialStatementType,
    FinancialStatementValue,
    FinancialStatementsData,
    StockProfile,
    StockSymbol,
)
from finscope_market_data.providers.base import ProviderError


class AkshareProvider:
    provider_code = "AKSHARE"
    provider_family = "EASTMONEY"
    priority = 10
    capabilities = {
        DataCapability.DAILY_BARS,
        DataCapability.CAPITAL_FLOW,
        DataCapability.PROFILE,
        DataCapability.FINANCIAL_STATEMENTS,
    }

    _META_FIELDS = {
        "SECUCODE",
        "SECURITY_CODE",
        "SECURITY_NAME_ABBR",
        "ORG_CODE",
        "ORG_TYPE",
        "REPORT_DATE",
        "REPORT_TYPE",
        "REPORT_DATE_NAME",
        "SECURITY_TYPE_CODE",
        "NOTICE_DATE",
        "UPDATE_DATE",
        "CURRENCY",
        "OPINION_TYPE",
        "OSOPINION_TYPE",
        "LISTING_STATE",
    }
    _INCOME_CONCEPTS = {
        "TOTAL_OPERATE_INCOME": ("REVENUE", "营业总收入"),
        "OPERATE_INCOME": ("OPERATING_REVENUE", "营业收入"),
        "TOTAL_OPERATE_COST": ("TOTAL_OPERATING_COST", "营业总成本"),
        "OPERATE_COST": ("OPERATING_COST", "营业成本"),
        "SALE_EXPENSE": ("SELLING_EXPENSE", "销售费用"),
        "MANAGE_EXPENSE": ("ADMIN_EXPENSE", "管理费用"),
        "RESEARCH_EXPENSE": ("RND_EXPENSE", "研发费用"),
        "FINANCE_EXPENSE": ("FINANCE_EXPENSE", "财务费用"),
        "OPERATE_PROFIT": ("OPERATING_PROFIT", "营业利润"),
        "TOTAL_PROFIT": ("TOTAL_PROFIT", "利润总额"),
        "INCOME_TAX": ("INCOME_TAX", "所得税费用"),
        "NETPROFIT": ("NET_PROFIT", "净利润"),
        "PARENT_NETPROFIT": ("NET_PROFIT_PARENT", "归母净利润"),
        "DEDUCT_PARENT_NETPROFIT": ("ADJUSTED_NET_PROFIT_PARENT", "扣非归母净利润"),
        "BASIC_EPS": ("BASIC_EPS", "基本每股收益"),
        "ASSET_IMPAIRMENT_INCOME": ("ASSET_IMPAIRMENT", "资产减值损失"),
        "CREDIT_IMPAIRMENT_INCOME": ("CREDIT_IMPAIRMENT", "信用减值损失"),
    }
    _BALANCE_CONCEPTS = {
        "MONETARYFUNDS": ("CASH", "货币资金"),
        "ACCOUNTS_RECE": ("ACCOUNTS_RECEIVABLE", "应收账款"),
        "NOTE_RECE": ("NOTES_RECEIVABLE", "应收票据"),
        "PREPAYMENT": ("PREPAYMENTS", "预付款项"),
        "INVENTORY": ("INVENTORY", "存货"),
        "CONTRACT_ASSET": ("CONTRACT_ASSETS", "合同资产"),
        "TOTAL_CURRENT_ASSETS": ("TOTAL_CURRENT_ASSETS", "流动资产合计"),
        "FIXED_ASSET": ("FIXED_ASSETS", "固定资产"),
        "CIP": ("CONSTRUCTION_IN_PROGRESS", "在建工程"),
        "INTANGIBLE_ASSET": ("INTANGIBLE_ASSETS", "无形资产"),
        "GOODWILL": ("GOODWILL", "商誉"),
        "SHORT_LOAN": ("SHORT_TERM_BORROWINGS", "短期借款"),
        "ACCOUNTS_PAYABLE": ("ACCOUNTS_PAYABLE", "应付账款"),
        "NOTE_PAYABLE": ("NOTES_PAYABLE", "应付票据"),
        "CONTRACT_LIAB": ("CONTRACT_LIABILITIES", "合同负债"),
        "TOTAL_CURRENT_LIAB": ("TOTAL_CURRENT_LIABILITIES", "流动负债合计"),
        "NONCURRENT_LIAB_1YEAR": ("CURRENT_PORTION_LONG_DEBT", "一年内到期的非流动负债"),
        "LONG_LOAN": ("LONG_TERM_BORROWINGS", "长期借款"),
        "BOND_PAYABLE": ("BONDS_PAYABLE", "应付债券"),
        "TOTAL_ASSETS": ("TOTAL_ASSETS", "资产总计"),
        "TOTAL_LIABILITIES": ("TOTAL_LIABILITIES", "负债合计"),
        "TOTAL_EQUITY": ("TOTAL_EQUITY", "所有者权益合计"),
        "TOTAL_PARENT_EQUITY": ("TOTAL_EQUITY_PARENT", "归母所有者权益"),
    }
    _CASH_FLOW_CONCEPTS = {
        "SALES_SERVICES": ("CASH_RECEIVED_FROM_CUSTOMERS", "销售商品、提供劳务收到的现金"),
        "TOTAL_OPERATE_INFLOW": ("OPERATING_CASH_INFLOW", "经营活动现金流入小计"),
        "BUY_SERVICES": ("CASH_PAID_FOR_GOODS", "购买商品、接受劳务支付的现金"),
        "PAY_STAFF_CASH": ("CASH_PAID_TO_EMPLOYEES", "支付给职工以及为职工支付的现金"),
        "PAY_ALL_TAX": ("TAXES_PAID", "支付的各项税费"),
        "NETCASH_OPERATE": ("OPERATING_CASH_FLOW", "经营活动现金流量净额"),
        "CONSTRUCT_LONG_ASSET": ("CAPITAL_EXPENDITURE", "购建长期资产支付的现金"),
        "NETCASH_INVEST": ("INVESTING_CASH_FLOW", "投资活动现金流量净额"),
        "RECEIVE_LOAN_CASH": ("BORROWINGS_RECEIVED", "取得借款收到的现金"),
        "PAY_DEBT_CASH": ("DEBT_REPAID", "偿还债务支付的现金"),
        "ASSIGN_DIVIDEND_PORFIT": ("DIVIDENDS_INTEREST_PAID", "分配股利、利润或偿付利息支付的现金"),
        "NETCASH_FINANCE": ("FINANCING_CASH_FLOW", "筹资活动现金流量净额"),
        "CCE_ADD": ("NET_INCREASE_CASH", "现金及现金等价物净增加额"),
        "END_CCE": ("ENDING_CASH_EQUIVALENTS", "期末现金及现金等价物余额"),
        "BEGIN_CCE": ("BEGINNING_CASH_EQUIVALENTS", "期初现金及现金等价物余额"),
    }

    def supports(self, capability: DataCapability, symbol: StockSymbol) -> bool:
        return capability in self.capabilities and importlib.util.find_spec("akshare") is not None

    async def fetch(self, capability: DataCapability, symbol: StockSymbol, **kwargs: Any) -> Any:
        try:
            import akshare as ak
        except ImportError as error:
            raise ProviderError("PROVIDER_DISABLED", "AkShare 未安装", False) from error
        try:
            if capability is DataCapability.DAILY_BARS:
                start_date = kwargs.get("start_date", "19900101")
                end_date = kwargs.get("end_date", "20500101")
                limit = min(max(int(kwargs.get("limit", 250)), 1), 1000)
                frame = await asyncio.to_thread(
                    ak.stock_zh_a_hist,
                    symbol=symbol.code,
                    period="daily",
                    start_date=start_date,
                    end_date=end_date,
                    adjust="qfq",
                )
                records = frame.to_dict(orient="records")
                return self.map_daily_records(records[-limit:], symbol)
            if capability is DataCapability.CAPITAL_FLOW:
                if kwargs.get("require_minute"):
                    raise ProviderError(
                        "UNSUPPORTED_GRANULARITY",
                        "AkShare provider only supplies daily capital flow",
                        False,
                    )
                frame = await asyncio.to_thread(
                    ak.stock_individual_fund_flow,
                    stock=symbol.code,
                    market=symbol.market.value.lower(),
                )
                points = self.map_flow_records(frame.to_dict(orient="records"), symbol)
                return CapitalFlowData(daily_points=points)
            if capability is DataCapability.PROFILE:
                frame = await asyncio.to_thread(ak.stock_individual_info_em, symbol=symbol.code)
                return self.map_profile_records(frame.to_dict(orient="records"), symbol)
            if capability is DataCapability.FINANCIAL_STATEMENTS:
                market_symbol = f"{symbol.market.value}{symbol.code}"
                income, balance, cash_flow = await asyncio.gather(
                    asyncio.to_thread(ak.stock_profit_sheet_by_report_em, symbol=market_symbol),
                    asyncio.to_thread(ak.stock_balance_sheet_by_report_em, symbol=market_symbol),
                    asyncio.to_thread(ak.stock_cash_flow_sheet_by_report_em, symbol=market_symbol),
                )
                return self.map_financial_records(
                    symbol=symbol,
                    period_end=str(kwargs["period_end"]),
                    report_type=str(kwargs["report_type"]),
                    scope=str(kwargs.get("scope", "CONSOLIDATED")),
                    income_records=income.to_dict(orient="records"),
                    balance_records=balance.to_dict(orient="records"),
                    cash_flow_records=cash_flow.to_dict(orient="records"),
                )
        except ProviderError:
            raise
        except Exception as error:
            raise ProviderError("AKSHARE_ERROR", f"AkShare 获取失败：{error}") from error
        raise ProviderError("UNSUPPORTED_CAPABILITY", capability.value, False)

    @staticmethod
    def map_daily_records(records: list[dict[str, Any]], symbol: StockSymbol) -> list[DailyBar]:
        return [
            DailyBar(
                symbol=symbol,
                trade_date=str(record["日期"]),
                open=_value(record.get("开盘")) or 0,
                close=_value(record.get("收盘")) or 0,
                high=_value(record.get("最高")) or 0,
                low=_value(record.get("最低")) or 0,
                volume=_value(record.get("成交量")) or 0,
                amount=_value(record.get("成交额")),
                amplitude=_value(record.get("振幅")),
                change_pct=_value(record.get("涨跌幅")),
                change=_value(record.get("涨跌额")),
                turnover_rate=_value(record.get("换手率")),
                adjustment="QFQ",
            )
            for record in records
        ]

    @staticmethod
    def map_flow_records(records: list[dict[str, Any]], symbol: StockSymbol) -> list[CapitalFlowPoint]:
        result: list[CapitalFlowPoint] = []
        for record in records:
            observed = datetime.strptime(str(record["日期"]), "%Y-%m-%d").replace(
                hour=15,
                tzinfo=ZoneInfo("Asia/Shanghai"),
            )
            result.append(
                CapitalFlowPoint(
                    symbol=symbol,
                    granularity="DAY_1",
                    observed_at=observed,
                    price=_value(record.get("收盘价")),
                    change_pct=_value(record.get("涨跌幅")),
                    main_net_inflow=_value(record.get("主力净流入-净额")),
                    main_net_inflow_ratio=_value(record.get("主力净流入-净占比")),
                    super_large_net_inflow=_value(record.get("超大单净流入-净额")),
                    super_large_net_inflow_ratio=_value(record.get("超大单净流入-净占比")),
                    large_net_inflow=_value(record.get("大单净流入-净额")),
                    large_net_inflow_ratio=_value(record.get("大单净流入-净占比")),
                    medium_net_inflow=_value(record.get("中单净流入-净额")),
                    medium_net_inflow_ratio=_value(record.get("中单净流入-净占比")),
                    small_net_inflow=_value(record.get("小单净流入-净额")),
                    small_net_inflow_ratio=_value(record.get("小单净流入-净占比")),
                )
            )
        return result

    @staticmethod
    def map_profile_records(records: list[dict[str, Any]], symbol: StockSymbol) -> StockProfile:
        fields = {str(item.get("item")): item.get("value") for item in records}
        return StockProfile(
            symbol=symbol,
            name=_string(fields.get("股票简称")),
            industry=_string(fields.get("行业")),
            listing_date=_string(fields.get("上市时间")),
            total_shares=_value(fields.get("总股本")),
            circulating_shares=_value(fields.get("流通股")),
            fields={key: value for key, value in fields.items() if value is None or isinstance(value, (str, int, float))},
        )

    @classmethod
    def map_financial_records(
        cls,
        *,
        symbol: StockSymbol,
        period_end: str,
        report_type: str,
        scope: str,
        income_records: list[dict[str, Any]],
        balance_records: list[dict[str, Any]],
        cash_flow_records: list[dict[str, Any]],
    ) -> FinancialStatementsData:
        income = cls._report_record(income_records, period_end)
        balance = cls._report_record(balance_records, period_end)
        cash_flow = cls._report_record(cash_flow_records, period_end)
        representative = income or balance or cash_flow
        if representative is None:
            raise ProviderError("REPORT_NOT_FOUND", f"未找到报告期 {period_end} 的财务报表", False)
        published_at = cls._date_time(representative.get("NOTICE_DATE"))
        currency = str(representative.get("CURRENCY") or "CNY")
        return FinancialStatementsData(
            report=FinancialReportMeta(
                symbol=symbol,
                period_end=period_end,
                report_type=report_type,
                scope=scope,
                published_at=published_at,
                audited=report_type == "ANNUAL",
                currency=currency,
            ),
            statements=[
                cls._statement(
                    FinancialStatementType.INCOME,
                    income,
                    cls._INCOME_CONCEPTS,
                    "CURRENT_YTD",
                ),
                cls._statement(
                    FinancialStatementType.BALANCE_SHEET,
                    balance,
                    cls._BALANCE_CONCEPTS,
                    "CURRENT_PERIOD_END",
                ),
                cls._statement(
                    FinancialStatementType.CASH_FLOW,
                    cash_flow,
                    cls._CASH_FLOW_CONCEPTS,
                    "CURRENT_YTD",
                ),
            ],
        )

    @staticmethod
    def _report_record(
        records: list[dict[str, Any]], period_end: str
    ) -> dict[str, Any] | None:
        for record in records:
            if str(record.get("REPORT_DATE", ""))[:10] == period_end:
                return record
        return None

    @classmethod
    def _statement(
        cls,
        statement_type: FinancialStatementType,
        record: dict[str, Any] | None,
        concepts: dict[str, tuple[str, str]],
        period_role: str,
    ) -> FinancialStatement:
        if record is None:
            return FinancialStatement(statement_type=statement_type, values=[])
        values: list[FinancialStatementValue] = []
        preferred = [field for field in concepts if field in record]
        remaining = sorted(
            field
            for field in record
            if field not in cls._META_FIELDS
            and field not in concepts
            and not field.endswith("_YOY")
        )
        for field in preferred + remaining:
            value = _decimal_text(record.get(field))
            if value is None:
                continue
            concept = concepts.get(field)
            values.append(
                FinancialStatementValue(
                    source_label=concept[1] if concept else field,
                    concept_code=concept[0] if concept else None,
                    period_role=period_role,
                    value=value,
                    source_field=field,
                )
            )
        return FinancialStatement(statement_type=statement_type, values=values)

    @staticmethod
    def _date_time(value: object) -> datetime | None:
        if value is None:
            return None
        text = str(value)[:19]
        try:
            return datetime.strptime(text, "%Y-%m-%d %H:%M:%S").replace(
                tzinfo=ZoneInfo("Asia/Shanghai")
            )
        except ValueError:
            return None


def _value(value: object) -> float | None:
    try:
        result = float(value)  # type: ignore[arg-type]
        return None if math.isnan(result) else result
    except (TypeError, ValueError):
        return None


def _string(value: object) -> str | None:
    return None if value is None else str(value)


def _decimal_text(value: object) -> str | None:
    if value is None:
        return None
    try:
        decimal = Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None
    if not decimal.is_finite():
        return None
    text = format(decimal, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text

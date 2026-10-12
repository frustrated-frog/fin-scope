"""Independent structured statements; preserve raw labels, units and report scope."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

from finscope_market_data.models import (DataCapability, FinancialReportMeta, FinancialStatement,
    FinancialStatementsData, FinancialStatementType, FinancialStatementValue)
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient

CONCEPTS = {
    '营业收入':'REVENUE','营业总收入':'TOTAL_REVENUE','营业成本':'OPERATING_COST',
    '营业总成本':'TOTAL_OPERATING_COST','营业利润':'OPERATING_PROFIT','利润总额':'TOTAL_PROFIT',
    '净利润':'NET_PROFIT','归属于母公司所有者的净利润':'NET_PROFIT_PARENT',
    '归属于母公司股东的净利润':'NET_PROFIT_PARENT','研发费用':'RND_EXPENSE',
    '销售费用':'SELLING_EXPENSE','管理费用':'ADMIN_EXPENSE','所得税费用':'INCOME_TAX',
    '基本每股收益':'BASIC_EPS','资产总计':'TOTAL_ASSETS','负债合计':'TOTAL_LIABILITIES',
    '所有者权益(或股东权益)合计':'TOTAL_EQUITY','所有者权益合计':'TOTAL_EQUITY',
    '流动资产合计':'TOTAL_CURRENT_ASSETS','货币资金':'CASH','应收账款':'ACCOUNTS_RECEIVABLE',
    '经营活动产生的现金流量净额':'OPERATING_CASH_FLOW','投资活动产生的现金流量净额':'INVESTING_CASH_FLOW',
    '筹资活动产生的现金流量净额':'FINANCING_CASH_FLOW',
    '购建固定资产、无形资产和其他长期资产支付的现金':'CAPITAL_EXPENDITURE',
    '现金及现金等价物净增加额':'NET_INCREASE_CASH',
}
REPORT_ENDS = {'Q1':(3,31),'HALF_YEAR':(6,30),'Q3':(9,30),'ANNUAL':(12,31)}


class SinaFinancialStatementsProvider:
    provider_code = 'SINA_FINANCIAL_STATEMENTS'
    provider_family = 'SINA'
    priority = 20
    capabilities = {DataCapability.FINANCIAL_STATEMENTS}
    max_retries = 0
    endpoint = 'https://quotes.sina.cn/cn/api/openapi.php/CompanyFinanceService.getFinanceReport2022'

    def __init__(self,http=None):
        self.http = http or ProviderHttpClient(minimum_interval_seconds=0.2)

    def supports(self,capability,symbol):
        return capability is DataCapability.FINANCIAL_STATEMENTS and symbol.market.value in {'SH','SZ','BJ'} and not symbol.is_market_pulse_index

    async def fetch(self,capability,symbol,**kwargs):
        requested = date.fromisoformat(kwargs['period_end'])
        report_type = kwargs['report_type']
        scope = kwargs.get('scope','CONSOLIDATED')
        if report_type not in REPORT_ENDS or (requested.month,requested.day) != REPORT_ENDS[report_type] or scope != 'CONSOLIDATED':
            raise ProviderError('UNSUPPORTED_REPORT', '报告期、类型或合并口径不匹配',False)
        types = [('lrb',FinancialStatementType.INCOME),('fzb',FinancialStatementType.BALANCE_SHEET),('llb',FinancialStatementType.CASH_FLOW)]
        statements, metadata, warnings = [],[],[]
        for source,statement_type in types:
            selected = None
            # Up to 128 periods, bounded pagination enables prior-year comparisons.
            for page in range(1,5):
                try:
                    payload = await self.http.get_json(self.provider_code,self.endpoint,
                        params={'paperCode':symbol.prefixed_code,'source':source,'type':'0','page':page,'num':32})
                except ProviderError as error:
                    warnings.append(f'{statement_type.value} 来源暂不可用：{error.error_type}')
                    break
                try:
                    result = payload['result']
                    if result['status']['code'] != 0:
                        raise ValueError('business status')
                    reports = result['data']['report_list']
                    if not isinstance(reports,dict):
                        raise ValueError('report list')
                    selected = reports.get(requested.strftime('%Y%m%d'))
                    if selected is not None or len(reports) < 32:
                        break
                except (KeyError,TypeError,ValueError) as error:
                    raise ProviderError('SCHEMA_DRIFT','新浪财报结构变化',False) from error
            if selected is None:
                warnings.append(f'{statement_type.value} 报告期 {requested} 暂缺')
                continue
            if (not isinstance(selected, dict) or selected.get('rType') != '合并期末'
                    or selected.get('rCurrency') != 'CNY'):
                warnings.append(f'{statement_type.value} 口径、币种或结构不匹配，已保留其他有效报表')
                continue
            try:
                values = self.parse_values(selected,statement_type)
            except ProviderError as error:
                warnings.append(f'{statement_type.value} 科目暂不可用：{error.error_type}')
                continue
            if not values:
                warnings.append(f'{statement_type.value} 尚无有效数值')
                continue
            statements.append(FinancialStatement(statement_type=statement_type,values=values))
            metadata.append(selected)
        if not statements:
            raise ProviderError('REPORT_NOT_FOUND',f'新浪未找到报告期 {requested} 的报表',False)
        published = []
        for item in metadata:
            stamp = item.get('publish_date')
            if stamp:
                try:
                    published.append(datetime.strptime(stamp,'%Y%m%d').replace(tzinfo=ZoneInfo('Asia/Shanghai')))
                except (ValueError,TypeError):
                    warnings.append('部分报表披露日期缺失或无效')
        return FinancialStatementsData(report=FinancialReportMeta(symbol=symbol,period_end=str(requested),
            report_type=report_type,scope=scope,currency='CNY',published_at=max(published) if published else None,
            audited=all(item.get('is_audit') == '已审计' for item in metadata)),statements=statements,warnings=warnings)

    @staticmethod
    def parse_values(report,statement_type):
        rows = report.get('data')
        if not isinstance(rows,list):
            raise ProviderError('SCHEMA_DRIFT','新浪财报科目列表缺失',False)
        result = []
        for row in rows:
            if not isinstance(row, dict):
                raise ProviderError('SCHEMA_DRIFT', '新浪财报科目结构无效', False)
            label = row.get('item_title')
            raw = row.get('item_value')
            if not label or raw in (None,'','--','-'):
                continue
            try:
                value = Decimal(str(raw).replace(',',''))
                if not value.is_finite():
                    raise InvalidOperation()
            except InvalidOperation as error:
                raise ProviderError('INVALID_FINANCIAL_VALUE','新浪财报包含非法数值',False) from error
            result.append(FinancialStatementValue(source_label=label,concept_code=CONCEPTS.get(label),
                source_field=row.get('item_field'),period_role=('CURRENT_PERIOD_END'
                    if statement_type is FinancialStatementType.BALANCE_SHEET else 'CURRENT_YTD'),
                value=str(value),unit_multiplier='1'))
        return result

    def warnings_for(self,capability,symbol,data):
        return list(data.warnings)

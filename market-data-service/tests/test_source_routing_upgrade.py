import asyncio
from datetime import date, datetime, UTC
import json
import sqlite3

import httpx
import pytest

from finscope_market_data.health import ProviderHealthRegistry
from finscope_market_data.models import (DataCapability, DataEnvelope, FinancialReportMeta,
    FinancialStatementsData, FinancialStatement, FinancialStatementType, FinancialStatementValue,
    QualityStatus, StockSymbol, StockQuote)
from finscope_market_data.providers.http import ProviderHttpClient
from finscope_market_data.providers.sina_financials import SinaFinancialStatementsProvider
from finscope_market_data.providers.tencent_minutes import TencentMinuteBarProvider
from finscope_market_data.providers.tencent_market import TencentMarketSnapshotService
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.router import ProviderRouter
from finscope_market_data.snapshot_store import SnapshotStore

SYMBOL = StockSymbol(market='SH',code='600519')


@pytest.mark.asyncio
async def test_slow_source_is_cancelled_and_fast_independent_source_still_returns(tmp_path):
    cancelled = asyncio.Event()
    class Source:
        provider_code='SLOW'; provider_family='SLOW'; priority=1; capabilities={DataCapability.QUOTE}
        timeout_seconds=0.02
        def supports(self,*args): return True
        async def fetch(self,*args,**kwargs):
            try: await asyncio.Event().wait()
            finally: cancelled.set()
    class Backup(Source):
        provider_code='FAST'; provider_family='FAST'; priority=2
        async def fetch(self,*args,**kwargs):
            return StockQuote(symbol=SYMBOL,price=10,observed_at=datetime(2026,10,12,7,tzinfo=UTC))
    router=ProviderRouter([Source(),Backup()],SnapshotStore(tmp_path/'a.db'),ProviderHealthRegistry(),
                          max_retries=0,request_budget_seconds=0.2)
    result=await router.fetch(DataCapability.QUOTE,SYMBOL)
    assert cancelled.is_set()
    assert result.source_code == 'FAST'
    assert result.quality_status == 'FRESH_FALLBACK'
    assert result.attempts[0].error_type == 'TIMEOUT'


@pytest.mark.asyncio
async def test_persistence_failure_keeps_online_result(tmp_path):
    class Source:
        provider_code='GOOD';provider_family='GOOD';priority=1
        def supports(self,*args): return True
        async def fetch(self,*args,**kwargs):
            return StockQuote(symbol=SYMBOL,price=10,observed_at=datetime.now(UTC))
    store=SnapshotStore(tmp_path/'a.db')
    def fail(*args): raise sqlite3.OperationalError('locked')
    store.save=fail
    result=await ProviderRouter([Source()],store,ProviderHealthRegistry()).fetch(DataCapability.QUOTE,SYMBOL)
    assert result.data.price == 10
    assert len(result.attempts) == 1
    assert '保存失败' in result.warnings[0]


@pytest.mark.asyncio
async def test_scoped_reports_never_return_another_period_on_failure(tmp_path):
    class Source:
        provider_code='REPORT';provider_family='REPORT';priority=1;fails=False
        def supports(self,*args): return True
        async def fetch(self,capability,symbol,**kwargs):
            if self.fails: raise ProviderError('OFFLINE','offline',False)
            return FinancialStatementsData(report=FinancialReportMeta(symbol=symbol,
                period_end=kwargs['period_end'],report_type='HALF_YEAR'),statements=[FinancialStatement(
                    statement_type=FinancialStatementType.INCOME,values=[FinancialStatementValue(
                        source_label='营业收入',period_role='CURRENT_YTD',value='100')])])
    source=Source()
    router=ProviderRouter([source],SnapshotStore(tmp_path/'a.db'),ProviderHealthRegistry(),max_retries=0)
    first=await router.fetch(DataCapability.FINANCIAL_STATEMENTS,SYMBOL,period_end='2026-06-30',report_type='HALF_YEAR')
    assert first.quality_status == 'PARTIAL_FRESH'
    source.fails=True
    missing=await router.fetch(DataCapability.FINANCIAL_STATEMENTS,SYMBOL,period_end='2025-06-30',report_type='HALF_YEAR')
    exact=await router.fetch(DataCapability.FINANCIAL_STATEMENTS,SYMBOL,period_end='2026-06-30',report_type='HALF_YEAR')
    assert missing.data is None
    assert exact.data.report.period_end == '2026-06-30'
    assert exact.quality_status == 'STALE_FALLBACK'


@pytest.mark.asyncio
async def test_sina_financials_preserve_available_statements_and_decimal_units():
    def respond(request):
        if request.url.params['source'] == 'llb':
            return httpx.Response(503)
        return httpx.Response(200,json={'result':{'status':{'code':0},'data':{'report_list':{
            '20260630':{'rType':'合并期末','rCurrency':'CNY','publish_date':'20260815','is_audit':'未审计',
                'data':[{'item_title':'营业收入','item_field':'BIZINCO','item_value':'92278072083.210000'}]}
        }}}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        provider=SinaFinancialStatementsProvider(ProviderHttpClient(client=client))
        result=await provider.fetch(DataCapability.FINANCIAL_STATEMENTS,SYMBOL,period_end='2026-06-30',report_type='HALF_YEAR')
    assert len(result.statements) == 2
    assert result.statements[0].values[0].value == '92278072083.210000'
    assert result.statements[0].values[0].unit_multiplier == '1'
    assert result.report.published_at.date() == date(2026,8,15)
    assert any('CASH_FLOW' in warning for warning in result.warnings)


def test_minute_amount_is_absent_and_source_times_are_preserved():
    bars=TencentMinuteBarProvider.parse([
        ['202610121430','10','11','12','9','123',{},'1.51'],
    ],SYMBOL,5)
    assert bars[0].volume == 12300
    assert bars[0].amount is None
    assert bars[0].observed_at.hour == 14
    assert bars[0].frequency_minutes == 5


def test_official_directory_prefers_new_bse_code_for_same_company():
    rows=TencentMarketSnapshotService.parse_directory({'stockList':[
        {'code':'830799','category':'A股','orgId':'same'},
        {'code':'920799','category':'A股','orgId':'same'},
        {'code':'900001','category':'B股','orgId':'b'},
    ]})
    assert [symbol.code for symbol in rows] == ['920799']


@pytest.mark.asyncio
async def test_same_capability_capacity_is_bounded(tmp_path):
    class Source:
        provider_code='S';provider_family='S';priority=1
        active=0;maximum=0
        def supports(self,*args): return True
        async def fetch(self,capability,symbol,**kwargs):
            self.active+=1;self.maximum=max(self.maximum,self.active)
            await asyncio.sleep(0.01)
            self.active-=1
            return StockQuote(symbol=symbol,price=10,observed_at=datetime.now(UTC))
    source=Source()
    router=ProviderRouter([source],SnapshotStore(tmp_path/'a.db'),ProviderHealthRegistry(),max_retries=0)
    await asyncio.gather(*[router.fetch(DataCapability.QUOTE,StockSymbol(market='SH',code=f'60000{n}')) for n in range(6)])
    assert source.maximum == 2


@pytest.mark.asyncio
async def test_simultaneous_quotes_share_one_request_but_not_mutable_response(tmp_path):
    class Source:
        provider_code='S';provider_family='S';priority=1;calls=0
        def supports(self,*args): return True
        async def fetch(self,capability,symbol,**kwargs):
            self.calls+=1
            await asyncio.sleep(0.01)
            return StockQuote(symbol=symbol,price=10,observed_at=datetime.now(UTC))
    source=Source()
    router=ProviderRouter([source],SnapshotStore(tmp_path/'a.db'),ProviderHealthRegistry(),max_retries=0)
    first,second=await asyncio.gather(router.fetch(DataCapability.QUOTE,SYMBOL),router.fetch(DataCapability.QUOTE,SYMBOL))
    assert source.calls == 1
    first.warnings.append('caller mutation')
    assert 'caller mutation' not in second.warnings


@pytest.mark.asyncio
async def test_hanging_primary_does_not_consume_backup_time_budget(tmp_path):
    class Source:
        provider_code='S';provider_family='S';priority=1
        def supports(self,*args): return True
        async def fetch(self,*args,**kwargs): await asyncio.Event().wait()
    class Backup(Source):
        provider_code='B';provider_family='B';priority=2
        async def fetch(self,capability,symbol,**kwargs):
            await asyncio.sleep(0.01)
            return StockQuote(symbol=symbol,price=10,observed_at=datetime.now(UTC))
    router=ProviderRouter([Source(),Backup()],SnapshotStore(tmp_path/'a.db'),ProviderHealthRegistry(),
                          request_budget_seconds=0.08,provider_timeout_seconds=2,max_retries=0)
    result=await router.fetch(DataCapability.QUOTE,SYMBOL)
    assert result.source_code == 'B'


def test_partial_daily_cache_expires_at_close_and_asof_is_not_future(tmp_path):
    from finscope_market_data.models import DailyBar
    from zoneinfo import ZoneInfo
    now=datetime(2026,10,12,10,30,tzinfo=ZoneInfo('Asia/Shanghai'))
    symbol=StockSymbol(market='SH',code='000300')
    bars=[DailyBar(symbol=symbol,trade_date='2026-10-12',open=10,high=11,low=9,close=10,
                   volume=1,is_complete=False)]
    store=SnapshotStore(tmp_path/'a.db')
    store.save(DataEnvelope(capability=DataCapability.DAILY_BARS,symbol=symbol,
        quality_status=QualityStatus.PARTIAL_FRESH,source_family='TENCENT',retrieved_at=now,
        coverage_limit=250,data=bars))
    router=ProviderRouter([],store,ProviderHealthRegistry(),now_provider=lambda:now)
    assert router._fresh_daily_bar_snapshot(symbol,1) is not None
    assert router._as_of(bars,now) == now
    router._now_provider=lambda:datetime(2026,10,12,15,20,tzinfo=ZoneInfo('Asia/Shanghai'))
    assert router._fresh_daily_bar_snapshot(symbol,1) is None

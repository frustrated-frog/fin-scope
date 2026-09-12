"""Bounded, idempotent readiness checks for explicitly requested theme members."""
from __future__ import annotations

import asyncio
from datetime import date, datetime
from time import monotonic
from typing import Callable, Literal
from zoneinfo import ZoneInfo

from pydantic import Field, ValidationError

from finscope_market_data.daily_research import (
    ResearchModel, _a_stock, _finite, _previous_session, is_closed_research_date,
)
from finscope_market_data.models import DataCapability, QualityStatus, StockSymbol
from finscope_market_data.router import ProviderRouter


class ResearchMemberResult(ResearchModel):
    schema_version: Literal['research-member-v1'] = 'research-member-v1'
    business_date: date
    instrument_code: str
    status: Literal['READY', 'PARTIAL', 'FAILED', 'SKIPPED']
    reason: Literal['COMPLETE', 'NO_DATA', 'DATE_MISSING', 'HISTORY_GAP', 'ADJUSTMENT_REQUIRED', 'NOT_CLOSED', 'UPSTREAM_FAILED']
    message: str
    valid_bars: int = Field(default=0, ge=0, le=22)
    required_bars: Literal[22] = 22
    source_code: str | None = None


MESSAGES = {
    'COMPLETE': '连续22个交易日前复权日线已齐备。',
    'NO_DATA': '本地尚无可用日线。',
    'DATE_MISSING': '请求交易日缺少有效日线。',
    'HISTORY_GAP': '连续交易日历史不足或存在缺口。',
    'ADJUSTMENT_REQUIRED': '日线尚未满足前复权口径。',
    'NOT_CLOSED': '日期尚未收盘、非交易日或不在已维护日历内。',
    'UPSTREAM_FAILED': '行情补齐暂时失败，请稍后重试。',
}


class ResearchMemberService:
    def __init__(self, router: ProviderRouter, now: Callable[[], datetime] | None = None,
                 timeout_seconds: float = 60, cooldown_seconds: float = 60):
        self.router = router
        self.now = now or (lambda: datetime.now(ZoneInfo('Asia/Shanghai')))
        self.timeout_seconds = timeout_seconds
        self.cooldown_seconds = cooldown_seconds
        self._semaphore = asyncio.Semaphore(2)
        self._inflight: dict[tuple[date, str], asyncio.Task] = {}
        self._cooldowns: dict[tuple[date, str], tuple[float, ResearchMemberResult]] = {}

    async def ensure(self, business_date: date, instrument_code: str) -> ResearchMemberResult:
        code, separator, market = instrument_code.partition('.')
        if separator != '.' or not code.isascii() or not _a_stock(f'{market}:{code}'):
            raise ValueError('证券代码必须为带市场后缀的A股代码')
        symbol = StockSymbol(market=market, code=code)
        if not is_closed_research_date(business_date, self.now()):
            return self._result(business_date, instrument_code, 'SKIPPED', 'NOT_CLOSED')
        local = self._local(business_date, instrument_code, symbol)
        if local.status == 'READY':
            return local
        key = (business_date, instrument_code)
        self._cooldowns = {key: value for key, value in self._cooldowns.items() if value[0] > monotonic()}
        cooldown = self._cooldowns.get(key)
        if cooldown is not None:
            return cooldown[1].model_copy(deep=True)
        task = self._inflight.get(key)
        if task is None:
            task = asyncio.create_task(self._run(business_date, instrument_code, symbol))
            self._inflight[key] = task
            task.add_done_callback(lambda done: self._inflight.pop(key, None))
        return (await asyncio.shield(task)).model_copy(deep=True)

    async def _run(self, business_date: date, instrument: str, symbol: StockSymbol) -> ResearchMemberResult:
        local = self._local(business_date, instrument, symbol)
        try:
            # The deadline includes waiting for a concurrency slot and provider locks.
            result = await asyncio.wait_for(
                self._fetch(business_date, instrument, symbol), timeout=self.timeout_seconds,
            )
        except Exception:
            result = local.model_copy(update={'status': 'FAILED', 'reason': 'UPSTREAM_FAILED',
                                               'message': MESSAGES['UPSTREAM_FAILED']})
        if result.status != 'READY':
            # Keep memory finite as well as time-bounded for a long-running service.
            if len(self._cooldowns) >= 1000:
                self._cooldowns.pop(next(iter(self._cooldowns)))
            self._cooldowns[(business_date, instrument)] = (monotonic() + self.cooldown_seconds, result)
        return result

    async def _fetch(self, business_date: date, instrument: str, symbol: StockSymbol) -> ResearchMemberResult:
        async with self._semaphore:
            local = self._local(business_date, instrument, symbol)
            if local.status == 'READY':
                return local
            history_count = self.router.snapshots.daily_bar_count(symbol)
            limit = min(5000, max(250, history_count))
            envelope = await self.router.fetch(
                DataCapability.DAILY_BARS, symbol, limit=limit, force_refresh=True,
                minimum_history_count=history_count,
            )
            result = self._local(business_date, instrument, symbol)
            if result.status != 'READY' and envelope.quality_status in (
                QualityStatus.UNAVAILABLE, QualityStatus.STALE_FALLBACK,
            ):
                result = result.model_copy(update={'status': 'FAILED', 'reason': 'UPSTREAM_FAILED',
                                                   'message': MESSAGES['UPSTREAM_FAILED']})
            return result

    def _local(self, business_date: date, instrument: str, symbol: StockSymbol) -> ResearchMemberResult:
        try:
            stored = self.router.snapshots.load(DataCapability.DAILY_BARS, symbol)
        except ValidationError:
            return self._result(business_date, instrument, 'PARTIAL', 'HISTORY_GAP')
        if stored is None or not stored.data:
            return self._result(business_date, instrument, 'PARTIAL', 'NO_DATA')
        bars = {}
        duplicates = set()
        for bar in stored.data:
            if bar.symbol != symbol:
                continue
            try:
                day = date.fromisoformat(bar.trade_date)
            except ValueError:
                continue
            if day <= business_date:
                if day in bars:
                    duplicates.add(day)
                bars[day] = bar
        for day in duplicates:
            bars.pop(day, None)
        day = business_date
        valid = 0
        reason = 'COMPLETE'
        for _ in range(22):
            bar = bars.get(day)
            if (bar is None or not all(_finite(value) for value in (
                    bar.open, bar.high, bar.low, bar.close, bar.volume,
            )) or bar.close <= 0):
                reason = 'DATE_MISSING' if valid == 0 else 'HISTORY_GAP'
                break
            if bar.adjustment != 'QFQ':
                reason = 'ADJUSTMENT_REQUIRED'
                break
            valid += 1
            day = _previous_session(day)
        return self._result(business_date, instrument, 'READY' if valid == 22 else 'PARTIAL',
                            reason, valid, stored.source_code)

    @staticmethod
    def _result(day: date, instrument: str, status: str, reason: str,
                valid: int = 0, source: str | None = None) -> ResearchMemberResult:
        return ResearchMemberResult(business_date=day, instrument_code=instrument, status=status,
                                    reason=reason, message=MESSAGES[reason], valid_bars=valid, source_code=source)

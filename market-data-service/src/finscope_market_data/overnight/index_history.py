"""Automatic completed index history, separate from immutable intraday snapshots."""
import asyncio
from datetime import date
import hashlib
import json
import logging

import numpy as np

from finscope_market_data.forecast.trading_calendar import previous_session
from finscope_market_data.models import DataCapability, StockSymbol
from finscope_market_data.overnight.automation_models import AutomationContext
from finscope_market_data.overnight.context_features import INDEX_CODES

logger = logging.getLogger(__name__)
KEY = 'overnightIndexHistory'
FEATURES = tuple(f'{code}_{name}' for code in INDEX_CODES for name in ('PREVIOUS_RETURN', 'RETURN_5D', 'VOLATILITY_20D'))


def index_features(history, signal_day):
    cursor, days = date.fromisoformat(str(signal_day)), []
    for _ in range(21):
        cursor = previous_session(cursor)
        if cursor is None:
            return None
        days.append(str(cursor))
    result = []
    for code in INDEX_CODES:
        prices = (history.get(code) or {}).get('closes', {})
        if any(day not in prices for day in days):
            return None
        values = np.asarray([prices[day] for day in days], dtype=float)
        if not np.all(np.isfinite(values)) or np.any(values <= 0):
            return None
        returns = values[:-1] / values[1:] - 1
        result.extend([float(returns[0]), float(values[0] / values[5] - 1), float(returns.std())])
    return result


def paired_index_rows(rows, history):
    by_day = {day: index_features(history, day) for day in {r['signalDate'] for r in rows}}
    base = [r for r in rows if by_day[r['signalDate']] is not None]
    enhanced = [{**r, 'features': r['features'] + by_day[r['signalDate']]} for r in base]
    return base, enhanced


def history_fingerprint(history):
    return hashlib.sha256(json.dumps(history, sort_keys=True, allow_nan=False).encode()).hexdigest()


def history_revision(history):
    # Partial collection must not trigger three expensive retraining passes.
    if any(not (history.get(code) or {}).get('throughDate') for code in INDEX_CODES):
        return 'PENDING'
    return min(history[code]['throughDate'] for code in INDEX_CODES)


class OvernightIndexHistory:
    def __init__(self, automation, router):
        self.automation, self.router = automation, router

    async def tick(self):
        now, meta = self.automation.clock(), self.automation.store
        settings = meta.get('context') or AutomationContext().model_dump(mode='json', by_alias=True)
        if not settings['enabled'] or '13:00' <= now.strftime('%H:%M') < '18:00':
            return
        end = previous_session(now.date())
        if end is None:
            return
        history = meta.get(KEY) or {}
        for code in INDEX_CODES:
            previous = history.get(code) or {}
            if previous.get('throughDate', '') >= str(end):
                continue
            job = meta.claim(f'{now.date()}|INDEX_HISTORY|{code}', now,
                {'phase': 'INDEX_HISTORY', 'signalDate': str(now.date()), 'instrumentCode': code})
            if not job:
                continue
            try:
                envelope = await asyncio.wait_for(self.router.fetch(DataCapability.DAILY_BARS,
                    StockSymbol(code=code[:6], market=code[-2:]), provider_family='TENCENT',
                    limit=500, include_current=False), timeout=45)
                if envelope.source_code != 'TENCENT_DAILY_RATIO_QFQ' or 'STALE' in str(envelope.quality_status):
                    raise ValueError('指数历史来源或新鲜度不符合要求')
                closes = {}
                for bar in envelope.data:
                    if bar.symbol.code != code[:6] or bar.symbol.market.value != code[-2:]:
                        raise ValueError('指数历史证券不一致')
                    if bar.trade_date > str(end) or not bar.is_complete:
                        continue
                    if not np.isfinite(bar.close) or bar.close <= 0:
                        raise ValueError('指数收盘价格无效')
                    if bar.trade_date in closes and closes[bar.trade_date] != bar.close:
                        raise ValueError('指数历史日期冲突')
                    closes[bar.trade_date] = bar.close
                if len(closes) < 21 or max(closes) < str(end):
                    raise ValueError('指数历史缺少最新完整交易日或窗口不足')
                history[code] = {'closes': closes, 'receivedAt': self.automation.clock().isoformat(),
                    'throughDate': max(closes), 'source': envelope.source_code,
                    'scope': 'COMPLETED_INDEX_DAILY_HISTORY'}
                meta.put(KEY, history)
                meta.finish(job, self.automation.clock(), status='COMPLETED', throughDate=max(closes), rowCount=len(closes))
            except Exception as error:
                logger.warning('Index history collection failed for %s: %s', code, type(error).__name__)
                meta.finish(job, self.automation.clock(), status='FAILED', reason='指数历史暂不可用；保留上次成功数据并有限重试')
            return

    async def run(self, stop):
        while not stop.is_set():
            try:
                await self.tick()
            except Exception:
                logger.exception('Index history worker tick failed')
            try:
                await asyncio.wait_for(stop.wait(), timeout=20)
            except TimeoutError:
                pass

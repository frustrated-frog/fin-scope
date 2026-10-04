from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from finscope_market_data.overnight.engine import predict, settle


class OvernightService:
    def __init__(self, store, provider, clock=None):
        self.store = store
        self.provider = provider
        self.clock = clock or (lambda: datetime.now(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None))

    def generate(self, request, freeze_all=False, settle_cached=True):
        now = self.clock()
        cutoff = datetime.fromisoformat(f'{request.signal_date}T{request.cutoff}:00')
        warnings = []
        if now >= cutoff:
            try:
                fetched = self.provider.fetch(request.instrument_code, now)
                self.store.save_bars(request.instrument_code, fetched)
            except Exception as error:
                warnings.append(f'分钟源不可用：{type(error).__name__}；仅使用已有分钟缓存')
        bars = self.store.bars(request.instrument_code, min(cutoff, now))
        report = predict(request, bars, now)
        completed = self.clock()
        report['generatedAt'] = completed.isoformat()
        if request.mode == 'TAIL_ENTRY' and completed >= cutoff + timedelta(minutes=5):
            report['evidenceKind'] = 'RETROSPECTIVE'
        report['sourceCode'] = self.provider.source
        history = self.store.history_import(request.instrument_code)
        if history:
            report['sourceCode'] += '+' + history['sourceCode']
            report['warnings'].append('包含事后获取的未复权分钟历史；历史重放不等于当时已留档的真实预测。')
            if history.get('minuteDifferenceCount', 0):
                report['warnings'].append('来源的分钟聚合存在差异；已核对开收盘价格和完整日成交额，按日期分源使用。')
            if not history.get('verifiedCompleteDays', 0):
                report['warnings'].append('暂无完整重叠日可核验跨源一致性；历史补数只供研究。')
        report['warnings'].extend(warnings)
        if report['status'] == 'WATCH' or freeze_all:
            report = self.store.freeze(request, report, [b.model_dump(mode='json') for b in bars])
        if settle_cached:
            self._settle_cached(now)
        return report

    def _settle_cached(self, now):
        for report in self.store.iter_history():
            if (report.get('outcome') or {}).get('status') == 'SETTLED':
                continue
            bars = self.store.bars(report['instrumentCode'], now)
            result = settle(report, bars, now)
            self.store.outcome(report['id'], now, result)

    def refresh_outcomes(self):
        now = self.clock()
        pending = [r for r in self.store.iter_history()
                   if (r.get('outcome') or {}).get('status') != 'SETTLED']
        pending.sort(key=lambda r: (r.get('outcome') or {}).get('quoteRefreshAt', ''))
        codes = list(dict.fromkeys(r['instrumentCode'] for r in pending))[:3]
        failures = {}
        # Bound each click to three parallel provider requests, rotating older checks first.
        with ThreadPoolExecutor(max_workers=3) as pool:
            futures = {code: pool.submit(self.provider.fetch, code, now) for code in codes}
            for code, future in futures.items():
                try:
                    self.store.save_bars(code, future.result())
                except Exception as error:
                    failures[code] = f'到期行情更新失败：{type(error).__name__}；结算仅使用已有缓存'
        for report in pending:
            code = report['instrumentCode']
            if code not in codes:
                continue
            result = settle(report, self.store.bars(code, now), now)
            result['quoteRefreshAt'] = now.isoformat()
            result['warnings'] = [failures[code]] if code in failures else []
            self.store.outcome(report['id'], now, result)
        return self.store.history()

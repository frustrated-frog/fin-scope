from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from finscope_market_data.overnight.engine import predict, settle


class OvernightService:
    def __init__(self, store, provider, clock=None):
        self.store = store
        self.provider = provider
        self.clock = clock or (lambda: datetime.now(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None))

    def generate(self, request):
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
        report['warnings'].extend(warnings)
        if report['status'] == 'WATCH':
            report = self.store.freeze(request, report, [b.model_dump(mode='json') for b in bars])
        self._settle_cached(now)
        return report

    def _settle_cached(self, now):
        for report in self.store.history():
            if (report.get('outcome') or {}).get('status') == 'SETTLED':
                continue
            bars = self.store.bars(report['instrumentCode'], now)
            result = settle(report, bars, now)
            self.store.outcome(report['id'], now, result)

    def refresh_outcomes(self):
        now = self.clock()
        pending = [r for r in self.store.history()
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

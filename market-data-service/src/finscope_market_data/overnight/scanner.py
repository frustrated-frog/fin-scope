"""A bounded live universe, independent of the expensive after-close discovery run."""
import asyncio
import math
import hashlib
import re
from datetime import datetime
from zoneinfo import ZoneInfo

from finscope_market_data.providers.http import ProviderHttpClient


class OvernightCandidateScanner:
    async def _quotes(self):
        http = ProviderHttpClient(timeout_seconds=8)
        rows = []
        try:
            # Two liquid/strong cohorts, up to 400 observations before de-duplication.
            for order in ('f6', 'f3'):
                for page in (1, 2):
                    payload = await http.get_json('EASTMONEY_TAIL_SCAN',
                        'https://82.push2.eastmoney.com/api/qt/clist/get', params={
                            'pn': page, 'pz': 100, 'po': 1, 'np': 1, 'fltt': 2, 'invt': 2,
                            'fid': order, 'fs': 'm:0 t:6,m:0 t:80,m:1 t:2',
                            'fields': 'f2,f3,f6,f10,f12,f13,f14,f15,f16,f17,f18,f124'})
                    data = (payload.get('data') or {}).get('diff')
                    if not isinstance(data, list) or not data:
                        raise ValueError('候选行情缺少完整分页，稍后重试')
                    rows.extend(data)
            return rows
        finally:
            await http.aclose()

    def scan(self, now, limit):
        rows = asyncio.run(self._quotes())
        received = datetime.now(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None)
        return self.select(rows, received, limit)

    @staticmethod
    def select(rows, now, limit):
        candidates = {}
        observations = {}
        broad = {}
        fresh = set()
        for row in rows:
            code = str(row.get('f12', ''))
            name = str(row.get('f14', ''))
            if not re.fullmatch(r'(?:600|601|603|605|000|001|002|003|300|301)\d{3}', code):
                continue
            if not name or any(flag in name.upper() for flag in ('ST', '退', 'N', 'C')):
                continue
            try:
                price, change, amount, ratio, high, low, opening = [float(row[key])
                    for key in ('f2', 'f3', 'f6', 'f10', 'f15', 'f16', 'f17')]
                observed = datetime.fromtimestamp(float(row['f124']), ZoneInfo('Asia/Shanghai')).replace(tzinfo=None)
                if not all(math.isfinite(v) for v in (price, change, amount, ratio, high, low, opening)):
                    continue
            except (KeyError, TypeError, ValueError, OverflowError, OSError):
                continue
            # A response receipt timestamp alone cannot prove a quote belongs to today.
            if observed.date() != now.date() or not 0 <= (now - observed).total_seconds() <= 600:
                continue
            fresh.add(code)
            board_limit = 19 if code.startswith(('300', '301')) else 9
            if low <= 0 or high < low or not low <= price <= high or opening <= 0:
                continue
            location = (price - low) / (high - low) if high > low else 0
            reasons = []
            if amount < 100_000_000:
                reasons.append('LOW_LIQUIDITY')
            if not .5 <= change < board_limit:
                reasons.append('CHANGE_OUTSIDE_RULE')
            if ratio < 1:
                reasons.append('LOW_VOLUME_RATIO')
            if location < .65 or price < opening or high == low:
                reasons.append('WEAK_OR_ONE_PRICE')
            # Ranking is an acquisition priority, never presented as an up probability.
            score = 30 * location + 8 * min(ratio, 4) + 2 * min(change, 8)
            symbol = code + ('.SH' if code.startswith('6') else '.SZ')
            observation = {'instrumentCode': symbol, 'instrumentName': name,
                'changePct': change, 'amount': amount, 'volumeRatio': ratio,
                'quoteAt': observed.isoformat(), 'priorityScore': round(score, 2)}
            observations[symbol] = {**observation, 'rejectionReasons': reasons}
            if not reasons:
                candidates[symbol] = observation
            if amount >= 100_000_000 and -board_limit < change < board_limit and high > low:
                broad[symbol] = observation
        if not fresh:
            raise ValueError('行情缺少可核验的当日时间戳，未使用旧行情生成候选')
        exploration = limit // 3
        selected = [{**row, 'selectionLane': 'MOMENTUM'} for row in sorted(candidates.values(),
            key=lambda r: (-r['priorityScore'], r['instrumentCode']))[:limit - exploration]]
        chosen = {row['instrumentCode'] for row in selected}
        remainder = sorted((row for code, row in broad.items() if code not in chosen),
            key=lambda row: hashlib.sha256(f"{now.date()}|{row['instrumentCode']}".encode()).hexdigest())
        selected.extend({**row, 'selectionLane': 'BROAD_RESEARCH'} for row in remainder[:limit - len(selected)])
        selected_codes = {row['instrumentCode'] for row in selected}
        for code, row in observations.items():
            row['selected'] = code in selected_codes
            row['ruleRejectionReasons'] = list(row['rejectionReasons'])
            if row['selected']:
                row['rejectionReasons'] = []
            if not row['selected'] and not row['rejectionReasons']:
                row['rejectionReasons'] = ['ACQUISITION_LIMIT']
        return {'candidates': selected, 'observations': list(observations.values()),
                'observedCount': len({str(row.get('f12')) for row in rows}), 'freshCount': len(fresh),
                'scope': '成交额与涨幅各前 200 条合并去重；非全市场覆盖', 'sourceCode': 'EASTMONEY_LIVE'}

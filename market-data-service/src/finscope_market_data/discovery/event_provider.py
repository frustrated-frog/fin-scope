"""External A-share event adapters, isolated from discovery orchestration."""
from __future__ import annotations

from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo
import json
import math

from finscope_market_data.discovery.evidence_io import freeze_json


class MarketEventProvider:
    def __init__(self, snapshot_dir: Path, loader=None, now=None):
        self.snapshot_dir = snapshot_dir
        self.loader = loader or self._load
        self.now = now or (lambda: datetime.now(ZoneInfo('Asia/Shanghai')))

    @staticmethod
    def _load(kind, day):
        import akshare as ak
        if kind == 'LIMIT_UP':
            return ak.stock_zt_pool_em(date=day.replace('-', '')).to_dict('records')
        if kind == 'BROKEN_LIMIT':
            return ak.stock_zt_pool_zbgc_em(date=day.replace('-', '')).to_dict('records')
        return ak.stock_zh_a_spot_em().to_dict('records')

    def scan(self, day: str, limit: int = 160):
        from datetime import date
        date.fromisoformat(day)
        now = self.now()
        if day > now.date().isoformat() or (day == now.date().isoformat() and now.hour < 15):
            return {'status': 'BEFORE_CLOSE', 'as_of_date': day, 'members': [], 'warnings': ['等待完整收盘事件数据']}
        path = self.snapshot_dir / f'{day}.json'
        if path.exists():
            frozen = json.loads(path.read_text())
            if frozen.get('as_of_date') == day and frozen.get('method') == 'market-events-v1':
                return _bounded(frozen, limit)
        members, warnings, coverage = {}, [], {}
        kinds = ['LIMIT_UP', 'BROKEN_LIMIT']
        if day == now.date().isoformat():
            kinds.append('SPOT')
        else:
            warnings.append('无当日冻结全市场快照：历史补跑只覆盖涨停及炸板池，不以今日行情回填')
        for kind in kinds:
            try:
                rows = self.loader(kind, day)
                if kind == 'SPOT' and not rows:
                    raise ValueError('全市场行情为空')
                coverage[kind] = len(rows)
                for row in rows:
                    code = str(row.get('代码', '')).zfill(6)
                    name = str(row.get('名称', ''))
                    change = _number(row.get('涨跌幅'))
                    ratio = _number(row.get('量比'))
                    if kind == 'SPOT' and not (change >= 5 or (ratio >= 2 and abs(change) >= 3)):
                        continue
                    if len(code) != 6 or not code.isdigit() or not name:
                        continue
                    item = members.setdefault(code, {'code': code, 'name': name,
                        'industry': str(row.get('所属行业') or ''), 'sources': [], 'change_pct': change})
                    item['sources'].append(kind)
            except Exception as error:
                warnings.append(f'{kind} 事件源不可用：{type(error).__name__}')
        ordered = sorted(members.values(), key=lambda x: (
            'LIMIT_UP' not in x['sources'], 'BROKEN_LIMIT' not in x['sources'], -x['change_pct'], x['code']))
        result = {'method': 'market-events-v1', 'as_of_date': day, 'retrieved_at': now.isoformat(),
                  'status': 'COMPLETE' if len(coverage) == 3 else 'PARTIAL' if coverage else 'UNAVAILABLE',
                  'source_counts': coverage, 'event_count': len(ordered), 'truncated_count': 0,
                  'members': ordered, 'warnings': warnings}
        # A partial fetch must remain retryable; completed dated evidence is immutable.
        if result['status'] == 'COMPLETE':
            result = freeze_json(path, result)
        return _bounded(result, limit)


def _bounded(result, limit):
    return {**result, 'members': result['members'][:limit],
            'truncated_count': max(0, len(result['members']) - limit)}


def _number(value):
    try:
        number = float(value)
        return number if math.isfinite(number) else 0.
    except (ValueError, TypeError):
        return 0.

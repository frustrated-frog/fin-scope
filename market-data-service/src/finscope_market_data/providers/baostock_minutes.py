"""Bounded, isolated access to the public BaoStock raw minute history SDK."""
from datetime import datetime, timedelta
import json
import re
import subprocess
import sys

from finscope_market_data.overnight.models import MinuteBar
from finscope_market_data.providers.base import ProviderError

SOURCE = 'BAOSTOCK_5M_RAW'
FIELDS = ('date', 'time', 'code', 'open', 'high', 'low', 'close', 'volume', 'amount', 'adjustflag')
MAX_BARS = 18000


class BaostockMinuteHistoryProvider:
    source = SOURCE

    def fetch(self, code: str, through: datetime, *, start=None):
        if not re.fullmatch(r'(?:60\d{4}\.SH|(?:00|30)\d{4}\.SZ)', code):
            raise ValueError('不支持的分钟历史代码')
        start = max(start or (through - timedelta(days=365)).date(), (through - timedelta(days=365)).date())
        if start > through.date():
            raise ValueError('历史分钟起止日期无效')
        # The SDK owns a process-global socket and has no cancellation API.
        # Isolate it from live acquisition; timeout kills/reaps the whole worker.
        try:
            result = subprocess.run([sys.executable, '-m', 'finscope_market_data.providers.baostock_worker',
                code, str(start), str(through.date())], capture_output=True, text=True, timeout=45, check=False)
        except subprocess.TimeoutExpired as error:
            raise ProviderError('TIMEOUT', '历史分钟源超时，后台稍后重试') from error
        if result.returncode != 0:
            raise ProviderError('HISTORY_UNAVAILABLE', '历史分钟源暂不可用，请确认依赖已同步', True)
        try:
            payload = json.loads(result.stdout)
            return self.parse(payload, code, through)
        except (ValueError, KeyError, TypeError) as error:
            raise ProviderError('SCHEMA_DRIFT', '历史分钟源返回无效数据', False) from error

    @staticmethod
    def parse(payload, code, through):
        expected_code = code[-2:].lower() + '.' + code[:6]
        if (not isinstance(payload, dict) or payload.get('fields') != list(FIELDS)
                or not isinstance(payload.get('rows'), list) or not 0 < len(payload['rows']) <= MAX_BARS):
            raise ValueError('历史分钟字段或行数无效')
        bars = {}
        for values in payload['rows']:
            if len(values) != len(FIELDS):
                raise ValueError('历史分钟字段不完整')
            row = dict(zip(FIELDS, values))
            if row['code'] != expected_code or row['adjustflag'] != '3':
                raise ValueError('代码或未复权口径不匹配')
            stamp = datetime.strptime(row['time'], '%Y%m%d%H%M%S%f')
            if str(stamp.date()) != row['date'] or stamp.second or stamp.microsecond or stamp.minute % 5:
                raise ValueError('分钟时间标签无效')
            clock = stamp.strftime('%H:%M')
            if not ('09:35' <= clock <= '11:30' or '13:05' <= clock <= '15:00'):
                raise ValueError('非交易时段分钟线')
            if not through - timedelta(days=365) <= stamp <= through:
                continue
            # Some non-trading intervals are all-zero placeholders, not prices.
            if all(float(row[key]) == 0 for key in ('open', 'high', 'low', 'close', 'volume', 'amount')):
                continue
            bar = MinuteBar(ended_at=stamp, **{key: row[key] for key in ('open', 'high', 'low', 'close', 'amount')})
            if stamp in bars and bars[stamp] != bar:
                raise ValueError('历史分钟数据冲突')
            bars[stamp] = bar
        if not bars:
            raise ValueError('历史分钟数据为空')
        return sorted(bars.values(), key=lambda bar: bar.ended_at)

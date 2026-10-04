"""Bounded public ex-right-date verification; adjustment is not total return."""
from datetime import date
import json
import math
import re
import subprocess
import sys

from finscope_market_data.providers.base import ProviderError

FIELDS = ('code', 'dividOperateDate', 'foreAdjustFactor', 'backAdjustFactor', 'adjustFactor')


class BaostockActionProvider:
    source = 'BAOSTOCK_ADJUST_FACTOR'

    def fetch(self, code, start, end):
        if not re.fullmatch(r'(?:60\d{4}\.SH|(?:00|30)\d{4}\.SZ)', code) or start > end:
            raise ValueError('公司行为查询参数无效')
        try:
            result = subprocess.run([sys.executable, '-m', 'finscope_market_data.providers.baostock_worker',
                code, str(start), str(end), 'actions'], capture_output=True, text=True, timeout=30, check=False)
            if result.returncode:
                raise ValueError('Source unavailable')
            return self.parse(json.loads(result.stdout), code, start, end)
        except (subprocess.TimeoutExpired, ValueError, KeyError, TypeError) as error:
            raise ProviderError('ACTIONS_UNAVAILABLE', '公司行为核验源暂不可用，保留未知状态') from error

    @staticmethod
    def parse(payload, code, start, end):
        if payload.get('fields') != list(FIELDS) or not isinstance(payload.get('rows'), list) or len(payload['rows']) > 100:
            raise ValueError('公司行为字段或数量异常')
        dates = set()
        for row in payload['rows']:
            if len(row) != len(FIELDS) or row[0] != code[-2:].lower() + '.' + code[:6]:
                raise ValueError('公司行为证券代码不匹配')
            day = date.fromisoformat(row[1])
            if not start <= day <= end or any(not math.isfinite(float(v)) or float(v) <= 0 for v in row[2:]):
                raise ValueError('公司行为日期或复权因子无效')
            dates.add(str(day))
        return {'instrumentCode': code, 'fromDate': str(start), 'throughDate': str(end),
                'exDates': sorted(dates), 'source': 'BAOSTOCK_ADJUST_FACTOR', 'status': 'COVERED'}

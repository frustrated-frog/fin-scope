"""Recent minute prices; missing amount is explicit and never estimated."""
from datetime import datetime
import math
from zoneinfo import ZoneInfo

from finscope_market_data.models import DataCapability, MinutePriceBar
from finscope_market_data.providers.base import ProviderError
from finscope_market_data.providers.http import ProviderHttpClient


class TencentMinuteBarProvider:
    provider_code = 'TENCENT_MINUTE_PRICE'
    provider_family = 'TENCENT'
    priority = 4
    capabilities = {DataCapability.MINUTE_BARS}
    max_retries = 0

    def __init__(self,http=None):
        self.http = http or ProviderHttpClient(minimum_interval_seconds=0.15)

    def supports(self,capability,symbol):
        return capability is DataCapability.MINUTE_BARS and symbol.market.value in {'SH','SZ'} and not symbol.is_market_pulse_index

    async def fetch(self,capability,symbol,**kwargs):
        frequency = int(kwargs.get('frequency',5))
        count = int(kwargs.get('limit',240))
        if frequency not in {1,5,15,30,60} or not 1 <= count <= 320:
            raise ProviderError('INVALID_REQUEST','腾讯分钟周期或数量不支持',False)
        error = None
        for host in ('https://ifzq.gtimg.cn','https://proxy.finance.qq.com/ifzqgtimg'):
            try:
                payload = await self.http.get_json(self.provider_code,host+'/appstock/app/kline/mkline',
                    params={'param':f'{symbol.prefixed_code},m{frequency},,{count}'},headers={'Referer':'https://gu.qq.com/'})
                if payload.get('code') != 0:
                    raise ValueError('minute business error')
                rows = payload['data'][symbol.prefixed_code][f'm{frequency}']
                return self.parse(rows,symbol,frequency)
            except (ValueError,KeyError,TypeError) as failure:
                error = ProviderError('SCHEMA_DRIFT','腾讯分钟线协议无效',False)
            except ProviderError as failure:
                error = failure
        raise error

    @staticmethod
    def parse(rows,symbol,frequency):
        if not isinstance(rows,list) or not rows:
            raise ProviderError('EMPTY_DATA','腾讯分钟线暂缺',True)
        result = {}
        for row in rows:
            try:
                if not isinstance(row, list) or len(row) < 6:
                    raise ValueError('minute row layout')
                stamp = datetime.strptime(row[0],'%Y%m%d%H%M').replace(tzinfo=ZoneInfo('Asia/Shanghai'))
                opening,close,high,low,volume = [float(value) for value in row[1:6]]
                if not all(math.isfinite(value) for value in (opening,close,high,low,volume)):
                    raise ValueError('nonfinite prices')
                if min(opening,close,high,low) <= 0 or volume < 0 or high < max(opening,close) or low > min(opening,close):
                    raise ValueError('invalid minute OHLC')
                volume *= 1 if symbol.code.startswith(('688','689')) else 100
                bar = MinutePriceBar(symbol=symbol,observed_at=stamp,frequency_minutes=frequency,
                    open=opening,high=high,low=low,close=close,volume=volume)
                if stamp in result and result[stamp] != bar:
                    raise ValueError('minute conflict')
                result[stamp] = bar
            except (ValueError,TypeError,IndexError) as error:
                raise ProviderError('SCHEMA_DRIFT','腾讯分钟线时间或数值无效',False) from error
        return [result[stamp] for stamp in sorted(result)]

    def warnings_for(self,capability,symbol,data):
        return ['腾讯分钟线只提供最近有限窗口，成交额未提供；近期成交量可能后续回填，不替代真实成交额样本']

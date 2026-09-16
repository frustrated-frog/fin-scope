from datetime import datetime, timedelta

from finscope_market_data.overnight.models import MinuteBar


class OvernightMinuteProvider:
    source = 'EASTMONEY_5M_RAW'

    def fetch(self, code: str, through: datetime):
        import akshare as ak
        frame = ak.stock_zh_a_hist_min_em(symbol=code.split('.')[0], period='5', adjust='',
            start_date=(through - timedelta(days=180)).strftime('%Y-%m-%d 09:00:00'),
            end_date=through.strftime('%Y-%m-%d %H:%M:%S'))
        return self.parse(frame.to_dict('records'), through)

    @staticmethod
    def parse(rows, through):
        bars = {}
        for row in rows:
            bar = MinuteBar(ended_at=datetime.fromisoformat(str(row['时间'])),
                open=row['开盘'], close=row['收盘'], high=row['最高'], low=row['最低'], amount=row['成交额'])
            if bar.ended_at > through:
                continue
            if bar.ended_at in bars and bars[bar.ended_at] != bar:
                raise ValueError('同一时点存在冲突分钟线')
            bars[bar.ended_at] = bar
        return sorted(bars.values(), key=lambda x: x.ended_at)

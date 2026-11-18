from datetime import date, datetime
import json

import pytest

from finscope_market_data.providers.tonghuashun_sector_history import TonghuashunSectorHistorySource


class Http:
    def __init__(self, today=None):
        self.paths = []
        self.closed = False
        self.today = today or {"1": "20261118", "11": "105", "13": 1000, "dt": "1510"}

    async def get_text(self, provider, url, **kwargs):
        self.paths.append(url)
        if url.endswith("today.js"):
            value = {"bk_881121": self.today}
        else:
            value = {"data": "20261116,100,101,99,100,1000;20261117,101,102,100,102,1000"}
        return "callback(" + json.dumps(value) + ");"

    async def aclose(self):
        self.closed = True


def source(http, now=datetime(2026, 11, 18, 16)):
    return TonghuashunSectorHistorySource(http_factory=lambda: http, now_provider=lambda: now)


def test_separately_published_closing_bar_completes_today_without_changing_history():
    http = Http()
    frame = source(http).history("881121", date(2026, 11, 16), date(2026, 11, 18), {})
    assert list(frame["日期"]) == [date(2026, 11, 16), date(2026, 11, 17), date(2026, 11, 18)]
    assert list(frame["收盘价"]) == [100, 102, 105]
    assert len(http.paths) == 2
    assert http.closed


@pytest.mark.parametrize("changes", [
    {"1": "20261117"}, {"1": "20261119"}, {"dt": "1130"}, {"dt": "1599"}, {"11": "NaN"}, {"11": "inf"}, {"13": 0}
])
def test_stale_future_intraday_or_invalid_current_bar_is_not_used(changes):
    current = {"1": "20261118", "11": "105", "13": 1000, "dt": "1510"}
    current.update(changes)
    http = Http(current)
    frame = source(http).history("881121", date(2026, 11, 16), date(2026, 11, 18), {})
    assert list(frame["日期"])[-1] == date(2026, 11, 17)
    assert http.closed


def test_current_quote_does_not_leak_into_historical_date_or_preclose_snapshot():
    http = Http()
    frame = source(http).history("881121", date(2026, 11, 16), date(2026, 11, 17), {})
    assert list(frame["日期"])[-1] == date(2026, 11, 17)
    assert all(not path.endswith("today.js") for path in http.paths)
    preclose = source(Http(), datetime(2026, 11, 18, 14, 59)).history("881121", date(2026, 11, 16), date(2026, 11, 18), {})
    assert list(preclose["日期"])[-1] == date(2026, 11, 17)


def test_annual_archive_fills_older_history_and_bad_responses_close_transport():
    http = Http()
    source(http).history("881121", date(2026, 1, 1), date(2026, 11, 17), {})
    assert any(path.endswith("2026.js") for path in http.paths)
    assert http.closed

    class Invalid(Http):
        async def get_text(self, *args, **kwargs):
            return "<html>rejected</html>"
    invalid = Invalid()
    with pytest.raises(Exception, match="JSONP"):
        source(invalid).history("881121", date(2026, 11, 16), date(2026, 11, 18), {})
    assert invalid.closed

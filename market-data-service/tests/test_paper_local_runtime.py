from datetime import datetime
from unittest.mock import Mock, patch

from finscope_market_data.paper.local_runtime import PaperWake, remaining_session


def test_assertion_only_covers_active_accounts_and_trading_hours():
    now = datetime.fromisoformat("2026-11-27T14:50:00+08:00")
    wake = PaperWake()
    wake.enabled = True
    child = Mock()
    child.poll.return_value = None
    with patch("finscope_market_data.paper.local_runtime.subprocess.Popen", return_value=child) as start:
        wake.update(now, lambda: True)
        wake.update(now, lambda: True)
        start.assert_called_once()
        assert remaining_session(now) == 17 * 60 and wake.active
        wake.update(now, lambda: False)
        child.terminate.assert_called_once()
    assert not wake.active
    assert remaining_session(now.replace(day=28)) == 0
    assert remaining_session(now.replace(month=10, day=1)) == 0

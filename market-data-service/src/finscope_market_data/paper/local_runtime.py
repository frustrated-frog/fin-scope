"""A bounded macOS idle-sleep assertion for active paper accounts."""

from datetime import timedelta
import logging
import os
import subprocess
import sys

from finscope_market_data.forecast.trading_calendar import next_session

log = logging.getLogger(__name__)


def remaining_session(now):
    day = now.date()
    if next_session(day - timedelta(days=1)) != day:
        return 0
    opening = now.replace(hour=9, minute=20, second=0, microsecond=0)
    closing = now.replace(hour=15, minute=7, second=0, microsecond=0)
    return max(0, int((closing-now).total_seconds())) if now >= opening else 0


class PaperWake:
    def __init__(self):
        self.enabled = sys.platform == "darwin" and os.getenv("FINSCOPE_PAPER_LOCAL_RUNNER") == "1"
        self.process = None

    def update(self, now, has_accounts):
        if not self.enabled:
            return
        seconds = remaining_session(now)
        if not seconds or not has_accounts():
            self.close()
        elif not self.active:
            try:
                self.process = subprocess.Popen(
                    ["/usr/bin/caffeinate", "-i", "-t", str(seconds), "-w", str(os.getpid())],
                    stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except OSError:
                log.exception("本机防空闲休眠未能启动")

    @property
    def active(self):
        return self.process is not None and self.process.poll() is None

    def close(self):
        if self.active:
            self.process.terminate()
            self.process.wait(timeout=3)
        self.process = None

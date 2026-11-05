"""The Java HTTP tests consume the same synthetic reports produced here."""
from datetime import datetime
import json
from pathlib import Path
from types import SimpleNamespace

from finscope_market_data.overnight.models import OvernightRequest
from finscope_market_data.overnight.provider import MinuteBatch
from finscope_market_data.overnight.service import OvernightService
from finscope_market_data.overnight.store import OvernightStore
from test_overnight import history


FIXTURE = Path(__file__).resolve().parents[2] / 'test-fixtures' / 'overnight' / 'history-v6.json'


def synthetic_history(directory):
    bars = history(4)
    day = bars[-1].ended_at.date()
    store = OvernightStore(directory / 'contract.db')
    for mode, cutoff in [('TAIL_ENTRY', '14:30'), ('AFTER_CLOSE_HOLDING', '15:00')]:
        now = datetime.fromisoformat(f'{day}T{cutoff}:30')
        available = [bar for bar in bars if bar.ended_at <= now]
        provider = SimpleNamespace(source='SYNTHETIC_PRIMARY', fetch=lambda code, at: MinuteBatch(
            available, 'SYNTHETIC_FALLBACK', [{'source': 'SYNTHETIC_PRIMARY', 'code': 'TIMEOUT'}]))
        service = OvernightService(store, provider, clock=lambda: now)
        request = OvernightRequest(instrumentCode='605058.SH', signalDate=day,
            mode=mode, cutoff=cutoff, costBasis=20 if mode == 'AFTER_CLOSE_HOLDING' else None,
            quantity=100 if mode == 'AFTER_CLOSE_HOLDING' else None,
            positionOpenedOn='2026-01-05' if mode == 'AFTER_CLOSE_HOLDING' else None)
        service.generate(request, freeze_all=True, settle_cached=False)
    return store.history()


def test_python_reports_match_shared_java_contract(tmp_path):
    actual = synthetic_history(tmp_path)
    expected = json.loads(FIXTURE.read_text())
    assert actual == expected
    assert all(row['modelVersion'] == 'overnight-v6-decision-direction' for row in actual)
    assert all(row['acquisition']['status'] == 'AVAILABLE' for row in actual)
    assert {row['mode'] for row in actual} == {'TAIL_ENTRY', 'AFTER_CLOSE_HOLDING'}

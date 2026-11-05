from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from types import SimpleNamespace

import pandas as pd
import pytest

from finscope_market_data.overnight.market_context import market_snapshot, market_groups, paired_market_panels
from finscope_market_data.overnight.market_ablation import evaluate_market_groups
from finscope_market_data.overnight.context_store import OvernightContextStore
from finscope_market_data.overnight.store import OvernightStore
from test_overnight_context import snapshot


def broad():
    observed = datetime(2026, 9, 21, 14, 20)
    frame = pd.DataFrame([{'code': f'sh60{i:04}', 'trade': 10, 'change_pct': 4 if i % 2 else -4,
                          'amount': 100, 'high': 11, 'observed_at': observed.replace(tzinfo=ZoneInfo('Asia/Shanghai'))}
                         for i in range(1000)])
    frame.attrs['expectedSymbols'] = 1000
    return frame, observed


def test_broad_timestamp_coverage_and_unknown_denominator():
    frame, observed = broad()
    received = observed + timedelta(seconds=30)
    assert market_snapshot(frame, observed, received)['quality'] == 'SUFFICIENT'
    frame.loc[:299, 'observed_at'] = (observed - timedelta(minutes=1)).replace(tzinfo=ZoneInfo('Asia/Shanghai'))
    result = market_snapshot(frame, observed, received)
    assert result['capturedSymbols'] == 700 and result['quality'] == 'INSUFFICIENT_COVERAGE'
    frame.loc[:, 'observed_at'] = (received + timedelta(seconds=1)).replace(tzinfo=ZoneInfo('Asia/Shanghai'))
    assert market_snapshot(frame, observed, received)['capturedSymbols'] == 0
    frame, observed = broad()
    frame.attrs.clear()
    assert market_snapshot(frame, observed, received)['quality'] == 'INSUFFICIENT_COVERAGE'


def test_freeze_survives_restart_excludes_quotes_from_status_and_rejects_future(tmp_path):
    frame, observed = broad()
    value = snapshot()
    value['market'] = market_snapshot(frame, observed, observed + timedelta(seconds=30))
    path = tmp_path / 'context.db'
    store = OvernightContextStore(OvernightStore(path))
    frozen = store.freeze(value)
    assert 'quotes' not in store.recent()[0]['market']
    store = OvernightContextStore(OvernightStore(path))
    assert store.at('2026-09-21', '14:30', observed + timedelta(minutes=10)) == frozen
    value['market']['quotes']['600000.SH']['observedAt'] = '2026-09-21T15:00:00'
    with pytest.raises(ValueError):
        store.freeze(value)


def test_industry_uses_only_available_memberships_and_excludes_self():
    frame, observed = broad()
    value = snapshot()
    value['market'] = market_snapshot(frame, observed, observed + timedelta(seconds=30))
    value['memberships'] = [{'industry': '881001', 'available_on': '2026-09-22',
                             'codes': [f'60000{i}.SH' for i in range(6)]}]
    assert 'INDUSTRY' not in market_groups('600000.SH', value)
    value['memberships'][0]['available_on'] = '2026-09-21'
    before = market_groups('600000.SH', value)
    assert before['INDUSTRY'][0] == pytest.approx(.008)
    value['market']['quotes']['600000.SH']['return'] = 999
    after = market_groups('600000.SH', value)
    assert before['MARKET'] == after['MARKET'] and before['SENTIMENT'] == after['SENTIMENT']
    assert before['INDUSTRY'][:2] == after['INDUSTRY'][:2]


def test_group_pairs_share_dates_codes_and_no_historical_reconstruction():
    frame, observed = broad()
    value = snapshot()
    value['market'] = market_snapshot(frame, observed, observed + timedelta(seconds=30))
    rows = [{'signalDate': '2026-09-21', 'instrumentCode': f'6000{i:02}.SH', 'features': [1.] * 26,
             'actualReturn': .01, 'exitAt': '2026-09-22T15:00:00'} for i in range(20)]
    contexts = SimpleNamespace(at=lambda day, cutoff, through: value)
    panels = paired_market_panels(rows, contexts, '14:30')
    base, enhanced = panels['MARKET']
    assert len(base) == len(enhanced) == 20
    assert all(a['instrumentCode'] == b['instrumentCode'] and len(b['features']) == 29 for a, b in zip(base, enhanced))
    audit = evaluate_market_groups(rows, contexts, '14:30', datetime(2026, 9, 23))
    assert audit['eligible'] is False and audit['groups'][0]['dayCount'] == 1
    assert audit['groups'][2]['dayCount'] == 0
    missing = paired_market_panels(rows, SimpleNamespace(at=lambda *args: None), '14:30')
    assert all(pair == ([], []) for pair in missing.values())


def test_capture_market_failure_does_not_block_existing_context(tmp_path):
    import asyncio
    from finscope_market_data.overnight.context_capture import OvernightContextCapture
    frame, observed = broad()
    now = observed + timedelta(seconds=30)
    async def unavailable(*args, **kwargs):
        raise RuntimeError('offline')
    auto = SimpleNamespace(clock=lambda: now, service=SimpleNamespace(
        store=OvernightStore(tmp_path / 'capture.db'), provider=SimpleNamespace(fetch_async=unavailable)))
    task = OvernightContextCapture(auto, SimpleNamespace(fetch=unavailable), tmp_path / 'none', tmp_path / 'history',
                                   market_loader=lambda day: frame)
    value = asyncio.run(task.collect([], observed, '14:30', observed + timedelta(minutes=9)))
    assert value['market']['capturedSymbols'] == 1000
    def fail(day):
        raise RuntimeError('offline')
    task.market_loader = fail
    value = asyncio.run(task.collect([], observed, '14:30', observed + timedelta(minutes=9)))
    assert value['market']['quality'] == 'UNAVAILABLE' and value['indices'] == {}


def test_ablation_fits_both_sides_only_after_coverage_and_label_maturity(monkeypatch):
    from finscope_market_data.overnight import market_ablation
    rows = []
    for offset in range(143):
        day = datetime(2025, 1, 1) + timedelta(days=offset)
        rows.extend({'signalDate': str(day.date()), 'instrumentCode': f'6000{i:02}.SH',
                     'features': [float(i)] * 26, 'actualReturn': .01,
                     'exitAt': (day + timedelta(days=1)).isoformat()} for i in range(20))
    pairs = (rows, [{**row, 'features': [*row['features'], .1, .2, .3]} for row in rows])
    monkeypatch.setattr(market_ablation, 'paired_market_panels', lambda *args: {'MARKET': pairs})
    calls = []
    def fit(values, through, cutoff):
        calls.append(values)
        return {'audit': {'historical': {'accuracy': .5}}}
    monkeypatch.setattr(market_ablation, 'fit_direction', fit)
    result = evaluate_market_groups(rows, None, '14:30', datetime(2025, 12, 1))
    assert result['groups'][0]['status'] == 'RESEARCH_ONLY' and not result['eligible']
    assert len(calls) == 2
    assert [(r['signalDate'], r['instrumentCode']) for r in calls[0]] == [(r['signalDate'], r['instrumentCode']) for r in calls[1]]
    calls.clear()
    result = evaluate_market_groups(rows, None, '14:30', datetime(2025, 2, 1))
    assert not calls and result['groups'][0]['status'] == 'ACCUMULATING'

from datetime import datetime
from types import SimpleNamespace

from finscope_market_data.overnight.universe import extend_universe, POOL_KEY
from test_overnight_history import flow


def test_cohort_is_balanced_deterministic_and_does_not_replace_missing_members():
    now = datetime(2026, 10, 5, 10)
    keys = [f'{exchange}:{prefix}{i:03}' for exchange, prefix in [('SH', '600'), ('SZ', '000'), ('SZ', '300')]
            for i in range(100)]
    result = extend_universe(None, keys + ['SH:688001', 'SZ:600001'], now)
    assert len(result['members']) == 120
    assert {board: sum(row['board'] == board for row in result['members'])
            for board in ['SH_MAIN', 'SZ_MAIN', 'GEM']} == {'SH_MAIN': 40, 'SZ_MAIN': 40, 'GEM': 40}
    assert extend_universe(None, reversed(keys), now) == result
    assert extend_universe(result, ['SH:605058'], now) == result


def test_pool_collects_history_without_holdings_or_selected_candidates(flow):
    from finscope_market_data.overnight.automation_models import AutomationContext
    task, auto, now, calls = flow
    auto.sync_context(AutomationContext())
    task.snapshots = SimpleNamespace(daily_bar_symbols=lambda: ['SH:605058', 'SZ:000001'])
    task.tick()
    task.tick()
    assert {call[0] for call in calls} == {'605058.SH', '000001.SZ'}
    assert len(auto.store.get(POOL_KEY)['members']) == 2


def test_ready_pool_keeps_updating_and_uses_incremental_history(flow):
    from test_overnight import history
    task, auto, now, calls = flow
    bars = history(145)
    task.minutes.import_history('605058.SH', bars, 'TEST', now[0])
    auto.store.put(POOL_KEY, extend_universe(None, ['SH:605058'], now[0]))
    intervals = []
    def fetch(code, through, *, start):
        intervals.append((start, through))
        return [bars[-1]]
    task.provider.fetch = fetch
    task.tick()
    task.tick()
    assert len(intervals) == 1
    assert intervals[0][0] < bars[-1].ended_at.date()
    assert intervals[0][1] == datetime(2026, 9, 22, 15)

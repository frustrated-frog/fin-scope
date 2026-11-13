from datetime import datetime, timedelta
from types import SimpleNamespace

import pytest

from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.action_verification import ActionVerificationStore
from finscope_market_data.overnight.training_inputs import input_revision, refresh_allowed, GAP_KEY
from finscope_market_data.overnight.joint_dataset import JointProfile
from finscope_market_data.overnight.history_backfill import OvernightHistoryBackfill
from test_overnight import history
from test_overnight_history import bar
from test_overnight_direction import direction_rows


def test_revision_changes_for_new_prices_and_actions_but_not_duplicate_download(tmp_path):
    store = OvernightStore(tmp_path / 'revision.db')
    meta = AutomationStore(store)
    now = datetime(2026, 11, 12, 19)
    revision = lambda: input_revision(store, meta, {'fingerprint': 'pool'}, {}, now.isoformat())
    initial = revision()
    rows = [bar('2026-11-11T09:35:00')]
    store.save_bars('600000.SH', rows)
    changed = revision()
    assert changed != initial
    store.save_bars('600000.SH', rows)
    assert revision() == changed
    store.import_history('600001.SH', rows, 'TEST', now)
    imported = revision()
    assert imported != changed
    store.import_history('600001.SH', rows, 'TEST', now)
    assert revision() == imported
    ActionVerificationStore(store).save({'instrumentCode': '600000.SH', 'fromDate': '2026-01-01',
        'throughDate': '2026-11-11', 'exDates': ['2026-11-10']}, now)
    assert revision() != imported
    with pytest.raises(ValueError, match='为空'):
        store.import_history('600001.SH', [], 'TEST', now)
    assert store.history_import('600001.SH')['lastDate'] == '2026-11-11'


def test_retrain_after_new_input_is_bounded_but_same_revision_retry_is_allowed():
    now = datetime(2026, 11, 12, 19)
    profile = JointProfile('TAIL_ENTRY', '14:30', 20)
    jobs = [{'key': 'job|' + profile.key, 'inputRevision': 'old', 'status': 'COMPLETED',
             'startedAt': (now - timedelta(minutes=40)).isoformat(), 'finishedAt': now.isoformat()}]
    assert not refresh_allowed(jobs, profile, 'new', now + timedelta(minutes=29))
    assert refresh_allowed(jobs, profile, 'new', now + timedelta(minutes=30))
    assert refresh_allowed(jobs, profile, 'old', now)


def test_research_can_repair_partial_days_without_modifying_live_inputs(tmp_path):
    store = OvernightStore(tmp_path / 'repair.db')
    bars = history(10)
    last = bars[-1].ended_at.date()
    partial = [b for b in bars if b.ended_at.date() == last][:2]
    now = datetime.combine(last, datetime.min.time()).replace(hour=18)
    store.save_bars('600000.SH', partial)
    store.import_history('600000.SH', bars, 'TEST', now)
    assert len([b for b in store.bars('600000.SH', now) if b.ended_at.date() == last]) == 2
    assert len([b for b in store.research_bars('600000.SH', now) if b.ended_at.date() == last]) == 48


def test_older_frozen_candidate_gap_is_requested_without_cache_or_pool_membership(tmp_path):
    store = OvernightStore(tmp_path / 'backfill.db')
    meta = AutomationStore(store)
    now = datetime(2026, 11, 12, 19)
    meta.put(f'{GAP_KEY}|14:30', {'records': [{'instrumentCode': '600001.SH', 'reason': 'MISSING_DECISION_BARS'},
                                           {'instrumentCode': '600002.SH', 'reason': 'NOT_DUE'}]})
    calls = []
    def fetch(code, through):
        calls.append(code)
        return [bar('2026-11-11T09:35:00')]
    auto = SimpleNamespace(store=meta, service=SimpleNamespace(store=store), clock=lambda: now)
    OvernightHistoryBackfill(auto, SimpleNamespace(source='TEST', fetch=fetch)).tick()
    assert calls == ['600001.SH']


def test_final_refit_does_not_reintroduce_action_affected_selection_rows(direction_rows):
    from copy import deepcopy
    from finscope_market_data.overnight.direction_learning import temporal_parts, fit_direction_before
    rows = deepcopy(direction_rows)
    through = '2026-07-01T12:00:00'
    _, selection, _ = temporal_parts(rows, through)
    blocked = {(r['signalDate'], r['instrumentCode']) for r in selection[:30]}
    for row in rows:
        if (row['signalDate'], row['instrumentCode']) in blocked:
            row['trainingEligible'] = False
    first = fit_direction_before(rows, through)
    for row in rows:
        if not row.get('trainingEligible', True):
            row['features'] = [999.] * len(row['features'])
    second = fit_direction_before(rows, through)
    assert first['model']['shape'] == second['model']['shape']
    assert first['model']['prior'] == second['model']['prior']

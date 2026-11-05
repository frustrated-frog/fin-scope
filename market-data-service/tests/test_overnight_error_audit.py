from datetime import datetime
from types import SimpleNamespace

import numpy as np
import pytest

from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.candidate_cohort import frozen_cohorts, mark_cohort
from finscope_market_data.overnight.direction_dataset import PROTOCOL, TARGET
from finscope_market_data.overnight.error_audit import build_error_audit
from finscope_market_data.overnight.up_selection import select_up_candidates
from test_overnight_multiscale import history_rows


def scan(day='2026-09-21', cutoff='14:30'):
    return {'key': f'{day}|SCAN|{cutoff}', 'signalDate': day, 'cutoff': cutoff, 'phase': 'DISCOVER',
        'status': 'COMPLETED', 'snapshotAt': f'{day}T14:20:00', 'sourceCode': 'TEST',
        'candidates': [{'instrumentCode': f'60000{i}.SH', 'selectionLane': 'MOMENTUM'} for i in range(3)]}


def report(code, probability, **changes):
    return {'id': code, 'signalDate': '2026-09-21', 'cutoff': '14:30', 'mode': 'TAIL_ENTRY',
        'instrumentCode': code, 'generatedAt': '2026-09-21T14:31:00', 'evidenceKind': 'FORWARD',
        'jointResearch': {'cohort': 'AUTOMATIC'}, 'sourceCode': 'TEST',
        'closeDirection': {'protocol': PROTOCOL, 'target': TARGET, 'artifactId': 'model', 'upProbability': probability},
        'outcome': {'closeDirection': {'status': 'SETTLED', 'actualUp': False, 'exitAt': '2026-09-22T15:00:00'}}, **changes}


def audit(reports, scans=None, now=datetime(2026, 9, 23), holdings=None):
    meta = SimpleNamespace(iter_jobs=lambda phase: iter((scans if scans is not None else [scan()]) if phase == 'DISCOVER' else holdings or []))
    contexts = SimpleNamespace(at=lambda *args: None)
    return build_error_audit(SimpleNamespace(iter_history=lambda: iter(reports)), meta, contexts, now)


def test_cohort_reads_beyond_ui_limit_and_rejects_late_or_recovery_snapshots(tmp_path):
    meta = AutomationStore(OvernightStore(tmp_path / 'cohort.db'))
    for i in range(205):
        job = meta.claim(str(i).zfill(3), datetime(2026, 9, 21), {'phase': 'DISCOVER'})
        meta.finish(job, datetime(2026, 9, 21), status='COMPLETED')
    assert len(meta.jobs(phase='DISCOVER')) == 100
    assert len(list(meta.iter_jobs('DISCOVER'))) == 205
    rows = [scan(), {**scan('2026-09-22'), 'snapshotAt': '2026-09-22T14:31:00'},
            {**scan('2026-09-23'), 'key': 'recovery'}]
    cohorts = frozen_cohorts(SimpleNamespace(iter_jobs=lambda phase: rows), '2026-09-24T12:00:00')
    assert list(cohorts) == [('2026-09-21', '14:30')]
    assert not frozen_cohorts(SimpleNamespace(iter_jobs=lambda phase: rows), '2026-09-21T14:29:00')
    assert mark_cohort([{'signalDate': '2026-09-20', 'instrumentCode': '600000.SH'}], cohorts, '14:30')[0]['candidateMember'] is None


def test_failed_candidates_stay_in_denominator_and_incomplete_days_cannot_score_top_three():
    result = audit([report('600000.SH', .9), report('600001.SH', .3)])['groups'][0]
    assert result['scheduledCount'] == 3 and result['settledCount'] == 2
    assert result['coverage'] == pytest.approx(2/3)
    assert result['missing'] == {'MISSING_PREDICTION': 1}
    assert result['metrics']['falseUpCount'] == 1
    assert result['metrics']['accuracy'] == .5
    assert result['upCandidates']['precision'] is None
    assert result['completeDays'] == 0
    assert any(r['dimension'] == 'marketState' and r['value'] == 'UNKNOWN' for r in result['slices'])


def test_late_replays_duplicates_and_future_outcomes_do_not_rewrite_original():
    reports = [report('600000.SH', .9), report('600000.SH', .1, id='later', generatedAt='2026-09-21T14:32:00'),
               report('600001.SH', .3, evidenceKind='RETROSPECTIVE')]
    result = audit(reports)['groups'][0]
    assert result['settledCount'] == 1 and result['metrics']['accuracy'] == 0
    assert audit(reports, now=datetime(2026, 9, 22, 14))['groups'] == []
    reports[0]['outcome']['closeDirection']['exitAt'] = '2026-09-24T15:00:00'
    assert audit(reports)['groups'][0]['settledCount'] == 0


def test_full_cohort_scores_top_candidates_and_keeps_legacy_separate():
    reports = [report(f'60000{i}.SH', .6) for i in range(3)]
    result = audit(reports)['groups'][0]
    assert result['completeDays'] == 1 and result['upCandidates']['precision'] == 0
    reports[0]['closeDirection']['protocol'] = 'old'
    assert audit(reports)['groups'][0]['missing']['LEGACY_PROTOCOL'] == 1


def test_holdings_are_diagnosed_separately_including_skipped_positions():
    jobs = [{'mode': 'AFTER_CLOSE_HOLDING', 'signalDate': '2026-09-21', 'instrumentCode': f'60000{i}.SH'} for i in range(2)]
    result = audit([report('600000.SH', .8, mode='AFTER_CLOSE_HOLDING', cutoff='15:00')], scans=[], holdings=jobs)['groups'][0]
    assert result['key'] == 'AFTER_CLOSE_HOLDING|15:00'
    assert result['scheduledCount'] == 2 and result['settledCount'] == 1


def selection_rows():
    rows = [{'signalDate': f'2026-09-{d:02}', 'instrumentCode': str(i), 'actualReturn': .01 if i < 12 else -.01,
             'candidateMember': True, 'candidateExpectedCount': 20} for d in range(1, 20) for i in range(20)]
    # Finds three winners, misses nine: the broad direction rule rejects it.
    probabilities = {'PRIOR': np.full(len(rows), .6),
                     'SPECIALIST': np.array([.7 if int(r['instrumentCode']) < 3 else .49 for r in rows])}
    return rows, probabilities


def test_specialist_optimizes_top_candidates_without_requiring_whole_pool_accuracy():
    rows, probabilities = selection_rows()
    chosen, detail = select_up_candidates(probabilities, rows)
    assert chosen == 'SPECIALIST'
    assert detail['scope'] == 'ACTUAL_CANDIDATE_OVERLAP'
    assert detail['candidates'][chosen]['precision'] == 1
    from finscope_market_data.overnight.direction_selection import select_direction
    assert select_direction(probabilities, rows)[0] == 'PRIOR'


def test_missing_real_cohorts_and_low_coverage_cannot_fall_back_to_research_winners():
    rows, probabilities = selection_rows()
    assert select_up_candidates(probabilities, [{**r, 'candidateMember': None} for r in rows])[0] == 'PRIOR'
    assert select_up_candidates(probabilities, [{**r, 'candidateExpectedCount': 100} for r in rows])[0] == 'PRIOR'
    probabilities['SPECIALIST'] = np.full(len(rows), .9)
    assert select_up_candidates(probabilities, rows)[0] == 'PRIOR'


def test_specialist_future_membership_and_external_rows_cannot_change_past_fit(history_rows):
    from finscope_market_data.overnight.direction_learning import fit_direction_before, covered_matured_rows
    from finscope_market_data.overnight.up_selection import POLICY
    from finscope_market_data.overnight.direction_candidates import MULTISCALE_RECIPE
    cutoff = '2026-06-01T14:30:00'
    rows = [{**r, 'candidateMember': True, 'candidateExpectedCount': 20} for r in history_rows]
    extra = {**rows[-1], 'instrumentCode': 'OUTSIDE', 'candidateOnly': True, 'actualReturn': 100}
    assert covered_matured_rows(rows + [extra], '2026-07-01')[1] == covered_matured_rows(rows, '2026-07-01')[1]
    before = fit_direction_before(rows, cutoff, selection_policy=POLICY, recipe=MULTISCALE_RECIPE)
    mutated = [{**r, 'candidateMember': False, 'actualReturn': 99} if r['exitAt'] >= cutoff else r for r in rows]
    assert fit_direction_before(mutated + [extra], cutoff, selection_policy=POLICY, recipe=MULTISCALE_RECIPE) == before


def test_external_candidates_require_original_context_and_match_live_feature_order(tmp_path):
    from test_overnight import history
    from test_overnight_context import snapshot
    from finscope_market_data.overnight.candidate_dataset import extend_candidate_rows
    bars = history(35)
    day = str(bars[-49].ended_at.date())
    code = '605058.SH'
    store = OvernightStore(tmp_path / 'extra.db')
    store.save_bars(code, bars)
    frozen = snapshot(day)
    cohorts = {(day, '14:30'): {'candidates': {code: {}}}}
    through = datetime.fromisoformat(bars[-1].ended_at.isoformat()).replace(hour=18)
    contexts = SimpleNamespace(at=lambda *args: frozen)
    rows = extend_candidate_rows([], store, contexts, cohorts, '14:30', through)
    assert len(rows) == 1 and rows[0]['candidateOnly']
    assert len(rows[0]['features']) == 68
    assert rows[0]['exitAt'] < through.isoformat()
    assert extend_candidate_rows([], store, SimpleNamespace(at=lambda *args: None), cohorts, '14:30', through) == []
    assert extend_candidate_rows(rows, store, contexts, cohorts, '14:30', through) == rows


def test_specialist_comparison_uses_whole_paired_days():
    from finscope_market_data.overnight.up_selection import PROTOCOL as UP_PROTOCOL
    reports = [report(f'60000{i}.SH', .6) for i in range(3)]
    for r in reports[:2]:
        r['closeDirection']['upSelection'] = {'protocol': UP_PROTOCOL, 'upProbability': .7}
    assert audit(reports)['groups'][0]['selectionComparison']['pairedDays'] == 0
    reports[-1]['closeDirection']['upSelection'] = {'protocol': UP_PROTOCOL, 'upProbability': .7}
    comparison = audit(reports)['groups'][0]['selectionComparison']
    assert comparison['pairedDays'] == 1 and not comparison['eligible']

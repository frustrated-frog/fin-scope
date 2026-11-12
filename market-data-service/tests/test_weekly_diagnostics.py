from copy import deepcopy
from datetime import datetime
from types import SimpleNamespace

import pytest

from finscope_market_data.overnight.up_detection import detection, summarize_detection
from finscope_market_data.overnight.weekly_diagnostics import build_weekly_diagnostics
from test_overnight_error_audit import report, scan


def test_always_down_cannot_look_like_successful_up_detection():
    rows = [{'signalDate': '2026-11-10', 'instrumentCode': str(i), 'probability': .4, 'actualUp': i < 2} for i in range(18)]
    result = summarize_detection(rows)
    assert result['metrics']['accuracy'] == result['metrics']['alwaysNotUpAccuracy'] == pytest.approx(16/18)
    assert result['metrics']['upPrecision'] is None and result['metrics']['upRecall'] == 0
    assert result['metrics']['missedUpCount'] == 2 and result['noUpDays'] == 1
    assert len(result['missedExamples']) == 2
    assert result['comparisons']['INDEX'] is None


def test_equal_date_weighting_and_no_actual_winners_remain_explicit():
    rows = [{'signalDate': '2026-11-10', 'instrumentCode': str(i), 'probability': .6, 'actualUp': True} for i in range(10)]
    rows += [{'signalDate': '2026-11-11', 'instrumentCode': 'a', 'probability': .6, 'actualUp': False}]
    assert detection(rows)['upPrecision'] == pytest.approx(.5)
    assert detection(rows[-1:])['upRecall'] is None
    for row in rows:
        row['alternatives'] = {'FRESH': .7, 'CALIBRATED': .3, 'INDEX': .6}
    del rows[0]['alternatives']['INDEX']
    result = summarize_detection(rows)
    assert result['pairedDays'] == 1
    assert result['comparisons']['PRIMARY']['sampleCount'] == 1


def test_weekly_counts_pending_missing_windows_and_original_complete_cohorts():
    scans = [scan(day) for day in ('2026-11-06', '2026-11-10', '2026-11-11', '2026-11-12')]
    reports = []
    for day, exit_day in [('2026-11-06', '2026-11-09'), ('2026-11-10', '2026-11-11'), ('2026-11-11', '2026-11-12'), ('2026-11-12', '2026-11-13')]:
        for i in range(3):
            value = report(f'60000{i}.SH', .4, id=f'{day}-{i}', signalDate=day, generatedAt=f'{day}T14:31:00')
            value['outcome']['closeDirection'].update(actualUp=True, exitAt=f'{exit_day}T15:00:00')
            reports.append(value)
    duplicate = deepcopy(reports[3])
    duplicate.update(id='duplicate', generatedAt='2026-11-10T14:32:00')
    duplicate['closeDirection']['upProbability'] = .9
    reports.append(duplicate)
    reports.remove(next(r for r in reports if r['id'] == '2026-11-11-0'))
    meta = SimpleNamespace(iter_jobs=lambda phase: iter(scans if phase == 'DISCOVER' else []), job=lambda key: None)
    result = build_weekly_diagnostics(SimpleNamespace(iter_history=lambda: iter(reports)), meta,
        SimpleNamespace(at=lambda *args: None), datetime(2026, 11, 12, 18))
    assert result['periodStart'] == '2026-11-09'
    assert result['recordCount'] == 8 and result['pendingCount'] == 3
    assert len(result['windows']) == 8 and all(r['status'] == 'NO_RECORD' for r in result['windows'])
    group = result['groups'][0]
    assert group['scheduledCount'] == 6 and group['settledCount'] == 5
    assert group['missing'] == {'MISSING_PREDICTION': 1}
    assert group['upDetection']['metrics']['sampleCount'] == 3  # Incomplete date cannot improve a score.
    assert group['upDetection']['metrics']['missedUpCount'] == 3
    assert group['upDetection']['metrics']['upPrecision'] is None


def test_unavailable_probability_does_not_crash_or_inflate_diagnostics():
    from test_overnight_error_audit import audit
    values = [report(f'60000{i}.SH', .6) for i in range(3)]
    values[0]['closeDirection']['upProbability'] = float('nan')
    result = audit(values)['groups'][0]
    assert result['missing'] == {'MISSING_MODEL': 1}
    assert result['upDetection']['metrics'] is None

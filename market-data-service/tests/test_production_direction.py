from collections import Counter
from copy import deepcopy
from datetime import date, datetime, timedelta

import pytest

from finscope_market_data.overnight.production_direction import (
    qualify, summarize_production, activate_direction, PROTOCOL, TRAINING_POLICY, CANDIDATE_PROTOCOL, TARGET,
)
from finscope_market_data.forecast.trading_calendar import next_session


class Meta:
    def __init__(self):
        self.values = {}

    def get(self, key):
        return self.values.get(key)

    def put_once(self, key, value):
        return self.values.setdefault(key, deepcopy(value))


def group(days=60, inverse=False):
    day = date(2026, 4, 1)
    rows, planned = [], []
    for _ in range(days):
        planned.append(str(day))
        for i in range(8):
            up = i < 4
            rows.append({'signalDate': str(day), 'instrumentCode': str(i), 'actualUp': up,
                'probability': .9 if up != inverse else .1, 'live': .5, 'baselineProbability': .5,
                'artifactId': 'model', 'momentumScore': -int(up)})
        day = next_session(day)
    return {'rows': rows, 'plannedDates': planned, 'scheduled': len(rows), 'paired': len(rows), 'missing': Counter()}


@pytest.mark.parametrize('name', ['ROLLING', 'ADAPTIVE'])
def test_requires_independent_days_then_qualifies_and_falls_back_on_degradation(name):
    meta = Meta()
    key = ('TAIL_ENTRY', '14:30', name)
    now = datetime(2026, 11, 12, 19)
    assert qualify(key, group(59), meta, now)['status'] == 'ACCUMULATING'
    passed = qualify(key, group(), meta, now)
    assert passed['status'] == 'QUALIFIED' and passed['eligible']
    original = deepcopy(passed['checkpoint'])
    degraded = group(80)
    for row in degraded['rows'][-20*8:]:
        row['probability'] = 1-row['probability']
    failed = qualify(key, degraded, meta, now)
    assert failed['status'] == 'MONITORING_DEGRADED' and not failed['eligible']
    assert failed['checkpoint'] == original
    sparse = group(80)
    sparse['rows'] = sparse['rows'][:-8*3]
    assert qualify(key, sparse, meta, now)['status'] == 'MONITORING_DEGRADED'


def test_failed_checkpoint_cannot_be_replaced_with_a_later_favorable_window():
    meta = Meta()
    key = ('AFTER_CLOSE_HOLDING', '15:00', 'ADAPTIVE')
    now = datetime(2026, 11, 12, 19)
    first = qualify(key, group(inverse=True), meta, now)
    assert first['status'] == 'CHECKPOINT_FAILED'
    assert qualify(key, group(100), meta, now)['status'] == 'CHECKPOINT_FAILED'


def test_missing_planned_days_cannot_disappear_from_checkpoint_coverage():
    values = group(80)
    values['rows'] = values['rows'][20*8:]
    result = qualify(('TAIL_ENTRY', '14:30', 'ROLLING'), values, Meta(), datetime(2026, 11, 12))
    assert result['checkpoint']['coverage'] == .75 and not result['eligible']


def activation():
    forecast = {'group': 'ROLLING', 'protocol': CANDIDATE_PROTOCOL, 'trainingPolicy': TRAINING_POLICY,
        'status': 'SHADOW', 'assessmentStatus': 'MODEL_ESTIMATE', 'selectedModel': 'SHAPE_LOGISTIC',
        'upProbability': .8, 'rawUpProbability': .8, 'trainedAt': '2026-11-11T19:00:00',
        'modelFitThrough': '2026-11-11T15:00:00', 'artifactId': 'new',
        'referenceAt': '2026-11-12T14:30:00', 'referencePrice': 10}
    report = {'mode': 'TAIL_ENTRY', 'cutoff': '14:30', 'evidenceKind': 'FORWARD',
        'dataThrough': '2026-11-12T14:30:00', 'jointResearch': {'cohort': 'AUTOMATIC'},
        'closeDirection': {'protocol': 'original', 'target': TARGET, 'upProbability': .4, 'referencePrice': 10,
                           'rollingSelectionModels': [forecast]}}
    gate = {'key': 'test', 'group': 'ROLLING', 'mode': 'TAIL_ENTRY', 'cutoff': '14:30', 'eligible': True,
            'status': 'QUALIFIED', 'outcomeThrough': '2026-11-11T15:00:00', 'dayCount': 60}
    monitor = {'protocol': PROTOCOL, 'target': TARGET, 'trainingPolicy': TRAINING_POLICY,
               'computedAt': '2026-11-12T09:00:00', 'groups': [gate]}
    return report, monitor


def test_live_activation_preserves_original_and_uses_frozen_past_gate():
    report, monitor = activation()
    activate_direction(report, monitor)
    main = report['closeDirection']
    assert main['upProbability'] == .8 and main['validated']
    assert main['incumbentPrediction']['upProbability'] == .4
    assert main['protocol'] == 'original' and main['activeModelProtocol'] == CANDIDATE_PROTOCOL
    assert main['activeSource'] == 'ROLLING_DIRECTION'


@pytest.mark.parametrize('scenario', ['future', 'expired', 'degraded', 'prior', 'wrong_policy', 'reference', 'retrospective'])
def test_future_stale_degraded_or_unusable_forecasts_keep_original(scenario):
    report, monitor = activation()
    forecast = report['closeDirection']['rollingSelectionModels'][0]
    if scenario == 'future':
        monitor['computedAt'] = '2026-11-12T15:00:00'
    elif scenario == 'expired':
        monitor['computedAt'] = '2026-11-10T15:00:00'
    elif scenario == 'degraded':
        monitor['groups'][0].update(status='MONITORING_DEGRADED', eligible=False)
    elif scenario == 'prior':
        forecast['selectedModel'] = 'PRIOR'
    elif scenario == 'wrong_policy':
        forecast['trainingPolicy'] = 'old'
    elif scenario == 'reference':
        forecast['referencePrice'] = 11
    else:
        report['evidenceKind'] = 'RETROSPECTIVE'
    activate_direction(report, monitor)
    assert report['closeDirection']['upProbability'] == .4


def test_production_statistics_start_with_new_policy_and_validate_actual_forecasts():
    from types import SimpleNamespace
    from test_candidate_validation import values
    from test_overnight_error_audit import scan
    rows = values()
    meta = Meta()
    meta.iter_jobs = lambda phase: [scan()] if phase == 'DISCOVER' else []
    store = SimpleNamespace(iter_history=lambda: iter(rows))
    now = datetime(2026, 9, 23)
    assert summarize_production(store, meta, now)['groups'] == []
    for row in rows:
        row['closeDirection']['rollingSelectionModels'][0].update(trainingPolicy=TRAINING_POLICY, baselineProbability=.5)
    result = summarize_production(store, meta, now)['groups'][0]
    assert result['dayCount'] == 1 and not result['eligible']
    rows[0]['closeDirection']['rollingSelectionModels'][0]['modelFitThrough'] = '2099-01-01'
    result = summarize_production(store, meta, now)['groups'][0]
    assert result['dayCount'] == 0 and result['scheduledCount'] == 3

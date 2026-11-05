import gzip
import importlib.util
import json
from pathlib import Path

import pytest

spec = importlib.util.spec_from_file_location('selection_audit', Path(__file__).parents[1] / 'scripts/audit_overnight_selection.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def test_rank_only_does_not_drop_below_threshold_days_and_ties_are_unbiased():
    rows = [{'signalDate': day, 'instrumentCode': str(i), 'actualReturn': .01 if i == 0 else -.01,
             'probability': p, 'raw': p} for day, p in [('2026-01-01', .4), ('2026-01-02', .6)] for i in range(4)]
    result = audit.scores(rows)
    assert result['rankingOnly']['selectedDays'] == 2
    assert result['thresholdSelection']['selectedDays'] == 1
    assert result['constantProbabilityDays'] == 2
    assert result['rankingOnly']['precision'] == .25
    assert result == audit.scores(list(reversed(rows)))


def test_duplicate_stock_dates_cannot_silently_overwrite():
    with pytest.raises(ValueError, match='Duplicate'):
        audit.keyed([{'signalDate': '2026-01-01', 'instrumentCode': '600000.SH'}] * 2)


def test_replay_fails_closed_if_stored_metric_cannot_be_reproduced(tmp_path, monkeypatch):
    path = tmp_path / 'frozen.json.gz'
    path.write_bytes(gzip.compress(json.dumps({'artifact': {'profile': {'cutoff': '14:30'},
        'closeDirection': {'audit': {'historical': {'accuracy': .6, 'brierScore': .2}}}},
        'panel': {'directionRows': []}, 'trainingStartedAt': '2026-11-05T21:00:00'}).encode()))
    monkeypatch.setattr(audit, 'fit_direction', lambda *args, **kwargs: {'audit': {'historical': {'accuracy': .5}}})
    with pytest.raises(ValueError, match='Frozen replay mismatch'):
        audit.replay(path)

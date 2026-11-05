import gzip
import importlib.util
import json
from pathlib import Path

import numpy as np
import pytest

spec = importlib.util.spec_from_file_location('selection_errors', Path(__file__).parents[1] / 'scripts/analyze_overnight_selection_errors.py')
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def test_fractional_rank_bands_conserve_mass_without_stock_order_bias():
    p = np.array([.8, .6, .6, .6, .2, .2, .2])
    weights = [audit.rank_weights(p, len(p)*i/5, len(p)*(i+1)/5) for i in range(5)]
    assert np.sum(weights, axis=0) == pytest.approx(np.ones(len(p)))
    assert [w.sum() for w in weights] == pytest.approx([len(p)/5]*5)
    for w in weights:
        assert w[1] == w[2] == w[3]
    top = audit.rank_weights(p, 0, 3)
    assert top == pytest.approx([1, 2/3, 2/3, 2/3, 0, 0, 0])


def test_constant_probabilities_do_not_invent_ranking_ability():
    rows = [{'signalDate': '2026-01-01', 'probability': .5, 'actualReturn': .01 if i < 2 else -.01} for i in range(10)]
    for i in range(5):
        result = audit.summarize(rows, audit.rank_weights([.5]*10, i*2, (i+1)*2))
        assert result['upRate'] == pytest.approx(.2)


def test_date_balance_and_empty_groups_are_explicit():
    rows = [{'signalDate': '2026-01-01', 'probability': .6, 'actualReturn': .01}]*10
    rows += [{'signalDate': '2026-01-02', 'probability': .6, 'actualReturn': -.01}]
    assert audit.summarize(rows, [1.]*11)['upRate'] == .5
    assert audit.summarize(rows, [0.]*11)['upRate'] is None
    assert audit.summarize(rows, [0.]*11)['days'] == 0


def test_mismatched_archived_labels_are_rejected(tmp_path):
    row = {'signalDate': '2026-01-01', 'instrumentCode': '600000.SH', 'actualReturn': .01, 'probability': .6, 'exitAt': '2026-01-02T15:00:00'}
    path = tmp_path / 'bad.json.gz'
    path.write_bytes(gzip.compress(json.dumps({'protocol': 'frozen-selection-checks-v1',
        'checks': {'CONTEXT': [row], 'INCUMBENT': [{**row, 'actualReturn': -.01}]}}).encode()))
    with pytest.raises(ValueError, match='differ'):
        audit.analyze(path)

from dataclasses import replace
from datetime import date, timedelta
import numpy as np
import pytest
from finscope_market_data.forecast.features import ForecastSample
from finscope_market_data.forecast.joint_dataset import JointDataset, JointRow
from finscope_market_data.forecast.joint_training import temporal_split, ranking_labels, train_joint_snapshot


def dataset(days=330, stocks=8):
    rng = np.random.default_rng(42)
    rows = []
    for index in range(days):
        day = date(2024, 1, 1) + timedelta(days=index)
        for code in range(stocks):
            x = rng.normal(size=3)
            value = .02 * x[0] + .001 * rng.normal()
            rows.append(JointRow(str(code), ForecastSample(day.isoformat(), day.isoformat(),
                (day + timedelta(days=1)).isoformat(), tuple(x), value)))
    return JointDataset((day + timedelta(days=1)).isoformat(), tuple(rows),
        {str(c): (.5, 0., 0.) for c in range(stocks)}, {str(c): str(c) for c in range(stocks)}, ('SIGNAL', 'MOMENTUM', 'NOISE'))


def test_split_purges_every_boundary_and_reserves_sixty_test_dates():
    split = temporal_split(dataset().rows)
    assert len({r.sample.signal_date for r in split['test']}) == 60
    for left, right in zip(('train', 'selection', 'calibration'), ('selection', 'calibration', 'test')):
        assert max(r.sample.exit_date for r in split[left]) < min(r.sample.signal_date for r in split[right])
    with pytest.raises(ValueError):
        temporal_split(dataset(days=200).rows)


def test_rank_groups_are_same_date_and_ties_get_same_relevance():
    rows = dataset(days=2).rows
    labels, groups = ranking_labels(rows)
    assert groups == [8, 8]
    assert len(labels) == sum(groups)
    assert min(labels) >= 0 and max(labels) <= 4
    ties = tuple(replace(r, sample=replace(r.sample, net_return=0)) for r in rows)
    assert len(set(ranking_labels(ties)[0])) == 1


def test_joint_models_learn_signal_and_keep_test_out_of_selection():
    data = dataset()
    result = train_joint_snapshot(data)
    evidence = result['evidence']
    assert evidence['pooledBrierScore'] < evidence['baselineBrierScore']
    assert evidence['rankIc'] > .5
    assert evidence['validationDayCount'] == 60
    assert result['predictions']['0']['upProbability'] > .5
    cutoff = temporal_split(data.rows)['test'][0].sample.signal_date
    changed = replace(data, rows=tuple(replace(r, sample=replace(r.sample, net_return=-r.sample.net_return))
        if r.sample.signal_date >= cutoff else r for r in data.rows))
    other = train_joint_snapshot(changed)
    assert evidence['selectedClassifier'] == other['evidence']['selectedClassifier']
    assert evidence['selectionBrierScore'] == other['evidence']['selectionBrierScore']
    assert evidence['selectionRankIc'] == other['evidence']['selectionRankIc']
    assert evidence['returnTarget'] == other['evidence']['returnTarget']
    assert evidence['selectionReturnMse'] == other['evidence']['selectionReturnMse']
    assert not other['evidence']['rankingEligible']


def test_display_pool_changes_ranking_evaluation_without_changing_probability_fit():
    data = dataset()
    full = train_joint_snapshot(data)
    narrowed = train_joint_snapshot(data, evaluation_codes={'0', '1', '2', '3', '4', '5'})
    assert narrowed['evidence']['displayUniverseCount'] == 6
    assert full['evidence']['pooledBrierScore'] == narrowed['evidence']['pooledBrierScore']
    assert full['evidence']['top5Return'] != narrowed['evidence']['top5Return']

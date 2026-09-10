import numpy as np
import pytest

from test_joint_dataset import histories
from finscope_market_data.forecast.joint_dataset import build_joint_dataset
from finscope_market_data.forecast.frozen_panel import save_frozen_panel, load_frozen_panel


def test_freeze_round_trip_keeps_unlabelled_observable_rows(tmp_path):
    source = histories()
    source['000001'].pop(90)
    data = build_joint_dataset(source, as_of=source['000002'][-1].trade_date, minimum_cross_section=2)
    path = tmp_path / 'inputs.npz'
    save_frozen_panel(path, data)
    restored = load_frozen_panel(path, data.as_of)
    assert restored.rows == data.rows
    assert restored.observable_rows == data.observable_rows
    assert len(restored.observable_rows) > len(restored.rows)


def test_legacy_freeze_is_explicitly_missing_observable_panel(tmp_path):
    path = tmp_path / 'old.npz'
    np.savez(path, features=[[1.]], returns=[.1], codes=['a'], signalDates=['2026-01-01'],
        exitDates=['2026-01-02'], featureCodes=['x'])
    data = load_frozen_panel(path, '2026-01-02')
    assert not data.observable_rows
    with pytest.raises(ValueError, match='可观测'):
        save_frozen_panel(tmp_path / 'new.npz', data)


def test_future_labels_are_rejected(tmp_path):
    path = tmp_path / 'old.npz'
    np.savez(path, features=[[1.]], returns=[.1], codes=['a'], signalDates=['2026-01-01'],
        exitDates=['2026-01-03'], featureCodes=['x'])
    with pytest.raises(ValueError, match='截止'):
        load_frozen_panel(path, '2026-01-02')

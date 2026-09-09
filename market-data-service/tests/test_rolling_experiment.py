import importlib.util
from pathlib import Path

import numpy as np
import pytest


def test_frozen_feature_inputs_replay_without_mutable_market_cache(monkeypatch, tmp_path):
    scripts = Path(__file__).parents[1] / 'scripts'
    monkeypatch.syspath_prepend(str(scripts))
    spec = importlib.util.spec_from_file_location('rolling_experiment', scripts / 'evaluate_rolling_direction.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    path = tmp_path / 'frozen.npz'
    np.savez_compressed(path, features=np.array([[.1, .2], [.3, .4]]), returns=np.array([.01, -.01]),
        codes=np.array(['000001', '000002']), signalDates=np.array(['2026-09-07'] * 2),
        exitDates=np.array(['2026-09-08'] * 2), featureCodes=np.array(['A', 'B']))
    data = module.load_frozen_inputs(path, '2026-09-08')
    assert data.rows[1].sample.features == (.3, .4)
    assert not data.rows[1].sample.positive
    assert data.feature_codes == ('A', 'B')
    with pytest.raises(ValueError, match='截止'):
        module.load_frozen_inputs(path, '2026-09-07')

from copy import deepcopy
import gzip
import importlib.util
import json
from pathlib import Path

import pytest

spec = importlib.util.spec_from_file_location('candidate_replay', Path(__file__).parents[1] / 'scripts/audit_candidate_rolling.py')
replay_script = importlib.util.module_from_spec(spec)
spec.loader.exec_module(replay_script)


def evidence():
    value = {'protocol': replay_script.SNAPSHOT_PROTOCOL, 'sourceArtifact': 'original', 'sourceCreatedAt': '2026-11-13T19:00:00',
        'through': '2026-11-14T00:58:00', 'cutoff': '14:30', 'readiness': {'days': []},
        'actionVerification': {}, 'previousSelection': {}, 'rows': [], 'cohorts': []}
    return {**value, 'inputFingerprint': replay_script.fingerprint(value)}


def test_offline_snapshot_uses_the_exact_frozen_inputs_without_a_database(monkeypatch):
    calls = []
    def fit(*args):
        calls.append(deepcopy(args))
        return {'audit': {'result': 'fixed'}}
    monkeypatch.setattr(replay_script, 'fit_rolling_candidates', fit)
    snapshot = json.loads(gzip.decompress(gzip.compress(json.dumps(evidence()).encode())))
    first = replay_script.replay(snapshot)
    assert first == replay_script.replay(snapshot)
    assert calls == [([], {}, {'days': []}, '2026-11-14T00:58:00', '14:30')]*2
    assert first['inputFingerprint'] == snapshot['inputFingerprint']


def test_changed_snapshot_cannot_keep_the_original_fingerprint():
    snapshot = evidence()
    snapshot['through'] = '2026-11-15T00:58:00'
    with pytest.raises(ValueError, match='指纹无效'):
        replay_script.replay(snapshot)

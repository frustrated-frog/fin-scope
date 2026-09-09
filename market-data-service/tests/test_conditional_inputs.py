import gzip
import importlib.util
import json
from pathlib import Path

import pytest
from test_joint_training import dataset


def test_conditional_input_rejects_mismatched_oof_target(monkeypatch, tmp_path):
    scripts = Path(__file__).parents[1] / 'scripts'
    monkeypatch.syspath_prepend(str(scripts))
    spec = importlib.util.spec_from_file_location('conditional_cli', scripts / 'evaluate_conditional_direction.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    rows = dataset(days=2, stocks=2).rows
    records = [dict(code=r.code, signalDate=r.sample.signal_date, exitDate=r.sample.exit_date,
                    returnValue=float(r.sample.net_return), positive=bool(r.sample.positive),
                    probabilities={'BASE_TREE:RAW': .5}, prior=.5) for r in rows]
    path = tmp_path / 'oof.gz'

    def write():
        with gzip.open(path, 'wt') as stream:
            stream.write('\n'.join(json.dumps(r) for r in records))

    write()
    start, base, _ = module.load_base_oof(path, rows)
    assert start == rows[0].sample.signal_date
    assert len(base) == len(rows)
    records[-1]['returnValue'] += .1
    write()
    with pytest.raises(ValueError, match='不一致'):
        module.load_base_oof(path, rows)

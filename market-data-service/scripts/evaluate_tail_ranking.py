"""Offline paired objective experiment on an immutable overnight artifact export."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import platform
import sys

import lightgbm
import numpy
import sklearn

from finscope_market_data.overnight.context_research import MODEL_FEATURES
from finscope_market_data.overnight.direction_dataset import TARGET
from finscope_market_data.overnight.tail_ranking import fit_tail_ranking, PROTOCOL


def evaluate(path):
    raw = path.read_bytes()
    frozen = json.loads(gzip.decompress(raw))
    artifact = frozen['artifact']
    context = artifact.get('contextDirection') or {}
    if context.get('target') != TARGET or context.get('features') != list(MODEL_FEATURES):
        raise ValueError('冻结模型的目标或特征协议不兼容')
    cutoff = artifact['profile']['cutoff']
    fitted = fit_tail_ranking(frozen['panel']['contextRows'], frozen['trainingStartedAt'], cutoff, include_checks=True)
    return {'inputSha256': hashlib.sha256(raw).hexdigest(), 'artifactId': artifact['id'], 'cutoff': cutoff,
            'status': 'COMPLETE' if fitted else 'INSUFFICIENT_DATA',
            'experiment': {k: v for k, v in fitted.items() if k != 'model'} if fitted else None}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--inputs', type=Path, nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as output:
        result = {'protocol': PROTOCOL, 'status': 'RUNNING', 'eligible': False,
            'environment': {'python': sys.version, 'platform': platform.platform(), 'numpy': numpy.__version__,
                            'lightgbm': lightgbm.__version__, 'sklearn': sklearn.__version__}, 'results': []}
        output.write(json.dumps(result))
        output.flush()
        try:
            for path in args.inputs:
                result['results'].append(evaluate(path))
                print(path.name, result['results'][-1]['status'], flush=True)
            result['status'] = 'COMPLETE'
        except Exception as error:
            result.update(status='FAILED', errorType=type(error).__name__)
            raise
        finally:
            output.seek(0)
            output.write(json.dumps(result, ensure_ascii=False, allow_nan=False))
            output.truncate()


if __name__ == '__main__':
    main()

"""Reproducible freshness/index ablation on frozen inputs, never a live forecast."""
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
from threadpoolctl import threadpool_limits

from finscope_market_data.overnight.direction_dataset import FEATURES, TARGET
from finscope_market_data.overnight.fresh_research import PROTOCOL, train_fresh_research


@threadpool_limits.wrap(limits=1)
def evaluate(path, history):
    raw = path.read_bytes()
    frozen = json.loads(gzip.decompress(raw))
    artifact = frozen['artifact']
    direction = artifact.get('closeDirection') or {}
    if direction.get('target') != TARGET or direction.get('features') != list(FEATURES):
        raise ValueError('冻结方向模型的目标或特征不兼容')
    cutoff = artifact['profile']['cutoff']
    result = train_fresh_research(frozen['panel']['directionRows'], history, frozen['trainingStartedAt'], cutoff)
    for value in result['models'].values():
        value.pop('model')
    return {'inputSha256': hashlib.sha256(raw).hexdigest(), 'artifactId': artifact['id'],
            'cutoff': cutoff, 'through': frozen['trainingStartedAt'], 'experiment': result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', type=Path, nargs='+', required=True)
    parser.add_argument('--index-history', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    history_bytes = args.index_history.read_bytes()
    history = json.loads(history_bytes)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as output:
        result = {'protocol': PROTOCOL, 'status': 'RUNNING', 'eligible': False,
            'evidenceKind': 'RETROSPECTIVE', 'indexHistorySha256': hashlib.sha256(history_bytes).hexdigest(),
            'limitations': ['开发区间反复使用，不能视作独立验收', '指数日线为后续采集的历史数据，非当时收到的盘中快照',
                            '研究池有覆盖和幸存偏差；未复权价格的公司行为核验仍不完整'],
            'environment': {'python': sys.version, 'platform': platform.platform(), 'numpy': numpy.__version__,
                            'lightgbm': lightgbm.__version__, 'sklearn': sklearn.__version__}, 'results': []}
        try:
            for path in args.inputs:
                result['results'].append(evaluate(path, history))
                print(path.name, result['results'][-1]['experiment']['status'], flush=True)
            result['status'] = 'COMPLETE'
        except Exception as error:
            result.update(status='FAILED', errorType=type(error).__name__)
            raise
        finally:
            output.write(json.dumps(result, ensure_ascii=False, allow_nan=False))


if __name__ == '__main__':
    main()

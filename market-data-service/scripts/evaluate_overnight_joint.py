"""Replay a predeclared minute cohort; produces an audit, never promotes a model.

Inputs: protocol.json (codes, through) and <code>.json.gz arrays of MinuteBar.
Run with PYTHONPATH=src python scripts/evaluate_overnight_joint.py --input-dir DIR --output-dir DIR.
"""
import argparse
from datetime import datetime
import gzip
import hashlib
import json
from pathlib import Path

from finscope_market_data.overnight.engine import TARGETS
from finscope_market_data.overnight.joint_dataset import PROTOCOL, build_panel, profiles
from finscope_market_data.overnight.joint_learning import fit_target
from finscope_market_data.overnight.models import MinuteBar


class FrozenMinutes:
    def __init__(self, root):
        self.root = root

    def bars(self, code, through):
        path = self.root / f'{code}.json.gz'
        if not path.exists():
            return []
        return [bar for value in json.loads(gzip.decompress(path.read_bytes()))
                if (bar := MinuteBar.model_validate(value)).ended_at <= through]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    protocol = json.loads((args.input_dir / 'protocol.json').read_text())
    through = datetime.fromisoformat(protocol['through'])
    manifest = {code: hashlib.sha256(gzip.decompress((args.input_dir / f'{code}.json.gz').read_bytes())).hexdigest()
                if (args.input_dir / f'{code}.json.gz').exists() else None for code in protocol['codes']}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    reports = []
    for profile in profiles({'tailCostBps': 20, 'holdingCostBps': 10}):
        rows, _, audit = build_panel(FrozenMinutes(args.input_dir), protocol['codes'], profile, through)
        targets = []
        for target in TARGETS:
            fitted = fit_target([row for row in rows if row['target'] == target], through.isoformat(), profile.cutoff)
            targets.append({'target': target, 'status': 'FITTED' if fitted else 'INSUFFICIENT_DATA',
                            'audit': fitted['audit'] if fitted else None})
        result = {'profile': profile.dump(), 'data': audit, 'targets': targets}
        reports.append(result)
        print(json.dumps({'profile': profile.key, 'rows': len(rows), 'targets': [row['status'] for row in targets]}), flush=True)
    output = {'protocol': PROTOCOL, 'inputProtocol': protocol, 'inputs': manifest, 'results': reports,
              'evidenceKind': 'HISTORICAL_DEVELOPMENT_AUDIT',
              'limitations': ['当前缓存股票池，有幸存与来源可用性偏差；失败成员保留缺口。',
                             '同一历史市场时期曾用于其他模型开发；不能等同于真实前瞻成绩。',
                             '未运行 60 日前瞻验收，不自动采用新模型；未验证实际成交与全部公司行为。']}
    (args.output_dir / 'audit.json').write_text(json.dumps(output, ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()

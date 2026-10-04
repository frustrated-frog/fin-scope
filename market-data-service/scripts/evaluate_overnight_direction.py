"""Frozen next-close direction experiment. Never select a recipe from this audit."""
import argparse
from datetime import datetime
import json
from pathlib import Path

from evaluate_overnight_joint import FrozenMinutes
from finscope_market_data.overnight.direction_dataset import PROTOCOL, build_direction_panel
from finscope_market_data.overnight.direction_learning import fit_direction
from finscope_market_data.overnight.joint_learning import fit_target


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--compare-legacy', action='store_true')
    args = parser.parse_args()
    inputs = json.loads((args.input_dir / 'protocol.json').read_text())
    through = datetime.fromisoformat(inputs['through'])
    args.output_dir.mkdir(parents=True, exist_ok=True)
    results = []
    for cutoff in ('14:30', '14:45', '15:00'):
        rows, data = build_direction_panel(FrozenMinutes(args.input_dir), inputs['codes'], cutoff, through)
        fitted = fit_direction(rows, through.isoformat(), cutoff)
        result = {'cutoff': cutoff, 'data': data, 'audit': fitted['audit'] if fitted else None}
        if args.compare_legacy:
            # Same price labels, features and evaluation dates. This is an ablation,
            # not the incumbent net-profit forecast, whose target is different.
            legacy = fit_target([{**r, 'actualNetReturn': r['actualReturn']} for r in rows], through.isoformat(), cutoff)
            result['legacyRecipeSamePriceTarget'] = legacy['audit'] if legacy else None
        results.append(result)
        (args.output_dir / 'audit.json').write_text(json.dumps({'protocol': PROTOCOL, 'inputs': inputs,
            'evidenceKind': 'HISTORICAL_DEVELOPMENT_AUDIT', 'results': results,
            'limitations': ['开发阶段已使用过的市场区间；不能视为独立验收。',
                            '未复权收盘价；公司行为未完整核验，股票池有覆盖和幸存偏差。']},
            ensure_ascii=False, indent=2, allow_nan=False))
        print(json.dumps({'cutoff': cutoff, 'rows': len(rows), 'status': 'FITTED' if fitted else 'INSUFFICIENT_DATA'}), flush=True)


if __name__ == '__main__':
    main()

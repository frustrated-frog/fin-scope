"""Predeclare paired ablations before fitting. Development evidence only."""
import argparse
from collections import Counter
from datetime import datetime
import gzip
import hashlib
import json
from pathlib import Path

from evaluate_overnight_joint import FrozenMinutes
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.context_features import build_context_panel
from finscope_market_data.overnight.direction_learning import fit_direction
from finscope_market_data.overnight.direction_selection import POLICY, PROTOCOL


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--test-days', type=int, default=40)
    args = parser.parse_args()
    inputs = json.loads((args.input_dir / 'protocol.json').read_text())
    through = datetime.fromisoformat(inputs['through'])
    files = {code: hashlib.sha256(gzip.decompress((args.input_dir / f'{code}.json.gz').read_bytes())).hexdigest()
             if (args.input_dir / f'{code}.json.gz').exists() else None for code in inputs['codes']}
    source = Path(__file__).resolve().parent.parent / 'src' / 'finscope_market_data'
    modules = sorted((source / 'overnight').glob('*.py')) + sorted((source / 'forecast').glob('*.py'))
    source_snapshot = {str(path.relative_to(source)): path.read_text() for path in modules}
    source_snapshot['scripts/evaluate_overnight_context.py'] = Path(__file__).read_text()
    source_bytes = json.dumps(source_snapshot, sort_keys=True, separators=(',', ':')).encode()
    code_fingerprint = hashlib.sha256(source_bytes).hexdigest()
    experiment = {'protocol': PROTOCOL, 'inputProtocol': inputs, 'inputs': files, 'testDays': args.test_days,
                  'cutoffs': ['14:30', '14:45', '15:00'], 'recipes': ['INCUMBENT', 'SELECTION_ONLY', 'POOL_CONTEXT'],
                  'seed': 42, 'codeFingerprint': code_fingerprint, 'evidenceKind': 'HISTORICAL_DEVELOPMENT_AUDIT',
                  'rule': 'Same rows and dates; daily refit with purged selection and calibration. No test-time recipe tuning.'}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    protocol_path = args.output_dir / 'protocol.json'
    if protocol_path.exists():
        if json.loads(protocol_path.read_text()) != experiment:
            raise ValueError('已冻结的实验协议不能覆盖；请使用新的输出目录')
    else:
        with protocol_path.open('x') as handle:
            json.dump(experiment, handle, ensure_ascii=False, indent=2)
        (args.output_dir / 'source.json.gz').write_bytes(gzip.compress(source_bytes))
    results = []
    for cutoff in experiment['cutoffs']:
        rows, data = build_context_panel(FrozenMinutes(args.input_dir), inputs['codes'], cutoff, through)
        same_stock_rows = [{**row, 'features': row['features'][:26]} for row in rows]
        checks, audits = {}, {}
        for recipe in experiment['recipes']:
            fitted = fit_direction(rows if recipe == 'POOL_CONTEXT' else same_stock_rows, through.isoformat(), cutoff,
                selection_policy='BRIER' if recipe == 'INCUMBENT' else POLICY, test_days=args.test_days, include_checks=True)
            if fitted:
                checks[recipe] = fitted.pop('checks')
                audits[recipe] = {'selectedCounts': dict(Counter(fold['selected'] for fold in fitted['audit']['folds'])),
                                  'audit': fitted['audit']}
        if len(checks) == len(experiment['recipes']):
            reference = checks['INCUMBENT']
            identities = [(r['signalDate'], r['instrumentCode']) for r in reference]
            for recipe, values in checks.items():
                if identities != [(r['signalDate'], r['instrumentCode']) for r in values]:
                    raise ValueError('模型对照股票和日期不一致')
                comparison = evaluate_direction([r['probability'] for r in values], [r['actualReturn'] > 0 for r in values],
                    [r['signalDate'] for r in values], {'INCUMBENT': [r['probability'] for r in reference],
                    'HISTORICAL_PRIOR': [r['baseline'] for r in reference]}, family_size=6)
                comparison.update(eligible=False, reason='历史开发审计，不能自动晋级')
                audits[recipe].update(pairedComparison=comparison,
                                      evaluationSymbolCount=len({row['instrumentCode'] for row in values}))
        result = {'cutoff': cutoff, 'data': data, 'recipes': audits,
                  'status': 'COMPLETE' if len(checks) == 3 else 'INSUFFICIENT_DATA'}
        results.append(result)
        (args.output_dir / 'audit.json').write_text(json.dumps({'experiment': experiment, 'results': results,
            'limitations': ['历史开发数据与已用市场区间重叠，并非独立验收。',
                '固定缓存股票池具有来源覆盖和幸存偏差；失败成员不替换。',
                '本实验未提供当时收到的指数及行业快照，这些列明确缺失。',
                '保持未复权涨跌目标；公司行为未核验。本对照不声称真实成交收益。']}, ensure_ascii=False, indent=2, allow_nan=False))
        print(json.dumps({'cutoff': cutoff, 'status': result['status'], 'rows': data['rows'],
            'accuracy': {key: value['audit']['historical']['accuracy'] for key, value in audits.items()}}), flush=True)


if __name__ == '__main__':
    main()

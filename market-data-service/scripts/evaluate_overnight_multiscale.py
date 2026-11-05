"""Three predeclared recipes on identical frozen stock/date/price labels.

Historical development diagnostics only. This command never publishes a model
or changes live gates; test-window results must not tune the recipes.
"""
import argparse
from datetime import datetime
import json
from pathlib import Path

from evaluate_overnight_joint import FrozenMinutes
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.context_features import build_context_panel
from finscope_market_data.overnight.direction_candidates import MULTISCALE_RECIPE
from finscope_market_data.overnight.direction_dataset import FEATURES as STOCK_FEATURES
from finscope_market_data.overnight.direction_learning import fit_direction
from finscope_market_data.overnight.direction_selection import POLICY, PROTOCOL
from finscope_market_data.overnight.multiscale_features import FEATURES as HISTORY_FEATURES


def compare(rows, through, cutoff):
    recipes = {
        'INCUMBENT': ([{**r, 'features': r['features'][:len(STOCK_FEATURES)]} for r in rows], {}),
        'CONTEXT_V3': ([{**r, 'features': r['features'][:-len(HISTORY_FEATURES)]} for r in rows], {'selection_policy': POLICY}),
        'MULTISCALE_V4': (rows, {'selection_policy': POLICY, 'recipe': MULTISCALE_RECIPE}),
    }
    fits = {name: fit_direction(panel, through, cutoff, include_checks=True, **options)
            for name, (panel, options) in recipes.items()}
    if not all(fits.values()):
        return {'status': 'INSUFFICIENT_DATA'}
    identities = lambda fit: [(r['signalDate'], r['instrumentCode'], r['actualReturn'], r['exitAt']) for r in fit['checks']]
    if any(identities(fit) != identities(fits['INCUMBENT']) for fit in fits.values()):
        raise ValueError('比较方案的测试股票、日期或标签不一致')
    checks = fits['MULTISCALE_V4']['checks']
    paired = evaluate_direction([r['probability'] for r in checks], [r['actualReturn'] > 0 for r in checks],
        [r['signalDate'] for r in checks],
        {name: [r['probability'] for r in fits[name]['checks']] for name in ('INCUMBENT', 'CONTEXT_V3')}, family_size=3)
    paired.update(eligible=False, reason='相同样本的历史开发对照，不能代替新的真实前瞻验收')
    return {'status': 'EVALUATED', 'recipes': {name: fit['audit'] for name, fit in fits.items()}, 'pairedComparison': paired}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--cutoffs', nargs='+', choices=('14:30', '14:45', '15:00'), default=['14:30', '14:45', '15:00'])
    args = parser.parse_args()
    inputs = json.loads((args.input_dir / 'protocol.json').read_text())
    through = datetime.fromisoformat(inputs['through'])
    args.output_dir.mkdir(parents=True, exist_ok=True)
    results = []
    for cutoff in args.cutoffs:
        rows, data = build_context_panel(FrozenMinutes(args.input_dir), inputs['codes'], cutoff, through, include_multiscale=True)
        result = {'cutoff': cutoff, 'data': data, **compare(rows, through.isoformat(), cutoff)}
        results.append(result)
        (args.output_dir / 'audit.json').write_text(json.dumps({'protocol': PROTOCOL, 'inputs': inputs,
            'evidenceKind': 'HISTORICAL_DEVELOPMENT_AUDIT', 'results': results,
            'limitations': ['开发阶段已使用的区间，非独立验收；三方案采用相同可用股票和日期。',
                '历史环境为固定研究池重建；未补造行业与指数历史数据。',
                '未复权价格，公司行为未完整核验，股票池有覆盖和幸存偏差。']},
            ensure_ascii=False, indent=2, allow_nan=False))
        print(json.dumps({'cutoff': cutoff, 'rows': len(rows), 'status': result['status']}), flush=True)


if __name__ == '__main__':
    main()

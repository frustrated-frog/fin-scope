"""Append an outcome artifact for an existing local prediction run; never retrain."""
import argparse
import hashlib
import json
from pathlib import Path

from finscope_market_data.forecast.executable_sources import load_research_histories
from finscope_market_data.forecast.prediction_outcomes import settle_predictions
from finscope_market_data.models import DailyBar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--snapshots', type=Path, default=Path('data/market-data-snapshots.db'))
    parser.add_argument('--as-of', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('结算输出已存在，请使用新文件')
    report_bytes = (args.run/'report.json').read_bytes()
    report = json.loads(report_bytes)
    original = json.loads((args.run/'input.json').read_text())
    code = report['instrumentCode']
    rows = load_research_histories(args.snapshots, {code}, args.as_of)[code]
    bars = [DailyBar.model_validate(row) for row in rows]
    result = settle_predictions(report, original, bars, args.as_of)
    result['predictionFingerprint'] = hashlib.sha256(report_bytes).hexdigest()
    result['outcomeBars'] = [bar.model_dump(mode='json') for bar in bars if bar.trade_date >= original['asOf']]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x') as output:
        json.dump(result, output, ensure_ascii=False, indent=2, allow_nan=False)
    print(json.dumps(dict(output=str(args.output), outcomes=[row['status'] for row in result['outcomes']])))


if __name__ == '__main__':
    main()

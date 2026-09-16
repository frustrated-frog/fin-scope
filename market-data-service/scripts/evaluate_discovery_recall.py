"""Read-only outcome settlement; never retrain or rewrite the selected report."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from finscope_market_data.discovery.recall import evaluate_recall
from finscope_market_data.discovery.recall_archive import load_outcome_inputs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', required=True, type=Path)
    parser.add_argument('--snapshots', type=Path, default=Path('data/market-data-snapshots.db'))
    parser.add_argument('--as-of', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    report = json.loads(args.report.read_text())
    frozen_input = load_outcome_inputs(args.snapshots, report['as_of_date'], args.as_of)
    result = evaluate_recall(report, frozen_input['histories'], frozen_input['calendar'], args.as_of)
    encoded = json.dumps(frozen_input, sort_keys=True, ensure_ascii=False)
    result['input_fingerprint'] = hashlib.sha256(encoded.encode()).hexdigest()
    result['report_fingerprint'] = hashlib.sha256(args.report.read_bytes()).hexdigest()
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / 'signal-report.json').write_bytes(args.report.read_bytes())
    (args.output / 'outcome-input.json').write_text(encoded)
    (args.output / 'evaluation.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({key: value for key, value in result.items()
                      if key not in {'observations', 'missing_codes', 'missed_winners'}}, ensure_ascii=False))


if __name__ == '__main__':
    main()

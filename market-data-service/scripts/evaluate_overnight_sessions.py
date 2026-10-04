"""Paired, date-balanced replay against the actual overnight production model.

Reads frozen .json.gz MinuteBar lists; never fetches data or publishes forecasts.
Parameters are fixed before evaluation. Historical results do not prove fills.
"""
import argparse
from datetime import date
import gzip
import hashlib
import json
from pathlib import Path

from threadpoolctl import threadpool_limits

from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.engine import build_samples, group_bars
from finscope_market_data.overnight.learning import fit_at, MIN_SAMPLES, evaluate, select_reference, MIN_VALIDATION, VALIDATION_WINDOW
from finscope_market_data.overnight.models import MinuteBar, OvernightRequest
from finscope_market_data.overnight.session_features import session_features, FEATURE_CODES

CANDIDATE_WEIGHT = .5


def replay(code, bars, start, end):
    grouped = group_bars(bars)
    through = max(bar.ended_at for bar in bars)
    observations = []
    for mode, cutoff, cost in [('TAIL_ENTRY', '14:30', 20), ('AFTER_CLOSE_HOLDING', '15:00', 10)]:
        request = OvernightRequest(instrument_code=code, signal_date=end, mode=mode, cutoff=cutoff, cost_bps=cost,
            **({'cost_basis': 1, 'quantity': 1, 'position_opened_on': start} if mode == 'AFTER_CLOSE_HOLDING' else {}))
        data = build_samples(grouped, request, through)
        context = {day.isoformat(): session_features(grouped, day, cutoff) for day in sorted(grouped)}
        for target, rows in data.items():
            enriched = [{**row, 'features': context[row['signalDate']]} for row in rows if context[row['signalDate']] is not None]
            checks = []
            for row in rows:
                if row['signalDate'] > str(end):
                    break
                decision = f"{row['signalDate']}T{cutoff}:00"
                incumbent = fit_at(rows, decision)
                if incumbent is None:
                    continue
                probability, raw, expected, lower, upper = incumbent.predict(row['features'])
                past = [check for check in checks if check['exitAt'] < decision][-VALIDATION_WINDOW:]
                policy = select_reference(probability, expected, lower, upper, incumbent,
                    evaluate(past, incumbent.calibration.status))
                checks.append(dict(signalDate=row['signalDate'], exitAt=row['exitAt'], probability=probability,
                    rawProbability=raw, prior=incumbent.baseline, actual=row['actualNetReturn'],
                    expected=expected, lower=lower, upper=upper))
                if (row['signalDate'] < str(start) or context[row['signalDate']] is None or len(past) < MIN_VALIDATION
                        or min(sum(sample['exitAt'] < decision for sample in values) for values in (rows, enriched)) < MIN_SAMPLES):
                    continue
                candidate = fit_at(enriched, decision)
                if candidate is None:
                    continue
                enhanced = candidate.predict(context[row['signalDate']])[0]
                observations.append(dict(instrumentCode=code, mode=mode, target=target,
                    signalDate=row['signalDate'], targetAt=row['exitAt'], actualNetReturn=row['actualNetReturn'],
                    probability=(1 - CANDIDATE_WEIGHT) * probability + CANDIDATE_WEIGHT * enhanced,
                    incumbentProbability=probability, candidateProbability=enhanced, prior=incumbent.baseline,
                    policyProbability=policy['upProbability'], probabilitySource=policy['probabilitySource'],
                    policyValidationCount=len(past), policyValidationThrough=past[-1]['exitAt'],
                    trainingThrough=candidate.audit['trainingThrough'],
                    calibrationStart=candidate.audit['calibrationStart'],
                    calibrationThrough=candidate.audit['calibrationThrough']))
    return observations


def summarize(observations):
    result = []
    for mode, target in sorted({(row['mode'], row['target']) for row in observations}):
        rows = [row for row in observations if (row['mode'], row['target']) == (mode, target)]
        evaluation = evaluate_direction([r['probability'] for r in rows],
            [int(r['actualNetReturn'] > 0) for r in rows], [r['signalDate'] for r in rows],
            {'INCUMBENT': [r['incumbentProbability'] for r in rows], 'PRIOR': [r['prior'] for r in rows],
             'NOT_PROFITABLE': [.49] * len(rows)})
        evaluation['task'] = 'OVERNIGHT_NET_DIRECTION'
        policy = evaluate_direction([r['policyProbability'] for r in rows],
            [int(r['actualNetReturn'] > 0) for r in rows], [r['signalDate'] for r in rows],
            {'INCUMBENT': [r['incumbentProbability'] for r in rows], 'PRIOR': [r['prior'] for r in rows],
             'NOT_PROFITABLE': [.49] * len(rows)})
        policy['task'] = 'OVERNIGHT_NET_DIRECTION'
        policy['modelReferenceCount'] = sum(r['probabilitySource'] == 'CALIBRATED_MODEL' for r in rows)
        result.append(dict(mode=mode, target=target, **evaluation, evidencePolicy=policy))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--start', type=date.fromisoformat, required=True)
    parser.add_argument('--end', type=date.fromisoformat, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or args.start > args.end:
        parser.error('输出必须是新目录，日期范围必须有效')
    paths = sorted(args.input_dir.glob('*.json.gz'))
    if not paths:
        parser.error('输入目录没有冻结的分钟数据')
    args.output.mkdir(parents=True)
    fingerprints = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}
    protocol = dict(incumbentVersion='overnight-local-v3-calibrated', candidate='SESSION_FEATURE_BLEND_RESEARCH_V1',
        evidencePolicyVersion='overnight-local-v4-evidence-gated',
        candidateWeight=CANDIDATE_WEIGHT, features=FEATURE_CODES, fingerprints=fingerprints,
        start=str(args.start), end=str(args.end), evidenceKind='RETROSPECTIVE',
        limitations=['当前缓存股票池含选择与幸存者偏差', '未复权跨日收益未全面核验公司行为',
                     '价格代理不证明可成交', '区间按日期分块，不能把同日多只股票当作独立交易日',
                     '多个退出目标仍有多重比较风险；不根据本次成绩自动切换生产模型'])
    (args.output / 'protocol.json').write_text(json.dumps(protocol, ensure_ascii=False, indent=2))
    observations = []
    with threadpool_limits(limits=1):
        for path in paths:
            bars = [MinuteBar.model_validate(row) for row in json.loads(gzip.decompress(path.read_bytes()))]
            rows = replay(path.name.removesuffix('.json.gz'), bars, args.start, args.end)
            observations.extend(rows)
            print(json.dumps({'code': path.stem, 'observations': len(rows)}), flush=True)
    report = dict(protocol=protocol, groups=summarize(observations))
    (args.output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))
    with gzip.open(args.output / 'observations.jsonl.gz', 'wt') as handle:
        for row in observations:
            handle.write(json.dumps(row, allow_nan=False) + '\n')


if __name__ == '__main__':
    main()

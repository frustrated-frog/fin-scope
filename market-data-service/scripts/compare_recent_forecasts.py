"""Read-only retrospective comparison against frozen forecasts; never a forward test.

Run from market-data-service with PYTHONPATH=src .venv/bin/python scripts/compare_recent_forecasts.py
  --database /absolute/finance.db --snapshots /absolute/market-data-snapshots.db --output /tmp/comparison.json
"""
import argparse
from collections import Counter
from datetime import datetime
import json
import hashlib
from pathlib import Path
import sqlite3
import statistics

from threadpoolctl import threadpool_limits

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.context import build_aligned_context
from finscope_market_data.forecast.features import build_samples, current_features
from finscope_market_data.forecast.production_fit import fit_production_model, evaluate_recent_candidate
from finscope_market_data.forecast.qualification import qualify_model
from finscope_market_data.forecast.return_distribution import forecast_return_distribution


def compare(database, snapshots):
    db = sqlite3.connect(f'file:{Path(database).resolve()}?mode=ro', uri=True)
    cache = sqlite3.connect(f'file:{Path(snapshots).resolve()}?mode=ro', uri=True)
    db.execute("BEGIN")
    cache.execute("BEGIN")
    histories = {}

    def history(code):
        if code not in histories:
            number, market = code.split('.')
            row = cache.execute('SELECT payload_json FROM market_data_snapshot WHERE capability=? AND symbol_key=?',
                                ('DAILY_BARS', f'{market}:{number}')).fetchone()
            histories[code] = sorted([DailyBar.model_validate(v) for v in json.loads(row[0])['data']],
                                     key=lambda v: v.trade_date) if row else []
        return histories[code]

    records = []
    for run_id, created, payload in db.execute('SELECT id, completed_at, report_json FROM stock_discovery_run WHERE status="SUCCEEDED"'):
        for deep in json.loads(payload).get('deep_evidence', []):
            records.append(('discovery', run_id, created, deep['forecast_report']))
    for run_id, created, payload in db.execute('SELECT id, created_at, report_json FROM single_stock_forecast_run'):
        records.append(('single', run_id, created, json.loads(payload)))
    seen, results, excluded = set(), [], Counter()
    for kind, run_id, created, report in sorted(records, key=lambda row: row[2]):
        code, day, horizon = report['instrumentCode'], report['asOfDate'], report.get('horizonDays', 5)
        key = (kind, code, day, horizon)
        if key in seen:
            continue
        seen.add(key)
        data = history(code)
        before = [bar for bar in data if bar.trade_date <= day]
        after = [bar for bar in data if bar.trade_date > day]
        if not before or before[-1].trade_date != day or len(after) <= horizon or report.get('upProbability') is None:
            excluded['not_matured_or_missing'] += 1
            continue
        if any(bar.adjustment != 'QFQ' for bar in data):
            excluded['unknown_adjustment'] += 1
            continue
        if created < day + 'T15:00:00' or created >= after[0].trade_date + 'T09:30:00':
            excluded['not_ex_ante'] += 1
            continue
        if report.get('productionModel', {}).get('applied'):
            excluded['already_recent_model'] += 1
            continue
        context = build_aligned_context(before, market_bars=[bar for bar in history('000300.SH') if bar.trade_date <= day])
        cost = report.get('strategyPolicy', {}).get('roundTripCostRate', .0015)
        samples = build_samples(before, cost, horizon, context)
        features = current_features(before, context)
        try:
            serving = fit_production_model(samples, cutoff=day, horizon_days=horizon,
                                           model_code=report['modelCompetition']['selectedModel'])
            distribution = forecast_return_distribution(samples, current_features=features, horizon_days=horizon, cutoff=day)
        except (KeyError, ValueError) as error:
            reason = f'{type(error).__name__}: {error}'
            excluded[reason] += 1
            actual = after[horizon].open / after[0].open - 1 - cost
            old = report['upProbability']
            results.append(dict(kind=kind, id=run_id, code=code, day=day, horizon=horizon, actual=actual,
                oldProbability=old, newProbability=old, oldCorrect=(old >= .5) == (actual > 0),
                newCorrect=(old >= .5) == (actual > 0), oldMedian=report.get('expectedNetReturn'),
                newMedian=report.get('expectedNetReturn'), oldLower=report.get('lowerNetReturn'),
                oldUpper=report.get('upperNetReturn'), newLower=report.get('lowerNetReturn'),
                newUpper=report.get('upperNetReturn'), fallbackReason=reason))
            continue
        actual = after[horizon].open / after[0].open - 1 - cost
        baseline = qualify_model(samples, independent_stride_days=horizon, model_code=report['modelCompetition']['selectedModel'])
        gate = evaluate_recent_candidate(samples, cutoff=day, horizon_days=horizon,
            model_code=report['modelCompetition']['selectedModel'], baseline=baseline)
        probability = report['upProbability']
        gated_probability = serving.predict(features)[1] if gate['passed'] else probability
        old = report['upProbability']
        result = dict(kind=kind, id=run_id, code=code, day=day, horizon=horizon, actual=actual,
                      oldProbability=old, newProbability=probability, gatedCandidateProbability=gated_probability, oldCorrect=(old >= .5) == (actual > 0),
                      newCorrect=(probability >= .5) == (actual > 0),
                      oldMedian=report.get('expectedNetReturn'), newMedian=distribution.p50,
                      oldLower=report.get('lowerNetReturn'), oldUpper=report.get('upperNetReturn'),
                      newLower=distribution.p10, newUpper=distribution.p90,
                      gate=gate, trainingThrough=serving.training_through, calibrationThrough=serving.calibration_through)
        results.append(result)
        print(f'{len(results)} {day} {code} {result["oldCorrect"]}->{result["newCorrect"]}', flush=True)
    db.close()
    cache.close()
    return dict(generatedAt=datetime.now().astimezone().isoformat(), evidenceKind='RETROSPECTIVE',
                inputFingerprints={code: hashlib.sha256(repr([(b.trade_date, b.open, b.close, b.adjustment) for b in bars]).encode()).hexdigest() for code, bars in histories.items()}, limitation='已见历史；行情使用当前前复权缓存、按信号日截断，非当时行情快照；不是新增未来成绩。',
                excluded=dict(excluded), summary=summarize(results), byHorizon={str(h): summarize([r for r in results if r['horizon'] == h]) for h in (1, 5, 20)}, records=results)


def summarize(rows):
    summary = dict(count=len(rows), stocks=len({row['code'] for row in rows}), dates=len({row['day'] for row in rows}))
    for prefix in ('old', 'new'):
        valid = [row for row in rows if row[prefix + 'Median'] is not None]
        summary[prefix] = dict(correct=sum(row[prefix + 'Correct'] for row in rows),
                              brier=statistics.mean((row[prefix + 'Probability'] - (row['actual'] > 0)) ** 2 for row in rows) if rows else None,
                              mae=statistics.mean(abs(row[prefix + 'Median'] - row['actual']) for row in valid) if valid else None,
                              coverage=statistics.mean(row[prefix + 'Lower'] <= row['actual'] <= row[prefix + 'Upper'] for row in valid) if valid else None,
                              width=statistics.mean(row[prefix + 'Upper'] - row[prefix + 'Lower'] for row in valid) if valid else None)
    for prefix in ('old', 'new'):
        valid = [r for r in rows if r[prefix + 'Median'] is not None]
        summary[prefix]['intervalScore'] = statistics.mean(r[prefix + 'Upper'] - r[prefix + 'Lower'] + 10 *
            (max(r[prefix + 'Lower'] - r['actual'], 0) + max(r['actual'] - r[prefix + 'Upper'], 0)) for r in valid) if valid else None
    summary['zeroReturnMae'] = statistics.mean(abs(row['actual']) for row in rows) if rows else None
    summary['byOriginalDirection'] = {}
    for correct in (True, False):
        group = [r for r in rows if r['oldCorrect'] == correct and r['oldMedian'] is not None]
        summary['byOriginalDirection'][str(correct)] = dict(count=len(group),
            oldMae=statistics.mean(abs(r['oldMedian'] - r['actual']) for r in group) if group else None,
            newMae=statistics.mean(abs(r['newMedian'] - r['actual']) for r in group) if group else None)
    summary['fixedErrors'] = sum(not row['oldCorrect'] and row['newCorrect'] for row in rows)
    summary['introducedErrors'] = sum(row['oldCorrect'] and not row['newCorrect'] for row in rows)
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', required=True)
    parser.add_argument('--snapshots', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    with threadpool_limits(limits=1):
        output = compare(args.database, args.snapshots)
    Path(args.output).write_text(json.dumps(output, ensure_ascii=False, indent=2))
    print(json.dumps(output['summary'], ensure_ascii=False, indent=2))

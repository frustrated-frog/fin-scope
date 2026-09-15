"""Fixed momentum/low-volatility baseline; no fitting, parameter search or future labels."""
from __future__ import annotations

from datetime import datetime, time, timedelta
import hashlib
import json
import math
from statistics import fmean, pstdev

from finscope_market_data.forecast.executable_archive import BaselineArchive
from finscope_market_data.forecast.features import FEATURE_CODES, current_features
from finscope_market_data.forecast.trading_calendar import next_session

BASELINE_VERSION = 'MOMENTUM_LOW_VOL_FIXED_V1'


def fingerprint(value: object) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'),
                                     allow_nan=False).encode()).hexdigest()


def trading_dates(start, end):
    if end < start:
        raise ValueError('End date precedes start date')
    dates = []
    day = next_session(start - timedelta(days=1))
    if day != start:
        raise ValueError('Start date is not a verified exchange session')
    while day is not None and day <= end:
        dates.append(day)
        day = next_session(day)
    if not dates or dates[-1] != end or not 7 <= len(dates) <= 1500:
        raise ValueError('Incomplete verified calendar or invalid replay length')
    return dates


def _zscore(values):
    mean = fmean(values)
    scale = pstdev(values)
    return [(value - mean) / scale if scale > 1e-12 else 0.0 for value in values]


def freeze_baseline(archive: BaselineArchive) -> tuple[dict, dict]:
    dates = trading_dates(archive.startDate, archive.endDate)
    signals = dates[:-1:5]
    if len(set(archive.completeUniverseDates)) != len(signals) or set(archive.completeUniverseDates) != set(signals):
        raise ValueError('Every signal date needs a complete universe declaration, including empty snapshots')
    by_day = {day: [] for day in signals}
    seen = set()
    industries = {}
    for row in archive.universe:
        key = (row.signalDate, row.instrumentCode)
        if row.signalDate not in by_day or key in seen:
            raise ValueError('Duplicate or off-schedule universe observation')
        cutoff = datetime.combine(row.signalDate, time(15, 30))
        if row.availableAt.tzinfo is not None or row.availableAt > cutoff:
            raise ValueError('Universe observation must be available by the Shanghai signal cutoff')
        if not row.eligible and not (row.rejectionReason or '').strip():
            raise ValueError('Ineligible universe member needs a rejection reason')
        if row.instrumentCode in industries and industries[row.instrumentCode] != row.industry:
            raise ValueError('Industry changes are not supported by the current replay engine')
        industries[row.instrumentCode] = row.industry
        seen.add(key)
        by_day[row.signalDate].append(row)
    codes = set(industries)
    _validate_execution(archive, dates, codes)
    histories = {}
    for code in sorted(codes):
        rows = archive.researchHistories.get(code, [])
        if len({bar.trade_date for bar in rows}) != len(rows):
            raise ValueError('Duplicate research bar: ' + code)
        for bar in rows:
            if f'{bar.symbol.code}.{bar.symbol.market.value}' != code or bar.adjustment != 'QFQ':
                raise ValueError('Research symbol or adjustment mismatch: ' + code)
        histories[code] = sorted(rows, key=lambda bar: bar.trade_date)
    batches = []
    diagnostics = []
    for day in signals:
        candidates, vectors, inputs = [], [], {}
        for row in sorted(by_day[day], key=lambda value: value.instrumentCode):
            past = [bar for bar in histories[row.instrumentCode] if bar.trade_date <= day.isoformat()]
            inputs[row.instrumentCode] = [bar.model_dump(mode='json') for bar in past]
            candidate = dict(instrumentCode=row.instrumentCode, industry=row.industry,
                             rankingScore=0.0, predictedPriceReturn=None, eligible=False,
                             rejectionReason=row.rejectionReason)
            reason = row.rejectionReason if not row.eligible else None
            if row.eligible:
                if len(past) < 61:
                    reason = 'INSUFFICIENT_HISTORY'
                elif past[-1].trade_date != day.isoformat():
                    reason = 'STALE_SIGNAL_BAR'
                elif any(bar.amount is None or not math.isfinite(bar.amount) or bar.amount <= 0 for bar in past[-20:]):
                    reason = 'MISSING_LIQUIDITY_DATA'
                elif fmean(bar.amount for bar in past[-20:]) < archive.minAverageAmount20d:
                    reason = 'LOW_LIQUIDITY'
                else:
                    try:
                        features = current_features(past)
                        if not all(math.isfinite(value) for value in features):
                            raise ValueError('Non-finite features')
                    except ValueError:
                        reason = 'INVALID_RESEARCH_HISTORY'
                    else:
                        vectors.append((len(candidates), features[FEATURE_CODES.index('MOMENTUM_20')],
                                        -features[FEATURE_CODES.index('VOLATILITY_20')]))
                        candidate['eligible'] = True
            candidate['rejectionReason'] = reason
            candidates.append(candidate)
        if vectors:
            momentum, low_vol = _zscore([v[1] for v in vectors]), _zscore([v[2] for v in vectors])
            for index, vector in enumerate(vectors):
                candidates[vector[0]]['rankingScore'] = 0.5 * momentum[index] + 0.5 * low_vol[index]
        cutoff = datetime.combine(day, time(15, 30))
        inputs['universe'] = [row.model_dump(mode='json') for row in by_day[day]]
        inputs['rule'] = dict(version=BASELINE_VERSION, minAverageAmount20d=archive.minAverageAmount20d)
        batches.append(dict(signalDate=day.isoformat(), informationCutoff=cutoff.isoformat(),
                            trainingLabelsMaturedBefore=None, signalMethod='FIXED_RULE',
                            protocolVersion=archive.protocol.version, modelVersion=BASELINE_VERSION,
                            dataFingerprint=fingerprint(inputs), universeEvidence=archive.universeEvidence,
                            candidates=candidates))
        diagnostics.append(dict(signalDate=day.isoformat(), eligible=len(vectors), candidates=len(candidates)))
    output = dict(protocol=archive.protocol.model_dump(), tradingDates=[day.isoformat() for day in dates],
                  calendarEvidence='finscope trading_calendar.py: verified exchange closures; ' + fingerprint([d.isoformat() for d in dates]),
                  priceBasis='RAW', corporateActionEvidence='coverage-manifest-sha256:' + fingerprint(
                      [row.model_dump(mode='json') for row in archive.corporateCoverage]),
                  hasCorporateActions=False, signals=batches,
                  bars=[row.model_dump(mode='json') for row in sorted(archive.executionBars, key=lambda r: (r.tradeDate, r.instrumentCode))])
    return output, dict(status='READY', baselineVersion=BASELINE_VERSION,
                        archiveFingerprint=fingerprint(archive.model_dump(mode='json')),
                        inputFingerprint=fingerprint(output), batches=diagnostics,
                        training='NONE: fixed 50/50 cross-sectional z-scores; no labels are read')


def _validate_execution(archive, dates, codes):
    expected = {(day, code) for day in dates for code in codes}
    observed = {(row.tradeDate, row.instrumentCode) for row in archive.executionBars}
    if len(observed) != len(archive.executionBars) or observed != expected:
        raise ValueError('Execution grid is incomplete, duplicated or contains extra rows')
    coverage = {row.instrumentCode: row for row in archive.corporateCoverage}
    if len(coverage) != len(archive.corporateCoverage) or set(coverage) != codes:
        raise ValueError('Every candidate needs corporate-action coverage evidence')
    for row in coverage.values():
        if row.coverageFrom > dates[0] or row.coverageThrough < dates[-1]:
            raise ValueError('Corporate-action coverage does not span replay interval')
        if any(dates[0] <= day <= dates[-1] for day in row.exDates):
            raise ValueError('Corporate actions in replay interval are not supported')

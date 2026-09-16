"""Price/volume event research; detection is separate from trade qualification."""
from __future__ import annotations

import math
import statistics
from collections import Counter


def strength_features(bars, code: str) -> dict[str, float]:
    if len(bars) < 21:
        return {}
    closes = [float(x.close) for x in bars]
    daily = closes[-1] / closes[-2] - 1
    # Adjusted closes only identify a limit-like move, never certify a legal limit price.
    threshold = .195 if code.startswith(('300', '301')) else .095
    streak = 0
    for index in range(len(closes) - 1, max(0, len(closes) - 6), -1):
        if closes[index] / closes[index - 1] - 1 < threshold:
            break
        streak += 1
    previous_amount = statistics.fmean(float(x.amount or 0) for x in bars[-21:-1])
    ratio = float(bars[-1].amount or 0) / previous_amount if previous_amount > 0 else 0
    breakout = closes[-1] > max(closes[-21:-1])
    recent = closes[-1] / closes[-6] - 1
    triggered = streak > 0 or (breakout and daily >= .03) or (ratio >= 2 and abs(daily) >= .03)
    return {'event_active': float(triggered), 'limit_like_streak': float(streak),
            'return_1': daily, 'return_5': recent, 'amount_ratio_20': ratio,
            'breakout_20': float(breakout),
            'strength_score': min(streak, 4) * 2 + min(max(recent, -.3), .5) * 10
                              + min(ratio, 5) * .3 + float(breakout)}


def select_research_targets(ranked, limit: int, sector_cap: int, strong_share: float):
    """Reserve seats for events, then stable trend; never relax the industry cap."""
    strong = sorted([x for x in ranked if x.factors.get('event_active', 0)],
                    key=lambda x: (-x.factors.get('strength_score', 0), x.code))
    selected, counts = [], Counter()
    seen = set()

    def take(values, maximum, lane):
        taken = 0
        for item in values:
            sectors = item.sector_codes or ['UNKNOWN']
            if item.code in seen or any(counts[s] >= sector_cap for s in sectors):
                continue
            item.research_lane = lane
            selected.append(item)
            seen.add(item.code)
            counts.update(sectors)
            taken += 1
            if taken >= maximum or len(selected) >= limit:
                break

    take(strong, max(1, math.ceil(limit * strong_share)), 'SHORT_TERM_STRENGTH')
    if len(selected) < limit:
        take(ranked, limit - len(selected), 'STABLE_TREND')
    return selected


def _interval(successes, count):
    p = successes / count
    denominator = 1 + 1.96 ** 2 / count
    center = (p + 1.96 ** 2 / (2 * count)) / denominator
    radius = 1.96 * math.sqrt(p * (1 - p) / count + 1.96 ** 2 / (4 * count ** 2)) / denominator
    return [max(0., center - radius), min(1., center + radius)]


def assess_strength(bars, code: str, calendar):
    """Chronological same-stock event frequencies with matured next-session labels."""
    dates = sorted(set(calendar))
    following = dict(zip(dates, dates[1:]))
    records = []
    all_returns = []
    current = strength_features(bars, code)
    for index in range(20, len(bars) - 1):
        signal, outcome = bars[index], bars[index + 1]
        if following.get(signal.trade_date) != outcome.trade_date:
            continue
        ret = float(outcome.close) / float(signal.close) - 1
        all_returns.append((outcome.trade_date, ret))
        factors = strength_features(bars[max(0, index - 60):index + 1], code)
        if not factors.get('event_active'):
            continue
        # Match limit-like events separately from ordinary breakout/volume events.
        if bool(current.get('limit_like_streak')) != bool(factors['limit_like_streak']):
            continue
        records.append({'signal_date': signal.trade_date, 'outcome_date': outcome.trade_date,
                        'up': ret > 0, 'return': ret, 'continuation': ret >= .03,
                        'limit_like': ret >= (.195 if code.startswith(('300', '301')) else .095),
                        'one_price_limit': (ret >= (.195 if code.startswith(('300', '301')) else .095)
                            and abs(float(outcome.high) - float(outcome.low)) < .005)
                            if getattr(outcome, 'low', None) is not None else None,
                        'fade': float(outcome.high) / float(signal.close) - 1 >= .03
                                and float(outcome.close) < float(outcome.open),
                        'open_to_close': float(outcome.close) / float(outcome.open) - 1})
    result = {'method': 'event-frequency-v1', 'status': 'INSUFFICIENT_DATA',
              'as_of_date': bars[-1].trade_date if bars else None,
              'sample_count': len(records), 'features': current,
              'execution_status': 'UNVERIFIED',
              'execution_note': '缺少次日竞价、封单与可成交证据；开盘至收盘收益仅为价格路径统计。',
              'observations': records, 'qualified': False}
    if not records:
        return result
    n = len(records)
    up = sum(x['up'] for x in records)
    result.update(up_probability=up / n, up_interval=_interval(up, n),
                  continuation_probability=sum(x['continuation'] for x in records) / n,
                  limit_like_probability=sum(x['limit_like'] for x in records) / n,
                  fade_probability=sum(x['fade'] for x in records) / n,
                  mean_return=statistics.fmean(x['return'] for x in records),
                  mean_open_to_close_return=statistics.fmean(x['open_to_close'] for x in records),
                  loss_rate=sum(x['return'] <= 0 for x in records) / n,
                  training_through=records[-1]['outcome_date'])
    one_price = [x['one_price_limit'] for x in records if x['one_price_limit'] is not None]
    result['one_price_limit_rate'] = statistics.fmean(one_price) if one_price else None
    # Expanding origin evaluation; no label from the held-out event enters its estimate.
    checks, baselines = [], []
    for index in range(20, n):
        past = records[:index]
        probability = sum(x['up'] for x in past) / len(past)
        current_record = records[index]
        matured = [x for x in past if x['outcome_date'] <= current_record['signal_date']]
        if len(matured) != len(past):
            continue
        checks.append((probability - float(current_record['up'])) ** 2)
        baseline_returns = [ret for day, ret in all_returns if day <= current_record['signal_date']]
        baseline = sum(ret > 0 for ret in baseline_returns) / len(baseline_returns)
        baselines.append((baseline - float(current_record['up'])) ** 2)
    result['validation_count'] = len(checks)
    result['brier_score'] = statistics.fmean(checks) if checks else None
    result['baseline_brier_score'] = statistics.fmean(baselines) if baselines else None
    result['status'] = 'WATCH' if n >= 30 else 'INSUFFICIENT_DATA'
    return result


def discovery_audit(candidates, targets, deep, scan):
    chosen = {x.code for x in targets}
    completed = {x.code for x in deep}
    events = [x for x in candidates if x.factors.get('event_active')]
    return {'method': 'discovery-recall-v1', 'selection_method': 'dual-lane-v1', 'scan': scan,
            'event_count': len(events), 'event_deep_count': sum(x.code in completed for x in events),
            'sector_seats': dict(Counter(s for x in targets for s in (x.sector_names or ['行业未知']))),
            'misses': [{'code': x.code, 'name': x.name,
                        'reasons': x.rejection_reasons or
                        (['DEEP_ANALYSIS_FAILED'] if x.code in chosen else ['SEAT_LIMIT_OR_SECTOR_CAP'])}
                       for x in events if x.code not in completed]}

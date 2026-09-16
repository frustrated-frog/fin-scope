"""Settle a frozen discovery report against a separately supplied outcome universe."""
from __future__ import annotations

import statistics
from datetime import datetime


def evaluate_recall(report: dict, histories: dict, calendar: list[str], as_of: str,
                    winner_threshold: float = .05) -> dict:
    signal_date = report['as_of_date']
    targets = sorted(day for day in set(calendar) if signal_date < day <= as_of)
    if not targets:
        return {'status': 'PENDING', 'signal_date': signal_date, 'observations': []}
    target_date = targets[0]
    candidates = {x['code']: x for x in report['candidates']}
    deep = {x['code'] for x in report['deep_evidence']}
    watch = {x['code']: x for x in report.get('strength_watchlist', [])}
    detection_available = 'strength_watchlist' in report
    observations, missing = [], []
    for code in sorted(set(histories) | set(candidates)):
        rows = {x['trade_date']: x for x in histories.get(code, []) if x['trade_date'] <= as_of}
        signal, outcome = rows.get(signal_date), rows.get(target_date)
        if not signal or not outcome:
            missing.append(code)
            continue
        if signal.get('adjustment') != 'QFQ' or outcome.get('adjustment') != 'QFQ':
            missing.append(code)
            continue
        ret = float(outcome['close']) / float(signal['close']) - 1
        candidate = candidates.get(code)
        reason = ('OUTSIDE_CANDIDATE_POOL' if candidate is None else
                  ','.join(candidate['rejection_reasons']) if not candidate['admitted'] else
                  'NOT_DEEP_REVIEWED' if code not in deep else 'DEEP_REVIEWED')
        assessment = watch.get(code, {}).get('assessment', {})
        probability = assessment.get('up_probability')
        fade = float(outcome['high']) / float(signal['close']) - 1 >= .03 and float(outcome['close']) < float(outcome['open'])
        observations.append({'code': code, 'actual_return': ret, 'winner': ret >= winner_threshold,
            'event_detected': code in watch, 'deep_reviewed': code in deep, 'reason': reason,
            'fade': fade, 'frozen_up_probability': probability,
            'brier': (probability - float(ret > 0)) ** 2 if probability is not None else None})
    winners = [x for x in observations if x['winner']]
    events = [x for x in observations if x['event_detected']]
    briers = [x['brier'] for x in events if x['brier'] is not None]
    return {'status': 'SETTLED' if observations else 'MISSING_OUTCOMES',
        'method': 'discovery-recall-v1', 'signal_date': signal_date, 'target_date': target_date,
        'evidence_kind': _evidence_kind(report, signal_date),
        'coverage_scope': 'SUPPLIED_SNAPSHOT_UNIVERSE_NOT_COMPLETE_MARKET',
        'covered_count': len(observations), 'missing_codes': missing, 'winner_threshold': winner_threshold,
        'winner_count': len(winners),
        'event_detection_available': detection_available,
        'event_recall': sum(x['event_detected'] for x in winners) / len(winners) if winners and detection_available else None,
        'deep_recall': sum(x['deep_reviewed'] for x in winners) / len(winners) if winners else None,
        'event_count': len(events),
        'event_loss_rate': sum(x['actual_return'] <= 0 for x in events) / len(events) if events else None,
        'event_fade_rate': sum(x['fade'] for x in events) / len(events) if events else None,
        'event_brier': statistics.fmean(briers) if briers else None,
        'missed_winners': [x for x in winners if not x['deep_reviewed']],
        'observations': observations}


def _evidence_kind(report, signal_date):
    try:
        observed = datetime.fromisoformat(report['retrieved_at'])
        return 'FORWARD' if observed.date().isoformat() == signal_date and observed.hour >= 15 else 'RETROSPECTIVE'
    except (KeyError, TypeError, ValueError):
        return 'RETROSPECTIVE'

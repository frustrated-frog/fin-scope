"""Paired prospective evidence on complete actual cohorts, including failures."""
from collections import Counter, defaultdict
from datetime import datetime
import math

from finscope_market_data.forecast.trading_calendar import next_session
from finscope_market_data.forecast.direction_evaluation import evaluate_direction
from finscope_market_data.overnight.candidate_cohort import scheduled_cohorts
from finscope_market_data.overnight.candidate_evaluation import evaluate_candidates
from finscope_market_data.overnight.direction_dataset import TARGET
from finscope_market_data.overnight.market_context import GROUPS
from finscope_market_data.overnight.market_research import PROTOCOL


def missing_reason(report, forecast, outcome, decision, target_at, now, *, protocol=PROTOCOL, require_context=True):
    if not report:
        return 'MISSING_PREDICTION'
    if forecast.get('protocol') != protocol or forecast.get('target') != TARGET:
        return 'MISSING_MODEL'
    if forecast.get('status') != 'SHADOW':
        return forecast.get('status', 'MISSING_MODEL')
    if (forecast.get('referenceAt') != decision or not forecast.get('artifactId')
            or forecast.get('trainedAt', '9999') >= decision
            or (require_context and (not forecast.get('contextFingerprint')
                                     or forecast.get('contextReceivedAt', '9999') > decision))):
        return 'INVALID_EVIDENCE'
    reference = forecast.get('referencePrice')
    if isinstance(reference, bool) or not isinstance(reference, (int, float)) or not math.isfinite(reference) or reference <= 0:
        return 'INVALID_EVIDENCE'
    for name in ('upProbability', 'matchedBaselineProbability', 'liveProbability'):
        value = forecast.get(name)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 1:
            return 'MISSING_PROBABILITY'
    if (outcome.get('status') != 'SETTLED' or not isinstance(outcome.get('actualUp'), bool)
            or outcome.get('exitAt') != target_at or target_at >= now
            or outcome.get('referenceAt') != decision or outcome.get('referencePrice') != forecast.get('referencePrice')):
        return 'MISSING_OUTCOME'
    return None


def summarize_market_forward(store, meta, now, *, protocol=PROTOCOL, groups=GROUPS,
                             forecast_field='marketGroups', validate=missing_reason, row_fields=(), summarizer=None,
                             cohort_validate=None):
    through = now.isoformat()
    cohorts = scheduled_cohorts(meta, through)
    originals = {}
    for report in sorted(store.iter_history(), key=lambda r: (r['generatedAt'], r['id'])):
        if (report.get('evidenceKind') != 'FORWARD' or report['generatedAt'] >= through
                or (report.get('jointResearch') or {}).get('cohort') != 'AUTOMATIC'):
            continue
        key = (report['signalDate'], report['cutoff'], report['instrumentCode'])
        originals.setdefault(key, report)
    group_names = tuple(groups)
    groups = defaultdict(lambda: {'scheduled': 0, 'paired': 0, 'days': 0, 'missing': Counter(), 'rows': []})
    for (day, cutoff), cohort in sorted(cohorts.items()):
        target = next_session(datetime.fromisoformat(day).date())
        target_at = f'{target}T15:00:00'
        if target is None or target_at >= through:
            continue
        mode = 'AFTER_CLOSE_HOLDING' if cutoff == '15:00' else 'TAIL_ENTRY'
        decision = f'{day}T{cutoff}:00'
        for name in group_names:
            group = groups[(mode, cutoff, name)]
            group['scheduled'] += len(cohort['candidates'])
            group['days'] += 1
            rows = []
            for code in sorted(cohort['candidates']):
                report = originals.get((day, cutoff, code), {})
                forecasts = (report.get('closeDirection') or {}).get(forecast_field) or []
                forecast = next((row for row in forecasts if row.get('group') == name), {})
                outcome = (report.get('outcome') or {}).get('closeDirection') or {}
                reason = validate(report, forecast, outcome, decision, target_at, through)
                if reason:
                    group['missing'][reason] += 1
                    continue
                rows.append({'signalDate': day, 'instrumentCode': code, 'actualUp': outcome['actualUp'],
                             'probability': forecast['upProbability'], 'baseline': forecast['matchedBaselineProbability'],
                             'live': forecast['liveProbability'], **{key: forecast[key] for key in row_fields}})
            if cohort_validate and len(rows) == len(cohort['candidates']):
                reason = cohort_validate(rows)
                if reason:
                    group['missing'][reason] += len(rows)
                    continue
            group['paired'] += len(rows)
            # Incomplete candidate days cannot improve either top-K or direction scores.
            if len(rows) == len(cohort['candidates']):
                group['rows'].extend(rows)
    return {'protocol': protocol, 'target': TARGET, 'computedAt': through,
            'groups': [(summarizer or summarize)(key, value) for key, value in sorted(groups.items())]}


def summarize(key, group):
    mode, cutoff, name = key
    rows = group['rows']
    days = len({r['signalDate'] for r in rows})
    metrics = evaluate_direction([r['probability'] for r in rows], [r['actualUp'] for r in rows],
        [r['signalDate'] for r in rows], {'MATCHED_BASELINE': [r['baseline'] for r in rows],
                                       'LIVE_FORECAST': [r['live'] for r in rows]}, family_size=3 * len(GROUPS)) if rows else None
    return {'key': '|'.join(key), 'mode': mode, 'cutoff': cutoff, 'group': name,
            'status': 'RESEARCH_ONLY' if days >= 60 else 'ACCUMULATING', 'eligible': False,
            'dayCount': days, 'requiredDays': 60, 'scheduledDays': group['days'],
            'scheduledCount': group['scheduled'], 'pairedCount': group['paired'],
            'coverage': group['paired'] / group['scheduled'], 'missing': dict(group['missing']),
            'metrics': metrics, 'upCandidates': evaluate_candidates(rows),
            'baselineCandidates': evaluate_candidates([{**r, 'probability': r['baseline']} for r in rows]),
            'reason': '仅使用完整实际候选日配对验证；结果供研究，不自动替换主预测'}

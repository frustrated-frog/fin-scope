"""Selection-only rows for actual discoveries outside the fixed training pool."""
from collections import defaultdict
from datetime import datetime

from finscope_market_data.forecast.industry_features import IndustryMembership
from finscope_market_data.overnight.action_verification import ActionVerificationStore
from finscope_market_data.overnight.context_features import context_features, stock_context
from finscope_market_data.overnight.direction_dataset import close_outcome, direction_features
from finscope_market_data.overnight.engine import group_bars
from finscope_market_data.overnight.multiscale_features import multiscale_features


def extend_candidate_rows(rows, store, contexts, cohorts, cutoff, through):
    existing = {(r['signalDate'], r['instrumentCode']) for r in rows}
    by_code, snapshots = defaultdict(list), {}
    for day, stamp in sorted(cohorts)[-240:]:
        snapshot = contexts.at(day, stamp, datetime.fromisoformat(f'{day}T{stamp}:00'))
        if stamp != cutoff or not snapshot:
            continue
        snapshots[day] = snapshot
        for code in cohorts[(day, stamp)]['candidates']:
            if (day, code) not in existing:
                by_code[code].append(day)
    extra = []
    for code, days in sorted(by_code.items()):
        grouped = group_bars(store.bars(code, through))
        for day in days:
            signal = datetime.fromisoformat(day).date()
            outcome = close_outcome(grouped, signal, through, cutoff)
            if outcome['status'] != 'SETTLED':
                continue
            snapshot = snapshots[day]
            memberships = [IndustryMembership(v['industry'], v['available_on'], tuple(v['codes'])) for v in snapshot.get('memberships', [])]
            local = direction_features(grouped, signal, cutoff, code)
            history = multiscale_features(grouped, signal, cutoff)
            context = context_features(code, stock_context(grouped, signal, cutoff), snapshot, memberships)
            if local is None or history is None or context is None:
                continue
            extra.append({'instrumentCode': code, 'signalDate': day, 'features': local + context + history,
                'actualReturn': outcome['actualReturn'], 'exitAt': outcome['exitAt'], 'candidateOnly': True})
    extra, _ = ActionVerificationStore(store).annotate(extra, through)
    return rows + extra

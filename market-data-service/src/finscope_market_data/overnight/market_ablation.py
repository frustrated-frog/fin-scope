"""Offline, matched-sample feature-group experiments; never promotes a model."""
from finscope_market_data.overnight.direction_learning import covered_matured_rows, fit_direction, TRAIN_DAYS, SELECTION_DAYS, CALIBRATION_DAYS, TEST_DAYS
from finscope_market_data.overnight.market_context import GROUPS, PROTOCOL, paired_market_panels


def evaluate_market_groups(rows, contexts, cutoff, through):
    required_days = TRAIN_DAYS + SELECTION_DAYS + CALIBRATION_DAYS + TEST_DAYS + 3
    result = {'protocol': PROTOCOL, 'computedAt': through.isoformat(), 'cutoff': cutoff,
              'status': 'ACCUMULATING', 'eligible': False, 'groups': []}
    for group, (base, enhanced) in paired_market_panels(rows, contexts, cutoff).items():
        # Apply the same date-coverage filter before either fit. Industry gaps
        # must not produce a different testing population for the challenger.
        covered, coverage = covered_matured_rows(base, through.isoformat())
        keys = {(row['signalDate'], row['instrumentCode']) for row in covered}
        enhanced = [row for row in enhanced if (row['signalDate'], row['instrumentCode']) in keys]
        days = len({row['signalDate'] for row in covered})
        item = {'group': group, 'features': list(GROUPS[group]), 'dayCount': days,
                'coverage': coverage, 'rowCount': len(covered), 'requiredDays': required_days, 'status': 'ACCUMULATING'}
        if days >= required_days:
            incumbent = fit_direction(covered, through.isoformat(), cutoff)
            challenger = fit_direction(enhanced, through.isoformat(), cutoff)
            if incumbent and challenger:
                item.update(status='RESEARCH_ONLY', baseline=incumbent['audit']['historical'],
                            enhanced=challenger['audit']['historical'])
        result['groups'].append(item)
    return result

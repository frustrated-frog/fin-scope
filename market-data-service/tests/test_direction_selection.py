from datetime import date, timedelta

import numpy as np

from finscope_market_data.overnight.direction_selection import select_direction


def selection_rows():
    return [{'signalDate': str(date(2026, 1, 1) + timedelta(days=i // 10)),
             'actualReturn': .01 if i % 10 < 6 else -.01} for i in range(200)]


def test_majority_accuracy_cannot_masquerade_as_direction_skill():
    rows = selection_rows()
    selected, audit = select_direction({'PRIOR': np.full(200, .6), 'ALL_UP': np.full(200, .61)}, rows)
    assert selected == 'PRIOR'
    assert audit['candidates']['ALL_UP']['accuracy'] == .6
    assert not audit['candidates']['ALL_UP']['eligible']


def test_two_class_skill_must_repeat_across_selection_subwindows():
    rows = selection_rows()
    p = np.array([.7 if r['actualReturn'] > 0 else .3 for r in rows])
    selected, audit = select_direction({'PRIOR': np.full(200, .6), 'SKILLED': p}, rows)
    assert selected == 'SKILLED'
    assert audit['candidates']['SKILLED']['subwindowWins'] == 3
    # A lucky third of the window cannot carry two periods worse than prior.
    p[70:] = 1 - p[70:]
    assert select_direction({'PRIOR': np.full(200, .6), 'LUCKY': p}, rows)[0] == 'PRIOR'

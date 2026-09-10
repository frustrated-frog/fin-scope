"""Leave-self-out historical peer features on a label-independent observable panel."""
from collections import defaultdict
from datetime import date, timedelta

import numpy as np

from finscope_market_data.industry_models import IndustryChange

INDUSTRY_CODES = ('SI_RETURN_1', 'SI_MOMENTUM_5', 'SI_MOMENTUM_20', 'SI_BREADTH_1',
    'SI_ACTIVITY', 'SI_DISPERSION_1', 'SI_RELATIVE_5', 'SI_RELATIVE_20', 'SI_VS_MARKET_5',
    'SI_PEER_COUNT', 'SI_PANEL_COVERAGE', 'SI_MISSING')


def industry_panel_features(rows, feature_codes, changes, *, availability='VENDOR_RECONSTRUCTION'):
    if availability not in ('VENDOR_RECONSTRUCTION', 'OBSERVED'):
        raise ValueError('未知行业可用时间口径')
    codes = ('MOMENTUM_5', 'MOMENTUM_20', 'OVERNIGHT_GAP', 'INTRADAY_RETURN', 'AMOUNT_RATIO_20_60')
    columns = [feature_codes.index(code) for code in codes]
    by_day, events = defaultdict(list), []
    for index, row in enumerate(rows):
        by_day[row.signal_date].append(index)
    for change in changes:
        if change.taxonomy != '008003':
            raise ValueError('行业分类口径不能混用')
        effective = (date.fromisoformat(change.effective_from) + timedelta(days=1)).isoformat()
        # The reconstruction lag is an assumption, never a fabricated available_at.
        known = (change.available_at or change.retrieved_at)[:10]
        usable = max(effective, known) if availability == 'OBSERVED' else effective
        events.append((usable, change.effective_from, change.code, change.industry))
    events.sort()
    active, cursor = {}, 0
    output = np.full((len(rows), len(INDUSTRY_CODES)), np.nan)
    for day, indices in sorted(by_day.items()):
        while cursor < len(events) and events[cursor][0] <= day:
            _, effective, code, industry = events[cursor]
            if code not in active or effective >= active[code][0]:
                active[code] = (effective, industry)
            cursor += 1
        x = np.asarray([rows[i].features for i in indices], dtype=float)[:, columns]
        ret1 = (1+x[:, 2])*(1+x[:, 3])-1
        groups = defaultdict(list)
        for local, index in enumerate(indices):
            if rows[index].code in active:
                groups[active[rows[index].code][1]].append(local)
        for local, index in enumerate(indices):
            industry = active.get(rows[index].code)
            peers = [j for j in groups[industry[1]] if j != local] if industry else []
            output[index, -3:] = (len(peers), len(peers)/max(1, len(indices)-1), 1.)
            if len(peers) < 2:
                continue
            p = x[peers]
            output[index] = (np.mean(ret1[peers]), np.mean(p[:, 0]), np.mean(p[:, 1]),
                np.mean(ret1[peers] > 0), np.mean(p[:, 4]), np.std(ret1[peers]),
                x[local, 0]-np.mean(p[:, 0]), x[local, 1]-np.mean(p[:, 1]),
                np.mean(p[:, 0])-np.mean(np.delete(x[:, 0], local)),
                len(peers), len(peers)/max(1, len(indices)-1), 0.)
    return output, INDUSTRY_CODES

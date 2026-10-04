"""Cross-provider checks use daily anchors; individual minute aggregation can differ."""
from collections import defaultdict


def compare_history(existing, incoming):
    days = defaultdict(dict)
    for bar in incoming:
        days[str(bar.ended_at.date())][bar.ended_at.strftime('%H:%M')] = bar
    old_days = defaultdict(dict)
    overlap, differences, anchors, complete_days = 0, 0, 0, 0
    for stamp, bar in existing.items():
        old_days[stamp[:10]][stamp[11:16]] = bar
    for day, fresh in days.items():
        old = old_days.get(day, {})
        for stamp in fresh.keys() & old.keys():
            overlap += 1
            differences += int(any(abs(getattr(old[stamp], key) - getattr(fresh[stamp], key)) > .011
                                   for key in ('open', 'high', 'low', 'close')))
        for stamp, key in (('09:35', 'open'), ('15:00', 'close')):
            if stamp in fresh and stamp in old:
                anchors += 1
                if abs(getattr(fresh[stamp], key) - getattr(old[stamp], key)) > .011:
                    raise ValueError('历史源与已采集开收盘价格冲突，未合并')
        if len(old) == len(fresh) == 48:
            complete_days += 1
            original = sum(bar.amount for bar in old.values())
            actual = sum(bar.amount for bar in fresh.values())
            if abs(original - actual) > max(1, original * .02):
                raise ValueError('历史源与已采集全天成交额口径冲突，未合并')
    return {'overlapBars': overlap, 'minuteDifferenceCount': differences,
            'verifiedPriceAnchors': anchors, 'verifiedCompleteDays': complete_days}

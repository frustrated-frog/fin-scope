"""Cross-provider checks use daily anchors; individual minute aggregation can differ."""
from collections import defaultdict


class MinuteHistoryConflict(ValueError):
    def __init__(self, day, error_type, message):
        super().__init__(message)
        self.day = day
        self.error_type = error_type


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
                    raise MinuteHistoryConflict(day, 'HISTORY_PRICE_CONFLICT', '历史源与已采集开收盘价格冲突，未合并')
        if len(old) == len(fresh) == 48:
            complete_days += 1
            original = sum(bar.amount for bar in old.values())
            actual = sum(bar.amount for bar in fresh.values())
            if abs(original - actual) > max(1, original * .02):
                raise MinuteHistoryConflict(day, 'HISTORY_AMOUNT_CONFLICT', '历史源与已采集全天成交额口径冲突，未合并')
    return {'overlapBars': overlap, 'minuteDifferenceCount': differences,
            'verifiedPriceAnchors': anchors, 'verifiedCompleteDays': complete_days}


def validated_history(existing, incoming):
    """Quarantine whole conflicting source-days, requiring independent matching days.

    If every overlap conflicts, keep the original atomic rejection: the provider's
    raw/adjustment identity has not been established for the rest of the batch.
    """
    days = defaultdict(list)
    old_days = defaultdict(dict)
    for bar in incoming:
        days[str(bar.ended_at.date())].append(bar)
    for stamp, bar in existing.items():
        old_days[stamp[:10]][stamp] = bar
    accepted, conflicts, trusted_days = [], [], 0
    first_conflict = None
    for day, bars in days.items():
        try:
            quality = compare_history(old_days.get(day, {}), bars)
            trusted_days += int(quality['verifiedPriceAnchors'] > 0 or quality['verifiedCompleteDays'] > 0)
            accepted.extend(bars)
        except MinuteHistoryConflict as error:
            first_conflict = first_conflict or error
            conflicts.append({'date': day, 'errorType': error.error_type, 'reason': str(error)})
    if conflicts and trusted_days == 0:
        raise first_conflict
    quality = compare_history(existing, accepted)
    quality.update({'quarantinedDays': [row['date'] for row in conflicts], 'conflicts': conflicts,
                    'verifiedMatchingDays': trusted_days,
                    'qualityStatus': 'PARTIAL' if conflicts else 'VERIFIED' if trusted_days else 'SOURCE_ONLY'})
    return sorted(accepted, key=lambda bar: bar.ended_at), quality

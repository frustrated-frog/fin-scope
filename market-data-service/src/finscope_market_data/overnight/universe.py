"""A persistent research cohort selected independently of today's returns.

The cached listing universe has survivorship/coverage bias. Membership is frozen
when first observed, never described as historical point-in-time constituents.
"""
import hashlib
import re

POOL_SIZE = 120
POOL_KEY = 'jointUniverse'
PROTOCOL = 'overnight-panel-v1'


def extend_universe(old, symbols, now):
    members = list((old or {}).get('members', []))
    existing = {row['instrumentCode'] for row in members}
    groups = {board: [] for board in ('SH_MAIN', 'SZ_MAIN', 'GEM')}
    for key in set(symbols):
        if not re.fullmatch(r'(?:SH:60[0135]\d{3}|SZ:(?:00[0123]|30[01])\d{3})', key):
            continue
        exchange, number = key.split(':')
        code = number + '.' + exchange
        if code in existing:
            continue
        board = 'GEM' if number.startswith('3') else 'SH_MAIN' if exchange == 'SH' else 'SZ_MAIN'
        groups[board].append(code)
    for group in groups.values():
        group.sort(key=lambda code: hashlib.sha256(f'{PROTOCOL}|{code}'.encode()).hexdigest())
    # Fill the least represented board first; no price, gain or momentum filter.
    while len(members) < POOL_SIZE and any(groups.values()):
        board = min((board for board in groups if groups[board]),
                    key=lambda board: (sum(row['board'] == board for row in members), board))
        members.append({'instrumentCode': groups[board].pop(0), 'board': board,
                        'joinedAt': now.isoformat()})
    digest = hashlib.sha256('|'.join(sorted(row['instrumentCode'] for row in members)).encode()).hexdigest()
    return {'protocol': PROTOCOL, 'targetSize': POOL_SIZE, 'members': members,
            'createdAt': (old or {}).get('createdAt', now.isoformat()), 'fingerprint': digest,
            'scope': '日线缓存中的沪深主板与创业板，按板块均衡及固定哈希选取；保留后续缺失成员，不按涨幅换股。',
            'limitation': '当前缓存股票池，不是历史全市场成分；退市与行业覆盖偏差尚未消除。'}

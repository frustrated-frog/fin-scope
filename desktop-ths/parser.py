"""Conservative parsing of THS accessibility nodes; never infer missing columns."""
import re
from datetime import datetime, timezone

MESSAGES = {
    'PERMISSION_REQUIRED': '读取程序尚未获得辅助功能权限。请在系统设置 → 隐私与安全性 → 辅助功能中允许启动它的终端，再重试。',
    'APP_NOT_RUNNING': '请先打开 Mac 同花顺，并进入龙虎榜页面。',
    'NO_WINDOW': '未读取到同花顺页面，请展开主窗口后重试。',
    'UNSUPPORTED_PAGE': '第一版仅支持龙虎榜，请在同花顺打开龙虎榜并选中一只股票。',
    'AMBIGUOUS': '发现多个当前股票或不一致的选中行，请保留一个龙虎榜页面后重试。',
    'TIMEOUT': '本次读取超时，请保持同花顺页面打开后重试。',
    'READER_UNAVAILABLE': '本机读取程序未就绪，请运行 desktop-ths/start.sh。',
    'BUSY': '已有一次读取正在进行，请稍后重试。',
    'READ_FAILED': '本次读取失败，请检查同花顺窗口后重试。',
}


def empty(status, captured_at=None):
    return dict(status=status, message=MESSAGES.get(status, ''), source='THS_DESKTOP_AX',
                capturedAt=captured_at or datetime.now(timezone.utc).isoformat(), dataDate=None,
                stockCode=None, stockName=None, price=None, changePct=None, netBuy=None,
                reason=None, fields=[], buySeats=[], sellSeats=[], warnings=[])


def text(node):
    for key in ('value', 'title', 'description'):
        value = node.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ''


def parse_capture(raw):
    result = empty(raw.get('status', 'READ_FAILED'), raw.get('capturedAt'))
    if raw.get('status') != 'OK':
        return result
    nodes = raw.get('nodes', [])
    children = {}
    by_id = {n['id']: n for n in nodes}
    for n in nodes:
        children.setdefault(n.get('parent'), []).append(n)

    def descendants(n):
        stack = list(reversed(children.get(n['id'], [])))
        while stack:
            child = stack.pop()
            yield child
            stack.extend(reversed(children.get(child['id'], [])))

    def leaves(n):
        own = text(n)
        if own:
            return own
        return ' '.join(dict.fromkeys(text(c) for c in descendants(n) if text(c)))

    if not any(n.get('role') not in ('AXApplication', 'AXMenuBar', 'AXMenuBarItem', 'AXFunctionRowTopLevelElement') for n in nodes):
        return empty('NO_WINDOW', result['capturedAt'])
    all_text = '\n'.join(text(n) for n in nodes)
    if '龙虎榜' not in all_text or '上榜' not in all_text:
        return empty('UNSUPPORTED_PAGE', result['capturedAt'])
    matches = set(re.findall(r'([^\s]+)\s+(\d{6})\s+(\d+(?:\.\d+)?)[↑↓]?\s+[+\-]?\d+(?:\.\d+)?\s+([+\-]?\d+(?:\.\d+)?)%', all_text))
    if len(matches) > 1:
        return empty('AMBIGUOUS', result['capturedAt'])
    result['status'] = 'PARTIAL'
    if len(matches) == 1:
        result['stockName'], result['stockCode'], result['price'], change = matches.pop()
        result['changePct'] = change + '%'
    dates = set(re.findall(r'更新日期\s*[:：]\s*(\d{4}-\d{2}-\d{2})', all_text))
    if len(dates) == 1:
        candidate = dates.pop()
        try:
            datetime.strptime(candidate, '%Y-%m-%d')
            result['dataDate'] = candidate
        except ValueError:
            pass
    reasons = set(re.findall(r'【上榜类型】\s*[:：]\s*([^\n]+)', all_text))
    if len(reasons) == 1:
        result['reason'] = reasons.pop()
    for row in [n for n in nodes if n.get('role') == 'AXRow' and n.get('selected')]:
        cells = [leaves(c) for c in children.get(row['id'], []) if c.get('role') == 'AXCell']
        if cells and re.fullmatch(r'\d{6}', cells[0]) and result['stockCode'] and cells[0] != result['stockCode']:
            return empty('AMBIGUOUS', result['capturedAt'])
        if not result['stockCode'] or not cells or cells[0] != result['stockCode']:
            continue
        table = by_id.get(row.get('parent'), {})
        headers = [text(c) for c in descendants(table) if c.get('role') == 'AXSortButton' or c.get('subrole') == 'AXSortButton'] if table else []
        if len(headers) == len(cells) and len(headers) >= 4 and headers[:2] == ['代码', '名称']:
            result['fields'] = [dict(label=h, value=v) for h, v in zip(headers, cells)]
            result['netBuy'] = dict(zip(headers, cells)).get('净买入')
        else:
            result['warnings'].append('选中行与表头未完整对应，未推测净买入等字段。')
    for table in [n for n in nodes if n.get('role') == 'AXTable']:
        labels = [text(c) for c in descendants(table)]
        side = 'buySeats' if any('买入金额最大前5名' in s for s in labels) else 'sellSeats' if any('卖出金额最大前5名' in s for s in labels) else None
        if side and result['stockCode']:
            for row in children.get(table['id'], []):
                if row.get('role') == 'AXRow':
                    raw_text = leaves(row)
                    if raw_text:
                        result[side].append(raw_text)
            result[side] = result[side][:5]
    if raw.get('truncated'):
        result['warnings'].append('读取范围达到上限，本次结果可能不完整。')
    for key, label in [('stockCode', '当前股票'), ('dataDate', '榜单日期'), ('reason', '上榜原因'), ('netBuy', '净买入')]:
        if not result[key]:
            result['warnings'].append(f'{label}未获取。')
    if not result['buySeats'] or not result['sellSeats']:
        result['warnings'].append('买卖席位未完整获取。')
    result['warnings'].append('价格与涨幅来自当前股票栏，其行情时间未暴露；不等于榜单日期对应的收盘数据。')
    complete = all(result[k] for k in ('stockCode', 'dataDate', 'reason', 'netBuy', 'buySeats', 'sellSeats')) and not raw.get('truncated')
    result['status'] = 'OK' if complete else 'PARTIAL'
    result['message'] = '已读取当前页面快照' if complete else '已读取部分页面内容，缺失字段已标注'
    return result

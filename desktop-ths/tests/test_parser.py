import unittest
from parser import parse_capture


def node(id, role, text='', parent=None, selected=False):
    return dict(id=id, role=role, value=text, title='', description='', help='', parent=parent, selected=selected)


def fixture():
    return {'status': 'OK', 'capturedAt': '2026-09-13T03:00:00Z', 'nodes': [
        node(0, 'AXWindow'),
        node(1, 'AXStaticText', '龙虎榜赚钱效应 买卖金额 更新日期:2026-09-11', 0),
        node(2, 'AXStaticText', '正和生态 605069 11.50↑ +1.05 +10.05%', 0),
        node(3, 'AXStaticText', '【上榜类型】:日涨幅偏离值达7%的证券', 0),
        node(4, 'AXTable', parent=0),
        node(5, 'AXRow', parent=4, selected=True),
        node(6, 'AXCell', parent=5), node(7, 'AXStaticText', '605069', 6),
        node(8, 'AXCell', parent=5), node(9, 'AXStaticText', '正和生态', 8),
        node(10, 'AXCell', parent=5), node(11, 'AXStaticText', '+10.05', 10),
        node(12, 'AXCell', parent=5), node(13, 'AXStaticText', '11.50', 12),
    ]}


class ParserTests(unittest.TestCase):
    def test_reads_current_stock_and_separates_dates(self):
        result = parse_capture(fixture())
        self.assertEqual('605069', result['stockCode'])
        self.assertEqual('11.50', result['price'])
        self.assertEqual('2026-09-11', result['dataDate'])
        self.assertEqual('2026-09-13T03:00:00Z', result['capturedAt'])
        self.assertEqual('PARTIAL', result['status'])
        self.assertIsNone(result['netBuy'])

    def test_does_not_infer_date_or_net_amount_from_short_row(self):
        raw = fixture()
        raw['nodes'][1]['value'] = '龙虎榜赚钱效应'
        result = parse_capture(raw)
        self.assertIsNone(result['dataDate'])
        self.assertIsNone(result['netBuy'])

    def test_conflicting_current_stocks_are_rejected(self):
        raw = fixture()
        raw['nodes'].append(node(15, 'AXStaticText', '江南水务 601199 6.47↑ +0.59 +10.03%', 0))
        result = parse_capture(raw)
        self.assertEqual('AMBIGUOUS', result['status'])
        self.assertIsNone(result['stockCode'])

    def test_other_page_not_mislabeled_as_dragon_tiger(self):
        raw = fixture()
        raw['nodes'] = raw['nodes'][2:3]
        self.assertEqual('UNSUPPORTED_PAGE', parse_capture(raw)['status'])

    def test_permission_failure_keeps_failure(self):
        result = parse_capture({'status': 'PERMISSION_REQUIRED', 'nodes': []})
        self.assertEqual('PERMISSION_REQUIRED', result['status'])
        self.assertIsNone(result['stockCode'])

    def test_menu_only_is_no_window_not_wrong_page(self):
        raw = {'status': 'OK', 'nodes': [node(0, 'AXMenuBar'), node(1, 'AXMenuBarItem', '同花顺', 0)]}
        self.assertEqual('NO_WINDOW', parse_capture(raw)['status'])

    def test_maps_net_buy_only_with_complete_matching_headers(self):
        raw = fixture()
        labels = ['代码', '名称', '涨幅%', '现价', '净买入']
        for i, label in enumerate(labels):
            raw['nodes'].append(node(20 + i, 'AXSortButton', label, 4))
        raw['nodes'] += [node(30, 'AXCell', parent=5), node(31, 'AXStaticText', '1875.87万', 30)]
        self.assertEqual('1875.87万', parse_capture(raw)['netBuy'])
        raw['nodes'].pop()
        raw['nodes'].pop()
        self.assertIsNone(parse_capture(raw)['netBuy'])

    def test_selected_row_from_another_stock_is_rejected(self):
        raw = fixture()
        raw['nodes'][7]['value'] = '601199'
        self.assertEqual('AMBIGUOUS', parse_capture(raw)['status'])

    def test_truncated_capture_never_claims_complete(self):
        raw = fixture()
        raw['truncated'] = True
        self.assertIn('读取范围达到上限', ' '.join(parse_capture(raw)['warnings']))

if __name__ == '__main__':
    unittest.main()

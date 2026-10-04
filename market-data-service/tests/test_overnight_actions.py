from datetime import date, datetime

import pytest

from finscope_market_data.overnight.action_verification import ActionVerificationStore
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.providers.baostock_actions import BaostockActionProvider, FIELDS


def test_company_action_contract_checks_identity_dates_and_factors():
    args = ('600000.SH', date(2026, 1, 1), date(2026, 9, 30))
    row = ['sh.600000', '2026-06-15', '1', '2', '1.01']
    result = BaostockActionProvider.parse({'fields': list(FIELDS), 'rows': [row]}, *args)
    assert result['exDates'] == ['2026-06-15']
    empty = BaostockActionProvider.parse({'fields': list(FIELDS), 'rows': []}, *args)
    assert empty['status'] == 'COVERED' and empty['exDates'] == []
    for column, value in ((0, 'sh.600001'), (1, '2026-10-09'), (2, 'nan'), (3, '-1')):
        changed = row.copy()
        changed[column] = value
        with pytest.raises(ValueError):
            BaostockActionProvider.parse({'fields': list(FIELDS), 'rows': [changed]}, *args)


def test_action_audit_preserves_labels_and_all_evaluation_rows(tmp_path):
    store = ActionVerificationStore(OvernightStore(tmp_path / 'actions.db'))
    row = {'instrumentCode': '600000.SH', 'signalDate': '2026-06-12', 'exitAt': '2026-06-15T15:00:00', 'actualReturn': -.05}
    now = datetime(2026, 9, 30, 19)
    unknown, audit = store.annotate([row], now)
    assert not unknown[0]['actionsVerified'] and unknown[0]['trainingEligible']
    store.save({'instrumentCode': '600000.SH', 'fromDate': '2026-01-01', 'throughDate': '2026-09-29',
                'exDates': ['2026-06-15'], 'status': 'COVERED'}, now)
    verified, audit = store.annotate([row], now)
    assert len(verified) == 1 and verified[0]['actualReturn'] == -.05
    assert audit['verifiedRows'] == audit['affectedRows'] == 1
    assert not verified[0]['trainingEligible']
    assert not store.annotate([row], datetime(2026, 9, 30, 18))[0][0]['actionsVerified']

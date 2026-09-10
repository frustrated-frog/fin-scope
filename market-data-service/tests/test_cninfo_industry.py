import pytest

from finscope_market_data.providers.cninfo_industry import parse_industry_changes


def test_history_keeps_only_exact_standard_and_preserves_effective_dates():
    raw = dict(resultcode=200, count=3, total=3, records=[
        dict(SECCODE='000001', F001V='008003', F004V='银行', VARYDATE='2021-07-30'),
        dict(SECCODE='000001', F001V='008003', F004V='金融服务', VARYDATE='2008-01-01'),
        dict(SECCODE='000001', F001V='008002', F004V='金融', VARYDATE='2019-01-01')])
    records = parse_industry_changes(raw,'000001','2026-09-10T08:00:00+08:00')
    assert [r.effective_from for r in records] == ['2008-01-01','2021-07-30']
    assert all(r.available_at is None for r in records)
    assert records[-1].industry == '银行'


def test_partial_response_and_wrong_code_cannot_silently_enter_history():
    with pytest.raises(ValueError):
        parse_industry_changes(dict(count=1,total=2,records=[]),'a','2026-09-10')
    with pytest.raises(ValueError):
        parse_industry_changes(dict(count=1,total=1,records=[dict(SECCODE='b',F001V='008003',F004V='银行',VARYDATE='2020-01-01')]),'a','2026-09-10')

from datetime import datetime

from test_fresh_research import fresh_reports
from finscope_market_data.overnight.up_detection import evidence, summarize_detection


def test_adaptive_pairs_do_not_depend_on_index_availability_and_exclude_invalid_dates():
    reports = fresh_reports()
    rows = []
    for report in reports:
        report['closeDirection']['freshModels'] = [f for f in report['closeDirection']['freshModels'] if f['group'] != 'INDEX']
        rows.append({'signalDate': report['signalDate'], 'instrumentCode': report['instrumentCode'],
            'actualUp': True, 'probability': .4,
            **evidence(report, report['closeDirection'], None, datetime(2026, 9, 23), '2026-09-22T15:00:00')})
    result = summarize_detection(rows)
    assert result['comparisonPairs']['ADAPTIVE']['pairedDays'] == 1
    assert result['comparisonPairs']['INDEX']['pairedDays'] == 0
    rows[0]['alternatives'].pop('ADAPTIVE')
    assert summarize_detection(rows)['comparisonPairs']['ADAPTIVE']['pairedDays'] == 0
    assert summarize_detection(rows)['comparisonPairs']['FRESH']['pairedDays'] == 1

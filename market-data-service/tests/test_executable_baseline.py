import json
from pathlib import Path
import sqlite3

import pytest

from finscope_market_data.forecast.executable_archive import BaselineArchive
from finscope_market_data.forecast.executable_baseline import freeze_baseline, trading_dates
from finscope_market_data.forecast.executable_sources import audit_snapshots, load_research_histories
from test_next_session import bars


def archive_dict():
    data = bars(85)
    dates = [row.trade_date for row in data[70:]]
    codes = ['600001.SH', '600002.SH']
    histories = {}
    for code in codes:
        histories[code] = [{**bar.model_dump(mode='json'), 'symbol': {'code': code[:6], 'market': 'SH'}} for bar in data]
    protocol = json.loads((Path(__file__).resolve().parents[2] / 'docs/quant/executable-protocol-v1.json').read_text())
    return dict(schemaVersion='EXECUTABLE_ARCHIVE_V1', protocol=protocol, startDate=dates[0], endDate=dates[-1],
                priceBasis='RAW', minAverageAmount20d=1000000, universeEvidence='SYNTHETIC_COMPLETE_UNIVERSE',
                completeUniverseDates=dates[:-1:5],
                universe=[dict(signalDate=day, instrumentCode=code, availableAt=day+'T15:00:00', industry='SYNTHETIC',
                               eligible=True, evidence='SYNTHETIC_ELIGIBILITY') for day in dates[:-1:5] for code in codes],
                executionBars=[dict(tradeDate=day, instrumentCode=code, open=10, close=10, openState='TRADABLE',
                                    sourceEvidence='SYNTHETIC_OPEN_STATE_AND_PRICE') for day in dates for code in codes],
                corporateCoverage=[dict(instrumentCode=code, coverageFrom=dates[0], coverageThrough=dates[-1],
                                        exDates=[], evidence='SYNTHETIC_NO_ACTIONS') for code in codes],
                researchHistories=histories)


def test_baseline_generates_frozen_rule_without_inventing_training_dates():
    archive = BaselineArchive.model_validate(archive_dict())
    bundle, audit = freeze_baseline(archive)
    assert audit['status'] == 'READY'
    assert len(bundle['signals']) == 3
    assert all(row['signalMethod'] == 'FIXED_RULE' and row['trainingLabelsMaturedBefore'] is None for row in bundle['signals'])
    assert all(row['predictedPriceReturn'] is None for row in bundle['signals'][0]['candidates'])
    assert all(row['rankingScore'] == 0 for row in bundle['signals'][0]['candidates'])
    assert freeze_baseline(archive) == (bundle, audit)


def test_future_prices_and_execution_outcomes_do_not_change_frozen_scores():
    original = archive_dict()
    before, _ = freeze_baseline(BaselineArchive.model_validate(original))
    cutoff = original['startDate']
    for rows in original['researchHistories'].values():
        for row in rows:
            if row['trade_date'] > cutoff:
                for key in ('open', 'high', 'low', 'close'):
                    row[key] *= 2
    original['executionBars'][-1]['close'] = 1
    after, _ = freeze_baseline(BaselineArchive.model_validate(original))
    assert after['signals'][0] == before['signals'][0]


def test_preserves_missing_history_candidates_and_explicit_rejections():
    source = archive_dict()
    source['researchHistories'].pop('600001.SH')
    bundle, _ = freeze_baseline(BaselineArchive.model_validate(source))
    candidate = bundle['signals'][0]['candidates'][0]
    assert candidate['eligible'] is False
    assert candidate['rejectionReason'] == 'INSUFFICIENT_HISTORY'
    assert len(bundle['signals'][0]['candidates']) == 2


@pytest.mark.parametrize('mutation,match', [
    (lambda x: x['executionBars'].pop(), 'grid'),
    (lambda x: x['corporateCoverage'][0].update(coverageThrough=x['startDate']), 'coverage'),
    (lambda x: x['corporateCoverage'][0]['exDates'].append(x['startDate']), 'Corporate actions'),
    (lambda x: x['universe'][0].update(availableAt=x['startDate']+'T16:00:00'), 'cutoff'),
    (lambda x: x['universe'].append(x['universe'][0]), 'Duplicate'),
    (lambda x: x['completeUniverseDates'].pop(), 'complete universe'),
])
def test_blocks_unverifiable_archives(mutation, match):
    source = archive_dict()
    mutation(source)
    with pytest.raises(ValueError, match=match):
        freeze_baseline(BaselineArchive.model_validate(source))


def test_calendar_does_not_skip_exchange_holidays_or_guess_unknown_years():
    from datetime import date
    dates = trading_dates(date(2026, 9, 21), date(2026, 10, 12))
    assert date(2026, 9, 25) not in dates
    assert date(2026, 10, 1) not in dates
    with pytest.raises(ValueError):
        trading_dates(date(2025, 12, 22), date(2026, 1, 20))


def test_snapshot_loading_and_audit_are_read_only(tmp_path):
    path = tmp_path / 'snapshots.db'
    with sqlite3.connect(path) as connection:
        connection.execute('CREATE TABLE market_data_snapshot(capability TEXT, symbol_key TEXT, payload_json TEXT)')
        rows = archive_dict()['researchHistories']['600001.SH']
        connection.execute('INSERT INTO market_data_snapshot VALUES (?, ?, ?)',
                           ('DAILY_BARS', 'SH:600001', json.dumps({'data': rows})))
    before = path.read_bytes()
    report = audit_snapshots(path)
    assert report['status'] == 'BLOCKED'
    assert report['adjustmentCounts'] == {'QFQ': 1}
    history = load_research_histories(path, {'600001.SH'}, rows[70]['trade_date'])
    assert len(history['600001.SH']) == 71
    assert path.read_bytes() == before


def test_published_java_fixture_matches_python_output():
    bundle, _ = freeze_baseline(BaselineArchive.model_validate(archive_dict()))
    published = Path(__file__).resolve().parents[2] / 'docs/quant/examples/executable-baseline-input-synthetic.json'
    assert bundle == json.loads(published.read_text())


def test_cli_preserves_blocked_audit_and_rejects_overwrite(tmp_path):
    import os
    import subprocess
    import sys
    root = Path(__file__).resolve().parents[1]
    manifest = tmp_path / 'manifest.json'
    source = archive_dict()
    source['corporateCoverage'][0]['exDates'].append(source['startDate'])
    manifest.write_text(json.dumps(source))
    output = tmp_path / 'frozen'
    cmd = [sys.executable, str(root / 'scripts/freeze_executable_baseline.py'), '--manifest', str(manifest), '--output', str(output)]
    env = {**os.environ, 'PYTHONPATH': str(root / 'src')}
    result = subprocess.run(cmd, env=env, capture_output=True, text=True)
    assert result.returncode == 2
    assert json.loads((output / 'audit.json').read_text())['status'] == 'BLOCKED'
    assert not (output / 'input.json').exists()
    before = (output / 'audit.json').read_bytes()
    assert subprocess.run(cmd, env=env, capture_output=True).returncode != 0
    assert (output / 'audit.json').read_bytes() == before

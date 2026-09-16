from finscope_market_data.discovery.recall import evaluate_recall


def test_frozen_report_audits_outside_pool_winners_and_failed_events():
    report = {'as_of_date': '2026-09-14', 'candidates': [
        {'code':'605058', 'admitted':True, 'rejection_reasons':[]},
        {'code':'600001', 'admitted':True, 'rejection_reasons':[]}],
        'deep_evidence': [{'code':'600001'}],
        'strength_watchlist':[{'code':'600001','assessment':{'up_probability':.7}}]}
    def prices(close):
        return [{'trade_date':'2026-09-14','close':10,'high':10,'open':10,'adjustment':'QFQ'},
                {'trade_date':'2026-09-15','close':close,'high':11,'open':10.5,'adjustment':'QFQ'}]
    result = evaluate_recall(report, {'605058':prices(11), '600001':prices(9), '600002':prices(11)},
                             ['2026-09-14', '2026-09-15'], '2026-09-15')
    assert result['winner_count'] == 2
    assert result['deep_recall'] == 0
    assert result['event_loss_rate'] == 1
    assert result['event_fade_rate'] == 1
    assert {x['reason'] for x in result['missed_winners']} == {'NOT_DEEP_REVIEWED','OUTSIDE_CANDIDATE_POOL'}
    assert abs(result['event_brier'] - .49) < 1e-8
    assert evaluate_recall(report, {}, ['2026-09-14'], '2026-09-14')['status'] == 'PENDING'


def test_missing_next_session_is_not_replaced_by_later_price():
    report = {'as_of_date':'2026-09-14','candidates':[],'deep_evidence':[]}
    result = evaluate_recall(report, {'605058':[
        {'trade_date':'2026-09-14','close':10,'adjustment':'QFQ'},
        {'trade_date':'2026-09-16','close':11,'adjustment':'QFQ'}]},
        ['2026-09-14','2026-09-15','2026-09-16'], '2026-09-16')
    assert result['status'] == 'MISSING_OUTCOMES'
    assert result['missing_codes'] == ['605058']


def test_daily_archive_never_rewrites_original_signal_and_marks_backfill(tmp_path, monkeypatch):
    from finscope_market_data.discovery.recall_archive import DiscoveryRecallArchive
    import json
    archive = DiscoveryRecallArchive(tmp_path, tmp_path / 'not-needed.db')
    original = {'as_of_date':'2026-09-14','retrieved_at':'2026-09-16T16:00:00',
                'candidates':[], 'deep_evidence':[]}
    assert archive.update(original) == []
    archive.update({**original, 'retrieved_at':'2026-09-14T16:00:00'})
    assert json.loads((tmp_path / '2026-09-14.json').read_text()) == original
    monkeypatch.setattr('finscope_market_data.discovery.recall_archive.load_outcome_inputs',
        lambda *args: {'calendar':['2026-09-14','2026-09-15'], 'histories':{'605058':[
            {'trade_date':'2026-09-14','open':10,'high':10,'close':10,'adjustment':'QFQ'},
            {'trade_date':'2026-09-15','open':10,'high':11,'close':11,'adjustment':'QFQ'}]}})
    result = archive.update({**original, 'as_of_date':'2026-09-15'})
    assert result[0]['evidence_kind'] == 'RETROSPECTIVE'
    assert result[0]['winner_count'] == 1
    assert result[0]['input_fingerprint']
    assert len(list(tmp_path.glob('outcomes-*.json'))) == 1


def test_concurrent_freeze_publishes_only_complete_immutable_json(tmp_path):
    from concurrent.futures import ThreadPoolExecutor
    from finscope_market_data.discovery.evidence_io import freeze_json
    import json
    path = tmp_path / 'snapshot.json'
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda i: freeze_json(path, {'id':i,'data':'x' * 10000}), range(8)))
    assert all(result == results[0] for result in results)
    assert json.loads(path.read_text()) == results[0]
    assert list(tmp_path.iterdir()) == [path]

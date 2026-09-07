from dataclasses import replace
from finscope_market_data.forecast.joint_snapshot import JointSnapshotStore, apply_joint_snapshot
from finscope_market_data.forecast.next_session_types import NextSessionPrediction
from test_joint_dataset import histories
from test_joint_training import dataset
from finscope_market_data.forecast.joint_training import train_joint_snapshot
from finscope_market_data.forecast.joint_dataset import history_fingerprint


def test_store_roundtrip_and_unknown_or_stale_snapshot_keep_local(tmp_path):
    store = JointSnapshotStore(tmp_path / 'joint.json')
    assert store.load() is None
    store.save({'schemaVersion': 1, 'predictions': {}})
    assert store.load()['predictions'] == {}
    bars = histories()['000001']
    local = NextSessionPrediction(status='WATCH', as_of_date=bars[-1].trade_date, generated_at='2025-04-30T16:00:00',
                                  last_close=10, data_fingerprint='local', up_probability=.51)
    assert apply_joint_snapshot(local, bars, store.load()).up_probability == .51
    assert apply_joint_snapshot(local, bars, None).joint_model is None


def test_joint_evidence_survives_serialization_and_changed_history_is_rejected():
    bars = histories()['000001']
    data = dataset()
    data = replace(data, as_of=bars[-1].trade_date, current_features_by_code={'000001': (.5, 0, 0)},
                   history_fingerprints={'000001': history_fingerprint(bars)})
    snapshot = train_joint_snapshot(data)
    local = NextSessionPrediction(status='WATCH', as_of_date=data.as_of, generated_at='2025-04-30T16:00:00',
                                  last_close=10, data_fingerprint='local', up_probability=.51, brier_score=.3)
    result = apply_joint_snapshot(local, bars, snapshot)
    assert result.joint_model is not None
    assert result.model_dump(by_alias=True)['jointModel']['rankingScore'] == snapshot['predictions']['000001']['rankingScore']
    changed = [*bars[:-1], bars[-1].model_copy(update={'volume': bars[-1].volume + 1})]
    assert apply_joint_snapshot(local, changed, snapshot).joint_model is None
    assert apply_joint_snapshot(local.model_copy(update={'status': 'STALE_DATA'}), bars, snapshot).joint_model is None


def test_promotion_requires_beating_original_model_and_does_not_relabel_pooled_accuracy():
    bars = histories()['000001']
    data = dataset()
    snapshot = train_joint_snapshot(data)
    snapshot['asOfDate'] = bars[-1].trade_date
    selected = snapshot['predictions']['0']
    selected.update(historyFingerprint=history_fingerprint(bars), predictionEligible=True,
                    stockBrierScore=.2, stockBaselineBrierScore=.25)
    snapshot['predictions']['000001'] = selected
    snapshot['evidence'].update(classificationEligible=True, regressionMse=.001, baselineRegressionMse=.002)
    local = NextSessionPrediction(status='WATCH', as_of_date=bars[-1].trade_date, generated_at='2025-04-30T16:00:00',
                                  last_close=10, data_fingerprint='local', up_probability=.51, brier_score=.3)
    result = apply_joint_snapshot(local, bars, snapshot)
    assert result.joint_model.applied
    assert result.up_probability == selected['upProbability']
    assert result.data_fingerprint != local.data_fingerprint
    assert result.accuracy is None
    better_local = local.model_copy(update={'brier_score': .1})
    assert not apply_joint_snapshot(better_local, bars, snapshot).joint_model.applied
    snapshot['evidence']['evidenceKind'] = 'RETROSPECTIVE'
    retrospective = apply_joint_snapshot(local, bars, snapshot)
    assert not retrospective.joint_model.applied
    assert retrospective.up_probability == local.up_probability
    assert retrospective.data_fingerprint == local.data_fingerprint
    assert retrospective.joint_model.evidence_kind == 'RETROSPECTIVE'

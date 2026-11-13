from copy import deepcopy
from datetime import datetime, timedelta
from types import SimpleNamespace

from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.joint_store import OvernightJointStore
from finscope_market_data.overnight.joint_worker import OvernightJointWorker
from finscope_market_data.overnight.joint_dataset import JointProfile, PROTOCOL
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.universe import POOL_KEY


def test_partial_training_keeps_last_model_and_resumes_only_failed_component(tmp_path, monkeypatch):
    from finscope_market_data.overnight import joint_worker as worker
    store = OvernightStore(tmp_path / 'models.db')
    meta, models = AutomationStore(store), OvernightJointStore(store)
    now = [datetime(2026, 11, 12, 19)]
    profile = JointProfile('TAIL_ENTRY', '14:30', 20)
    old = models.publish({'protocol': PROTOCOL, 'profile': profile.dump(), 'createdAt': '2026-11-11T19:00:00',
        'labelsThrough': '2026-11-10T15:00:00', 'targets': {}, 'data': {}, 'context': {}}, {})
    codes = [f'600{i:03}.SH' for i in range(20)]
    meta.put(POOL_KEY, {'members': [{'instrumentCode': c} for c in codes], 'fingerprint': 'fixed', 'createdAt': '2026-01-01'})
    monkeypatch.setattr(store, 'coverage', lambda _: [{'instrumentCode': c, 'completeDays': 180} for c in codes])
    monkeypatch.setattr(worker, 'profiles', lambda _: [profile])
    row = {'target': '10:00', 'signalDate': '2026-11-10', 'instrumentCode': codes[0], 'actualReturn': .01,
           'exitAt': '2026-11-11T15:00:00', 'features': [0.] * 26}
    fitted = {'audit': {key: '2026-11-11T15:00:00' for key in ('trainingThrough', 'calibrationThrough', 'testThrough')}}
    calls = {'direction': 0, 'candidate': 0, 'data': 0}
    def panel(*args):
        calls['data'] += 1
        return [row], {}, {}
    def direction(*args):
        calls['direction'] += 1
        assert args[0][0]['trainingEligible']
        return deepcopy(fitted)
    def candidate(*args, **kwargs):
        calls['candidate'] += 1
        if calls['candidate'] == 1:
            raise RuntimeError('temporary training error')
        return deepcopy(fitted)
    monkeypatch.setattr(worker, 'build_panel', panel)
    monkeypatch.setattr(worker, 'build_direction_panel', lambda *args: ([row], {}))
    monkeypatch.setattr(worker, 'fit_target', lambda *args: deepcopy(fitted))
    monkeypatch.setattr(worker, 'fit_direction', direction)
    monkeypatch.setattr(worker, 'fit_context_direction', lambda *args: (None, []))
    monkeypatch.setattr(worker, 'build_actual_candidate_panel', lambda *args, **kwargs: ([row], {'days': [], 'records': []}))
    monkeypatch.setattr(worker, 'fit_up_selection', candidate)
    monkeypatch.setattr(worker, 'evaluate_market_groups', lambda *args, **kwargs: None)
    monkeypatch.setattr(worker, 'train_fresh_research', lambda *args, **kwargs: None)
    auto = SimpleNamespace(store=meta, service=SimpleNamespace(store=store), clock=lambda: now[0])
    task = OvernightJointWorker(auto, SimpleNamespace(models=models, contexts=None))
    task.tick()
    failed = meta.jobs(phase='JOINT')[0]
    assert failed['status'] == 'FAILED' and failed['failedComponents'] == ['CANDIDATE']
    assert models.latest(profile.key, now[0])['id'] == old['id']
    assert models.checkpoint(failed['artifactId'])[0]['trainingStatus'] == 'PARTIAL'
    now[0] += timedelta(minutes=2)
    task.tick()
    complete = meta.jobs(phase='JOINT')[0]
    assert complete['status'] == 'COMPLETED' and complete['attempts'] == 2
    assert calls == {'direction': 1, 'candidate': 2, 'data': 1}
    assert models.latest(profile.key, now[0])['id'] == complete['artifactId']
    task.tick()
    assert calls['candidate'] == 2
    store.save_bars(codes[0], [])
    assert meta.jobs(phase='JOINT')[0]['attempts'] == 2
    from test_overnight_history import bar
    store.save_bars(codes[0], [bar('2026-11-11T09:35:00')])
    task.tick()
    assert calls == {'direction': 1, 'candidate': 2, 'data': 1}
    now[0] += timedelta(minutes=30)
    task.tick()
    assert calls == {'direction': 2, 'candidate': 3, 'data': 2}
    assert len(meta.jobs(phase='JOINT')) == 2
    assert meta.jobs(phase='JOINT')[0]['status'] == 'COMPLETED'
    task.tick()
    assert calls['data'] == 2

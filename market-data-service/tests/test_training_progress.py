from datetime import datetime, timedelta

import pytest

from finscope_market_data.overnight.automation_store import AutomationStore
from finscope_market_data.overnight.store import OvernightStore
from finscope_market_data.overnight.training_checkpoint import TrainingCheckpoint


def test_job_history_uses_execution_time_before_applying_display_limit(tmp_path):
    meta = AutomationStore(OvernightStore(tmp_path / 'progress.db'))
    now = datetime(2026, 11, 14)
    for phase in ('JOINT', 'DISCOVER', 'HISTORY'):
        old = meta.claim(f'z-{phase}', now, {'phase': phase})
        meta.finish(old, now, status='COMPLETED')
        new = meta.claim(f'a-{phase}', now + timedelta(minutes=1), {'phase': phase})
        assert meta.jobs(phase=phase, limit=1)[0]['key'] == new['key']
    assert meta.jobs(limit=1)[0]['key'] == 'a-DISCOVER'
    assert meta.jobs(history=True, limit=1)[0]['key'] == 'a-HISTORY'


def test_progress_is_visible_during_training_and_survives_completion(tmp_path):
    meta = AutomationStore(OvernightStore(tmp_path / 'progress.db'))
    now = datetime(2026, 11, 14)
    job = meta.claim('one', now, {'phase': 'JOINT'})
    checkpoint = TrainingCheckpoint(progress=lambda stage, summary: meta.training_progress(job, now, stage, summary))
    def fitting():
        saved = meta.job('one')
        assert saved['trainingStage'] == 'DIRECTION'
        assert saved['componentStatus'] == {'DIRECTION': {'status': 'RUNNING'}}
        assert saved['status'] == 'RUNNING' and 'finishedAt' not in saved
        return {'model': 'private-coefficients'}
    checkpoint.run('DIRECTION', fitting)
    saved = meta.job('one')
    assert saved['componentStatus']['DIRECTION']['status'] == 'READY'
    assert 'private-coefficients' not in str(saved)
    meta.finish(job, now, status='COMPLETED')
    assert meta.job('one')['progressAt'] == now.isoformat()
    with pytest.raises(ValueError, match='租约'):
        meta.training_progress(job, now, 'STALE', {})


def test_expired_worker_cannot_overwrite_reclaimed_job_progress(tmp_path):
    meta = AutomationStore(OvernightStore(tmp_path / 'progress.db'))
    now = datetime(2026, 11, 14)
    old = meta.claim('one', now, {'phase': 'JOINT'})
    new = meta.claim('one', now + timedelta(minutes=11), {'phase': 'JOINT'})
    meta.training_progress(new, now, 'CANDIDATE', {})
    with pytest.raises(ValueError, match='租约'):
        meta.training_progress(old, now, 'DIRECTION', {})
    assert meta.job('one')['trainingStage'] == 'CANDIDATE'

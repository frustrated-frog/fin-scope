"""Shared daily predictions. Store JSON outputs rather than executable pickles."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Sequence

from finscope_market_data.models import DailyBar
from finscope_market_data.forecast.joint_dataset import history_fingerprint
from finscope_market_data.forecast.next_session_types import NextSessionJointEvidence, NextSessionPrediction


class JointSnapshotStore:
    def __init__(self, path: str | Path):
        self.path = Path(path)

    def load(self) -> dict | None:
        try:
            snapshot = json.loads(self.path.read_text())
            if snapshot.get('schemaVersion') == 1 and isinstance(snapshot.get('predictions'), dict):
                return snapshot
        except (OSError, ValueError, AttributeError):
            pass
        return None

    def save(self, snapshot: dict) -> None:
        payload = json.dumps(snapshot, ensure_ascii=False, allow_nan=False)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with NamedTemporaryFile(mode='w', dir=self.path.parent, delete=False) as handle:
            handle.write(payload)
            temporary = Path(handle.name)
        try:
            temporary.replace(self.path)
        finally:
            temporary.unlink(missing_ok=True)


def apply_joint_snapshot(local: NextSessionPrediction, bars: Sequence[DailyBar], snapshot: dict | None) -> NextSessionPrediction:
    if local.status not in {'READY', 'WATCH'}:
        return local
    if not snapshot or snapshot.get('asOfDate') != local.as_of_date:
        return local.model_copy(update={'warnings': [*local.warnings, '尚无同日联合模型，先运行股票发现生成共享截面预测']})
    code = bars[-1].symbol.code
    prediction = snapshot.get('predictions', {}).get(code)
    if not prediction or prediction.get('historyFingerprint') != history_fingerprint(bars):
        return local.model_copy(update={'warnings': [*local.warnings, '联合截面未覆盖该股票或行情已变化，本次使用单股模型']})
    evidence = snapshot.get('evidence', {})
    # The richer model must also beat this stock's existing rolling probability model.
    applied = bool(prediction.get('predictionEligible') and local.brier_score is not None
                   and prediction['stockBrierScore'] < local.brier_score)
    reason = '联合方向概率通过独立测试，并优于该股原概率模型' if applied else '联合方向保留为对照，尚未通过方向及该股原概率模型比较'
    if evidence.get('evidenceKind') == 'RETROSPECTIVE':
        applied = False
        reason = '该历史窗口已参与方法研发，仅作回归对照；等待新方法冻结后的完整前瞻验证窗口'
    return_applied = bool(applied and evidence['regressionMse'] < evidence['baselineRegressionMse']
                          and evidence['intervalCoverage'] >= .7)
    if applied and not return_applied:
        reason += '；收益幅度保留原单股模型'
    fields = {key: prediction[key] for key in ('rankingScore', 'rankingPercentile', 'stockValidationCount',
              'stockBrierScore', 'stockBaselineBrierScore', 'upProbability', 'expectedReturn')}
    joint = NextSessionJointEvidence.model_validate({**evidence, **fields, 'applied': applied, 'returnApplied': return_applied, 'reason': reason})
    if not applied:
        return local.model_copy(update={'joint_model': joint})
    probability = prediction['upProbability']
    updates = dict(joint_model=joint, model_code=evidence['selectedClassifier'], model_version=snapshot['modelVersion'],
        status='READY', decision='UP' if probability >= .55 else 'DOWN' if probability <= .45 else 'ABSTAIN',
        data_fingerprint=hashlib.sha256((local.data_fingerprint + snapshot['dataFingerprint']).encode()).hexdigest(),
        up_probability=probability, training_through=prediction['trainingThrough'],
        calibration_through=prediction['calibrationThrough'], training_sample_count=prediction['trainingSampleCount'],
        calibration_sample_count=prediction['calibrationSampleCount'], validation_sample_count=prediction['stockValidationCount'],
        brier_score=prediction['stockBrierScore'], baseline_brier_score=prediction['stockBaselineBrierScore'],
        # These two aggregate statistics are kept in joint evidence, never mislabeled as single-stock metrics.
        accuracy=None, interval_coverage=None, direction_evaluation=None,
        warnings=['联合模型使用同日股票截面及历史价量；60 日独立测试通过不保证未来准确率',
                  '按当前可用股票池进行条件性比较，未消除幸存者偏差；收盘涨跌不等于可成交收益'])
    if return_applied:
        updates.update(expected_return=prediction['expectedReturn'], lower_return=prediction['lowerReturn'],
                       upper_return=prediction['upperReturn'])
    else:
        updates['interval_coverage'] = local.interval_coverage
        updates['warnings'].append('收益幅度与区间沿用原单股模型；上述训练与校准时点对应方向模型')
    return local.model_copy(update=updates)

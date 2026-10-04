import type { OvernightTarget } from './overnightTypes';

const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const conclusions: Record<string, string> = {
  INSUFFICIENT_VALIDATION: '独立验证样本不足',
  BASELINE_NOT_BEATEN: '尚未超过历史基线',
  RECENT_DEGRADATION: '近期效果退化',
  CALIBRATION_UNAVAILABLE: '概率校准证据不足',
  INTERVAL_UNRELIABLE: '误差范围覆盖不足',
  HISTORICAL_EDGE: '历史对照改善，等待前瞻验证',
};

export function OvernightTargetEvidence({ target }: { target: OvernightTarget }) {
  const evidence = target.reliability;
  if (!evidence) {
    return null;
  }
  return <div className="overnight-target-evidence">
    <p className="overnight-reliability" data-improved={evidence.status === 'HISTORICAL_EDGE'}>
      {conclusions[evidence.status] ?? '可靠性待确认'}
    </p>
    <p>历史基线盈利比例 {percent(target.baselineProbability)}</p>
    <details><summary>查看独立验证依据</summary>
      {target.probabilitySource === 'HISTORICAL_BASELINE' && <p>未采用的模型概率 {percent(target.modelUpProbability)}；以下诊断评价原模型，主参考值使用当时冻结的历史基线。</p>}
      <dl>
        <div><dt>概率误差改善</dt><dd>{percent(evidence.brierSkill)}</dd></div>
        <div><dt>最近 {evidence.recentCount ?? 0} 次改善</dt><dd>{percent(evidence.recentBrierSkill)}</dd></div>
        <div><dt>校准前 / 后 Brier</dt><dd>{evidence.rawBrier?.toFixed(3) ?? '—'} / {target.brierScore?.toFixed(3) ?? '—'}</dd></div>
        <div><dt>模型 / 基线方向命中</dt><dd>{percent(target.directionAccuracy)} / {percent(evidence.baselineAccuracy)}</dd></div>
        <div><dt>{percent(evidence.nominalCoverage)} 区间实际覆盖</dt><dd>{percent(evidence.intervalCoverage)}</dd></div>
        <div><dt>收益预测平均绝对误差</dt><dd>{percent(evidence.expectedReturnMae)}</dd></div>
      </dl>
      <p>改善为正表示概率误差低于当时的历史基线；为负表示更差。校准和区间均先冻结，再对后续样本验证。</p>
      <p>训练 {target.trainingCount ?? '—'} 例 · 独立校准 {target.calibrationCount ?? '—'} 例 · 顺序验证 {evidence.count} 例</p>
      <p>训练标签截至 {target.trainingThrough?.replace('T', ' ') ?? '—'}<br />校准标签截至 {target.calibrationThrough?.replace('T', ' ') ?? '—'}</p>
      <p>验证信号日 {evidence.from ?? '—'} 至 {evidence.through ?? '—'}；这些是历史顺序验证，真实前瞻成绩见下方验收区。</p>
      {target.calibrationStatus !== 'FITTED' && <p>{target.calibrationReason ?? '独立校准未完成，当前保留原始模型概率。'}</p>}
    </details>
  </div>;
}

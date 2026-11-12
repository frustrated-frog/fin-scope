import type { OvernightJointState } from './overnightJointTypes';
import type { OvernightMode } from './overnightTypes';
import './OvernightProductionProgress.css';

const phases: Record<string, string> = {
  ACCUMULATING: '等待真实结果', CHECKPOINT_FAILED: '首批验收未通过',
  QUALIFIED: '达到启用条件', MONITORING_DEGRADED: '近期退化，暂停采用',
};
const names: Record<string, string> = { ROLLING: '真实候选择模', ADAPTIVE: '方向滚动择模' };

export function OvernightProductionProgress({ state, mode }: { state: OvernightJointState; mode: OvernightMode }) {
  const monitor = state.productionDirection;
  if (monitor?.protocol !== 'overnight-production-direction-v1'
      || monitor.target !== 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE') {
    return null;
  }
  const groups = monitor.groups.filter(group => group.mode === mode);
  return <section className="overnight-production" aria-label="新方案启用进度">
    <header><h5>新方案何时采用</h5><span>真实结果决定</span></header>
    {!groups.length && <p>等待新版预测的次日结果，历史补数不计入启用进度。</p>}
    <div className="overnight-production-grid">{groups.map(group => <article key={group.key}>
      <header><b>{group.cutoff} · {names[group.group] ?? group.group}</b>
        <span data-qualified={group.eligible}>{phases[group.status] ?? '等待核验'}</span></header>
      <div className="overnight-production-count"><strong>{Math.min(group.dayCount, group.requiredDays)}</strong>
        <span>/ {group.requiredDays} 个完整交易日</span></div>
      <progress value={Math.min(group.dayCount, group.requiredDays)} max={group.requiredDays}
        aria-label={`${group.cutoff} ${names[group.group] ?? group.group}验收进度`} />
      <p>{group.status === 'CHECKPOINT_FAILED' ? '本版首批验收已固定，后续好成绩不会覆盖失败结果。'
        : group.status === 'QUALIFIED' ? '下一次预测仍会检查模型与数据；是否采用以该次记录为准。'
          : group.status === 'MONITORING_DEGRADED' ? '首批曾通过，近期覆盖或表现未达标，继续使用原判断。'
            : '同一天多只股票只算一天；还需优于原判断和简单基准。'}</p>
      <small>近期完整日期覆盖 {(group.recentCoverage * 100).toFixed(0)}% · 首批验收{group.checkpointPassed ? '通过' : group.status === 'ACCUMULATING' ? '未到期' : '未通过'}</small>
    </article>)}</div>
    <p>首批验收固定使用 60 个完整交易日；之后持续检查最近 20 个计划交易日。缺数、验证过期或表现退化时保留原判断。</p>
  </section>;
}

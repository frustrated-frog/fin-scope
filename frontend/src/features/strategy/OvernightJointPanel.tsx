import type { OvernightJointState } from './overnightJointTypes';
import type { OvernightAutomationJob, OvernightMode, OvernightTarget } from './overnightTypes';
import './OvernightJointPanel.css';
import { OvernightDirectionSummary } from './OvernightDirectionPanel';

const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const targetName = (target: string) => target === 'OPEN' ? '次日开盘' : target === 'CLOSE' ? '次日收盘' : `次日 ${target}`;
const labels: Record<string, string> = {
  ACCUMULATING: '积累对照', QUALIFIED: '通过对照', CHECKPOINT_FAILED: '未通过对照', MONITORING_DEGRADED: '近期退化，已回退',
};

export function OvernightJointPanel({ state, mode }: { state?: OvernightJointState; mode: OvernightMode }) {
  if (!state) {
    return null;
  }
  const models = state.models.filter(model => model.profile.mode === mode);
  const groups = state.forward?.groups.filter(group => group.mode === mode) ?? [];
  const active = groups.filter(group => group.eligible && models.some(model =>
    model.profile.cutoff === group.cutoff && model.profile.costBps === group.costBps)).length;
  const failed = state.jobs.find(job => job.mode === mode && ['FAILED', 'INSUFFICIENT_DATA'].includes(job.status));
  const title = !models.length ? '公共样本正在积累' : active ? `${active} 个退出时点通过前瞻对照` : '联合模型正在并行验证';
  return <section className="overnight-joint" aria-label="联合预测能力">
    <header><div><span className="overnight-joint-label">预测能力</span><h5>{title}</h5></div>
      <span className="overnight-joint-phase">{active ? '按时点采用' : models.length ? '保留现有判断' : '后台自动准备'}</span></header>
    <p>{!models.length ? '后台持续补齐公共股票池的分钟历史，满足条件后自动训练。'
      : '联合学习多只股票的走势、成交变化和前一交易日的样本池环境，再与原有判断逐日对照。'}</p>
    <dl className="overnight-joint-facts"><div><dt>公共股票池</dt><dd>{state.poolSize}<small> / {state.targetSize} 只</small></dd></div>
      <div><dt>已有 140 日完整行情</dt><dd>{state.readySymbols}<small> 只</small></dd></div>
      <div><dt>当前模式的联合模型</dt><dd>{models.length}<small> 个决策时点</small></dd></div></dl>
    {!models.length && <p className="overnight-joint-hint">至少 {state.minimumSymbols} 只具备足够历史后尝试训练；行情覆盖达标不等于预测已有效。</p>}
    <OvernightDirectionSummary state={state} mode={mode} />
    <details><summary>样本范围与新旧模型对照 <span>{groups.length ? `${groups.length} 组记录` : '等待前瞻结果'}</span></summary>
      <p>验收需要首批 {state.forward?.requiredDays ?? 60} 个结果完整的前瞻交易日。同一天的多只股票合计为一天；历史补数不增加这项进度。</p>
      {groups.length > 0 && <div className="overnight-joint-table" tabIndex={0} role="region" aria-label="前瞻对照成绩"><table>
        <thead><tr><th>决策 / 退出</th><th>独立交易日</th><th>联合 / 原有概率误差</th><th>联合 / 原有命中</th><th>结论</th></tr></thead>
        <tbody>{groups.map(group => <tr key={group.key}>
          <th scope="row">{group.cutoff} → {targetName(group.target)}<small>成本 {group.costBps} 基点 · 可核验 {percent(group.coverage)}</small></th>
          <td>{group.dayCount} / {group.requiredDays}</td><td>{group.metrics?.brierScore.toFixed(3) ?? '—'} / {group.metrics?.comparisons.INCUMBENT?.brierScore.toFixed(3) ?? '—'}</td>
          <td>{percent(group.metrics?.accuracy)} / {percent(group.metrics?.comparisons.INCUMBENT?.accuracy)}</td>
          <td data-passed={group.eligible}>{labels[group.status] ?? '待确认'}</td></tr>)}</tbody></table></div>}
      {groups.length > 0 && <p className="overnight-joint-scroll-hint">左右滑动表格，查看完整对照。</p>}
      <p>概率误差越低越好。还需同时超过历史盈利比例、通过按日期分块的比较、满足净收益与数据覆盖条件；未通过首批验收不会不断重试直到通过。</p>
      {models.map(model => <div className="overnight-joint-model" key={model.id}><b>{model.profile.cutoff} · {model.data.symbolCount} 只股票参与学习</b>
        <span>{model.data.dayCount} 个历史信号日 · 数据至 {model.labelsThrough.slice(0, 10)}</span>
        <small>最近训练 {model.createdAt.slice(0, 16).replace('T', ' ')}。历史检验成绩仅供诊断。</small></div>)}
      {failed && <p role="status">{failed.reason ?? '联合训练尚未完成，后台将继续积累。'}</p>}
      <p>{state.scope ?? '从已有日线缓存按板块均衡选取固定股票池，保留后续缺失成员。'}</p>
      <p>{state.limitation ?? '公共样本池尚不代表历史全市场，不能消除退市与覆盖偏差。'} 行业环境和公司行为尚未完整核验。</p>
      {state.coverageAt && <small>覆盖统计更新于 {state.coverageAt.slice(0, 16).replace('T', ' ')} · 训练避开 13:00—18:00</small>}
    </details>
  </section>;
}

export function OvernightJointRanking({ job }: { job: OvernightAutomationJob }) {
  const ranking = job.ranking;
  if (!ranking || ranking.status === 'WAITING_MODEL') {
    return null;
  }
  const active = ranking.status === 'ACTIVE';
  return <div className="overnight-joint-ranking" aria-label={`${job.cutoff}净收益排序`}>
    <b>{job.cutoff} · {active ? ranking.opportunityStatus === 'NO_QUALIFIED' ? '本轮没有通过净收益与风险条件的候选' : '通过对照的净收益排序' : '净收益排序仍在验证'}</b>
    <p>{active ? '固定比较次日 10:00 的退出路径，结合盈利概率、成本和下跌风险，每轮最多保留 3 只。' : '研究排序与现有判断同时留档，暂不作为优先候选依据。'}</p>
    {active && ranking.candidates.length > 0 && <ol>{ranking.candidates.map(row => <li key={row.instrumentCode}>
      <span>{job.candidates?.find(candidate => candidate.instrumentCode === row.instrumentCode)?.instrumentName ?? row.instrumentCode}</span>
      <span>预期净收益 <b>{percent(row.expectedNetReturn)}</b></span><span>跌超 2% 的概率 {percent(row.downsideProbability)}</span></li>)}</ol>}
  </div>;
}

export function OvernightJointEvidence({ target }: { target: OvernightTarget }) {
  const joint = target.joint;
  if (!joint || joint.status !== 'AVAILABLE') {
    return null;
  }
  return <details className="overnight-joint-evidence"><summary>{joint.adopted ? '联合模型 · 已通过前瞻对照' : '联合模型 · 并行观察'}</summary>
    <dl><div><dt>联合模型盈利概率</dt><dd>{percent(joint.upProbability)}</dd></div>
      <div><dt>预测净收益</dt><dd>{percent(joint.expectedNetReturn)}</dd></div>
      <div><dt>净收益低于 -2% 的概率</dt><dd>{joint.downsideCalibrationStatus === 'FITTED' ? percent(joint.downsideProbability) : '风险样本不足'}</dd></div>
      <div><dt>前瞻对照</dt><dd>{joint.forwardDays} / 60 个交易日</dd></div></dl>
    <p>{labels[joint.forwardStatus] ?? '等待对照'}。{joint.adopted ? '当前主参考值来自联合模型。' : '当前主参考值仍沿用原有判断。'}</p>
  </details>;
}

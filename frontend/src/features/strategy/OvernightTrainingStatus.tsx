import type { OvernightJointState } from './overnightJointTypes';
import type { OvernightMode } from './overnightTypes';

const stages: Record<string, string> = { PREPARE: '准备训练样本', TRADE: '训练收益模型', DIRECTION: '训练涨跌模型',
  CONTEXT: '训练环境对照', CANDIDATE_INPUTS: '核对实际候选', CANDIDATE: '逐日回放候选',
  RANKING: '训练候选排序', MARKET: '验证市场特征', FRESH: '训练近期方向模型', PUBLISH: '保存完整模型' };

export function OvernightTrainingStatus({ state, mode }: { state: OvernightJointState; mode: OvernightMode }) {
  if (!state.trainingPolicy) {
    return null;
  }
  const cutoffs = mode === 'TAIL_ENTRY' ? ['14:30', '14:45'] : ['15:00'];
  const models = state.models.filter(model => model.profile.mode === mode);
  const jobs = state.jobs.filter(job => job.mode === mode)
    .sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? ''));
  const rows = cutoffs.map(cutoff => {
    const model = models.find(model => model.profile.cutoff === cutoff);
    const job = jobs.find(job => job.cutoff === cutoff);
    const ready = model?.trainingPolicy === state.trainingPolicy && model?.trainingStatus === 'COMPLETED';
    const status = job?.status === 'RUNNING' ? stages[job.trainingStage ?? ''] ?? '训练进行中'
      : job?.status === 'FAILED' ? '本轮训练未完成' : ready ? '新版模型已就绪' : '等待新版训练';
    const done = Object.values(job?.componentStatus ?? {}).filter(item => ['READY', 'INSUFFICIENT_DATA'].includes(item.status)).length;
    return { cutoff, model, job, ready, status, done };
  });
  return <details className="overnight-direction-summary" aria-label="当前训练状态">
    <summary>训练状态 <span>{rows.filter(row => row.ready).length} / {cutoffs.length} 个时点新版就绪</span></summary>
    {rows.map(row => <div className="overnight-direction-summary-row" key={row.cutoff}>
      <b>{row.cutoff} · {row.status}</b>
      <span>{row.model ? row.ready ? '当前使用新版模型' : '当前保留上一版本' : '尚无可用模型'}</span>
      {row.job?.status === 'RUNNING' && <small>{row.job.componentStatus && <>已结束 {row.done} 项检查与训练。</>}
        {row.job.progressAt ? `最近进度 ${row.job.progressAt.replace('T', ' ').slice(0, 19)}` : '等待训练阶段回报。'}</small>}
      {row.model && <small>当前模型生成于 {row.model.createdAt.replace('T', ' ').slice(0, 16)} · 样本结果截至 {row.model.labelsThrough.slice(0, 10)}</small>}
    </div>)}
    <p>新版就绪仅表示训练完成。新方案是否用于某次判断，仍由独立前瞻验收决定。</p>
  </details>;
}

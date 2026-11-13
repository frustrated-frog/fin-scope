import type { CandidateTraining, CandidateTrainingComparison } from './overnightCandidateTypes';

const names: Record<string, string> = { CANDIDATE: '真实候选专训', POOL_ROLLING: '原公共池方案',
  POOL_LOGISTIC: '公共池逻辑回归', POOL_PRIOR: '公共池历史比例', CANDIDATE_PRIOR: '候选历史比例' };
const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;

export function OvernightCandidateTraining({ training, comparison }: {
  training: CandidateTraining; comparison?: CandidateTrainingComparison;
}) {
  const actual = comparison?.arms.CANDIDATE;
  return <section aria-label="真实尾盘候选训练与回放">
    <p><b>真实候选直接参加训练</b></p>
    <p>{training.trainingDays} 日 · {training.trainingRows} 条训练样本，其中 {training.outsidePoolRows} 条来自原公共池之外。
      已知除权影响排除 {training.excludedRows} 条，公司行为未核验 {training.unverifiedActionRows} 条。</p>
    {training.status !== 'READY' && <p>至少需要 {training.minimumDays} 日、{training.minimumRows} 条记录，且涨跌两类各覆盖至少两日；目前保留基准，后台继续积累。</p>}
    {actual && comparison ? <>
      <p>以下是 {comparison.firstDate} 至 {comparison.lastDate} 的 {comparison.dayCount} 个完整日期、{comparison.sampleCount} 条同名单对照。
        另有 {comparison.warmupDays} 日用于预热；计划已到期 {comparison.plannedDays} 日。</p>
      <div className="overnight-candidate-audit-table overnight-candidate-support-table" tabIndex={0} role="region" aria-label="专训历史对照，可横向滚动">
        <table><caption>历史回放 · 每一天只用此前已到期结果训练</caption>
          <thead><tr><th>方案</th><th>涨跌正确率</th><th>前三名上涨</th><th>概率误差 ↓</th></tr></thead>
          <tbody>{Object.entries(names).map(([key, name]) => {
            const metric = comparison.arms[key];
            return <tr key={key}><th>{name}</th><td>{percent(metric?.accuracy)}</td>
              <td>{percent(metric?.topK.precision)}</td><td>{metric?.brierScore.toFixed(3) ?? '—'}</td></tr>;
          })}<tr><th>当日涨幅排序</th><td>—</td><td>{percent(comparison.momentum?.precision)}</td><td>—</td></tr></tbody>
        </table>
      </div>
      <p>专训在 {actual.calls.selectedDays} / {comparison.dayCount} 日报看涨，共 {actual.calls.selectionCount} 条，命中 {percent(actual.calls.precision)}。
        概率误差越低越好；同分股票按平均权重比较。</p>
      {comparison.lastFiveDates.arms.CANDIDATE && <p>时间末段 {comparison.lastFiveDates.dayCount} 日：专训涨跌正确率 {percent(comparison.lastFiveDates.arms.CANDIDATE.accuracy)}，
        原公共池方案 {percent(comparison.lastFiveDates.arms.POOL_ROLLING?.accuracy)}。</p>}
      <p>这是历史开发诊断，样本已经用于研究；不等于上线后的准确率。主判断是否替换仍由独立真实前瞻验收决定。</p>
    </> : <p>尚无训练完成后的完整回放日，不展示虚构的准确率。</p>}
  </section>;
}

import type { OvernightMode } from './overnightTypes';
import type { WeeklyDirectionAudit } from './weeklyDirectionTypes';
import './OvernightWeeklyPerformance.css';

const percent = (n?: number | null) => n == null ? '—' : `${(n * 100).toFixed(1)}%`;
const modelNames: Record<string, string> = { PRIMARY: '当时主判断', FRESH: '最新样本', CALIBRATED: '概率校准', INDEX: '指数增强' };

export function OvernightWeeklyPerformance({ audit, mode }: { audit?: WeeklyDirectionAudit; mode: OvernightMode }) {
  if (!audit || audit.protocol !== 'overnight-weekly-diagnostics-v1' || audit.target !== 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE') {
    return null;
  }
  const archive = audit.archives.find(row => row.mode === mode);
  const groups = audit.groups.filter(row => row.mode === mode);
  const missed = audit.windows.filter(row => !['COMPLETED', 'EMPTY'].includes(row.status));
  return <section className="overnight-weekly" aria-label="本周看涨识别">
    <header><div><span>本周 · {audit.periodStart.slice(5)} — {audit.periodEnd.slice(5)}</span><h4>找到了多少真正上涨的股票</h4></div>
      <p>已留档 <b>{archive?.recordCount ?? 0}</b> 条 · 待次日结果 <b>{archive?.pendingCount ?? 0}</b> 条</p></header>
    {mode === 'TAIL_ENTRY' && missed.length > 0 && <details className="overnight-weekly-gaps">
      <summary>{missed.length} / {audit.windows.length} 个已结束尾盘窗口未完整完成</summary>
      {missed.map(row => <p key={`${row.signalDate}|${row.cutoff}`}><b>{row.signalDate.slice(5)} {row.cutoff}</b> · {row.reason || '暂无完整执行记录'}
        {row.diagnostic?.gapSeconds != null && <> · 调度间隔 {Math.round(row.diagnostic.gapSeconds)} 秒</>}</p>)}
      <p>缺失窗口单独记录，不把盘后补跑计入真实预测成绩。</p>
    </details>}
    {!groups.length && <p>本周尚无已到期的有效名单。未到期和漏跑都不计作预测正确。</p>}
    <div className="overnight-weekly-grid">{groups.map(group => {
      const detail = group.upDetection;
      const score = detail.metrics;
      return <article key={group.key}>
        <header><h5>{group.cutoff} → 次日收盘</h5><span>{group.completeDays} 个完整日期 · 尚在验证</span></header>
        <dl className="overnight-weekly-metrics">
          <div><dt>看涨命中率</dt><dd>{percent(score?.upPrecision)}</dd><small>看涨后，实际上涨的比例</small></div>
          <div><dt>上涨识别率</dt><dd>{percent(score?.upRecall)}</dd><small>实际上涨中，提前识别的比例</small></div>
        </dl>
        {score ? <>
          <p>看涨 {score.predictedUpCount} 条，命中 {score.trueUpCount} 条；实际上涨 {score.actualUpCount} 条，漏判 {score.missedUpCount} 条。</p>
          <p>看涨覆盖 {percent(score.predictedUpRate)} · 无看涨候选 {detail.noUpDays} / {score.dayCount} 日</p>
          <div className="overnight-weekly-baseline"><span>整体猜对 {percent(score.accuracy)}</span><span>全判不涨 {percent(score.alwaysNotUpAccuracy)}</span></div>
        </> : <p>尚无完整日期可统计；缺失数据不会变成 0% 命中率。</p>}
        <small>已核验 {group.settledCount} / {group.scheduledCount} 条已到期计划记录；仅完整日期进入上述成绩。</small>
        <details><summary>逐日结果与漏判分析</summary>
          <div className="overnight-weekly-table"><table><caption>逐日核验 · 固定 50% 看涨阈值</caption>
            <thead><tr><th>日期</th><th>看涨</th><th>命中</th><th>漏判上涨</th><th>全判不涨</th></tr></thead>
            <tbody>{detail.daily.map(row => <tr key={row.signalDate}><th>{row.signalDate.slice(5)}</th><td>{row.predictedUpCount}</td><td>{row.trueUpCount}</td><td>{row.missedUpCount}</td><td>{percent(row.alwaysNotUpAccuracy)}</td></tr>)}</tbody>
          </table></div>
          <p>新旧方案在同一批完整名单上比较：{detail.pairedDays} 日。缺少任一方案的日期不参与对照。</p>
          <div className="overnight-weekly-table"><table><caption>独立对照 · 不自动切换主判断</caption>
            <thead><tr><th>方案</th><th>看涨命中</th><th>上涨识别</th><th>看涨覆盖</th></tr></thead>
            <tbody>{Object.entries(modelNames).map(([key, label]) => <tr key={key}><th>{label}</th><td>{percent(detail.comparisons[key]?.upPrecision)}</td><td>{percent(detail.comparisons[key]?.upRecall)}</td><td>{percent(detail.comparisons[key]?.predictedUpRate)}</td></tr>)}</tbody>
          </table></div>
          <p>漏判上涨记录 {detail.missedExampleTotal} 条，按日期展示最近 {detail.missedExamples.length} 条。以下是当时保存的输入，不证明因果。</p>
          {detail.missedExamples.map(row => <div className="overnight-weekly-example" key={`${row.signalDate}|${row.instrumentCode}`}>
            <b>{row.signalDate.slice(5)} · {row.instrumentCode}</b><span>当时上涨概率 {percent(row.probability)}</span>
            <small>原始 {percent(row.rawProbability)} / 校准 {percent(row.calibratedProbability)} · 判断前指数均涨跌 {percent(row.indexReturn)}</small>
            <small>模型生成 {row.trainedAt?.replace('T', ' ').slice(0, 16) ?? '未记录'} · 训练样本截止 {row.trainingThrough?.slice(0, 10) ?? '旧档案未记录'}</small>
          </div>)}
        </details>
      </article>;
    })}</div>
    <p className="overnight-weekly-footnote">统计按上涨概率 ≥ 50% 划分看涨，按日期等权；条数为实际记录数。同一股票在不同窗口分别核验。没有看涨记录时命中率为“—”，不会记作全对。最近刷新 {audit.computedAt.replace('T', ' ').slice(0, 16)}。</p>
  </section>;
}

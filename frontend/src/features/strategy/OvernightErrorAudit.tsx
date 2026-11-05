import type { OvernightMode } from './overnightTypes';
import type { ErrorAudit } from './overnightErrorTypes';
import './OvernightErrorAudit.css';
const percent = (value?: number | null) => value == null ? '—' : `${(value * 100).toFixed(1)}%`;
const labels: Record<string, string> = { signalDate: '判断日期', marketState: '研究池状态', source: '分钟数据来源',
  lane: '入选方式', board: '股票板块', confidence: '预测强度', UNKNOWN: '缺少记录', UP: '研究池偏强',
  DOWN: '研究池偏弱', MIXED: '研究池震荡', GEM: '创业板', MAIN: '主板', HIGH: '较明确', LOW: '接近五五开',
  MOMENTUM: '量价强势', PRICE_STRENGTH: '价格强度', BROAD_RESEARCH: '分散观察', HOLDING: '已有持仓',
  MISSING_PREDICTION: '没有按时预测', MISSING_MODEL: '模型或特征不足', MISSING_OUTCOME: '次日结果缺失',
  LEGACY_PROTOCOL: '旧版口径，单独排除' };

export function OvernightErrorAudit({ audit, mode }: { audit?: ErrorAudit; mode: OvernightMode }) {
  const groups = audit?.target === 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE' ? audit.groups.filter(g => g.mode === mode) : [];
  return <section className="overnight-error-audit" aria-label="错判与候选质量">
    <div className="overnight-auto-section-title"><h5>选出的股票，后来怎么样</h5><span>{audit ? `更新于 ${audit.computedAt.slice(11, 16)}` : '后台自动分析'}</span></div>
    {!groups.length && <p>等待已到期的候选与预测完成对齐。缺少历史快照时不倒填成绩。</p>}
    <div className="overnight-performance-grid">{groups.map(group => <article className="overnight-score" key={group.key}>
      <header><b>{group.cutoff} 判断</b><span>{group.completeDays} 个完整核验日</span></header>
      <div className="overnight-score-comparison"><div><small>看涨前 3 只 · 实际上涨比例</small><strong>{percent(group.upCandidates.precision)}</strong></div></div>
      <p>{group.upCandidates.selectedDays} 日有看涨候选。仅用整批结果齐全的日期，未完成项不当作成功。</p>
      <dl><div><dt>新版可核验 / 计划研判</dt><dd>{group.settledCount} / {group.scheduledCount}</dd></div>
        <div><dt>看涨却未涨</dt><dd>{group.metrics?.falseUpCount ?? '—'}</dd></div>
        <div><dt>漏掉的上涨</dt><dd>{group.metrics?.missedUpCount ?? '—'}</dd></div></dl>
      <details><summary>查看错判分布与数据缺口</summary>
        <p>这些是分组表现，不代表涨跌的因果解释；不足 20 个日期的分组标为小样本。</p>
        {Object.entries(group.missing).map(([reason, count]) => <p key={reason}>{labels[reason] ?? reason}：{count} 只次</p>)}
        {!!group.slices.length && <div className="overnight-error-table"><table><thead><tr><th>分组</th><th>方向命中</th><th>看涨未涨</th><th>漏涨</th><th>样本</th></tr></thead>
          <tbody>{group.slices.map(row => <tr key={`${row.dimension}-${row.value}`}><td>{labels[row.dimension]} · {labels[row.value] ?? row.value}</td>
            <td>{percent(row.accuracy)}</td><td>{row.falseUpCount}</td><td>{row.missedUpCount}</td>
            <td>{row.dayCount} 日 / {row.sampleCount} 例{row.status === 'SMALL_SAMPLE' ? ' · 小样本' : ''}</td></tr>)}</tbody></table></div>}
        <p>看涨候选专用方案：{group.selectionComparison.pairedDays} 个完整同日对照。原方案 / 新方案的候选上涨比例：{percent(group.selectionComparison.incumbent.precision)} / {percent(group.selectionComparison.challenger.precision)}。仍在独立验证。</p>
        {group.rankingComparison && <div aria-label="上涨排序独立对照">
          <p><b>哪种方法更会选出上涨股票</b></p>
          {group.rankingComparison.pairedDays > 0 ? <>
            <p>{group.rankingComparison.pairedDays} 个完整同日对照 · {group.rankingComparison.pairedRows} 只次。各方法从同一候选池取前 3 只。</p>
            <div className="overnight-error-table"><table>
              <thead><tr><th>选择方法</th><th>次日实际上涨比例</th></tr></thead>
              <tbody>{([['RANKER', '上涨排序'], ['CLASSIFIER', '同输入分类'], ['MOMENTUM', '当日涨幅排序'], ['UNIFORM', '同池平均']] as const).map(([key, name]) =>
                <tr key={key}><td>{name}</td><td>{percent(group.rankingComparison?.results[key].precision)}</td></tr>)}</tbody>
            </table></div>
            <p>这是已入选股票的实际结果，不是单只股票的上涨概率；新方案仍在验证，不替换主判断。</p>
          </> : <p>等待新版排序与分类预测同时留档，并完成次日核验。暂不显示准确率。</p>}
          {group.rankingComparison.missingPairedDays > 0 && <p>{group.rankingComparison.missingPairedDays} 个结果完整日缺少整批配对预测，未计入对照。</p>}
        </div>}
      </details>
    </article>)}</div>
  </section>;
}

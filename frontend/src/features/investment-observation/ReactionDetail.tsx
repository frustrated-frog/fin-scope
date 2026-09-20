import type { ReactionSample } from './reactionTypes';
import { dateTime, eventLabels, pathLabels, signed, sourceHref, statusLabels } from './reactionTypes';
import { ReactionChart } from './ReactionChart';

export function ReactionDetail({ sample, samples, busy, onRefresh, onArchive, onCompare, onResearch }: {
  sample: ReactionSample;
  samples: ReactionSample[];
  busy: boolean;
  onRefresh: () => void;
  onArchive: () => void;
  onCompare: () => void;
  onResearch?: (question: string) => void;
}) {
  const calculation = sample.calculation;
  const href = sourceHref(sample.sourceUrl);
  const overlaps = calculation ? samples.filter(other => other.id !== sample.id && (other.sourceIdentity || other.majorEventId) !== (sample.sourceIdentity || sample.majorEventId)
    && other.instrumentCode === sample.instrumentCode && other.publishedAt
    && other.publishedAt.slice(0, 10) >= calculation.baselineDate
    && other.publishedAt.slice(0, 10) <= (calculation.windows[calculation.windows.length - 1]?.endDate || '')) : [];
  return <div className="reaction-detail">
    <div className="reaction-detail-head">
      <div><span>{sample.eventType && eventLabels[sample.eventType]} · {sample.instrumentCode}</span><h4>{sample.title}</h4></div>
      <div className="reaction-actions">
        {sample.state === 'OBSERVING' && <button disabled={busy} onClick={onRefresh}>更新此样本</button>}
        {calculation && <button onClick={onCompare}>加入同类对照</button>}
        <button disabled={busy} onClick={onArchive}>{sample.state === 'ARCHIVED' ? '恢复观察' : '归档样本'}</button>
      </div>
    </div>
    <p>{sample.summary || '来源未提供摘要。'}</p>
    <div className="reaction-facts">
      <div><span>信息公开</span><strong>{dateTime(sample.publishedAt)}</strong></div>
      <div><span>系统首次捕获</span><strong>{dateTime(sample.firstCapturedAt)}</strong></div>
      <div><span>登记观察</span><strong>{dateTime(sample.registeredAt)}</strong></div>
    </div>
    <p className="reaction-note">{sample.relationNote}{sample.historicalBackfill && ' · 历史补录：公开日期早于登记日期。'}</p>
    {href && <a href={href} target="_blank" rel="noreferrer">查看原始来源 ↗</a>}
    {!href && <p className="reaction-note">原始来源链接不可用，已保留登记时的文字快照。</p>}
    {sample.refreshError && <p className="reaction-warning" role="status">{sample.refreshError} 最近尝试：{dateTime(sample.lastAttemptAt)}</p>}
    {!calculation ? <p className="reaction-empty">事件已登记，尚无成功计算的行情结果。系统会自动补齐事件前行情并持续更新后续窗口。</p> : <>
      <ReactionChart series={[
        { label: sample.instrumentName || sample.instrumentCode, metric: 'stockReturnPct', points: calculation.points, color: 'var(--reaction-ink)' },
        { label: '沪深300', metric: 'benchmarkReturnPct', points: calculation.points, color: 'var(--reaction-teal)' }
      ]} />
      <p className="reaction-path-label">{pathLabels[calculation.pathType]}<span>起点 {calculation.baselineDate} 收盘 · 首个反应交易日 {calculation.firstSession}</span></p>
      <div className="reaction-window-grid">{calculation.windows.map(window => <article key={window.sessions}>
        <span>前 {window.sessions} 个交易日 · 截至 {window.endDate}</span>
        <strong>{window.status === 'READY' ? signed(window.relativeReturnPp, ' pp') : statusLabels[window.status]}</strong>
        <small>个股 {signed(window.stockReturnPct, '%')} / 沪深300 {signed(window.benchmarkReturnPct, '%')}</small>
      </article>)}</div>
      {onResearch && <button className="reaction-research" onClick={() => onResearch(
        `请研究 ${sample.instrumentName}（${sample.instrumentCode}）在 ${sample.publishedAt} 公开的“${sample.title}”之后的市场反应。` +
        `观察起点为 ${calculation.baselineDate} 收盘，基准为沪深300。` + calculation.windows.map(window =>
          `前${window.sessions}个交易日：${window.status === 'READY' ? `个股${signed(window.stockReturnPct, '%')}，相对基准${signed(window.relativeReturnPp, '个百分点')}` : statusLabels[window.status]}`).join('；') +
        '。请比较可能解释与其他同期信息，区分事实、推断及无法确认的因果关系。'
      )}>把这个差异带入研究 →</button>}
      <details className="reaction-method"><summary>逐日数据与计算口径</summary>
        <p>所有时间为北京时间。股票使用同批前复权收盘价；相对表现 = 个股累计收益 − 沪深300同期累计收益（百分点）。窗口按交易所交易日固定，不随个股停牌顺延。</p>
        <p>“优势保留／回吐／后续走强”等标签仅在五日窗口完整后生成。首日相对表现 ≥ 2pp、五日回落 ≥ 2pp 标记回吐；否则保留首日一半以上优势标记优势保留。首日绝对相对表现 &lt; 1pp、五日 ≥ 2pp 标记后续走强；其余五日 ≤ −2pp 标记相对走弱。</p>
        <p>计算时间 {dateTime(calculation.calculatedAt)} · 个股行情截至 {calculation.stockAsOf} · 基准行情截至 {calculation.benchmarkAsOf}</p>
        <p>行情来源：{calculation.stockSource} / {calculation.benchmarkSource} · 质量：{calculation.stockQuality} / {calculation.benchmarkQuality} · 方法：{calculation.methodVersion}</p>
        <ul>{calculation.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul>
        <div className="reaction-table-scroll"><table><thead><tr><th>交易日</th><th>偏移</th><th>收盘价</th><th>前复权收盘价</th><th>成交量（源单位）</th><th>成交额（源单位）</th><th>个股 %</th><th>基准 %</th><th>相对 pp</th><th>状态</th></tr></thead><tbody>
          {calculation.points.map(point => <tr key={point.session}><td>{point.tradeDate}</td><td>{point.session > 0 ? '+' : ''}{point.session}</td><td>{point.close ?? '—'}</td><td>{point.adjustedClose ?? '—'}</td><td>{point.volume?.toLocaleString() ?? '—'}</td><td>{point.amount?.toLocaleString() ?? '—'}</td><td>{signed(point.stockReturnPct)}</td><td>{signed(point.benchmarkReturnPct)}</td><td>{signed(point.relativeReturnPp)}</td><td>{statusLabels[point.status]}</td></tr>)}
        </tbody></table></div>
      </details>
      <aside className="reaction-overlaps"><h5>期间其他已登记事件</h5>
        {overlaps.length ? <ul>{overlaps.map(other => <li key={other.id}>{dateTime(other.publishedAt)} · {other.title}</li>)}</ul>
          : <p>当前已加载样本中没有同股票的其他重叠事件。这不代表期间没有其他重要信息。</p>}
      </aside>
    </>}
  </div>;
}

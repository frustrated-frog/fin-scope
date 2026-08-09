import { StockLearningCardView } from '../../shared/types';

const dimensionLabels: Record<string, string> = {
  SPACE: '空间', PROFIT_MODEL: '盈利模式', COMPETITION: '竞争格局',
  GOVERNANCE: '治理结构', VALUATION: '定价观察', COUNTER_CASE: '反方验证'
};

const stageLabels: Record<string, string> = {
  QUEUED: '学习卡已进入生成队列…',
  COLLECTING_EVIDENCE: '正在按六个学习维度收集公开资料…',
  SYNTHESIZING_CARDS: '公开资料收集完成，正在生成六维学习卡…'
};

const statusLabels: Record<string, string> = {
  READY: '已生成', DEGRADED: '部分完成', FAILED: '生成失败', RUNNING: '生成中'
};

export function StockLearningCardDetail({ view, busy, onBack, onRegenerate }: {
  view: StockLearningCardView;
  busy: boolean;
  onBack: () => void;
  onRegenerate: () => void;
}) {
  const run = view.latestRun;
  const evidenceFor = (dimension: string) => (run?.evidence ?? []).filter(item => item.dimensionCode === dimension);
  return <section className="stock-learning-card-detail">
    <header className="stock-learning-card-detail-head">
      <button type="button" className="stock-learning-card-back" aria-label="返回全部股票" onClick={onBack}>← 返回全部股票</button>
      <div className="stock-learning-card-detail-title"><span>{view.card.code}</span><h3>{view.card.name || view.card.code}</h3><p>六维研究学习卡 · 仅依据公开资料</p></div>
      <div className="stock-learning-card-detail-actions">{run ? <em data-status={run.status}>{statusLabels[run.status] ?? run.status}</em> : null}<button type="button" onClick={onRegenerate} disabled={busy || run?.status === 'RUNNING'}>{busy ? '正在提交…' : '重新生成'}</button></div>
    </header>
    <p className="stock-learning-card-boundary">这是研究学习材料，不构成投资建议；不会生成买卖、仓位或目标价格结论。</p>
    {!run ? <div className="stock-learning-card-empty">这只股票还没有可阅读的学习卡。</div> : null}
    {run?.status === 'RUNNING' ? <div className="stock-learning-card-running">{stageLabels[run.stage ?? 'QUEUED'] ?? '股票学习卡 Agent 正在运行…'}</div> : null}
    {run && run.status !== 'RUNNING' ? <article className="stock-learning-card-result">
      <p className="stock-learning-card-summary">{run.summary}</p>
      {run.userMessage ? <p className="stock-learning-card-warning" role="status">{run.userMessage}</p> : null}
      {run.warningMessage ? <p className="stock-learning-card-warning">{run.warningMessage}</p> : null}
      <div className="stock-learning-card-claims">{run.claims.map(claim => <section key={claim.dimensionCode} data-status={claim.status}><header><span>{dimensionLabels[claim.dimensionCode] ?? claim.dimensionCode}</span><small>{claim.status === 'FAILED' ? '生成失败' : claim.status === 'INSUFFICIENT_EVIDENCE' ? '证据不足' : claim.confidence === 'LOW' ? '低置信度' : claim.confidence}</small></header>{claim.failureMessage ? <p className="stock-learning-card-claim-error">{claim.failureMessage}</p> : null}<p>{claim.judgment}</p><dl><div><dt>为什么</dt><dd>{claim.rationale}</dd></div><div><dt>反方</dt><dd>{claim.counterargument}</dd></div><div><dt>未知</dt><dd>{claim.unknowns}</dd></div></dl>{evidenceFor(claim.dimensionCode).length ? <div className="stock-learning-card-sources"><b>公开来源</b>{evidenceFor(claim.dimensionCode).map(item => item.url?.startsWith('http') ? <a key={item.evidenceCode} href={item.url} target="_blank" rel="noreferrer">[{item.evidenceCode}] {item.title || item.source}</a> : <span key={item.evidenceCode}>[{item.evidenceCode}] {item.title || item.source}</span>)}</div> : null}</section>)}</div>
      {run.watchItems.length ? <footer className="stock-learning-card-watch"><b>后续观察</b>{run.watchItems.map(item => <span key={item.metric}>{item.metric} · {item.frequency}</span>)}</footer> : null}
    </article> : null}
  </section>;
}

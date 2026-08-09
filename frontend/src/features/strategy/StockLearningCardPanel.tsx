import { FormEvent, useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { StockLearningCardRun, StockLearningCardView } from '../../shared/types';

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
  READY: '已生成', DEGRADED: '部分完成', FAILED: '生成失败'
};

export function StockLearningCardPanel({ addToast, setMessage }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void; setMessage: (message: string) => void }) {
  const [code, setCode] = useState('');
  const [view, setView] = useState<StockLearningCardView | null>(null);
  const [busy, setBusy] = useState(false);

  async function load(nextCode = code) {
    const normalized = nextCode.trim();
    if (!/^\d{6}$/.test(normalized)) return;
    try {
      const value = await api<StockLearningCardView>(`/api/stock-learning-cards/${normalized}`);
      setView(value);
      setMessage('股票学习卡已同步');
    } catch (error) { addToast(error instanceof Error ? error.message : '学习卡加载失败', 'error'); }
  }

  useEffect(() => {
    if (view?.latestRun?.status !== 'RUNNING' || !code) return undefined;
    const timer = window.setInterval(() => { load(); }, 2500);
    return () => window.clearInterval(timer);
  }, [code, view?.latestRun?.status]);

  async function start(event: FormEvent) {
    event.preventDefault();
    const normalized = code.trim();
    if (!/^\d{6}$/.test(normalized)) { addToast('请输入六位 A 股代码', 'error'); return; }
    setBusy(true);
    try {
      const run = await api<StockLearningCardRun>(`/api/stock-learning-cards/${normalized}/runs`, { method: 'POST' });
      setView(current => ({ card: current?.card ?? { code: normalized, name: normalized }, latestRun: run }));
      addToast('学习卡已进入研究队列', 'info');
    } catch (error) { addToast(error instanceof Error ? error.message : '学习卡生成失败', 'error'); }
    finally { setBusy(false); }
  }

  const run = view?.latestRun;
  return <section className="stock-learning-card-panel">
    <header className="stock-learning-card-hero">
      <div><p>LIUJIE FRAMEWORK · 仅供学习</p><h3>股票研究学习卡</h3><span>选择一只 A 股，Agent 按空间、盈利模式、竞争、治理、定价与反方验证整理公开证据。</span></div>
      <form onSubmit={start}><label>股票代码<input aria-label="股票代码" value={code} maxLength={6} inputMode="numeric" onChange={event => setCode(event.target.value.replace(/\D/g, ''))} placeholder="例如 600519" /></label><button type="submit" disabled={busy}>{busy ? '正在提交…' : '生成学习卡'}</button></form>
    </header>
    <p className="stock-learning-card-boundary">这是研究学习材料，不构成投资建议；不会生成买卖、仓位或目标价格结论。</p>
    {!run ? <div className="stock-learning-card-empty">输入你想学习的股票代码后，Agent 会自动开始研究。</div> : null}
    {run?.status === 'RUNNING' ? <div className="stock-learning-card-running">{stageLabels[run.stage ?? 'QUEUED'] ?? '股票学习卡 Agent 正在运行…'}</div> : null}
    {run && run.status !== 'RUNNING' ? <article className="stock-learning-card-result">
      <header><div><span>{view?.card.name || view?.card.code}</span><h4>{view?.card.code}</h4></div><em data-status={run.status}>{statusLabels[run.status] ?? run.status}</em></header>
      <p className="stock-learning-card-summary">{run.summary}</p>
      {run.userMessage ? <p className="stock-learning-card-warning" role="status">{run.userMessage}</p> : null}
      {run.warningMessage ? <p className="stock-learning-card-warning">{run.warningMessage}</p> : null}
      <div className="stock-learning-card-claims">{run.claims.map(claim => <section key={claim.dimensionCode} data-status={claim.status}><header><span>{dimensionLabels[claim.dimensionCode] ?? claim.dimensionCode}</span><small>{claim.status === 'FAILED' ? '生成失败' : claim.status === 'INSUFFICIENT_EVIDENCE' ? '证据不足' : claim.confidence === 'LOW' ? '低置信度' : claim.confidence}</small></header>{claim.failureMessage ? <p className="stock-learning-card-claim-error">{claim.failureMessage}</p> : null}<p>{claim.judgment}</p><dl><div><dt>为什么</dt><dd>{claim.rationale}</dd></div><div><dt>反方</dt><dd>{claim.counterargument}</dd></div><div><dt>未知</dt><dd>{claim.unknowns}</dd></div></dl></section>)}</div>
      {run.watchItems.length ? <footer className="stock-learning-card-watch"><b>后续观察</b>{run.watchItems.map(item => <span key={item.metric}>{item.metric} · {item.frequency}</span>)}</footer> : null}
    </article> : null}
  </section>;
}

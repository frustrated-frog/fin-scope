import { FormEvent, useEffect, useState } from 'react';
import { api } from '../../shared/api/client';
import { StockLearningCardRun, StockLearningCardSummary, StockLearningCardView } from '../../shared/types';
import { StockLearningCardDetail } from './StockLearningCardDetail';

const statusLabels: Record<string, string> = {
  READY: '已生成', DEGRADED: '部分完成', FAILED: '生成失败', RUNNING: '生成中'
};

const stageLabels: Record<string, string> = {
  QUEUED: '等待开始', COLLECTING_EVIDENCE: '收集公开资料', SYNTHESIZING_CARDS: '生成六维解读'
};

function formatTime(value?: string) {
  if (!value) return '刚刚更新';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}

export function StockLearningCardPanel({ addToast, setMessage }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void; setMessage: (message: string) => void }) {
  const [code, setCode] = useState('');
  const [summaries, setSummaries] = useState<StockLearningCardSummary[]>([]);
  const [view, setView] = useState<StockLearningCardView | null>(null);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loadingList, setLoadingList] = useState(true);

  async function loadSummaries(silent = false) {
    try {
      const values = await api<StockLearningCardSummary[]>('/api/stock-learning-cards');
      setSummaries(values);
    } catch (error) {
      if (!silent) addToast(error instanceof Error ? error.message : '学习卡列表加载失败', 'error');
    } finally { setLoadingList(false); }
  }

  async function openCard(nextCode: string, silent = false) {
    try {
      const value = await api<StockLearningCardView>(`/api/stock-learning-cards/${nextCode}`);
      setSelectedCode(nextCode); setView(value); setCode(nextCode);
      if (!silent) setMessage('股票学习卡已同步');
      if (value.latestRun?.status !== 'RUNNING') await loadSummaries(true);
    } catch (error) {
      if (!silent) addToast(error instanceof Error ? error.message : '学习卡加载失败', 'error');
    }
  }

  useEffect(() => { loadSummaries(); }, []);

  useEffect(() => {
    if (view?.latestRun?.status !== 'RUNNING' || !selectedCode) return undefined;
    const timer = window.setInterval(() => { openCard(selectedCode, true); }, 2500);
    return () => window.clearInterval(timer);
  }, [selectedCode, view?.latestRun?.status]);

  async function generate(nextCode: string) {
    const run = await api<StockLearningCardRun>(`/api/stock-learning-cards/${nextCode}/runs`, { method: 'POST' });
    const known = summaries.find(item => item.code === nextCode);
    setSelectedCode(nextCode);
    setView(current => ({ card: current?.card.code === nextCode ? current.card : { code: nextCode, name: known?.name ?? nextCode }, latestRun: run }));
    addToast('学习卡已进入研究队列', 'info');
    await loadSummaries(true);
  }

  async function start(event: FormEvent) {
    event.preventDefault();
    const normalized = code.trim();
    if (!/^\d{6}$/.test(normalized)) { addToast('请输入六位 A 股代码', 'error'); return; }
    setBusy(true);
    try { await generate(normalized); }
    catch (error) { addToast(error instanceof Error ? error.message : '学习卡生成失败', 'error'); }
    finally { setBusy(false); }
  }

  async function regenerate() {
    if (!selectedCode) return;
    setBusy(true);
    try { await generate(selectedCode); }
    catch (error) { addToast(error instanceof Error ? error.message : '学习卡生成失败', 'error'); }
    finally { setBusy(false); }
  }

  if (view && selectedCode) return <StockLearningCardDetail view={view} busy={busy} onBack={() => { setView(null); setSelectedCode(null); loadSummaries(true); }} onRegenerate={regenerate} />;

  return <section className="stock-learning-card-panel">
    <header className="stock-learning-card-hero">
      <div><p>LIUJIE FRAMEWORK · 仅供学习</p><h3>股票研究学习卡</h3><span>每只股票保留一张学习入口，点击后阅读空间、盈利模式、竞争、治理、定价与反方验证。</span></div>
      <form onSubmit={start}><label>股票代码<input aria-label="股票代码" value={code} maxLength={6} inputMode="numeric" onChange={event => setCode(event.target.value.replace(/\D/g, ''))} placeholder="例如 600519" /></label><button type="submit" disabled={busy}>{busy ? '正在提交…' : '生成学习卡'}</button></form>
    </header>
    <p className="stock-learning-card-boundary">这是研究学习材料，不构成投资建议；不会生成买卖、仓位或目标价格结论。</p>
    <div className="stock-learning-library-head"><div><span>STOCK FILES</span><h4>我的股票学习卡</h4></div><small>{summaries.length ? `${summaries.length} 只股票` : '等待第一只股票'}</small></div>
    {loadingList ? <div className="stock-learning-card-empty">正在读取学习卡…</div> : null}
    {!loadingList && !summaries.length ? <div className="stock-learning-card-empty">还没有股票学习卡。输入一只 A 股代码，Agent 会从公开资料开始整理。</div> : null}
    {summaries.length ? <div className="stock-learning-library-grid">{summaries.map(item => <button type="button" className="stock-learning-library-card" data-status={item.status} key={item.code} aria-label={`查看${item.name} ${item.code} 学习卡`} onClick={() => openCard(item.code)}>
      <header><div><span>{item.code}</span><h5>{item.name}</h5></div><em>{statusLabels[item.status] ?? item.status}</em></header>
      <p>{item.status === 'RUNNING' ? stageLabels[item.stage ?? 'QUEUED'] ?? 'Agent 正在运行' : item.summary || '打开查看最新六维解读'}</p>
      <div className="stock-learning-dimension-track" aria-label={`已完成 ${item.completedDimensions}/${item.totalDimensions} 个维度`}>{Array.from({ length: item.totalDimensions }, (_, index) => <i key={index} data-ready={index < item.completedDimensions} />)}</div>
      <footer><span>{item.completedDimensions}/{item.totalDimensions} 维完成</span><time>{formatTime(item.completedAt || item.updatedAt)}</time></footer>
    </button>)}</div> : null}
  </section>;
}

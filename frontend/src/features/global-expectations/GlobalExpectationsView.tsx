import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import type { GlobalExpectationItem } from '../../shared/types';

const fallbackItems: GlobalExpectationItem[] = [
  { id: 1, theme: '科技供应链', question: '美国是否会在今年进一步扩大 AI 芯片出口限制？', marketUrl: 'https://polymarket.com', probability: 62, change24h: 9.4, volume: 1284000, openInterest: 482000, spread: 2, endDate: '2026-12-31', observation: '观察限制范围、具体产品与美国商务部正式文件。', status: 'SIGNAL', observedAt: '刚刚' },
  { id: 2, theme: '能源资源', question: '今年原油价格是否会突破每桶 100 美元？', marketUrl: 'https://polymarket.com', probability: 31, change24h: -3.1, volume: 856000, openInterest: 291000, spread: 3, endDate: '2026-12-31', observation: '观察中东供给、航运与战略储备政策。', status: 'WATCHING', observedAt: '5 分钟前' },
  { id: 3, theme: '全球宏观', question: '美联储下次会议是否会降息？', marketUrl: 'https://polymarket.com', probability: 44, change24h: 0.8, volume: 2140000, openInterest: 687000, spread: 1, endDate: '2026-09-17', observation: '观察就业、通胀与联储官员表态。', status: 'WATCHING', observedAt: '5 分钟前' }
];

export function GlobalExpectationsView({ addToast }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void }) {
  const [items, setItems] = useState<GlobalExpectationItem[]>(fallbackItems);
  const [theme, setTheme] = useState('全部');
  const [signalsOnly, setSignalsOnly] = useState(false);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setItems(await api<GlobalExpectationItem[]>('/api/global-expectations'));
    } catch {
      addToast('暂未取得新快照，正在展示本地观察样例', 'info');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);
  const themes = ['全部', ...new Set(items.map((item) => item.theme))];
  const visibleItems = useMemo(() => items.filter((item) => (theme === '全部' || item.theme === theme) && (!signalsOnly || item.status === 'SIGNAL')), [items, theme, signalsOnly]);
  const signalCount = items.filter((item) => item.status === 'SIGNAL').length;

  return <section className="expectations-workspace">
    <header className="expectations-hero">
      <div><p className="eyebrow">GLOBAL EXPECTATION MONITOR</p><h3>全球预期 <span>· 观察海外市场正在重新定价什么</span></h3><p>这是一组待核验的外部认知变化，不构成股票评分或交易建议。</p></div>
      <dl><div><dt>监控覆盖</dt><dd>{items.length}</dd></div><div><dt>预期异动</dt><dd>{signalCount}</dd></div><div><dt>刷新状态</dt><dd>{loading ? '更新中' : '已就绪'}</dd></div></dl>
    </header>
    <div className="expectations-toolbar"><div role="group" aria-label="主题过滤">{themes.map((item) => <button className={theme === item ? 'active' : ''} type="button" key={item} onClick={() => setTheme(item)}>{item}</button>)}</div><label><input type="checkbox" checked={signalsOnly} onChange={(event) => setSignalsOnly(event.target.checked)} />只看异动</label><button type="button" onClick={() => void load()} disabled={loading}>{loading ? '刷新中…' : '刷新快照'}</button></div>
    <div className="expectations-grid">{visibleItems.map((item) => <article className={`expectation-card ${item.status === 'SIGNAL' ? 'is-signal' : ''}`} key={item.id}><header><span>{item.theme}</span><small>{item.status === 'SIGNAL' ? '待核验' : '持续观察'} · {item.observedAt}</small></header><h4>{item.question}</h4><div className="probability-row"><strong>{item.probability}¢</strong><span>YES 概率</span><b className={(item.change24h || 0) >= 0 ? 'up' : 'down'}>{(item.change24h || 0) >= 0 ? '+' : ''}{item.change24h ?? '—'}pp / 24h</b></div><div className="probability-track" aria-label={`YES 概率 ${item.probability}%`}><i style={{ width: `${item.probability}%` }} /></div><dl className="market-quality"><div><dt>成交</dt><dd>{formatMoney(item.volume)}</dd></div><div><dt>OI</dt><dd>{formatMoney(item.openInterest)}</dd></div><div><dt>价差</dt><dd>{item.spread ?? '—'}¢</dd></div><div><dt>到期</dt><dd>{item.endDate || '—'}</dd></div></dl><footer><p>{item.observation}</p><a href={item.marketUrl} target="_blank" rel="noreferrer">查看原市场 ↗</a></footer></article>)}</div>
  </section>;
}

function formatMoney(value?: number) { return value == null ? '—' : value >= 1000000 ? `$${(value / 1000000).toFixed(1)}m` : `$${Math.round(value / 1000)}k`; }

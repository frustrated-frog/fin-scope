import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import type { GlobalExpectationItem } from '../../shared/types';

export function GlobalExpectationsView({ addToast }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void }) {
  const [items, setItems] = useState<GlobalExpectationItem[]>([]);
  const [theme, setTheme] = useState('全部');
  const [signalsOnly, setSignalsOnly] = useState(false);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<GlobalExpectationItem | null>(null);

  const load = async (force = false) => {
    setLoading(true);
    try {
      setItems(await api<GlobalExpectationItem[]>(force ? '/api/global-expectations/refresh' : '/api/global-expectations', force ? { method: 'POST' } : undefined));
    } catch {
      addToast('暂未取得市场快照，已保留上一次可用观察', 'info');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => { void load(); }, 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const themes = ['全部', ...new Set(items.map((item) => item.theme))];
  const visibleItems = useMemo(() => items.filter((item) => (theme === '全部' || item.theme === theme) && (!signalsOnly || item.status === 'SIGNAL')), [items, theme, signalsOnly]);
  const signalCount = items.filter((item) => item.status === 'SIGNAL').length;

  return <section className="expectations-workspace">
    <header className="expectations-hero">
      <div><p className="eyebrow">GLOBAL EXPECTATION MONITOR</p><h3>全球预期 <span>· 观察海外市场正在重新定价什么</span></h3><p>这是一组待核验的外部认知变化，不构成股票评分或交易建议。</p></div>
      <dl><div><dt>监控覆盖</dt><dd>{items.length}</dd></div><div><dt>预期异动</dt><dd>{signalCount}</dd></div><div><dt>刷新状态</dt><dd>{loading ? '更新中' : '已就绪'}</dd></div></dl>
    </header>
    <div className="expectations-toolbar"><div role="group" aria-label="主题过滤">{themes.map((item) => <button className={theme === item ? 'active' : ''} type="button" key={item} onClick={() => setTheme(item)}>{item}</button>)}</div><label><input type="checkbox" checked={signalsOnly} onChange={(event) => setSignalsOnly(event.target.checked)} />只看异动</label><button type="button" onClick={() => void load(true)} disabled={loading}>{loading ? '刷新中…' : '刷新快照'}</button></div>
    {visibleItems.length === 0 ? <div className="expectations-empty"><strong>正在获取观察池</strong><p>官方市场快照到达后，会在这里显示 5m / 1h / 24h 的认知变化。</p></div> : <div className="expectations-grid">{visibleItems.map((item) => <article className={`expectation-card ${item.status === 'SIGNAL' ? 'is-signal' : ''}`} key={item.id}><header><span>{item.theme}</span><small>{item.dataStatus === 'STALE' ? '缓存数据' : item.dataStatus === 'PARTIAL' ? '历史缓存' : item.status === 'SIGNAL' ? '待核验' : '持续观察'} · {item.observedAt}</small></header><h4>{item.question}</h4><div className="probability-row"><strong>{item.probability}¢</strong><span>YES 概率</span><b className={movementClass(item.change5m ?? item.change1h ?? item.change24h)}>{formatMovement(item.change5m ?? item.change1h ?? item.change24h)} / {item.change5m != null ? '5m' : item.change1h != null ? '1h' : '24h'}</b></div><div className="probability-track" aria-label={`YES 概率 ${item.probability}%`}><i style={{ width: `${item.probability}%` }} /></div><div className="movement-strip" aria-label="变化窗口"><span>5m <b className={movementClass(item.change5m)}>{formatMovement(item.change5m)}</b></span><span>1h <b className={movementClass(item.change1h)}>{formatMovement(item.change1h)}</b></span><span>24h <b className={movementClass(item.change24h)}>{formatMovement(item.change24h)}</b></span></div><dl className="market-quality"><div><dt>成交</dt><dd>{formatMoney(item.volume)}</dd></div><div><dt>OI</dt><dd>{formatMoney(item.openInterest)}</dd></div><div><dt>价差</dt><dd>{item.spread ?? '—'}¢</dd></div><div><dt>到期</dt><dd>{item.endDate || '—'}</dd></div></dl><footer><p>{item.observation}</p><div><button type="button" onClick={() => setSelected(item)}>查看变化详情</button><a href={item.marketUrl} target="_blank" rel="noreferrer">查看原市场 ↗</a></div></footer></article>)}</div>}
    {selected && <ExpectationDetail item={selected} onClose={() => setSelected(null)} />}
  </section>;
}

function ExpectationDetail({ item, onClose }: { item: GlobalExpectationItem; onClose: () => void }) {
  const points = item.priceHistory ?? [];
  const min = Math.min(...points.map((point) => point.probability), item.probability);
  const max = Math.max(...points.map((point) => point.probability), item.probability);
  return <div className="expectation-dialog-backdrop" role="presentation" onMouseDown={onClose}><section className="expectation-dialog" role="dialog" aria-modal="true" aria-label={`${item.question} 的变化详情`} onMouseDown={(event) => event.stopPropagation()}><header><div><p className="eyebrow">OBSERVATION TRACE</p><h4>{item.question}</h4></div><button type="button" aria-label="关闭变化详情" onClick={onClose}>×</button></header><div className="detail-probability"><strong>{item.probability}¢</strong><span>当前 YES 概率 · 最近刷新 {item.lastRefreshAt || item.observedAt || '—'}</span></div><div className="detail-windows"><span>5m <b className={movementClass(item.change5m)}>{formatMovement(item.change5m)}</b></span><span>1h <b className={movementClass(item.change1h)}>{formatMovement(item.change1h)}</b></span><span>24h <b className={movementClass(item.change24h)}>{formatMovement(item.change24h)}</b></span></div><div className="detail-history" aria-label="官方价格轨迹">{points.length ? points.map((point) => <i key={`${point.observedAt}-${point.probability}`} title={`${point.observedAt} · ${point.probability}¢`} style={{ height: `${20 + ((point.probability - min) / Math.max(1, max - min)) * 80}%` }} />) : <p>官方历史价格暂不可用。</p>}</div><p className="detail-observation">{item.observation}</p></section></div>;
}

function formatMoney(value?: number) { return value == null ? '—' : value >= 1000000 ? `$${(value / 1000000).toFixed(1)}m` : `$${Math.round(value / 1000)}k`; }
function movementClass(value?: number) { return (value ?? 0) >= 0 ? 'up' : 'down'; }
function formatMovement(value?: number) { return value == null ? '暂无历史' : `${value >= 0 ? '+' : ''}${value.toFixed(1)}pp`; }

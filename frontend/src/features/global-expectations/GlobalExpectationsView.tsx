import { useEffect, useMemo, useState } from 'react';

import { api } from '../../shared/api/client';
import type { GlobalExpectationEventGroup, GlobalExpectationItem, GlobalExpectationsFeed } from '../../shared/types';

const EXPECTATION_THEMES = ['全部', '政治', '财务', '地缘冲突', '科技', '经济'];

export function GlobalExpectationsView({ addToast }: { addToast: (message: string, type?: 'success' | 'error' | 'info') => void }) {
  const [items, setItems] = useState<GlobalExpectationItem[]>([]);
  const [feed, setFeed] = useState<GlobalExpectationsFeed>({ marketCount: 0, eventCount: 0, signalCount: 0, generatedAt: '等待刷新', groups: [] });
  const [mode, setMode] = useState<'signals' | 'ranking'>('signals');
  const [theme, setTheme] = useState('全部');
  const [signalsOnly, setSignalsOnly] = useState(false);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<GlobalExpectationItem | null>(null);

  const load = async (force = false) => {
    setLoading(true);
    try {
      if (force) {
        await api<GlobalExpectationItem[]>('/api/global-expectations/refresh', { method: 'POST' });
      }
      const [nextItems, nextFeed] = await Promise.all([
        api<GlobalExpectationItem[]>('/api/global-expectations'),
        api<GlobalExpectationsFeed>('/api/global-expectations/feed')
      ]);
      setItems(nextItems);
      setFeed(nextFeed);
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
  const visibleItems = useMemo(() => {
    const themedItems = theme === '全部'
      ? [...new Map(items.map((item) => [item.marketUrl, item])).values()]
      : items.filter((item) => item.theme === theme);
    return themedItems.filter((item) => !signalsOnly || item.status === 'SIGNAL');
  }, [items, theme, signalsOnly]);
  const visibleGroups = useMemo(() => feed.groups.filter((group) =>
    (theme === '全部' || group.themes.includes(theme)) && (!signalsOnly || group.status === 'SIGNAL')), [feed.groups, theme, signalsOnly]);

  return <section className="expectations-workspace">
    <header className="expectations-hero">
      <div><p className="eyebrow">GLOBAL EXPECTATION MONITOR</p><h3>全球预期 <span>· 观察海外市场正在重新定价什么</span></h3><p>这是一组待核验的外部认知变化，不构成股票评分或交易建议。</p></div>
      <dl><div><dt>监控覆盖</dt><dd>{feed.marketCount || items.length}</dd></div><div><dt>事件异动</dt><dd>{feed.signalCount}</dd></div><div><dt>刷新状态</dt><dd>{loading ? '更新中' : '已就绪'}</dd></div></dl>
    </header>
    <div className="expectations-modebar"><div role="group" aria-label="展示方式"><button className={mode === 'signals' ? 'active' : ''} type="button" onClick={() => setMode('signals')}>异动流</button><button className={mode === 'ranking' ? 'active' : ''} type="button" onClick={() => setMode('ranking')}>分类榜</button></div><span>{mode === 'signals' ? `${feed.eventCount} 个聚合事件 · 最近刷新 ${feed.generatedAt}` : '五个分类分别按 24h 成交量排序'}</span></div>
    <div className="expectations-toolbar"><div role="group" aria-label="主题过滤">{EXPECTATION_THEMES.map((item) => <button className={theme === item ? 'active' : ''} type="button" key={item} onClick={() => setTheme(item)}>{item}</button>)}</div><label><input type="checkbox" checked={signalsOnly} onChange={(event) => setSignalsOnly(event.target.checked)} />{mode === 'signals' ? '只看强异动' : '只看异动'}</label><button type="button" onClick={() => void load(true)} disabled={loading}>{loading ? '刷新中…' : '刷新快照'}</button></div>
    {mode === 'signals'
      ? <ExpectationSignalFeed groups={visibleGroups} onSelect={setSelected} />
      : visibleItems.length === 0 ? <ExpectationEmpty /> : <div className="expectations-grid">{visibleItems.map((item) => <ExpectationCard item={item} onSelect={setSelected} key={`${item.theme}-${item.marketUrl}`} />)}</div>}
    {selected && <ExpectationDetail item={selected} onClose={() => setSelected(null)} />}
  </section>;
}

function ExpectationSignalFeed({ groups, onSelect }: { groups: GlobalExpectationEventGroup[]; onSelect: (item: GlobalExpectationItem) => void }) {
  if (groups.length === 0) {
    return <ExpectationEmpty />;
  }
  return <div className="expectation-signal-feed">{groups.map((group) => <article className={`expectation-event ${group.status === 'SIGNAL' ? 'is-signal' : ''}`} key={group.id}>
    <div className="expectation-event-rail"><i /><span>{group.status === 'SIGNAL' ? 'NOW' : 'WATCH'}</span></div>
    <div className="expectation-event-body">
      <header><div className="expectation-event-tags">{group.themes.map((item) => <span key={item}>{item}</span>)}</div><strong>异动强度 {group.signalScore}</strong></header>
      <h4>{group.title}</h4>
      <div className="expectation-event-meta"><span>{group.markets.length} 个预测选项</span><span>24h 成交 {formatMoney(group.volume24h)}</span></div>
      {group.signalReasons.length > 0 && <div className="expectation-reasons">{group.signalReasons.map((reason) => <span key={reason}>{reason}</span>)}</div>}
      <div className="expectation-outcomes">{group.markets.map((market) => <button type="button" onClick={() => onSelect(market)} key={market.marketId || market.marketUrl}><span>{market.question}</span><strong>{market.probability}¢</strong><small>{market.rank ? `#${market.rank}` : '—'} · {formatMovement(market.change1h ?? market.change5m ?? market.change24h)}</small></button>)}</div>
      <div className="expectation-enrichment"><InterpretationPanel group={group} /><RadarPanel group={group} /></div>
    </div>
  </article>)}</div>;
}

function InterpretationPanel({ group }: { group: GlobalExpectationEventGroup }) {
  const interpretation = group.interpretation;
  if (interpretation?.status === 'READY') {
    return <section className="expectation-ai"><header><span>AI SHORT READ</span><b>异步增强</b></header><dl><div><dt>发生了什么</dt><dd>{interpretation.happened}</dd></div><div><dt>意味着什么</dt><dd>{interpretation.meaning}</dd></div><div><dt>关联变量</dt><dd>{interpretation.relatedVariables}</dd></div><div><dt>下一步观察</dt><dd>{interpretation.nextObservation}</dd></div></dl></section>;
  }
  const message = interpretation?.status === 'QUEUED' ? 'AI 正在后台生成短解读，不影响当前异动事实。' : interpretation?.failureMessage || '当前仅展示确定性异动事实。';
  return <section className="expectation-ai is-muted"><header><span>AI SHORT READ</span><b>{interpretation?.status === 'QUEUED' ? '生成中' : '未增强'}</b></header><p>{message}</p></section>;
}

function RadarPanel({ group }: { group: GlobalExpectationEventGroup }) {
  return <section className="expectation-radar"><header><span>LOCAL RADAR</span><b>{group.radarMatches.length} 条匹配</b></header>{group.radarMatches.length > 0 ? <ul>{group.radarMatches.map((match) => <li key={match.eventId}><strong>{match.title}</strong><p>{match.summary}</p></li>)}</ul> : <p>本地近三天资讯中暂未发现高置信匹配。</p>}</section>;
}

function ExpectationCard({ item, onSelect }: { item: GlobalExpectationItem; onSelect: (item: GlobalExpectationItem) => void }) {
  return <article className={`expectation-card ${item.status === 'SIGNAL' ? 'is-signal' : ''}`}><header><span>{item.theme}{item.rank ? ` · #${item.rank}` : ''}</span><small>{item.dataStatus === 'STALE' ? '缓存数据' : item.dataStatus === 'PARTIAL' ? '历史缓存' : item.status === 'SIGNAL' ? '待核验' : '持续观察'} · {item.observedAt}</small></header><h4>{item.question}</h4><div className="probability-row"><strong>{item.probability}¢</strong><span>YES 概率</span><b className={movementClass(item.change5m ?? item.change1h ?? item.change24h)}>{formatMovement(item.change5m ?? item.change1h ?? item.change24h)} / {item.change5m != null ? '5m' : item.change1h != null ? '1h' : '24h'}</b></div><div className="probability-track" aria-label={`YES 概率 ${item.probability}%`}><i style={{ width: `${item.probability}%` }} /></div><div className="movement-strip" aria-label="变化窗口"><span>5m <b className={movementClass(item.change5m)}>{formatMovement(item.change5m)}</b></span><span>1h <b className={movementClass(item.change1h)}>{formatMovement(item.change1h)}</b></span><span>24h <b className={movementClass(item.change24h)}>{formatMovement(item.change24h)}</b></span></div><dl className="market-quality"><div><dt>24h 成交</dt><dd>{formatMoney(item.volume24h ?? item.volume)}</dd></div><div><dt>排名变化</dt><dd>{item.rankChange ? `${item.rankChange > 0 ? '↑' : '↓'}${Math.abs(item.rankChange)}` : '—'}</dd></div><div><dt>OI</dt><dd>{formatMoney(item.openInterest)}</dd></div><div><dt>到期</dt><dd>{item.endDate || '—'}</dd></div></dl><footer><p>{item.signalReasons?.[0] || item.observation}</p><div><button type="button" onClick={() => onSelect(item)}>查看变化详情</button><a href={item.marketUrl} target="_blank" rel="noreferrer">查看原市场 ↗</a></div></footer></article>;
}

function ExpectationEmpty() {
  return <div className="expectations-empty"><strong>正在获取观察池</strong><p>官方市场快照到达后，会在这里显示确定性异动与事件聚合。</p></div>;
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

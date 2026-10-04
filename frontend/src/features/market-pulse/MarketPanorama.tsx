import { useEffect, useMemo, useState } from 'react';
import { api } from '../../shared/api/client';
import type { MarketPulseWorkspace, StockDiscoveryMarketContext } from './marketPulseTypes';
import type { DailyResearch } from './marketResearchTypes';
import type { WatchFocus } from '../watchlist/watchFocusTypes';
import { PanoramaTrend } from './PanoramaTrend';
import { PanoramaMap } from './PanoramaMap';
import { PanoramaMatrix } from './PanoramaMatrix';
import { PanoramaRotation } from './PanoramaRotation';
import { PanoramaCatalysts } from './PanoramaCatalysts';
import { amount, buildFrames, indexColors, pct, ratio, type PanoramaFrame, type PanoramaMetric } from './panoramaModel';
import './MarketPanorama.css';

type Props = {
  workspace: MarketPulseWorkspace; dates: string[]; refreshing: boolean;
  onLoad: (date: string) => void; onRefresh: () => void;
  onOpenStock?: (code: string) => void; onOpenStockDiscovery?: (context?: StockDiscoveryMarketContext) => void;
};
export function MarketPanorama({ workspace, dates, refreshing, onLoad, onRefresh, onOpenStock, onOpenStockDiscovery }: Props) {
  const [history, setHistory] = useState<{ date: string; frames: PanoramaFrame[] }>();
  const [historyState, setHistoryState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const [windowSize, setWindowSize] = useState(20);
  const [selectedDate, setSelectedDate] = useState(workspace.businessDate ?? '');
  const [selectedCode, setSelectedCode] = useState('');
  const [metric, setMetric] = useState<PanoramaMetric>('return1d');
  const [benchmark, setBenchmark] = useState('');
  const [research, setResearch] = useState<DailyResearch>();
  const [stockState, setStockState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [watched, setWatched] = useState<WatchFocus[]>([]);
  useEffect(() => {
    const abort = new AbortController();
    const date = workspace.businessDate ?? '';
    setHistoryState('loading');
    setSelectedDate(date);
    void api<PanoramaFrame[]>(`/api/market-pulse/panorama?businessDate=${date}&limit=60`, { signal: abort.signal }).then(result => {
      if (!abort.signal.aborted) {
        if (!Array.isArray(result)) {
          throw new Error('无效的全景历史');
        }
        setHistory({ date, frames: result });
        setHistoryState('ready');
      }
    }).catch(() => {
      if (!abort.signal.aborted) {
        setHistory(undefined);
        setHistoryState('error');
      }
    });
    return () => abort.abort();
  }, [workspace.businessDate, workspace.generatedAt, attempt]);
  useEffect(() => {
    const abort = new AbortController();
    void api<WatchFocus[]>('/api/watchlist/focuses', { signal: abort.signal }).then(result => {
      if (!abort.signal.aborted && Array.isArray(result)) {
        setWatched(result);
      }
    }).catch(() => {
      if (!abort.signal.aborted) {
        setWatched([]);
      }
    });
    return () => abort.abort();
  }, []);
  const frames = useMemo(() => buildFrames(history && history.date === workspace.businessDate ? history.frames : [], workspace).slice(-windowSize), [history, workspace, windowSize]);
  const active = frames.find(frame => frame.businessDate === selectedDate) ?? frames[frames.length - 1];
  const date = active?.businessDate ?? workspace.businessDate ?? '';
  const position = Math.max(0, frames.findIndex(frame => frame.businessDate === date));
  useEffect(() => {
    const abort = new AbortController();
    setStockState('loading');
    setResearch(undefined);
    const timer = window.setTimeout(() => {
      void api<DailyResearch>(`/api/market-pulse/research/${date}`, { signal: abort.signal }).then(result => {
        if (!abort.signal.aborted) {
          if (result.businessDate !== date || !Array.isArray(result.stocks)) {
            throw new Error('个股日期不一致');
          }
          setResearch(result);
          setStockState('ready');
        }
      }).catch(() => {
        if (!abort.signal.aborted) {
          setStockState('error');
        }
      });
    }, 220);
    return () => { window.clearTimeout(timer); abort.abort(); };
  }, [date, attempt, workspace.generatedAt]);
  const watchedCodes = useMemo(() => new Set(watched.filter(item => item.type === 'STOCK').map(item => item.code.split('.')[0])), [watched]);
  const definitions = useMemo(() => {
    const values = new Map<string, string>();
    frames.forEach(frame => frame.indices.forEach(index => values.set(index.code, index.name)));
    return [...values];
  }, [frames]);
  const choose = (nextDate: string, code?: string) => {
    setSelectedDate(nextDate);
    if (code !== undefined) {
      setSelectedCode(code);
    }
  };
  return <div className="mpa-workspace">
    <header className="mpa-masthead"><div><span className="mpa-eyebrow">MARKET PULSE / 全景与演变</span><h2>看清市场的方向</h2><p>从整体走势，到行业扩散，再到主线的持续与切换。</p></div><div className="mpa-asof"><span>观察截面</span><strong>{date}</strong><small>{historyState === 'loading' ? '正在加载历史…' : `当前区间 ${frames.length} 个观测日`}</small></div></header>
    <div className="mpa-controls" aria-label="全景统一控制">
      <div className="mpa-segment" role="group" aria-label="观察区间">{[20, 40, 60].map(size => <button key={size} type="button" aria-pressed={windowSize === size} onClick={() => setWindowSize(size)}>{size}日</button>)}</div>
      <label className="mpa-select">趋势基准<select aria-label="趋势比较基准" value={benchmark} onChange={event => setBenchmark(event.target.value)}><option value="">共同起点</option>{definitions.map(([code, name]) => <option value={code} key={code}>{name}</option>)}</select></label>
      <label className="mpa-select">截至<select aria-label="全景截至日期" value={workspace.businessDate} onChange={event => onLoad(event.target.value)}>{[...new Set([workspace.businessDate ?? '', ...dates])].filter(Boolean).sort().reverse().map(value => <option key={value} value={value}>{value}</option>)}</select></label>
      <button className="mpa-refresh" type="button" aria-label="立即补刷新" disabled={refreshing} onClick={onRefresh}>{refreshing ? '更新中…' : '↻ 更新行情'}</button>
    </div>
    {(historyState === 'error' || stockState === 'error') && <div className="mpa-load-error" role="status">{historyState === 'error' ? '历史截面加载失败，当前显示已有指数与宽度数据。' : '个股样本暂未加载，市场总览仍可使用。'}<button type="button" onClick={() => setAttempt(value => value + 1)}>重试</button></div>}
    <div className="mpa-context-strip"><span className="mpa-context-date">{date.slice(5)}</span><p>{active?.headline ?? '沿时间轴观察市场方向与行业强弱变化'}</p><span>上涨占比 <b>{ratio(active?.advanceRatio)}</b></span><span>成交额 <b>{amount(active?.totalAmount)}</b></span></div>
    <PanoramaTrend frames={frames} selectedDate={date} benchmark={benchmark} onSelect={choose} />
    <div className="mpa-style-strip" aria-label="宽基指数风格对照"><div><strong>宽基表现</strong><span>当日 / 近20日</span></div>{(active?.indices ?? []).map((index, i) => <div key={index.code}><i style={{ background: indexColors[i % indexColors.length] }} /><span>{index.name}</span><strong>{pct(index.return1d)}</strong><small>{pct(index.return20d)}</small></div>)}</div>
    <PanoramaMap sectors={active?.sectors ?? []} selectedCode={selectedCode} onSelect={setSelectedCode} metric={metric} onMetric={setMetric} stocks={research && research.businessDate === date ? research.stocks : []} stocksLoading={stockState === 'loading'} stocksError={stockState === 'error'} businessDate={date} watchedCodes={watchedCodes} onOpenStock={onOpenStock} onOpenStockDiscovery={onOpenStockDiscovery} />
    <div className="mpa-evolution"><PanoramaMatrix frames={frames} selectedDate={date} selectedCode={selectedCode} metric={metric} onSelect={choose} /><PanoramaRotation sectors={active?.sectors ?? []} selectedCode={selectedCode} onSelect={setSelectedCode} businessDate={date} /></div>
    <PanoramaCatalysts workspace={workspace} businessDate={date} sectors={active?.sectors ?? []} selectedCode={selectedCode} onSelect={setSelectedCode}
      hasSnapshot={date === workspace.businessDate || (history?.date === workspace.businessDate && !!history?.frames.some(frame => frame.businessDate === date))} />
    <div className="mpa-timeline" aria-label="全景时间轴">
      <button type="button" aria-label="前一个观测日" disabled={position === 0} onClick={() => choose(frames[position - 1].businessDate)}>←</button>
      <div><div className="mpa-timeline-labels"><time>{frames[0]?.businessDate}</time><strong>{date}</strong><time>{frames[frames.length - 1]?.businessDate}</time></div><input type="range" aria-label="全景观察日期" aria-valuetext={date} min={0} max={Math.max(0, frames.length - 1)} value={position} onChange={event => choose(frames[Number(event.target.value)].businessDate)} /></div>
      <button type="button" aria-label="后一个观测日" disabled={position >= frames.length - 1} onClick={() => choose(frames[position + 1].businessDate)}>→</button>
      <button className="mpa-latest" type="button" disabled={position === frames.length - 1} onClick={() => choose(frames[frames.length - 1]!.businessDate)}>回到区间末日</button>
    </div>
  </div>;
}

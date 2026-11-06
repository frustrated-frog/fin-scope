import { useEffect, useMemo, useRef, useState } from 'react';
import type { WatchlistItem } from '../../shared/types';
import { compareStockVisits, createStockVisitSnapshot, parseStockVisitSnapshot } from './stockVisitChanges';
import './stockMarketInsights.css';

const STORAGE_KEY = 'watchlist.stockLastVisit.v1';

export function WatchlistStockChanges({ items, ready, onOpen }: {
  items: WatchlistItem[];
  ready: boolean;
  onOpen: (item: WatchlistItem, report: boolean) => void;
}) {
  const [baseline] = useState(() => {
    try {
      return parseStockVisitSnapshot(localStorage.getItem(STORAGE_KEY));
    } catch {
      return undefined;
    }
  });
  const latestAccepted = useRef(baseline);
  const [storageUnavailable, setStorageUnavailable] = useState(false);
  const snapshot = useMemo(() => createStockVisitSnapshot(items, latestAccepted.current), [items, ready]);
  const changes = ready ? compareStockVisits(baseline, snapshot) : [];

  useEffect(() => {
    if (!ready || items.filter((item) => item.type === 'STOCK').length === 0) {
      return;
    }
    latestAccepted.current = snapshot;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
      setStorageUnavailable(false);
    } catch {
      setStorageUnavailable(true);
    }
  }, [snapshot, items, ready]);

  if (!ready || items.every((item) => item.type !== 'STOCK')) {
    return null;
  }
  return <details className="watchlist-stock-changes">
    <summary>股票变化回顾 <span>{baseline ? `${changes.length} 项变化` : '首次查看，建立本地基线'}</span></summary>
    {baseline && <p>与上次查看 {new Date(baseline.recordedAt).toLocaleString('zh-CN')} 比较，仅记录当前浏览器中的股票变化。</p>}
    {changes.length === 0 ? <p>{baseline ? '当前没有可确认的新变化。' : '下次进入自选页时，将自动对比价格、异动区间和归因报告变化。'}</p>
      : <ul>{changes.slice(0, 30).map((change) => {
        const item = items.find((value) => value.type === 'STOCK' && value.code === change.code)!;
        return <li key={`${change.code}-${change.kind}`}><button type="button" onClick={() => onOpen(item, change.kind === 'REPORT')}>
          <strong>{item.name || item.code}</strong><span>{change.detail}</span>
        </button></li>;
      })}</ul>}
    {changes.length > 30 && <p>另有 {changes.length - 30} 项变化，已记录最新快照。</p>}
    {storageUnavailable && <p>浏览器本地存储暂不可用，本次变化无法保存到下次查看。</p>}
  </details>;
}

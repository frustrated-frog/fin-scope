import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { useViewRevision } from '../../shared/api/useViewRevision';
import { loadRadarEventOverview } from './radarEventDetailApi';
import { RadarEventDetailDrawer } from './RadarEventDetailDrawer';
import type { RadarEventDetail } from './researchRadarTypes';
import './radarChanges.css';

const labels = { ALL: '全部', PROGRESS: '有新进展', SPREAD: '传播加快', NEW: '新出现', COVERAGE: '新增报道' };
type Kind = keyof typeof labels;
interface Change {
  eventId: string; title: string; summary: string; firstObservedAt: string; changedAt: string;
  kind: Exclude<Kind, 'ALL'>; newFact?: string; reportTitle?: string;
  sourcesBefore?: number; sourcesAfter?: number; addedReports?: number;
}
interface ChangePage { asOf: string; hasMore: boolean; items: Change[] }

function explanation(item: Change) {
  if (item.kind === 'PROGRESS') {
    return item.newFact;
  }
  if (item.kind === 'SPREAD') {
    return `独立来源从 ${item.sourcesBefore} 个增至 ${item.sourcesAfter} 个，报道净增 ${item.addedReports} 条。`;
  }
  if (item.kind === 'COVERAGE') {
    return item.reportTitle || `报道净增 ${item.addedReports} 条。`;
  }
  return item.summary || item.title;
}

export function RadarChangesPanel() {
  const [minutes, setMinutes] = useState(30);
  const [kind, setKind] = useState<Kind>('ALL');
  const [page, setPage] = useState<ChangePage>();
  const [index, setIndex] = useState(0);
  const [revision, setRevision] = useState(0);
  const [pending, setPending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<RadarEventDetail>();
  const [detailError, setDetailError] = useState('');
  const [opening, setOpening] = useState('');
  const sequence = useRef(0);
  const selection = useRef(0);

  async function load(nextPage: number, asOf?: string) {
    const request = ++sequence.current;
    setLoading(true);
    setError('');
    const params = new URLSearchParams({ minutes: String(minutes), page: String(nextPage) });
    if (kind !== 'ALL') {
      params.set('kind', kind);
    }
    if (asOf) {
      params.set('asOf', asOf);
    }
    try {
      const result = await api<ChangePage>(`/api/research-radar/changes?${params}`);
      if (request === sequence.current) {
        setPage(previous => {
          if (!nextPage || !previous) {
            return result;
          }
          const ids = new Set(previous.items.map(item => item.eventId));
          return { ...result, items: [...previous.items, ...result.items.filter(item => !ids.has(item.eventId))] };
        });
        setIndex(nextPage);
      }
    } catch (reason) {
      if (request === sequence.current) {
        setError(reason instanceof Error ? reason.message : '最近变化读取失败');
      }
    } finally {
      if (request === sequence.current) {
        setLoading(false);
      }
    }
  }
  useEffect(() => {
    setPage(undefined);
    setPending(false);
    setDetailError('');
    setOpening('');
    void load(0);
    return () => { sequence.current++; selection.current++; };
  }, [minutes, kind, revision]);
  useViewRevision(['news-river'], () => setPending(true));

  async function open(id: string) {
    const request = ++selection.current;
    setOpening(id);
    setDetailError('');
    try {
      const overview = await loadRadarEventOverview(id);
      if (request === selection.current) {
        setDetail(overview);
      }
    } catch (reason) {
      if (request === selection.current) {
        setDetailError(reason instanceof Error ? reason.message : '事件详情读取失败');
      }
    } finally {
      if (request === selection.current) {
        setOpening('');
      }
    }
  }

  return <section className="news-view radar-changes" aria-label="最近变化">
    <header className="news-section-heading">
      <div><h2>最近变化</h2>{page && <small>截至 {page.asOf.slice(11, 16)} · 北京时间</small>}</div>
      <div className="radar-changes-controls">
        <select aria-label="观察时段" value={minutes} onChange={event => setMinutes(Number(event.target.value))}>
          {[30, 60, 120].map(value => <option key={value} value={value}>最近 {value} 分钟</option>)}
        </select>
        <button type="button" className="ghost-button" onClick={() => setRevision(value => value + 1)} disabled={loading}>
          {pending ? '查看更新' : '刷新'}
        </button>
      </div>
    </header>
    <nav className="radar-state-filters" aria-label="变化类型">
      {(Object.keys(labels) as Kind[]).map(code => <button type="button" key={code} aria-pressed={code === kind}
        className={code === kind ? 'active' : ''} onClick={() => setKind(code)}>{labels[code]}</button>)}
    </nav>
    {error && <p className="news-window-error" role="alert">{error}</p>}
    {detailError && <p className="news-window-error" role="alert">{detailError}</p>}
    {loading && !page && <p role="status">正在读取最近变化…</p>}
    {page && !page.items.length && <p className="radar-empty-copy">最近 {minutes} 分钟内没有{kind === 'ALL' ? '已记录的变化' : '此类变化'}。</p>}
    <ul className="radar-changes-list">{page?.items.map(item => <li key={item.eventId}>
      <div className="radar-changes-meta"><time dateTime={item.changedAt}>{item.changedAt.slice(11, 16)}</time>
        <span data-kind={item.kind}>{labels[item.kind]}</span></div>
      <div><button type="button" disabled={opening === item.eventId} onClick={() => void open(item.eventId)}>{item.title}</button>
        <p>{explanation(item)}</p></div>
    </li>)}</ul>
    {page?.hasMore && <button type="button" className="ghost-button" disabled={loading} onClick={() => void load(index + 1, page.asOf)}>查看更多</button>}
    {detail && <RadarEventDetailDrawer event={detail.event} overview={detail} onClose={() => setDetail(undefined)}/>}
  </section>;
}

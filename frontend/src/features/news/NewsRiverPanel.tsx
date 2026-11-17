import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api/client';
import { useViewRevision } from '../../shared/api/useViewRevision';
import { NewsRiverChart } from './NewsRiverChart';
import { bucketTotal, lastFrame, riverCategories, shanghaiToday, timeLabel } from './newsRiverTypes';
import type { RiverCategory, RiverFrame, RiverItem, RiverOverview, RiverPage } from './newsRiverTypes';
import './newsRiver.css';

const changeLabels = { FIRST_ENTRY: '首次上榜', RETURNING: '再度上榜', RISING: '排名上升', FALLING: '排名下降', STEADY: '持续在榜' };
export function NewsRiverPanel({ onOpenCurrent }: { onOpenCurrent: (eventId: string) => void }) {
  const [date, setDate] = useState(shanghaiToday);
  const [overview, setOverview] = useState<RiverOverview>();
  const [pending, setPending] = useState<RiverOverview>();
  const [frameId, setFrameId] = useState<string>();
  const [category, setCategory] = useState<RiverCategory>('ALL');
  const [bucket, setBucket] = useState<string>();
  const [page, setPage] = useState<RiverPage>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [overviewError, setOverviewError] = useState('');
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [expanded, setExpanded] = useState<string>();
  const overviewRef = useRef(overview); overviewRef.current = overview;
  const dateRef = useRef(date); dateRef.current = date;
  const overviewRequest = useRef(0);
  const pageRequest = useRef(0);

  async function loadOverview(background = false) {
    const sequence = ++overviewRequest.current;
    const target = dateRef.current;
    if (!background) {
      setOverviewLoading(true);
    }
    try {
      const result = await api<RiverOverview>(`/api/news/river?date=${target}`);
      if (sequence !== overviewRequest.current || target !== dateRef.current) {
        return;
      }
      if (!Array.isArray(result.frames) || !Array.isArray(result.buckets)) {
        throw new Error('时间轴数据暂不可用');
      }
      setOverviewError('');
      if (background && overviewRef.current?.date === target) {
        if (lastFrame(result.frames)?.id !== lastFrame(overviewRef.current.frames)?.id) {
          setPending(result);
        }
      } else {
        setOverview(result); setPending(undefined); setBucket(undefined);
        setFrameId(lastFrame(result.frames)?.id);
      }
    } catch (reason) {
      if (sequence === overviewRequest.current) {
        setOverviewError(reason instanceof Error ? reason.message : '时间轴读取失败');
      }
    } finally {
      if (sequence === overviewRequest.current) {
        setOverviewLoading(false);
      }
    }
  }
  useEffect(() => {
    setOverviewError(''); setError(''); setLoading(false); setOverview(undefined); setPending(undefined); setPage(undefined); setFrameId(undefined); setExpanded(undefined);
    void loadOverview();
    return () => { overviewRequest.current++; pageRequest.current++; };
  // A date change starts an independent immutable browsing session.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);
  useViewRevision(['news-river'], () => { void loadOverview(true); });

  async function loadPage(target: string, selectedCategory: RiverCategory, selectedBucket: string | undefined, index = 0) {
    const sequence = ++pageRequest.current;
    setLoading(true); setError('');
    try {
      const params = new URLSearchParams({ batchId: target, category: selectedCategory, page: String(index) });
      if (selectedBucket) {
        params.set('bucket', selectedBucket);
      }
      const result = await api<RiverPage>(`/api/news/river/frame?${params}`);
      if (sequence !== pageRequest.current) {
        return;
      }
      setPage(previous => index && previous ? { ...result, items: [...previous.items, ...result.items] } : result);
    } catch (reason) {
      if (sequence === pageRequest.current) {
        setError(reason instanceof Error ? reason.message : '事件读取失败');
      }
    } finally {
      if (sequence === pageRequest.current) {
        setLoading(false);
      }
    }
  }
  useEffect(() => {
    setPage(undefined); setError(''); setExpanded(undefined); setLoading(Boolean(frameId)); pageRequest.current++;
    const timer = frameId ? window.setTimeout(() => { void loadPage(frameId, category, bucket); }, 120) : undefined;
    return () => { window.clearTimeout(timer); pageRequest.current++; };
  }, [frameId, category, bucket]);

  const frames = overview?.frames ?? [];
  const position = Math.max(0, frames.findIndex(item => item.id === frameId));
  const frame = frames[position];
  const isLatest = frameId === lastFrame(frames)?.id && date === shanghaiToday();
  const total = (overview?.buckets ?? []).reduce((sum, item) => sum + bucketTotal(item), 0);
  const pendingCount = Math.max(0, (pending?.buckets ?? []).reduce((sum, item) => sum + bucketTotal(item), 0) - total);
  const ranking = !bucket;

  function selectFrame(target: RiverFrame) { setFrameId(target.id); setBucket(undefined); }
  function returnToLatest() {
    if (date !== shanghaiToday()) {
      setDate(shanghaiToday());
      return;
    }
    const next = pending ?? overview;
    if (next) {
      setOverview(next);
      setPending(undefined);
      setFrameId(lastFrame(next.frames)?.id);
      setBucket(undefined);
    }
  }
  function selectBucket(start: string) {
    const end = new Date(`${start}+08:00`).getTime() + 30 * 60_000;
    const target = [...frames].reverse().find(item => new Date(`${item.publishedAt.slice(0, 23)}+08:00`).getTime() < end);
    if (target && target.publishedAt >= start) {
      setFrameId(target.id);
      setBucket(start);
    }
  }

  return <section className="news-river" aria-label="新闻河流与历史回放">
    <header className="river-header">
      <div><span className="river-eyebrow">NEWS RIVER <span aria-hidden="true">/</span> 信息的时间切面</span>
        <h1>看见新闻，如何汇成热点。</h1><p>沿着时间浏览今天的新事件，回到当时的榜单。</p></div>
      <label className="river-date">浏览日期<input aria-label="浏览日期" type="date" value={date} max={shanghaiToday()}
        min={overview?.earliestDate} onChange={event => {
          if (event.target.value) {
            setDate(event.target.value);
          }
        }} /></label>
    </header>
    {overviewError && <div className="river-error" role="alert">{overviewError}<button type="button" onClick={() => void loadOverview()}>重新读取</button></div>}
    <div className="river-instrument">
      <div className="river-instrument-heading"><div><span className="river-status-dot" /><strong>{date === shanghaiToday() ? '今日信息流' : `${date} 信息流`}</strong>
        <span className="river-total">{total.toLocaleString()} 个新收录事件</span></div><span>点击曲线中的时段，展开对应事件</span></div>
      <div className="river-legend" role="group" aria-label="资讯主题">{riverCategories.map(item =>
        <button key={item.code} type="button" data-category={item.code.toLowerCase()} aria-pressed={category === item.code}
          onClick={() => setCategory(item.code)}>{item.field && <i aria-hidden="true" />}{item.name}</button>)}</div>
      <NewsRiverChart date={date} buckets={overview?.buckets ?? []} selected={bucket} category={category} firstRecordedAt={frames[0]?.publishedAt} lastRecordedAt={lastFrame(frames)?.publishedAt} onSelect={selectBucket} />
      <div className="river-time-controls">
        <div className="river-time-readout"><span>{isLatest ? '最近一次发布' : '正在回看'}</span><strong>{timeLabel(frame?.publishedAt)}</strong></div>
        <div className="river-scrubber"><div><span>{timeLabel(frames[0]?.publishedAt)}</span><span>{timeLabel(lastFrame(frames)?.publishedAt)}</span></div>
          <input type="range" aria-label="选择历史时刻" min={0} max={Math.max(0, frames.length - 1)} value={position} disabled={frames.length < 2}
            onChange={event => selectFrame(frames[Number(event.target.value)])} /></div>
        <div className="river-step-buttons"><button type="button" aria-label="上一个时刻" disabled={!position || !frames.length} onClick={() => selectFrame(frames[position - 1])}>←</button>
          <button type="button" aria-label="下一个时刻" disabled={position >= frames.length - 1 || !frames.length} onClick={() => selectFrame(frames[position + 1])}>→</button></div>
        <button type="button" className="river-return" disabled={!frames.length && date === shanghaiToday()} onClick={returnToLatest}>回到实时 <span aria-hidden="true">↗</span></button>
      </div>
    </div>
    {pending && <div className="river-new-events" role="status"><span>{pendingCount ? `又有 ${pendingCount} 个新事件汇入` : '榜单已有新的变化'} · 当前阅读位置已保留</span>
      <button type="button" onClick={returnToLatest}>查看更新 →</button></div>}
    <div className="river-list-heading"><div><span className="river-eyebrow">{ranking ? 'THE MOMENT' : 'NEW ARRIVALS'}</span>
      <h2>{ranking ? `${timeLabel(frame?.publishedAt)} 的热点榜单` : `${timeLabel(bucket)}–${nextHalfHour(bucket!)} 新收录的事件`}</h2>
      <p>{ranking ? '标题、摘要与排名保留在当时。' : '按系统收录时间排列，同一事件计一次。'}</p></div>
      <div>{bucket && <button type="button" className="river-clear" onClick={() => setBucket(undefined)}>查看该时刻榜单</button>}<span>{page?.total ?? 0} 件</span></div></div>
    {loading && !page && <p className="river-empty" role="status">正在展开这个时刻…</p>}
    {error && <div className="river-error" role="alert">{error}<button type="button" onClick={() => frameId && void loadPage(frameId, category, bucket)}>重试读取</button></div>}
    {overviewLoading && !overview && <p className="river-empty" role="status">正在读取时间轴…</p>}
    {overview && !loading && !error && !overviewError && !page?.items.length && <div className="river-empty"><span aria-hidden="true">◷</span><h3>{frames.length ? '这个切面暂时没有事件' : '时间，从第一次记录开始'}</h3>
      <p>{frames.length ? '试试其他主题或时段。' : '雷达下一次成功更新后，曲线和历史榜单会从这里开始积累。'}</p></div>}
    <div className="river-event-list">{page?.items.map(item => <RiverEvent key={item.eventKey} item={item} expanded={expanded === item.eventKey}
      onExpand={() => setExpanded(expanded === item.eventKey ? undefined : item.eventKey)} onOpenCurrent={onOpenCurrent} />)}</div>
    {page && page.items.length < page.total && <button type="button" className="river-load-more" disabled={loading} onClick={() => frameId && void loadPage(frameId, category, bucket, page.page + 1)}>{loading ? '正在读取…' : `继续查看 · 还有 ${page.total - page.items.length} 件`}</button>}
    <footer className="river-footnote">北京时间 · 保留最近七天的实际记录 · 曲线统计新收录事件，榜单按成功发布时刻回放</footer>
  </section>;
}
function nextHalfHour(start: string) {
  const minute = Number(start.slice(14, 16)); const hour = Number(start.slice(11, 13));
  return minute === 0 ? `${start.slice(11, 13)}:30` : `${String(hour + 1).padStart(2, '0')}:00`;
}
function RiverEvent({ item, expanded, onExpand, onOpenCurrent }: { item: RiverItem; expanded: boolean; onExpand: () => void; onOpenCurrent: (id: string) => void }) {
  return <article className="river-event" data-category={item.category.toLowerCase()}>
    <span className="river-event-position">{item.rank ? String(item.rank).padStart(2, '0') : timeLabel(item.observedAt)}</span>
    <div className="river-event-content"><div className="river-event-meta"><span>{riverCategories.find(c => c.code === item.category)?.name ?? '其他'}</span>
      {item.change && <span className={`river-change river-change-${item.change.toLowerCase()}`}>{changeLabels[item.change]}{item.change === 'RISING' ? ` ↑${item.rankDelta}` : ''}</span>}
      {item.sourceCount != null && <span>{item.sourceCount} 个来源 · {item.reportCount} 条报道</span>}</div>
      <button className="river-event-title" type="button" aria-expanded={expanded} onClick={onExpand}>{item.title}<span aria-hidden="true">{expanded ? '−' : '+'}</span></button>
      {expanded && <div className="river-event-detail"><p>{item.summary || '当时尚未保存摘要，可查看当前事件的后续信息。'}</p><button type="button" onClick={() => onOpenCurrent(item.eventId)}>查看实时详情 ↗</button></div>}</div>
    {item.hotspotScore != null && <div className="river-event-score"><strong>{item.hotspotScore}</strong><span>热度</span><i style={{ width: `${Math.min(100, item.hotspotScore)}%` }} /></div>}
  </article>;
}

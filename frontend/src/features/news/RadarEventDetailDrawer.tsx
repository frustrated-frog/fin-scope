import { useEffect, useRef, useState } from 'react';

import { ApiError, api } from '../../shared/api/client';
import { RadarRankHistory } from './RadarRankHistory';
import { RadarTimeline } from './RadarTimeline';
import { RadarTrustPanel } from './RadarTrustPanel';
import { loadRadarEventOverview, loadRadarEventSection, type RadarContextSection } from './radarEventDetailApi';
import type { RadarEvent, RadarEventDetail, RadarInterpretation, RadarWorkspaceState } from './researchRadarTypes';
import './RadarEventDetailDrawer.css';

const POLL_INTERVAL_MS = 1_500;
const MAX_POLL_RETRIES = 4;
type Tab = 'interpretation' | RadarContextSection;
type ContextSection = { data: RadarEventDetail } | { error: string };
const tabs: Array<[Tab, string]> = [
  ['interpretation', '解读'], ['timeline', '事件脉络'], ['evidence', '证据'], ['tracking', '跟踪'],
];
const priorityTicks = Array.from({ length: 40 }, (_, index) => index);

export function RadarEventDetailDrawer({ event, categoryName, overview, onClose, onEventChange }: {
  event: RadarEvent;
  categoryName?: string;
  overview?: RadarEventDetail;
  onClose: () => void;
  onEventChange?: (event: RadarEvent) => void;
}) {
  const [detail, setDetail] = useState(overview);
  const [tab, setTab] = useState<Tab>('interpretation');
  const [error, setError] = useState(''); const [requesting, setRequesting] = useState(false);
  const [pollFailures, setPollFailures] = useState(0);
  const [sections, setSections] = useState<Partial<Record<RadarContextSection, ContextSection>>>({});
  const [contextRevision, setContextRevision] = useState(0);
  const stopped = useRef(false); const timer = useRef<number>();
  const polling = useRef(false);
  const readRequested = useRef(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const eventPath = `/api/research-radar/events/${event.id}`;
  const section = tab === 'interpretation' ? undefined : sections[tab];
  const context = section && 'data' in section ? section.data : undefined;

  useEffect(() => {
    const element = dialog.current;
    if (!element) {
      return;
    }
    if (typeof element.showModal === 'function') {
      element.showModal();
    } else {
      element.setAttribute('open', '');
    }
    return () => {
      if (typeof element.close === 'function') {
        element.close();
      } else {
        element.removeAttribute('open');
      }
    };
  }, []);

  useEffect(() => {
    let active = true;
    stopped.current = false;
    void load(() => active);
    return () => {
      active = false;
      stopped.current = true;
      window.clearTimeout(timer.current);
    };
  }, [event.id]);

  useEffect(() => {
    if (!detail || detail.workspaceState?.read || readRequested.current) {
      return;
    }
    readRequested.current = true;
    void api<RadarWorkspaceState>(`${eventPath}/state`, { method: 'PATCH', body: JSON.stringify({ read: true }) })
      .then((state) => {
        if (!stopped.current) {
          setDetail((current) => current && {
            ...current,
            workspaceState: { ...(current.workspaceState ?? state), read: state.read, readAt: state.readAt },
          });
        }
      })
      .catch(() => undefined);
  }, [detail, eventPath]);

  useEffect(() => {
    if (tab === 'interpretation' || section) {
      return;
    }
    let active = true;
    void loadRadarEventSection(event.id, tab).then((data) => {
      if (active) {
        setSections((current) => ({ ...current, [tab]: { data } }));
      }
    }).catch((failure: unknown) => {
      if (active) {
        const message = failure instanceof Error ? failure.message : '事件资料加载失败';
        setSections((current) => ({ ...current, [tab]: { error: message } }));
      }
    });
    return () => { active = false; };
  }, [tab, section, event.id, contextRevision]);

  async function load(isActive = () => !stopped.current) {
    try {
      const next = overview ?? await loadRadarEventOverview(event.id);
      if (!isActive()) {
        return;
      }
      setDetail(next);
      setError('');
      if (next.interpretation?.status === 'UNAVAILABLE') {
        setTab((current) => current === 'interpretation' ? 'evidence' : current);
      }
      onEventChange?.({ ...event, read: true, followed: next.workspaceState?.followed ?? event.followed,
        disposition: next.workspaceState?.disposition ?? event.disposition });
      if (next.interpretation && isPending(next.interpretation)) {
        schedulePoll(next.interpretation, 0);
      }
    } catch (loadError) {
      if (isActive()) {
        setError(loadError instanceof Error ? loadError.message : '事件详情加载失败');
      }
    }
  }

  function invalidateInterpretationContext() {
    setSections((current) => ({ tracking: current.tracking }));
    setContextRevision((current) => current + 1);
  }

  async function requestInterpretation() {
    setTab('interpretation');
    if (!detail || requesting || isPending(detail.interpretation) || !shouldRequest(detail.interpretation)) {
      return;
    }
    setRequesting(true);
    setError('');
    try {
      const queued = await api<RadarInterpretation>(`${eventPath}/interpretation?automatic=false`, { method: 'POST' });
      if (stopped.current) {
        return;
      }
      setDetail((current) => current ? { ...current, interpretation: queued } : current);
      if (isPending(queued)) {
        schedulePoll(queued, 0);
      } else if (queued.status === 'SUCCESS') {
        invalidateInterpretationContext();
      }
    } catch (requestError) {
      if (!stopped.current) {
        setError(requestError instanceof Error ? requestError.message : '事件解读生成失败');
      }
    } finally {
      if (!stopped.current) {
        setRequesting(false);
      }
    }
  }
  function schedulePoll(pending: RadarInterpretation, failures: number) {
    window.clearTimeout(timer.current);
    const delay = POLL_INTERVAL_MS * Math.min(2 ** failures, 6);
    timer.current = window.setTimeout(() => void pollInterpretation(pending, failures), delay);
  }

  async function pollInterpretation(pending: RadarInterpretation, failures: number) {
    if (stopped.current || polling.current) {
      return;
    }
    window.clearTimeout(timer.current);
    polling.current = true;
    try {
      const next = pending.id == null
        ? (await loadRadarEventOverview(event.id)).interpretation
        : await api<RadarInterpretation>(`${eventPath}/interpretation/${pending.id}`);
      if (stopped.current) {
        return;
      }
      if (!next) {
        throw new ApiError(404, '解读记录不存在或已过期');
      }
      setPollFailures(0);
      setDetail((current) => current && { ...current, interpretation: next });
      if (isPending(next)) {
        schedulePoll(next, 0);
      } else {
        invalidateInterpretationContext();
      }
    } catch (readError) {
      if (stopped.current) {
        return;
      }
      const attempts = failures + 1;
      if (attempts <= MAX_POLL_RETRIES) {
        setPollFailures(attempts);
        schedulePoll(pending, attempts);
      } else if (readError instanceof ApiError && readError.status === 404) {
        setPollFailures(0);
        setDetail((current) => current && { ...current, interpretation: {
          ...pending, status: 'FAILED', failureMessage: '解读记录已过期，可重新生成。',
        } });
      } else {
        setPollFailures(attempts);
      }
    } finally {
      polling.current = false;
    }
  }

  async function updateState(patch:{followed?:boolean;disposition?:'ACTIVE'|'LATER'|'IGNORED'}){
    const state=await api<RadarWorkspaceState>(`/api/research-radar/events/${event.id}/state`,{method:'PATCH',body:JSON.stringify(patch)});
    setDetail((current)=>current?{...current,workspaceState:state}:current);onEventChange?.({...event,read:state.read,followed:state.followed,disposition:state.disposition});
  }

  const interpretation = detail?.interpretation;
  const result = interpretation?.result;
  const followed = Boolean(detail?.workspaceState?.followed ?? event.followed);
  const disposition = detail?.workspaceState?.disposition ?? event.disposition;
  const reasons = event.reasons?.slice(0, 3) ?? [];
  const titlePrefix = `【${event.title}】`;
  const summary = event.summary?.startsWith(titlePrefix)
    ? event.summary.slice(titlePrefix.length).trim() || event.summary
    : event.summary;
  const evidence = sections.evidence && 'data' in sections.evidence ? sections.evidence.data : undefined;
  const timeline = sections.timeline && 'data' in sections.timeline ? sections.timeline.data.timeline : undefined;
  const counts: Partial<Record<Tab, number>> = {
    timeline: timeline?.length,
    evidence: evidence ? evidence.signals.length + (evidence.evidence?.length ?? 0)
      : event.signalCount + (event.evidenceCount ?? 0),
    tracking: event.researchRunCount,
  };
  const actionLabel = pollFailures ? '解读状态待读取' : requesting || isPending(interpretation)
    ? '解读生成中…' : result && !interpretation?.stale ? '查看 AI 解读' : '生成 AI 解读';

  return <dialog ref={dialog} className="radar-detail-drawer" aria-labelledby="radar-detail-title"
    onCancel={(cancelEvent) => { cancelEvent.preventDefault(); onClose(); }}>
    <div className="radar-detail-scroll">
      <div className="radar-dossier">
        <header className="radar-dossier-header">
          <div className="radar-dossier-id"><span>EVENT DOSSIER</span><small>#{event.id}</small></div>
          <button type="button" className="radar-drawer-close" aria-label="关闭事件解读" onClick={onClose}>×</button>
          <h2 id="radar-detail-title">{event.title}</h2>
          <div className="radar-dossier-meta">
            {categoryName ? <span>{categoryName}</span> : null}
            {event.lastSeenAt ? <span>更新于 <time dateTime={event.lastSeenAt}>{formatDateTime(event.lastSeenAt, true)}</time></span> : null}
            <span>{event.sourceCount} 个独立来源共同报道</span>
          </div>
        </header>

        <div className="radar-dossier-layout">
          <div className="radar-dossier-main">
            <nav className="radar-dossier-tabs" aria-label="事件详情栏目">
              {tabs.map(([code, label]) => <button type="button" key={code} aria-pressed={tab === code} onClick={() => setTab(code)}>
                {label}{counts[code] ? <small aria-hidden="true">{counts[code]}</small> : null}
              </button>)}
            </nav>
            {tab === 'interpretation' ? <>
              {summary ? <p className="radar-detail-overview">{summary}</p> : null}
              <section className="radar-detail-section" aria-labelledby="interpretation-heading">
                <div className="radar-detail-section-heading">
                  <h3 id="interpretation-heading">事件解读</h3>
                  {pollFailures ? <span>待读取</span> : detail ? <StatusBadge interpretation={interpretation}/> : null}
                </div>
                {error ? <div className="radar-dossier-notice" role="alert">
                  <p>{error}</p>
                  {!detail ? <button type="button" className="radar-dossier-retry" onClick={() => void load()}>重试加载</button> : null}
                </div> : null}
                {pollFailures && interpretation ? <div className="radar-dossier-notice" role="status">
                  <p>{pollFailures <= MAX_POLL_RETRIES ? '暂时读不到解读进度，正在自动重试。' : '解读进度暂不可读，请重试读取。'}</p>
                  <button type="button" className="radar-dossier-retry" onClick={() => void pollInterpretation(interpretation, 0)}>重试读取</button>
                </div> : null}
                {interpretation?.stale && result ? <p className="radar-dossier-notice">这是此前证据版本的解读，可点击「生成 AI 解读」更新。</p> : null}
                {!detail ? (error ? null : <DrawerSkeleton/>) : result ? <div className="radar-interpretation-grid">
                  <div className="radar-dossier-findings">
                    <InterpretationBlock label="已确认事实" value={result.factSummary} fact singleSource={event.sourceCount === 1}/>
                    <InterpretationBlock label="本次新变化" value={result.newDevelopment}/>
                  </div>
                  <InterpretationBlock label="为什么重要" value={result.whyItMatters} accent/>
                  <InterpretationList label="影响链条" values={result.impactChain} ordered/>
                  <div className="radar-dossier-findings">
                    <InterpretationList label="仍存疑点" values={result.uncertainties}/>
                    <InterpretationList label="下一步观察" values={result.nextObservations}/>
                  </div>
                </div> : interpretation?.status === 'UNAVAILABLE' ? <p className="radar-dossier-notice">模型解读未启用，可继续查看证据与来源、事件脉络。</p>
                  : isPending(interpretation) || requesting ? (pollFailures ? null : <p className="radar-dossier-notice" role="status">正在整理事件事实与证据…</p>)
                    : <p className="radar-dossier-notice">{interpretation?.failureMessage || '点击「生成 AI 解读」开始。'}</p>}
              </section>
            </> : null}
            {tab !== 'interpretation' && !section ? <DrawerSkeleton label="正在加载事件资料"/> : null}
            {tab !== 'interpretation' && section && 'error' in section ? <div className="radar-dossier-notice" role="alert">
              <p>{section.error}</p>
              <button type="button" className="radar-dossier-retry"
                onClick={() => setSections((current) => ({ ...current, [tab]: undefined }))}>重试加载</button>
            </div> : null}

            {tab === 'timeline' && context ? <section className="radar-detail-section">
              <div className="radar-detail-section-heading"><h3>事件脉络</h3><span>{context.timeline?.length ?? 0} 个节点</span></div>
              <RadarTimeline items={context.timeline}/>
            </section> : null}

            {tab === 'evidence' && context ? <section className="radar-detail-section">
              <div className="radar-detail-section-heading"><h3>证据与来源</h3><span>{context.signals.length + (context.evidence?.length ?? 0)} 条</span></div>
              <RadarTrustPanel trust={context.trust}/>
              {context.evidence?.map((item, index) => <Evidence key={item.id ?? `${item.toolCode}-${index}`}
                title={item.title} source={item.sourceName || evidenceTypeLabel(item.evidenceType)} meta={evidenceTypeLabel(item.evidenceType)}
                url={item.url} summary={item.summary}/>)}
              {context.signals.map((signal) => <Evidence key={signal.id} title={signal.title} source={signal.sourceName}
                meta={formatDateTime(signal.publishedAt)} url={signal.url} summary={signal.content || signal.matchReason}/>)}
              {context.agentTrace?.length ? <details className="radar-agent-trace radar-drawer-trace"><summary>Agent 运行状态</summary>
                <ol>{context.agentTrace.map((trace, index) => <li key={`${trace.nodeName}-${index}`}>
                  <div><strong>{agentNodeLabel(trace.nodeName)}</strong><span>{trace.status} · {trace.durationMs}ms</span></div>
                  {trace.summary ? <p>{trace.summary}</p> : null}
                  {trace.fallbackUsed ? <small>已降级：{trace.fallbackReason || trace.errorType || '使用确定性结果'}</small> : null}
                </li>)}</ol>
              </details> : null}
            </section> : null}

            {tab === 'tracking' && context ? <section className="radar-detail-section">
              <div className="radar-detail-section-heading"><h3>临时跟踪</h3><span>随事件缓存过期</span></div>
              <p className="radar-empty-copy">需要长期保存和记录后续变化时，请先将事件记入大事记。</p>
              <RadarRankHistory eventId={event.id}/>
              <div className="radar-research-links"><h3>关联研究结论</h3>
                {context.researchLinks?.length ? <ul>{context.researchLinks.map((link) => <li key={link.id}>
                  <div><strong>研究运行 #{link.researchRunId}</strong><span>{link.status}</span></div>
                  {link.questionSnapshot ? <p>{link.questionSnapshot}</p> : null}
                  {link.summary ? <small>{link.summary}</small> : null}
                </li>)}</ul> : <p className="radar-empty-copy">从事件卡片启动研究后，结论会回到这里。</p>}
              </div>
            </section> : null}
          </div>
          <aside className="radar-dossier-rail" aria-label="事件指标与操作">
            <div className="radar-dossier-priority">
              <div className="radar-rail-label"><span>PRIORITY</span><span>/ 100</span></div>
              <div className="radar-priority-value">
                <strong>{event.priorityScore}</strong><span>{event.recommendation}</span>
              </div>
              <div className="radar-priority-ticks" aria-hidden="true">
                {priorityTicks.map((tick) => <i key={tick} className={tick < Math.round(event.priorityScore * 0.4) ? 'is-filled' : undefined}/>)}
              </div>
              <p>研究优先级</p>
            </div>
            <div className="radar-dossier-verification" data-cross-checked={event.sourceCount >= 2}>
              <div className="radar-rail-label"><span>VERIFICATION</span><strong>{event.sourceCount} 个独立来源</strong></div>
              <div className="radar-source-meter" aria-hidden="true">
                <i className={event.sourceCount > 0 ? 'is-filled' : undefined}/>
                <i className={event.sourceCount >= 2 ? 'is-filled' : undefined}/>
              </div>
              {event.uncertainty ? <p>{event.uncertainty}</p> : null}
            </div>
            {reasons.length ? <div className="radar-dossier-clues">
              <div className="radar-rail-label">研究线索</div>
              <ul>{reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
            </div> : null}
            <div className="radar-dossier-actions">
              <button type="button" className="radar-dossier-primary"
                disabled={!detail || requesting || interpretation?.status === 'UNAVAILABLE'} onClick={() => void requestInterpretation()}>
                <span aria-hidden="true">✦</span>{actionLabel}
              </button>
              <div className="radar-drawer-state">
                <button type="button" aria-pressed={followed} onClick={() => void updateState({ followed: !followed })}>
                  <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 2.5h8v11l-4-3-4 3z"/></svg>
                  {followed ? '取消临时关注' : '临时关注'}
                </button>
                <button type="button" aria-pressed={disposition === 'LATER'} onClick={() => void updateState({ disposition: 'LATER' })}>
                  <svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="5.5"/><path d="M8 5v3l2 1.5"/></svg>
                  稍后看
                </button>
                <button type="button" aria-pressed={disposition === 'IGNORED'} onClick={() => void updateState({ disposition: 'IGNORED' })}>
                  <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M2 2l12 12M2 8s2-4 6-4 6 4 6 4a9 9 0 0 1-2 2.3M4 5.7A10 10 0 0 0 2 8s2 4 6 4c.9 0 1.7-.2 2.4-.5"/></svg>
                  忽略
                </button>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  </dialog>;
}

function Evidence({title,source,meta,url,summary}:{title:string;source:string;meta:string;url?:string;summary?:string}){return <article className="radar-drawer-evidence"><div><span>{source}</span><small>{meta}</small></div>{url?<a href={url} target="_blank" rel="noreferrer">{title}</a>:<strong>{title}</strong>}{summary?<p>{summary}</p>:null}</article>;}
function shouldRequest(value?: RadarInterpretation) { return value?.status !== 'UNAVAILABLE' && (!value || value.stale || value.status === 'FAILED'); }
function isPending(value?: RadarInterpretation) { return !value?.stale && (value?.status === 'QUEUED' || value?.status === 'RUNNING'); }
function StatusBadge({ interpretation }: { interpretation?: RadarInterpretation }) {
  if (!interpretation) {
    return <span>未生成</span>;
  }
  if (interpretation.stale) {
    return <span>证据已更新</span>;
  }
  switch (interpretation.status) {
    case 'SUCCESS': return <span className="is-success">解读完成</span>;
    case 'UNAVAILABLE': return <span>未启用</span>;
    case 'FAILED': return <span className="is-error">生成失败</span>;
    default: return <span>生成中</span>;
  }
}
function interpretationText(value: string) {
  return value.replace(/[ \t]*\[(?:signal|evidence):\d+(?:[ \t]*[,，、][ \t]*(?:signal|evidence):\d+)*\]/gi, '').trim();
}
function InterpretationBlock({ label, value, accent, fact, singleSource }: {
  label: string; value: string; accent?: boolean; fact?: boolean; singleSource?: boolean;
}) {
  const className = accent ? 'radar-dossier-impact' : fact ? 'radar-dossier-fact' : undefined;
  return <article className={className}>
    <header className="radar-interpretation-heading">
      <h4>{accent ? <><span lang="en">WHY IT MATTERS</span> · </> : null}{label}</h4>
      {singleSource ? <span className="radar-single-source">单一来源</span> : null}
    </header>
    <p>{interpretationText(value)}</p>
  </article>;
}
function InterpretationList({ label, values, ordered = false }: { label: string; values: string[]; ordered?: boolean }) {
  const List = ordered ? 'ol' : 'ul';
  const content = values.map(interpretationText).filter(Boolean);
  return <article className={ordered ? 'radar-dossier-chain' : undefined}>
    <header className="radar-interpretation-heading">
      <h4>{label}</h4><small>{content.length}</small>
    </header>
    <List>{content.map((value, index) => <li key={index}>{value}</li>)}</List>
  </article>;
}
function DrawerSkeleton({ label = '正在加载事件详情' }: { label?: string }) {
  return <div className="radar-drawer-skeleton" aria-label={label}><span/><span/><span/></div>;
}
function evidenceTypeLabel(value?:string){if(value==='ANNOUNCEMENT')return '公司公告';if(value==='INTERACTION')return '互动问答';if(value==='BROKER_REPORT')return '机构研报';if(value==='NEWS_FLASH'||value==='PUBLIC_NEWS')return '公开资讯';return '补充资料';}
function agentNodeLabel(value:string){if(value==='radar-evidence-plan')return '证据规划';if(value==='radar-evidence-synthesis')return '证据综合';if(value.startsWith('radar-tool-'))return '多源检索';if(value==='radar-event-interpretation')return '事件解读';return value;}
function formatDateTime(value?: string, includeYear = false) {
  const date = value ? new Date(value) : undefined;
  if (!date || Number.isNaN(date.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat('sv-SE', {
    year: includeYear ? 'numeric' : undefined,
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date);
}

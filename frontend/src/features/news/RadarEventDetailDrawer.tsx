import { RadarRankHistory } from "./RadarRankHistory";
import { useEffect, useRef, useState } from 'react';

import { ApiError, api } from '../../shared/api/client';
import { RadarTimeline } from './RadarTimeline';
import { RadarTrustPanel } from './RadarTrustPanel';
import type { RadarEvent, RadarEventDetail, RadarInterpretation, RadarWorkspaceState } from './researchRadarTypes';

const POLL_INTERVAL_MS = 1_500;
const MAX_POLL_RETRIES = 4;
type Tab = 'interpretation' | 'timeline' | 'evidence' | 'tracking';

export function RadarEventDetailDrawer({ event, onClose, onEventChange }: { event: RadarEvent; onClose: () => void; onEventChange?: (event: RadarEvent) => void }) {
  const [detail, setDetail] = useState<RadarEventDetail>(); const [tab,setTab]=useState<Tab>('interpretation');
  const [error, setError] = useState(''); const [requesting, setRequesting] = useState(false);
  const [pollFailures, setPollFailures] = useState(0);
  const stopped = useRef(false); const timer = useRef<number>();
  const polling = useRef(false);
  const eventPath = `/api/research-radar/events/${event.id}`;

  useEffect(() => {
    let active = true;
    stopped.current = false; setDetail(undefined); setError(''); setPollFailures(0); setTab('interpretation');
    void load(() => active);
    function onKeyDown(keyEvent: KeyboardEvent) { if (keyEvent.key === 'Escape') onClose(); }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      active = false;
      stopped.current = true;
      window.clearTimeout(timer.current);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [event.id]);

  async function load(isActive = () => !stopped.current) {
    try {
      const next = await api<RadarEventDetail>(eventPath);
      if (!isActive()) {
        return;
      }
      setDetail(next);
      setError('');
      if (next.interpretation?.status === 'UNAVAILABLE') {
        setTab('evidence');
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

  async function refreshInterpretationContext() {
    try {
      const next = await api<RadarEventDetail>(eventPath);
      if (!stopped.current) {
        setDetail((current) => current && { ...current, timeline: next.timeline, trust: next.trust, agentTrace: next.agentTrace });
      }
    } catch {
      // The saved interpretation is already visible. Reopening the drawer refreshes its context.
    }
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
        void refreshInterpretationContext();
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
        ? (await api<RadarEventDetail>(eventPath)).interpretation
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
        void refreshInterpretationContext();
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

  const interpretation = detail?.interpretation; const result = interpretation?.result;
  return <div className="radar-drawer-backdrop" onMouseDown={(mouseEvent) => { if (mouseEvent.target === mouseEvent.currentTarget) onClose(); }}>
    <aside className="radar-detail-drawer" role="dialog" aria-modal="true" aria-labelledby="radar-detail-title">
      <header><div><span>EVENT RESEARCH DOSSIER</span><h2 id="radar-detail-title">{event.title}</h2><div className="radar-drawer-state">
        <button type="button" disabled={!detail || requesting || interpretation?.status === 'UNAVAILABLE'} onClick={() => void requestInterpretation()}>
          {pollFailures ? '解读状态待读取' : requesting || isPending(interpretation) ? '解读生成中…' : result && !interpretation?.stale ? '查看 AI 解读' : '生成 AI 解读'}
        </button>
        <button type="button" aria-pressed={Boolean(detail?.workspaceState?.followed??event.followed)} onClick={()=>void updateState({followed:!(detail?.workspaceState?.followed??event.followed)})}>{detail?.workspaceState?.followed??event.followed?'取消临时关注':'临时关注'}</button><button type="button" onClick={()=>void updateState({disposition:'LATER'})}>稍后看</button><button type="button" onClick={()=>void updateState({disposition:'IGNORED'})}>忽略</button></div></div><button type="button" className="radar-drawer-close" aria-label="关闭事件解读" onClick={onClose}>×</button></header>
      <nav className="radar-dossier-tabs" aria-label="事件详情栏目">{([['interpretation','解读'],['timeline','事件脉络'],['evidence','证据'],['tracking','跟踪']] as [Tab,string][]).map(([code,label])=><button type="button" key={code} className={tab===code?'active':''} aria-pressed={tab===code} onClick={()=>setTab(code)}>{label}</button>)}</nav>
      <div className="radar-detail-scroll">
        <section className="radar-detail-overview"><div><strong>{event.priorityScore}</strong><span>研究优先级</span></div><p>{event.summary}</p><small>{event.sourceCount} 个独立来源共同报道</small></section>

        {error && !detail ? <div role="alert"><p>{error}</p><button type="button" className="ghost-button" onClick={() => void load()}>重试加载</button></div> : null}
        {tab==='interpretation'?<section className="radar-detail-section" aria-labelledby="interpretation-heading">
          <div className="radar-detail-section-heading"><h3 id="interpretation-heading">事件解读</h3>{pollFailures ? <span>待读取</span> : <StatusBadge interpretation={interpretation}/>}</div>
          {detail && error ? <div role="alert"><p>{error}</p></div> : null}
          {pollFailures && interpretation ? <div role="status">
            <p>{pollFailures <= MAX_POLL_RETRIES ? '暂时读不到解读进度，正在自动重试。' : '解读进度暂不可读，请重试读取。'}</p>
            <button type="button" className="ghost-button" onClick={() => void pollInterpretation(interpretation, 0)}>重试读取</button>
          </div> : null}
          {interpretation?.stale && result ? <p>这是此前证据版本的解读，可点击上方按钮更新。</p> : null}
          {!detail?(error ? null : <DrawerSkeleton/>):result?<div className="radar-interpretation-grid"><InterpretationBlock label="已确认事实" value={result.factSummary}/><InterpretationBlock label="本次新变化" value={result.newDevelopment}/><InterpretationBlock label="为什么重要" value={result.whyItMatters} accent/><InterpretationList label="影响链条" values={result.impactChain} ordered/><InterpretationList label="仍存疑点" values={result.uncertainties}/><InterpretationList label="下一步观察" values={result.nextObservations}/></div>
          :interpretation?.status === 'UNAVAILABLE' ? <p>模型解读未启用，可继续查看证据与来源、事件脉络。</p>
            :isPending(interpretation) || requesting ? (pollFailures ? null : <p role="status">正在整理事件事实与证据…</p>)
              :<p>{interpretation?.failureMessage || '点击上方「生成 AI 解读」开始。'}</p>}
        </section>:null}

        {tab==='timeline'?<section className="radar-detail-section"><div className="radar-detail-section-heading"><h3>事件脉络</h3><span>{detail?.timeline?.length??0} 个节点</span></div><RadarTimeline items={detail?.timeline}/></section>:null}

        {tab==='evidence'?<section className="radar-detail-section"><div className="radar-detail-section-heading"><h3>证据与来源</h3><span>{(detail?.signals.length??0)+(detail?.evidence?.length??0)} 条</span></div><RadarTrustPanel trust={detail?.trust}/>
          {detail?.evidence?.map((item,index)=><Evidence key={item.id??`${item.toolCode}-${index}`} title={item.title} source={item.sourceName||evidenceTypeLabel(item.evidenceType)} meta={evidenceTypeLabel(item.evidenceType)} url={item.url} summary={item.summary}/>)}
          {detail?.signals.map((signal)=><Evidence key={signal.id} title={signal.title} source={signal.sourceName} meta={formatDateTime(signal.publishedAt)} url={signal.url} summary={signal.content || signal.matchReason}/>)}
          {detail?.agentTrace?.length?<details className="radar-agent-trace radar-drawer-trace"><summary>Agent 运行状态</summary><ol>{detail.agentTrace.map((trace,index)=><li key={`${trace.nodeName}-${index}`}><div><strong>{agentNodeLabel(trace.nodeName)}</strong><span>{trace.status} · {trace.durationMs}ms</span></div>{trace.summary?<p>{trace.summary}</p>:null}{trace.fallbackUsed?<small>已降级：{trace.fallbackReason||trace.errorType||'使用确定性结果'}</small>:null}</li>)}</ol></details>:null}
        </section>:null}

        {tab==='tracking'?<section className="radar-detail-section"><div className="radar-detail-section-heading"><h3>临时跟踪</h3><span>随事件缓存过期</span></div><p className="radar-empty-copy">需要长期保存和记录后续变化时，请先将事件记入大事记。</p>
          <RadarRankHistory eventId={event.id} /><div className="radar-research-links"><h3>关联研究结论</h3>{detail?.researchLinks?.length?<ul>{detail.researchLinks.map((link)=><li key={link.id}><div><strong>研究运行 #{link.researchRunId}</strong><span>{link.status}</span></div>{link.questionSnapshot?<p>{link.questionSnapshot}</p>:null}{link.summary?<small>{link.summary}</small>:null}</li>)}</ul>:<p className="radar-empty-copy">从事件卡片启动研究后，结论会回到这里。</p>}</div>
        </section>:null}
      </div>
    </aside>
  </div>;
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
function InterpretationBlock({label,value,accent=false}:{label:string;value:string;accent?:boolean}){return <article className={accent?'is-accent':''}><span>{label}</span><p>{value}</p></article>;}
function InterpretationList({label,values,ordered=false}:{label:string;values:string[];ordered?:boolean}){const List=ordered?'ol':'ul';return <article><span>{label}</span><List>{values.map((value)=><li key={value}>{value}</li>)}</List></article>;}
function DrawerSkeleton(){return <div className="radar-drawer-skeleton" aria-label="正在加载事件详情"><span/><span/><span/></div>;}
function evidenceTypeLabel(value?:string){if(value==='ANNOUNCEMENT')return '公司公告';if(value==='INTERACTION')return '互动问答';if(value==='BROKER_REPORT')return '机构研报';if(value==='NEWS_FLASH'||value==='PUBLIC_NEWS')return '公开资讯';return '补充资料';}
function agentNodeLabel(value:string){if(value==='radar-evidence-plan')return '证据规划';if(value==='radar-evidence-synthesis')return '证据综合';if(value.startsWith('radar-tool-'))return '多源检索';if(value==='radar-event-interpretation')return '事件解读';return value;}
function formatDateTime(value?:string){const date=value?new Date(value):undefined;return date&&!Number.isNaN(date.getTime())?new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(date):'--';}

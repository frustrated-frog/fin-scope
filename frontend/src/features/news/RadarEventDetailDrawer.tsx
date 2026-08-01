import { useEffect, useRef, useState } from 'react';

import { api } from '../../shared/api/client';
import { RadarObservationList } from './RadarObservationList';
import { RadarTimeline } from './RadarTimeline';
import { RadarTrustPanel } from './RadarTrustPanel';
import type { RadarEvent, RadarEventDetail, RadarInterpretation, RadarWorkspaceState } from './researchRadarTypes';

const POLL_INTERVAL_MS = 1_500;
type Tab = 'interpretation' | 'timeline' | 'evidence' | 'tracking';

export function RadarEventDetailDrawer({ event, onClose, onEventChange }: { event: RadarEvent; onClose: () => void; onEventChange?: (event: RadarEvent) => void }) {
  const [detail, setDetail] = useState<RadarEventDetail>(); const [tab,setTab]=useState<Tab>('interpretation');
  const [error, setError] = useState(''); const [requesting, setRequesting] = useState(false);
  const stopped = useRef(false); const timer = useRef<number>();

  useEffect(() => {
    stopped.current = false; setTab('interpretation'); void load(true);
    function onKeyDown(keyEvent: KeyboardEvent) { if (keyEvent.key === 'Escape') onClose(); }
    document.addEventListener('keydown', onKeyDown);
    return () => { stopped.current = true; if (timer.current) window.clearTimeout(timer.current); document.removeEventListener('keydown', onKeyDown); };
  }, [event.id]);

  async function load(requestIfMissing: boolean) {
    try {
      const next = await api<RadarEventDetail>(`/api/research-radar/events/${event.id}`); if (stopped.current) return;
      setDetail(next); setError(''); onEventChange?.({...event,read:true,followed:next.workspaceState?.followed??event.followed,disposition:next.workspaceState?.disposition??event.disposition});
      if (requestIfMissing && shouldRequest(next.interpretation)) { await requestInterpretation(); return; }
      if (isPending(next.interpretation)) schedulePoll();
    } catch (loadError) { if (!stopped.current) setError(loadError instanceof Error ? loadError.message : '事件详情加载失败'); }
  }

  async function requestInterpretation() {
    if (requesting) return; setRequesting(true);
    try { const queued = await api<RadarInterpretation>(`/api/research-radar/events/${event.id}/interpretation`, { method: 'POST' }); if (stopped.current) return; setDetail((current) => current ? { ...current, interpretation: queued } : current); schedulePoll(); }
    catch (requestError) { if (!stopped.current) setError(requestError instanceof Error ? requestError.message : '事件解读生成失败'); }
    finally { if (!stopped.current) setRequesting(false); }
  }
  function schedulePoll() { if (stopped.current) return; if (timer.current) window.clearTimeout(timer.current); timer.current = window.setTimeout(() => void load(false), POLL_INTERVAL_MS); }
  async function updateState(patch:{followed?:boolean;disposition?:'ACTIVE'|'LATER'|'IGNORED'}){
    const state=await api<RadarWorkspaceState>(`/api/research-radar/events/${event.id}/state`,{method:'PATCH',body:JSON.stringify(patch)});
    setDetail((current)=>current?{...current,workspaceState:state}:current);onEventChange?.({...event,read:state.read,followed:state.followed,disposition:state.disposition});
  }

  const interpretation = detail?.interpretation; const result = interpretation?.result;
  return <div className="radar-drawer-backdrop" onMouseDown={(mouseEvent) => { if (mouseEvent.target === mouseEvent.currentTarget) onClose(); }}>
    <aside className="radar-detail-drawer" role="dialog" aria-modal="true" aria-labelledby="radar-detail-title">
      <header><div><span>EVENT RESEARCH DOSSIER</span><h2 id="radar-detail-title">{event.title}</h2><div className="radar-drawer-state"><button type="button" aria-pressed={Boolean(detail?.workspaceState?.followed??event.followed)} onClick={()=>void updateState({followed:!(detail?.workspaceState?.followed??event.followed)})}>{detail?.workspaceState?.followed??event.followed?'取消关注':'关注变化'}</button><button type="button" onClick={()=>void updateState({disposition:'LATER'})}>稍后看</button><button type="button" onClick={()=>void updateState({disposition:'IGNORED'})}>忽略</button></div></div><button type="button" className="radar-drawer-close" aria-label="关闭事件解读" onClick={onClose}>×</button></header>
      <nav className="radar-dossier-tabs" aria-label="事件详情栏目">{([['interpretation','解读'],['timeline','事件脉络'],['evidence','证据'],['tracking','跟踪']] as [Tab,string][]).map(([code,label])=><button type="button" key={code} className={tab===code?'active':''} aria-pressed={tab===code} onClick={()=>setTab(code)}>{label}</button>)}</nav>
      <div className="radar-detail-scroll">
        <section className="radar-detail-overview"><div><strong>{event.priorityScore}</strong><span>研究优先级</span></div><p>{event.summary}</p><small>{event.sourceCount} 个独立来源共同报道</small></section>

        {tab==='interpretation'?<section className="radar-detail-section" aria-labelledby="interpretation-heading">
          <div className="radar-detail-section-heading"><h3 id="interpretation-heading">事件解读</h3><StatusBadge interpretation={interpretation}/></div>
          {!detail?<DrawerSkeleton/>:result?<div className="radar-interpretation-grid"><InterpretationBlock label="已确认事实" value={result.factSummary}/><InterpretationBlock label="本次新变化" value={result.newDevelopment}/><InterpretationBlock label="为什么重要" value={result.whyItMatters} accent/><InterpretationList label="影响链条" values={result.impactChain} ordered/><InterpretationList label="仍存疑点" values={result.uncertainties}/><InterpretationList label="下一步观察" values={result.nextObservations}/></div>
          :<div className="radar-interpretation-pending" aria-live="polite"><span aria-hidden="true"/><div><strong>{error?'暂时无法生成解读':'解读生成中…'}</strong><p>{error||'Agent 正在后台整理事实与影响链，不会阻塞雷达页面。'}</p></div>{error?<button type="button" className="ghost-button" onClick={()=>void requestInterpretation()} disabled={requesting}>重新生成</button>:null}</div>}
        </section>:null}

        {tab==='timeline'?<section className="radar-detail-section"><div className="radar-detail-section-heading"><h3>事件脉络</h3><span>{detail?.timeline?.length??0} 个节点</span></div><RadarTimeline items={detail?.timeline}/></section>:null}

        {tab==='evidence'?<section className="radar-detail-section"><div className="radar-detail-section-heading"><h3>证据与来源</h3><span>{(detail?.signals.length??0)+(detail?.evidence?.length??0)} 条</span></div><RadarTrustPanel trust={detail?.trust}/>
          {detail?.evidence?.map((item,index)=><Evidence key={item.id??`${item.toolCode}-${index}`} title={item.title} source={item.sourceName||evidenceTypeLabel(item.evidenceType)} meta={evidenceTypeLabel(item.evidenceType)} url={item.url} summary={item.summary}/>)}
          {detail?.signals.map((signal)=><Evidence key={signal.id} title={signal.title} source={signal.sourceName} meta={formatDateTime(signal.publishedAt)} url={signal.url} summary={signal.matchReason}/>)}
          {detail?.agentTrace?.length?<details className="radar-agent-trace radar-drawer-trace"><summary>Agent 运行状态</summary><ol>{detail.agentTrace.map((trace,index)=><li key={`${trace.nodeName}-${index}`}><div><strong>{agentNodeLabel(trace.nodeName)}</strong><span>{trace.status} · {trace.durationMs}ms</span></div>{trace.summary?<p>{trace.summary}</p>:null}{trace.fallbackUsed?<small>已降级：{trace.fallbackReason||trace.errorType||'使用确定性结果'}</small>:null}</li>)}</ol></details>:null}
        </section>:null}

        {tab==='tracking'?<section className="radar-detail-section"><div className="radar-detail-section-heading"><h3>下一步观察</h3><span>{detail?.observations?.filter((item)=>item.status==='OPEN').length??0} 项待验证</span></div><RadarObservationList eventId={event.id} items={detail?.observations} onChange={(observations)=>setDetail((current)=>current?{...current,observations}:current)}/>
          <div className="radar-research-links"><h3>关联研究结论</h3>{detail?.researchLinks?.length?<ul>{detail.researchLinks.map((link)=><li key={link.id}><div><strong>研究运行 #{link.researchRunId}</strong><span>{link.status}</span></div>{link.questionSnapshot?<p>{link.questionSnapshot}</p>:null}{link.summary?<small>{link.summary}</small>:null}</li>)}</ul>:<p className="radar-empty-copy">从事件卡片启动研究后，结论会回到这里。</p>}</div>
        </section>:null}
      </div>
    </aside>
  </div>;
}

function Evidence({title,source,meta,url,summary}:{title:string;source:string;meta:string;url?:string;summary?:string}){return <article className="radar-drawer-evidence"><div><span>{source}</span><small>{meta}</small></div>{url?<a href={url} target="_blank" rel="noreferrer">{title}</a>:<strong>{title}</strong>}{summary?<p>{summary}</p>:null}</article>;}
function shouldRequest(value?: RadarInterpretation) { return !value || value.stale || value.status === 'FAILED' || value.status === 'UNAVAILABLE'; }
function isPending(value?: RadarInterpretation) { return value?.status === 'QUEUED' || value?.status === 'RUNNING'; }
function StatusBadge({ interpretation }: { interpretation?: RadarInterpretation }) { if (interpretation?.status === 'SUCCESS'&&!interpretation.stale)return <span className="is-success">解读完成</span>;if(interpretation?.status==='FAILED'||interpretation?.status==='UNAVAILABLE')return <span className="is-error">生成失败</span>;return <span>生成中</span>; }
function InterpretationBlock({label,value,accent=false}:{label:string;value:string;accent?:boolean}){return <article className={accent?'is-accent':''}><span>{label}</span><p>{value}</p></article>;}
function InterpretationList({label,values,ordered=false}:{label:string;values:string[];ordered?:boolean}){const List=ordered?'ol':'ul';return <article><span>{label}</span><List>{values.map((value)=><li key={value}>{value}</li>)}</List></article>;}
function DrawerSkeleton(){return <div className="radar-drawer-skeleton" aria-label="正在加载事件详情"><span/><span/><span/></div>;}
function evidenceTypeLabel(value?:string){if(value==='ANNOUNCEMENT')return '公司公告';if(value==='INTERACTION')return '互动问答';if(value==='BROKER_REPORT')return '机构研报';if(value==='NEWS_FLASH'||value==='PUBLIC_NEWS')return '公开资讯';return '补充资料';}
function agentNodeLabel(value:string){if(value==='radar-evidence-plan')return '证据规划';if(value==='radar-evidence-synthesis')return '证据综合';if(value.startsWith('radar-tool-'))return '多源检索';if(value==='radar-event-interpretation')return '事件解读';return value;}
function formatDateTime(value?:string){const date=value?new Date(value):undefined;return date&&!Number.isNaN(date.getTime())?new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(date):'--';}

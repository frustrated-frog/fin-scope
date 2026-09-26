import { AttributionEventContext, AttributionEventSource } from '../../shared/types';
import './attributionEventContext.css';

const frameworks = { EARNINGS: '业绩变化', ORDER: '订单与合作', PRODUCT_PRICE: '产品价格', GOVERNANCE: '治理与资本事项', INDUSTRY: '行业进展', OTHER: '事件研究' };
const changes = { FIRST_DISCLOSURE: '首次披露', SUBSTANTIVE_PROGRESS: '实质推进', CONFIRMATION: '后续确认', REPRINT: '旧闻延续', UNCLEAR: '增量待确认' };
const directions = { POSITIVE: '偏利好', NEGATIVE: '偏利空', MIXED: '多空兼有', NEUTRAL: '影响中性', UNCLEAR: '方向待判断' };

function safeUrl(value: string) {
  try {
    return ['https:', 'http:'].includes(new URL(value).protocol);
  } catch {
    return false;
  }
}

function Citations({ ids, sources }: { ids?: string[]; sources: AttributionEventSource[] }) {
  const selected = sources.filter(source => ids?.includes(source.id) && safeUrl(source.url));
  if (selected.length === 0) {
    return null;
  }
  return <span className="attribution-event-citations">{selected.map(source =>
    <a href={source.url} target="_blank" rel="noreferrer" key={source.id} title={source.title} aria-label={`来源：${source.title}`}>{source.id} ↗</a>
  )}</span>;
}

/** 仅追加在原报告末尾；无扩展快照的历史报告不新增空壳。 */
export function AttributionEventContextSection({ context }: { context?: AttributionEventContext | null }) {
  if (!context) {
    return null;
  }
  const sources = context.sources || [];
  return <section className="attribution-event-context" aria-label="事件脉络与本次增量">
    <header className="attribution-event-section-head">
      <div><span className="attribution-event-eyebrow">事件深读</span><h4>事件脉络与本次增量</h4></div>
      <span className="attribution-event-asof">{context.asOfDate && `截至 ${context.asOfDate} · `}{context.status === 'UNAVAILABLE' ? '补充研究未完成' : context.status === 'PARTIAL' ? '部分脉络待补充' : '事件研究已完成'}</span>
    </header>
    {context.summary && <p className="attribution-event-summary">{context.summary}</p>}
    {(context.events || []).map((event, index) => <section className="attribution-event-dossier" key={`${event.title}-${index}`} aria-label={event.title}>
      <header className="attribution-event-dossier-head">
        <h5>{event.title}</h5>
        <div className="attribution-event-tags"><span>{frameworks[event.framework || 'OTHER']}</span><span>{changes[event.changeType || 'UNCLEAR']}</span></div>
      </header>
      {event.currentStage && <p className="attribution-event-stage"><strong>当前阶段</strong>{event.currentStage}</p>}
      {event.relationshipBasis && <p className="attribution-event-relation"><strong>事项关联</strong>{event.relationshipBasis}</p>}
      {!!event.timeline?.length && <div className="attribution-event-progress">
        <h6>关键进展 <span>事实按时间排列，不代表确定的因果关系</span></h6>
        <ol>{event.timeline.map((item, itemIndex) => <li key={itemIndex}>
          <time>{item.date || '日期待确认'}</time><div><p>{item.description}</p><Citations ids={item.sourceIds} sources={sources} /></div>
        </li>)}</ol>
      </div>}
      {(event.priorState || event.newInformation) && <div className="attribution-event-comparison">
        {event.priorState && <section><h6>此前已经知道什么</h6><p>{event.priorState}</p></section>}
        {event.newInformation && <section><h6>本次新增了什么</h6><p>{event.newInformation}</p></section>}
      </div>}
      {event.impactAnalysis && <section className="attribution-event-impact">
        <h6>对公司意味着什么 {event.impactDirection && <span className={`attribution-event-direction direction-${event.impactDirection.toLowerCase()}`}>{directions[event.impactDirection]}</span>}</h6>
        <p>{event.impactAnalysis}</p><span className="attribution-event-inference">影响分析 · 包含机制推演与成立条件</span>
      </section>}
      {(event.changedJudgment || event.unchangedJudgment) && <div className="attribution-event-judgments">
        {event.changedJudgment && <section><h6>哪些判断改变了</h6><p>{event.changedJudgment}</p></section>}
        {event.unchangedJudgment && <section><h6>哪些判断尚未改变</h6><p>{event.unchangedJudgment}</p></section>}
      </div>}
      {!!event.pendingConditions?.length && <section className="attribution-event-pending"><h6>接下来关注什么</h6><ul>{event.pendingConditions.map((item, itemIndex) => <li key={itemIndex}>{item}</li>)}</ul></section>}
      <footer className="attribution-event-evidence"><span>本事件依据</span><Citations ids={event.sourceIds} sources={sources} />{!sources.some(source => event.sourceIds?.includes(source.id) && safeUrl(source.url)) && <span>引用待核验，此处保留为待验证分析</span>}</footer>
    </section>)}
    {!!context.warnings?.length && <div className="attribution-event-notes" role="status">{context.warnings.map((warning, index) => <p key={index}>{warning}</p>)}</div>}
    {sources.length > 0 && <details className="attribution-event-sources"><summary>本章研究来源 · {sources.length} 条{sources.some(source => source.supplemental) ? '，包含事件定向补查' : ''}</summary>
      <ul>{sources.map(source => <li key={source.id}>
        <div><span>{source.id}</span>{safeUrl(source.url) ? <a href={source.url} target="_blank" rel="noreferrer">{source.title || source.url} ↗</a> : <span>{source.title}</span>}</div>
        <small>{source.publishedAt || '发布时间待确认'}{source.supplemental ? ' · 定向补查' : ' · 原报告材料'}</small>
        {source.content && <p>{source.content.slice(0, 240)}{source.content.length > 240 ? '…' : ''}</p>}
      </li>)}</ul>
    </details>}
  </section>;
}

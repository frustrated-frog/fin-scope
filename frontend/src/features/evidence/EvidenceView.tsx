import { useMemo, useState } from 'react';
import type { CSSProperties } from 'react';

import { EventCluster, EvidenceItem } from '../../shared/types';

function uniqueValues(values: Array<string | undefined>) {
  return Array.from(new Set(values.filter(Boolean))) as string[];
}

export function EvidenceView({ evidenceItems, events, onOpenEvent }: { evidenceItems: EvidenceItem[]; events: EventCluster[]; onOpenEvent: (eventId: number) => void }) {
  const [eventId, setEventId] = useState('ALL');
  const [sourceTier, setSourceTier] = useState('ALL');
  const [evidenceType, setEvidenceType] = useState('ALL');
  const visible = useMemo(() => evidenceItems.filter((item) => (
    (eventId === 'ALL' || item.eventId === Number(eventId))
      && (sourceTier === 'ALL' || item.sourceTier === sourceTier)
      && (evidenceType === 'ALL' || item.evidenceType === evidenceType)
  )), [evidenceItems, eventId, sourceTier, evidenceType]);
  const grouped = useMemo(() => {
    const result = new Map<string, EvidenceItem[]>();
    visible.forEach((item) => {
      const key = String(item.eventId);
      result.set(key, [...(result.get(key) || []), item]);
    });
    return Array.from(result.values());
  }, [visible]);
  const confirmed = grouped.filter((items) => items.some((item) => item.evidenceType === 'FACT' || item.evidenceType === 'TIMELINE'));
  const partial = grouped.filter((items) => !confirmed.includes(items));
  const primary = visible.filter((item) => ['REGULATOR', 'OFFICIAL', 'COMPANY'].includes(item.sourceTier)).length;
  const directEvidence = visible.filter((item) => item.evidenceType === 'FACT' || item.evidenceType === 'TIMELINE').length;
  const directRatio = visible.length ? Math.round((directEvidence / visible.length) * 100) : 0;
  const tiers = uniqueValues(evidenceItems.map((item) => item.sourceTier));
  const types = uniqueValues(evidenceItems.map((item) => item.evidenceType));
  const eventMap = new Map(events.map((item) => [item.id, item]));

  return <section className="evidence-workbench evidence-apple-workbench">
    <header className="evidence-workbench-head">
      <div className="evidence-hero-copy">
        <span className="section-kicker">Evidence Review</span>
        <h3>证据审阅台</h3>
        <p className="muted">把事实、线索和缺口放在同一个可追溯界面里，先确认材料质量，再进入判断。</p>
        <div className="evidence-hero-pills" aria-label="证据审阅摘要">
          <span>{visible.length} 条材料</span>
          <span>{grouped.length} 个事件</span>
          <span>{primary} 条一手来源</span>
        </div>
      </div>
      <fieldset className="evidence-filters" aria-label="证据筛选条件">
        <legend>筛选条件</legend>
        <label><span>事件</span><select aria-label="证据事件筛选" value={eventId} onChange={(e) => setEventId(e.target.value)}><option value="ALL">所有事件</option>{events.map((item) => <option key={item.id} value={item.id}>{item.canonicalTitle}</option>)}</select></label>
        <label><span>来源</span><select aria-label="证据来源层级" value={sourceTier} onChange={(e) => setSourceTier(e.target.value)}><option value="ALL">所有来源</option>{tiers.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label><span>类型</span><select aria-label="证据类型" value={evidenceType} onChange={(e) => setEvidenceType(e.target.value)}><option value="ALL">所有类型</option>{types.map((item) => <option key={item}>{item}</option>)}</select></label>
      </fieldset>
    </header>
    <div className="evidence-health" aria-label="证据审阅指标"><div><span>审阅状态</span><strong>{visible.length ? '持续研判中' : '等待材料'}</strong><small>不把材料数量误当作结论确定性</small></div><div><span>可直接确认</span><strong>{directEvidence}</strong><small>事实 / 时间线材料</small></div><div><span>待验证判断</span><strong>{partial.length}</strong><small>数据或影响推断</small></div><div><span>直接材料占比</span><strong>{directRatio}%</strong><small>{primary} / {visible.length} 一手来源</small></div></div>
    {!visible.length ? <div className="evidence-empty"><strong>尚未形成可研判的证据</strong><p>先在事件详情中补充文章或证据，再回到这里审查判断与缺口。</p></div> : <div className="evidence-workbench-grid"><main>
      <section className="evidence-section"><div className="event-detail-section-head"><strong>证据链</strong><span>事实与推断分开呈现</span></div>{grouped.map((items) => <ThesisCard key={`${items[0].eventId}-${items[0].id}`} items={items} event={eventMap.get(items[0].eventId)} onOpenEvent={onOpenEvent} />)}</section>
    </main><aside>
      <section className="evidence-section"><div className="event-detail-section-head"><strong>来源构成</strong><span>按当前筛选统计</span></div>{tiers.map((tier) => {
        const count = visible.filter((item) => item.sourceTier === tier).length;
        const share = visible.length ? Math.round((count / visible.length) * 100) : 0;
        return <div className="evidence-source-row" key={tier} style={{ '--source-share': `${share}%` } as CSSProperties}><span>{tier}</span><strong>{count}</strong></div>;
      })}</section>
      <section className="evidence-section"><div className="event-detail-section-head"><strong>证据缺口</strong></div><div className="evidence-gap"><strong>优先补独立的一手来源或可比数据</strong><p>当前材料只能确认已出现的事实；涉及影响范围、持续期和投资含义的判断，需要后续公告、数据或监管细则验证。</p></div></section>
    </aside></div>}
  </section>;
}

function ThesisCard({ items, event, onOpenEvent }: { items: EvidenceItem[]; event?: EventCluster; onOpenEvent: (id: number) => void }) {
  const direct = items.some((item) => item.evidenceType === 'FACT' || item.evidenceType === 'TIMELINE');
  const first = items[0];
  return <article className={direct ? 'thesis-card thesis-card-confirmed' : 'thesis-card thesis-card-partial'}>
    <div className="thesis-card-head"><span className={direct ? 'evidence-state confirmed' : 'evidence-state partial'}>{direct ? '已证实' : '部分支持'}</span>{event && <button className="link-button" type="button" onClick={() => onOpenEvent(event.id)}>{event.canonicalTitle}</button>}</div>
    <strong className="thesis-statement">{event?.canonicalTitle || first.claim}</strong><p>{direct ? '该事件的以下材料由可直接引用的事实或时间线支持。' : '以下材料属于数据或影响线索，尚不能单独构成确定结论。'}</p>
    <div className="thesis-evidence-list">{[...items].sort((a, b) => b.confidence - a.confidence).map((item) => <div className="thesis-evidence" key={item.id}><div><span className="source-tier">{item.sourceTier} · {item.evidenceType}</span><p>{item.claim}</p><small>{item.articlePublishedAt?.slice(0, 10) || '日期未知'} {item.articleUrl && <> · <a href={item.articleUrl} target="_blank" rel="noopener noreferrer">查看原文</a></>}</small></div><strong>{item.confidence}</strong></div>)}</div>
  </article>;
}

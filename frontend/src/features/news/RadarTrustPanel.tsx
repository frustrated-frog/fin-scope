import type { RadarTrust } from './researchRadarTypes';

export function RadarTrustPanel({ trust }: { trust?: RadarTrust }) {
  if (!trust) return null;
  return <section className="radar-trust-panel" aria-label="证据可信度">
    <div><span>独立来源</span><strong>{trust.independentSourceCount}</strong></div>
    <div><span>引用覆盖</span><strong>{trust.citationCoveredCount}/{trust.citationTotalCount}</strong></div>
    <div><span>来源结构</span><strong>{trust.concentration}</strong></div>
    <p>{Object.entries(trust.sourceTierCounts).map(([tier,count])=>`${tier} × ${count}`).join(' · ') || '暂无来源分层'}</p>
    {trust.conflicts.length ? <ul>{trust.conflicts.map((item)=><li key={item}>{item}</li>)}</ul> : <small>当前未发现跨来源数值冲突</small>}
    <small>{trust.limitation}</small>
  </section>;
}

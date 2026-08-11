import type { IndustryChainGraph } from './industryChainTypes';
import { prosperityLabel, statusTone, supplyDemandLabel } from './IndustryChainResearchPanel';

export function IndustryChainInspector({ graph, selectedNodeKey }: {
  graph: IndustryChainGraph;
  selectedNodeKey?: string;
}) {
  const node = graph.nodes.find((item) => item.nodeKey === selectedNodeKey);
  const refs = new Set(node?.evidenceRefs ?? graph.evidence.map((item) => item.evidenceCode));
  const evidence = graph.evidence.filter((item) => refs.has(item.evidenceCode));
  const stageProfile = graph.researchContent?.stageProfiles.find((item) => item.nodeKey === node?.nodeKey);
  const companyProfile = graph.researchContent?.companyProfiles.find((item) => item.nodeKey === node?.nodeKey);

  return (
    <aside className="ic-inspector" aria-label="图谱详情">
      <div className="ic-inspector-head">
        <span>Research dossier</span>
        <strong>{node ? node.name : '图谱概览'}</strong>
      </div>
      {node ? (
        <>
          <p className="ic-inspector-description">{node.description}</p>
          <dl className="ic-metadata">
            <div><dt>节点类型</dt><dd>{node.type}</dd></div>
            <div><dt>置信度</dt><dd>{node.confidence}</dd></div>
            {node.stockCode && <div><dt>股票代码</dt><dd>{node.stockCode}</dd></div>}
          </dl>
          {stageProfile && (
            <section className="ic-inspector-research" aria-label="环节经营画像">
              <div className="ic-inspector-state-line">
                <span className={statusTone(stageProfile.prosperity)}>{prosperityLabel(stageProfile.prosperity)}</span>
                <span>{supplyDemandLabel(stageProfile.supplyDemand)}</span>
              </div>
              <InspectorFact label="商业模式" value={stageProfile.businessModel} />
              <InspectorFact label="价值获取" value={stageProfile.valueCapture} />
              <InspectorFact label="核心瓶颈" value={stageProfile.bottleneck} emphasis />
              <InspectorPhrases label="跟踪指标" items={stageProfile.coreMetrics} />
              <InspectorPhrases label="行业壁垒" items={stageProfile.barriers} />
            </section>
          )}
          {companyProfile && (
            <section className="ic-inspector-research" aria-label="公司竞争画像">
              <InspectorFact label="产业位置" value={companyProfile.industryPosition} />
              <InspectorPhrases label="核心产品" items={companyProfile.coreProducts} />
              <InspectorPhrases label="下游领域" items={companyProfile.downstreamMarkets} />
              <InspectorPhrases label="竞争优势" items={companyProfile.competitiveAdvantages} />
            </section>
          )}
        </>
      ) : (
        <>
          <p className="ic-inspector-description">{graph.summary}</p>
          <div className="ic-limitations"><span>研究边界</span><p>{graph.limitations}</p></div>
        </>
      )}
      <div className="ic-evidence-list">
        <div className="ic-section-title"><span>Evidence</span><strong>{evidence.length}</strong></div>
        {evidence.map((item) => (
          <article className="ic-evidence" key={item.evidenceCode}>
            <span>{item.evidenceCode} · {item.sourceTier || 'T3'}</span>
            {item.url ? <a href={item.url} target="_blank" rel="noreferrer">{item.title}</a> : <strong>{item.title}</strong>}
            <small>{item.source}{item.publishedAt ? ` · ${item.publishedAt}` : ''}</small>
            {item.excerpt && <p>{item.excerpt}</p>}
          </article>
        ))}
      </div>
    </aside>
  );
}

function InspectorFact({ label, value, emphasis = false }: { label: string; value: string; emphasis?: boolean }) {
  if (!value) return null;
  return <div className={`ic-inspector-fact ${emphasis ? 'is-emphasis' : ''}`}><span>{label}</span><p>{value}</p></div>;
}

function InspectorPhrases({ label, items }: { label: string; items: string[] }) {
  if (!items.length) return null;
  return <div className="ic-inspector-phrases"><span>{label}</span><div>{items.map((item) => <i key={item}>{item}</i>)}</div></div>;
}

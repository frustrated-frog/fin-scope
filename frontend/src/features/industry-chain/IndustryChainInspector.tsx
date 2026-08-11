import { directSemanticNeighbors, relatedNodeKeys } from './industryChainProjection';
import type { IndustryChainGraph, IndustryChainNodeType } from './industryChainTypes';
import { prosperityLabel, statusTone, supplyDemandLabel } from './IndustryChainResearchPanel';

export function IndustryChainInspector({
  graph, selectedNodeKey, expanded = false,
  onSelectNode = () => undefined, onToggleExpanded = () => undefined
}: {
  graph: IndustryChainGraph;
  selectedNodeKey?: string;
  expanded?: boolean;
  onSelectNode?: (nodeKey: string) => void;
  onToggleExpanded?: (nodeKey: string) => void;
}) {
  const node = graph.nodes.find((item) => item.nodeKey === selectedNodeKey);
  const refs = new Set(node?.evidenceRefs ?? graph.evidence.map((item) => item.evidenceCode));
  const evidence = graph.evidence.filter((item) => refs.has(item.evidenceCode));
  const stageProfile = graph.researchContent?.stageProfiles.find((item) => item.nodeKey === node?.nodeKey);
  const companyProfile = graph.researchContent?.companyProfiles.find((item) => item.nodeKey === node?.nodeKey);
  const nodeProfile = graph.researchContent?.nodeProfiles?.find((item) => item.nodeKey === node?.nodeKey);
  const relatedNodes = node ? relatedNodeKeys(graph, node.nodeKey)
    .map((key) => graph.nodes.find((item) => item.nodeKey === key)).filter(Boolean) : [];
  const directChildren = node ? directSemanticNeighbors(graph, node.nodeKey) : [];

  return (
    <aside className="ic-inspector" aria-label="图谱详情">
      <div className="ic-inspector-head">
        <span>{node ? nodeTypeLabel(node.type) : 'Research dossier'}</span>
        <strong>{node ? node.name : '图谱概览'}</strong>
        {node && <small>Semantic dossier · {node.nodeKey}</small>}
      </div>
      {node ? (
        <>
          <p className="ic-inspector-description">{node.description}</p>
          <dl className="ic-metadata">
            <div><dt>节点类型</dt><dd>{nodeTypeLabel(node.type)}</dd></div>
            <div><dt>信息置信</dt><dd>{confidenceLabel(node.confidence)}</dd></div>
            {node.stockCode && <div><dt>股票代码</dt><dd>{node.stockCode}</dd></div>}
          </dl>
          {nodeProfile && (
            <section className="ic-inspector-research ic-semantic-dossier" aria-label="语义节点画像">
              <div className="ic-inspector-state-line">
                <span>{maturityLabel(nodeProfile.maturity)}</span>
                <span>价值 {levelLabel(nodeProfile.valueLevel)}</span>
                <span>瓶颈 {levelLabel(nodeProfile.bottleneckLevel)}</span>
                <span>国产化 {localizationLabel(nodeProfile.localizationLevel)}</span>
              </div>
              <InspectorFact label="节点定义" value={nodeProfile.definition} />
              <InspectorFact label="产业作用" value={nodeProfile.function} emphasis />
              <InspectorPhrases label="关键投入" items={nodeProfile.inputs} />
              <InspectorPhrases label="主要产出" items={nodeProfile.outputs} />
              <InspectorPhrases label="成本驱动" items={nodeProfile.costDrivers} />
              <InspectorPhrases label="价值驱动" items={nodeProfile.valueDrivers} />
              <InspectorPhrases label="进入壁垒" items={nodeProfile.barriers} />
              <InspectorPhrases label="核心指标" items={nodeProfile.coreMetrics} />
              <InspectorPhrases label="主要风险" items={nodeProfile.risks} />
            </section>
          )}
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
          {directChildren.length > 0 && (
            <button type="button" className="ic-inspector-expand"
              aria-label={`${expanded ? '收起' : '展开'} ${node.name} 的关联分支`}
              onClick={() => onToggleExpanded(node.nodeKey)}>
              <span aria-hidden="true">{expanded ? '−' : '+'}</span>
              {expanded ? '收起关联分支' : `展开 ${directChildren.length} 个关联分支`}
            </button>
          )}
          {relatedNodes.length > 0 && (
            <section className="ic-related-nodes" aria-label="关联节点">
              <div className="ic-section-title"><span>Related nodes</span><strong>{relatedNodes.length}</strong></div>
              <div>{relatedNodes.map((item) => item && (
                <button type="button" key={item.nodeKey} aria-label={`查看关联节点 ${item.name}`}
                  onClick={() => onSelectNode(item.nodeKey)}>
                  <span>{nodeTypeLabel(item.type)}</span><strong>{item.name}</strong><i aria-hidden="true">↗</i>
                </button>
              ))}</div>
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

function nodeTypeLabel(type: IndustryChainNodeType) {
  return ({
    INDUSTRY_CHAIN: '产业主题', STAGE: '产业环节', MATERIAL: '关键材料', EQUIPMENT: '核心设备',
    COMPONENT: '核心部件', PRODUCT: '产品与能力', TECHNOLOGY: '技术路线', APPLICATION: '下游应用',
    COMPANY: '代表公司'
  } as Record<IndustryChainNodeType, string>)[type];
}

function maturityLabel(value: string) {
  return ({ EMERGING: '技术萌芽', SCALING: '规模化成长', MATURE: '成熟稳定', DECLINING: '路线衰退' } as Record<string, string>)[value] || value;
}

function levelLabel(value: string) {
  return ({ HIGH: '高', MEDIUM: '中', LOW: '低' } as Record<string, string>)[value] || value;
}

function localizationLabel(value: string) {
  return ({ LOW: '较低', MEDIUM: '提升中', HIGH: '较高', LEADING: '全球领先' } as Record<string, string>)[value] || value;
}

function confidenceLabel(value: string) {
  return ({ HIGH: '高', MEDIUM: '中', LOW: '低' } as Record<string, string>)[value] || value;
}

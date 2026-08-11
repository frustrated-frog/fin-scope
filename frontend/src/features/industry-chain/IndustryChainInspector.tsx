import type { IndustryChainGraph } from './industryChainTypes';

export function IndustryChainInspector({ graph, selectedNodeKey }: {
  graph: IndustryChainGraph;
  selectedNodeKey?: string;
}) {
  const node = graph.nodes.find((item) => item.nodeKey === selectedNodeKey);
  const refs = new Set(node?.evidenceRefs ?? graph.evidence.map((item) => item.evidenceCode));
  const evidence = graph.evidence.filter((item) => refs.has(item.evidenceCode));

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

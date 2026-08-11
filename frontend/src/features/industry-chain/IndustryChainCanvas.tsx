import { useMemo } from 'react';

import { focusNeighborhood, layoutIndustryGraph } from './industryChainLayout';
import { directSemanticNeighbors, projectSemanticGraph, semanticNodeTone } from './industryChainProjection';
import type { IndustryChainGraph, IndustryChainLayer, IndustryChainNodeType } from './industryChainTypes';

export function IndustryChainCanvas({
  graph, selectedNodeKey, search, focusMode, expandedNodeKeys, activeLayer = 'STRUCTURE',
  eventCounts = {}, highlightedPath = [], onSelectNode, onToggleExpanded
}: {
  graph: IndustryChainGraph;
  selectedNodeKey?: string;
  search: string;
  focusMode: boolean;
  expandedNodeKeys: Set<string>;
  activeLayer?: IndustryChainLayer;
  eventCounts?: Record<string, number>;
  highlightedPath?: string[];
  onSelectNode: (key: string) => void;
  onToggleExpanded: (key: string) => void;
}) {
  const projectionExpansion = useMemo(() => {
    const result = new Set(expandedNodeKeys);
    if (highlightedPath.length > 0) {
      const highlighted = new Set(highlightedPath);
      graph.edges.forEach((edge) => {
        if (edge.type === 'BELONGS_TO_STAGE' && highlighted.has(edge.sourceKey)) result.add(edge.targetKey);
      });
    }
    return result;
  }, [expandedNodeKeys, graph.edges, highlightedPath]);
  const projectedGraph = useMemo(
    () => projectSemanticGraph(graph, projectionExpansion, activeLayer),
    [activeLayer, graph, projectionExpansion]
  );
  const layout = useMemo(() => layoutIndustryGraph(projectedGraph), [projectedGraph]);
  const focused = useMemo(() => new Set(
    focusMode && selectedNodeKey ? focusNeighborhood(graph, selectedNodeKey, 3) : graph.nodes.map((node) => node.nodeKey)
  ), [focusMode, graph, selectedNodeKey]);
  const normalizedSearch = search.trim().toLocaleLowerCase();
  const highlightedNodes = new Set(highlightedPath);
  const highlightedEdges = new Set(highlightedPath.slice(0, -1).map((key, index) => `${key}::${highlightedPath[index + 1]}`));

  return (
    <section className="ic-canvas-shell" role="region" aria-label={`${graph.name}产业链图谱`}>
      <div className="ic-desktop-graph">
        <div className="ic-canvas-scroll">
        <div className="ic-canvas" style={{ width: layout.width, height: layout.height }}>
          <div className="ic-lane-grid" aria-hidden="true">
            {layout.stages.map((stage, index) => (
              <div className="ic-lane" key={stage.nodeKey} style={{ left: index * 292 }}>
                <span>{String(index + 1).padStart(2, '0')}</span>
              </div>
            ))}
          </div>
          <svg className="ic-edge-layer" width={layout.width} height={layout.height} aria-label="产业关系">
            <defs>
              <marker id="ic-arrow-logic" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" />
              </marker>
              <marker id="ic-arrow-disclosed" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" />
              </marker>
              <marker id="ic-arrow-inferred" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" />
              </marker>
            </defs>
            {layout.edges.map((edge, index) => {
              const selected = selectedNodeKey === edge.sourceKey || selectedNodeKey === edge.targetKey;
              const pathId = `ic-edge-path-${index}`;
              return <g key={edge.edgeKey}>
                <path id={pathId} d={edge.path}
                  className={`ic-edge ic-edge--${edge.nature.toLocaleLowerCase()} ic-edge--${edge.route} ic-edge-type--${edge.type.toLocaleLowerCase()} ic-edge-strength--${(edge.strength ?? 'PRIMARY').toLocaleLowerCase()} ${focused.has(edge.sourceKey) && focused.has(edge.targetKey) ? '' : 'is-muted'} ${highlightedEdges.has(`${edge.sourceKey}::${edge.targetKey}`) ? 'is-event-path' : ''}`}
                  markerEnd={`url(#ic-arrow-${edge.nature.toLocaleLowerCase().replace('industry_', '')})`}
                  vectorEffect="non-scaling-stroke" />
                {selected && <text className="ic-edge-label"><textPath href={`#${pathId}`} startOffset="50%">
                  {edge.directionNote || edge.description}
                </textPath></text>}
              </g>;
            })}
          </svg>
          {layout.nodes.map((node) => {
            const count = directSemanticNeighbors(graph, node.nodeKey).length;
            const profile = graph.researchContent?.nodeProfiles?.find((item) => item.nodeKey === node.nodeKey);
            const tone = semanticNodeTone(profile, activeLayer);
            const match = Boolean(normalizedSearch && (`${node.name} ${node.description} ${node.stockCode ?? ''}`)
              .toLocaleLowerCase().includes(normalizedSearch));
            return (
              <div className={`ic-node-wrap ${focused.has(node.nodeKey) ? '' : 'is-muted'}`}
                key={node.nodeKey} style={{ left: node.x, top: node.y, width: node.width }}>
                <button type="button"
                  className={`ic-node ic-node--${node.type.toLocaleLowerCase()} ic-tone--${tone} ${selectedNodeKey === node.nodeKey ? 'is-selected' : ''} ${match ? 'is-search-match' : ''} ${highlightedNodes.has(node.nodeKey) ? 'is-event-path' : ''}`}
                  style={{ height: node.height }}
                  data-search-match={match ? 'true' : 'false'}
                  aria-label={`${node.name} · ${node.description}`}
                  onClick={() => onSelectNode(node.nodeKey)}
                  onDoubleClick={() => count > 0 && onToggleExpanded(node.nodeKey)}>
                  <span className="ic-node-kicker">{nodeLabel(node.type)} {node.stockCode && `· ${node.stockCode}`}</span>
                  <strong>{node.name}</strong>
                  <small>{node.description}</small>
                </button>
                {(eventCounts[node.nodeKey] ?? 0) > 0 && (
                  <span className="ic-event-badge" aria-label={`${node.name}关联 ${eventCounts[node.nodeKey]} 条动态`}>
                    {eventCounts[node.nodeKey]}
                  </span>
                )}
                {count > 0 && (
                  <button className="ic-node-expand" type="button"
                    aria-label={`${expandedNodeKeys.has(node.nodeKey) ? '收起' : '展开'} ${node.name} 的 ${count} 个关联节点`}
                    aria-expanded={expandedNodeKeys.has(node.nodeKey)}
                    onClick={() => onToggleExpanded(node.nodeKey)}>
                    <span aria-hidden="true">{expandedNodeKeys.has(node.nodeKey) ? '−' : '+'}</span>
                    {expandedNodeKeys.has(node.nodeKey) ? '收起分支' : `${count} 个关联节点`}
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>
        <div className="ic-legend" aria-label="关系图例">
          <span><i className="is-logic" />行业逻辑</span>
          <span><i className="is-disclosed" />公开披露</span>
          <span><i className="is-inferred" />研究推断</span>
        </div>
      </div>
      <div className="ic-mobile-reader" aria-label="移动端产业链阅读器">
        {layout.stages.map((stage, index) => (
          <section key={stage.nodeKey}>
            <header><span>{String(index + 1).padStart(2, '0')}</span><div><strong>{stage.name}</strong><small>{stage.description}</small></div></header>
            <div>
              {layout.nodes.filter((node) => node.column === index && node.type !== 'STAGE').map((node) => (
                <button key={node.nodeKey} type="button" onClick={() => onSelectNode(node.nodeKey)}
                  className={selectedNodeKey === node.nodeKey ? 'is-selected' : ''}>
                  <span>{nodeLabel(node.type)}{node.stockCode && ` · ${node.stockCode}`}</span>
                  <strong>{node.name}</strong><small>{node.description}</small>
                </button>
              ))}
            </div>
          </section>
        ))}
      </div>
    </section>
  );
}

function nodeLabel(type: IndustryChainNodeType) {
  if (type === 'STAGE') return '产业环节';
  if (type === 'MATERIAL') return '关键材料';
  if (type === 'EQUIPMENT') return '核心设备';
  if (type === 'COMPONENT') return '核心部件';
  if (type === 'PRODUCT') return '产品 / 能力';
  if (type === 'TECHNOLOGY') return '技术路线';
  if (type === 'APPLICATION') return '下游应用';
  if (type === 'COMPANY') return '代表公司';
  return '产业主题';
}

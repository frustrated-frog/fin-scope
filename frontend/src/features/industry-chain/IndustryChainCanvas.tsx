import { useMemo } from 'react';

import { focusNeighborhood, layoutIndustryGraph } from './industryChainLayout';
import type { IndustryChainGraph } from './industryChainTypes';

export function IndustryChainCanvas({
  graph, selectedNodeKey, search, focusMode, expandedCompanyKeys,
  onSelectNode, onToggleCompanies
}: {
  graph: IndustryChainGraph;
  selectedNodeKey?: string;
  search: string;
  focusMode: boolean;
  expandedCompanyKeys: Set<string>;
  onSelectNode: (key: string) => void;
  onToggleCompanies: (key: string) => void;
}) {
  const layout = useMemo(() => layoutIndustryGraph(graph, { expandedCompanyKeys }),
    [graph, expandedCompanyKeys]);
  const focused = useMemo(() => new Set(
    focusMode && selectedNodeKey ? focusNeighborhood(graph, selectedNodeKey, 3) : graph.nodes.map((node) => node.nodeKey)
  ), [focusMode, graph, selectedNodeKey]);
  const normalizedSearch = search.trim().toLocaleLowerCase();

  return (
    <section className="ic-canvas-shell" role="region" aria-label={`${graph.name}产业链图谱`}>
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
              <marker id="ic-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                <path d="M 0 0 L 10 5 L 0 10 z" />
              </marker>
            </defs>
            {layout.edges.map((edge) => (
              <path key={edge.edgeKey} d={edge.path}
                className={`ic-edge ic-edge--${edge.nature.toLocaleLowerCase()} ${focused.has(edge.sourceKey) && focused.has(edge.targetKey) ? '' : 'is-muted'}`}
                markerEnd="url(#ic-arrow)" />
            ))}
          </svg>
          {layout.nodes.map((node) => {
            const count = layout.companyCounts.get(node.nodeKey) ?? 0;
            const match = Boolean(normalizedSearch && (`${node.name} ${node.description} ${node.stockCode ?? ''}`)
              .toLocaleLowerCase().includes(normalizedSearch));
            return (
              <div className={`ic-node-wrap ${focused.has(node.nodeKey) ? '' : 'is-muted'}`}
                key={node.nodeKey} style={{ left: node.x, top: node.y, width: node.width }}>
                <button type="button"
                  className={`ic-node ic-node--${node.type.toLocaleLowerCase()} ${selectedNodeKey === node.nodeKey ? 'is-selected' : ''} ${match ? 'is-search-match' : ''}`}
                  style={{ height: node.height }}
                  data-search-match={match ? 'true' : 'false'}
                  aria-label={`${node.name} · ${node.description}`}
                  onClick={() => onSelectNode(node.nodeKey)}>
                  <span className="ic-node-kicker">{nodeLabel(node.type)} {node.stockCode && `· ${node.stockCode}`}</span>
                  <strong>{node.name}</strong>
                  <small>{node.description}</small>
                </button>
                {count > 0 && (
                  <button className="ic-company-toggle" type="button"
                    aria-label={`${expandedCompanyKeys.has(node.nodeKey) ? '收起' : '展开'} ${node.name} 的 ${count} 家公司`}
                    onClick={() => onToggleCompanies(node.nodeKey)}>
                    {expandedCompanyKeys.has(node.nodeKey) ? '收起公司' : `${count} 家代表公司`}
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
    </section>
  );
}

function nodeLabel(type: string) {
  if (type === 'STAGE') return '产业环节';
  if (type === 'PRODUCT') return '产品 / 能力';
  if (type === 'COMPANY') return '代表公司';
  return '产业主题';
}

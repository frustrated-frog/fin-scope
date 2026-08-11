import { describe, expect, it } from 'vitest';

import { focusNeighborhood, layoutIndustryGraph } from './industryChainLayout';
import type { IndustryChainGraph } from './industryChainTypes';

const graph: IndustryChainGraph = {
  name: 'AI算力', summary: '芯片到数据中心', limitations: '供销关系以公告为准',
  schemaVersion: 'INDUSTRY_CHAIN_V1', nodes: [
    node('stage:chip', 'STAGE', '芯片', 1),
    node('stage:server', 'STAGE', '服务器', 2),
    node('stage:dc', 'STAGE', '数据中心', 3),
    node('product:gpu', 'PRODUCT', 'GPU'),
    node('company:300308', 'COMPANY', '中际旭创', undefined, '300308')
  ], edges: [
    edge('flow:1', 'stage:chip', 'stage:server', 'FLOWS_TO'),
    edge('flow:2', 'stage:server', 'stage:dc', 'FLOWS_TO'),
    edge('belongs:gpu', 'product:gpu', 'stage:chip', 'BELONGS_TO_STAGE'),
    edge('company:gpu', 'company:300308', 'product:gpu', 'PARTICIPATES_IN')
  ], evidence: []
};

describe('industryChainLayout', () => {
  it('orders stage lanes and places products inside their stage column', () => {
    const layout = layoutIndustryGraph(graph);

    expect(layout.stages.map((stage) => stage.nodeKey)).toEqual([
      'stage:chip', 'stage:server', 'stage:dc'
    ]);
    expect(layout.nodes.find((node) => node.nodeKey === 'product:gpu')?.column).toBe(0);
  });

  it('collapses companies into a count while keeping a stable layout', () => {
    const layout = layoutIndustryGraph(graph, { expandedCompanyKeys: new Set() });

    expect(layout.nodes.some((node) => node.nodeKey === 'company:300308')).toBe(false);
    expect(layout.companyCounts.get('product:gpu')).toBe(1);
  });

  it('reserves vertical space for expanded companies inside their product group', () => {
    const expandedGraph: IndustryChainGraph = {
      ...graph,
      nodes: [...graph.nodes, node('product:hbm', 'PRODUCT', 'HBM')],
      edges: [...graph.edges,
        edge('belongs:hbm', 'product:hbm', 'stage:chip', 'BELONGS_TO_STAGE')]
    };

    const layout = layoutIndustryGraph(expandedGraph, {
      expandedCompanyKeys: new Set(['product:gpu'])
    });
    const product = nodeByKey(layout, 'product:gpu');
    const company = nodeByKey(layout, 'company:300308');
    const nextProduct = nodeByKey(layout, 'product:hbm');

    expect(company.y).toBeGreaterThanOrEqual(product.y + product.height + 28);
    expect(nextProduct.y).toBeGreaterThanOrEqual(company.y + company.height + 24);
  });

  it('routes stage, membership and company edges through dedicated channels', () => {
    const layout = layoutIndustryGraph(graph, {
      expandedCompanyKeys: new Set(['product:gpu'])
    });

    expect(edgeByKey(layout, 'flow:1').route).toBe('stage-flow');
    expect(edgeByKey(layout, 'belongs:gpu').route).toBe('stage-membership');
    expect(edgeByKey(layout, 'company:gpu').route).toBe('company-link');
    expect(edgeByKey(layout, 'belongs:gpu').path).not.toContain(' C ');
  });

  it('finds a bounded focus neighborhood without looping on malformed cycles', () => {
    const cyclic: IndustryChainGraph = {
      ...graph,
      edges: [...graph.edges, edge('cycle', 'stage:dc', 'stage:chip', 'FLOWS_TO')]
    };

    expect(focusNeighborhood(cyclic, 'company:300308', 2)).toEqual(expect.arrayContaining([
      'company:300308', 'product:gpu', 'stage:chip'
    ]));
  });
});

function nodeByKey(layout: ReturnType<typeof layoutIndustryGraph>, nodeKey: string) {
  return layout.nodes.find((item) => item.nodeKey === nodeKey)!;
}

function edgeByKey(layout: ReturnType<typeof layoutIndustryGraph>, edgeKey: string) {
  return layout.edges.find((item) => item.edgeKey === edgeKey)!;
}

function node(nodeKey: string, type: IndustryChainGraph['nodes'][number]['type'], name: string,
              stageOrder?: number, stockCode?: string) {
  return { nodeKey, type, name, description: name, stageOrder, stockCode,
    confidence: 'HIGH' as const, evidenceRefs: ['E1'] };
}

function edge(edgeKey: string, sourceKey: string, targetKey: string,
              type: IndustryChainGraph['edges'][number]['type']) {
  return { edgeKey, sourceKey, targetKey, type, nature: 'INDUSTRY_LOGIC' as const,
    description: type, confidence: 'HIGH' as const, evidenceRefs: ['E1'] };
}

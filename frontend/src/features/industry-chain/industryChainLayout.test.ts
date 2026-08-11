import { describe, expect, it } from 'vitest';

import { focusNeighborhood, layoutIndustryGraph } from './industryChainLayout';
import { projectSemanticGraph } from './industryChainProjection';
import type { IndustryChainGraph } from './industryChainTypes';

const graph: IndustryChainGraph = {
  name: 'AI算力', summary: '芯片到数据中心', limitations: '供销关系以公告为准',
  schemaVersion: 'INDUSTRY_CHAIN_V1', nodes: [
    node('stage:chip', 'STAGE', '芯片', 1),
    node('stage:server', 'STAGE', '服务器', 2),
    node('stage:dc', 'STAGE', '数据中心', 3),
    node('material:copper', 'MATERIAL', '高纯铜'),
    node('equipment:lithography', 'EQUIPMENT', '光刻设备'),
    node('product:gpu', 'PRODUCT', 'GPU'),
    node('company:300308', 'COMPANY', '中际旭创', undefined, '300308')
  ], edges: [
    edge('flow:1', 'stage:chip', 'stage:server', 'FLOWS_TO'),
    edge('flow:2', 'stage:server', 'stage:dc', 'FLOWS_TO'),
    edge('belongs:copper', 'material:copper', 'stage:chip', 'BELONGS_TO_STAGE'),
    edge('belongs:lithography', 'equipment:lithography', 'stage:chip', 'BELONGS_TO_STAGE'),
    edge('belongs:gpu', 'product:gpu', 'stage:chip', 'BELONGS_TO_STAGE'),
    edge('company:gpu', 'company:300308', 'product:gpu', 'PARTICIPATES_IN')
  ], evidence: []
};

describe('industryChainLayout', () => {
  it('orders stage lanes and semantic node groups inside their stage column', () => {
    const projected = projectSemanticGraph(graph, new Set(['stage:chip']), 'STRUCTURE');
    const layout = layoutIndustryGraph(projected);

    expect(layout.stages.map((stage) => stage.nodeKey)).toEqual([
      'stage:chip', 'stage:server', 'stage:dc'
    ]);
    expect(layout.nodes.find((node) => node.nodeKey === 'material:copper')?.column).toBe(0);
    expect(layout.nodes.find((node) => node.nodeKey === 'equipment:lithography')?.column).toBe(0);
    expect(layout.nodes.find((node) => node.nodeKey === 'product:gpu')?.column).toBe(0);
  });

  it('keeps stage columns stable while semantic descendants expand', () => {
    const collapsed = layoutIndustryGraph(projectSemanticGraph(graph, new Set(), 'STRUCTURE'));
    const expanded = layoutIndustryGraph(projectSemanticGraph(
      graph, new Set(['stage:chip', 'product:gpu']), 'STRUCTURE'
    ));

    expect(nodeByKey(collapsed, 'stage:chip').x).toBe(nodeByKey(expanded, 'stage:chip').x);
    expect(nodeByKey(expanded, 'company:300308').y).toBeGreaterThan(nodeByKey(expanded, 'product:gpu').y);
  });

  it('routes stage, membership and company edges through dedicated channels', () => {
    const layout = layoutIndustryGraph(projectSemanticGraph(
      graph, new Set(['stage:chip', 'product:gpu']), 'STRUCTURE'
    ));

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

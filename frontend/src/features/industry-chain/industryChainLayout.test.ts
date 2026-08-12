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
  it('divides a wide canvas equally across three stages and centers every node', () => {
    const layout = layoutIndustryGraph(graph, 1500);

    expect(layout.laneWidth).toBe(500);
    expect(layout.width).toBe(1500);
    expect(layout.stages.map((stage) => stage.x)).toEqual([134, 634, 1134]);
    expect(nodeByKey(layout, 'material:copper').x).toBe(146);
  });

  it('divides a wide canvas equally across four stages', () => {
    const fourStageGraph: IndustryChainGraph = {
      ...graph,
      nodes: [...graph.nodes, node('stage:application', 'STAGE', '应用', 4)],
      edges: [...graph.edges, edge('flow:3', 'stage:dc', 'stage:application', 'FLOWS_TO')]
    };

    const layout = layoutIndustryGraph(fourStageGraph, 1600);

    expect(layout.laneWidth).toBe(400);
    expect(layout.width).toBe(1600);
    expect(layout.stages.map((stage) => stage.x)).toEqual([84, 484, 884, 1284]);
  });

  it('preserves a readable minimum lane width when the canvas is narrow', () => {
    const layout = layoutIndustryGraph(graph, 720);

    expect(layout.laneWidth).toBe(292);
    expect(layout.width).toBe(876);
  });

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

  it('gives legacy operating cards enough space for readable highlights', () => {
    const legacyGraph = {
      ...graph,
      nodes: graph.nodes.filter((item) => item.type === 'STAGE'),
      edges: graph.edges.filter((item) => item.type === 'FLOWS_TO'),
      researchContent: {
        overview: {
          lifecycle: 'GROWTH', prosperity: 'RISING', supplyDemand: 'TIGHT', cycleType: '成长周期',
          demandDrivers: [], supplyDrivers: [], keyVariables: [], bottlenecks: [], overcapacityRisks: [], trendTags: []
        },
        companyProfiles: [], nodeProfiles: [],
        stageProfiles: [{
          nodeKey: 'stage:chip', roleSummary: '计算核心', businessModel: '芯片销售', costStructure: '研发与制造',
          valueCapture: '性能与生态溢价', bottleneck: '先进制程与高带宽存储供给', prosperity: 'RISING',
          supplyDemand: 'TIGHT', lifecycle: 'GROWTH', profitDrivers: [], barriers: ['软硬件生态'],
          coreMetrics: ['出货量'], risks: [], keyVariables: ['良率'], trendTags: []
        }]
      }
    } as IndustryChainGraph;
    const stage = nodeByKey(layoutIndustryGraph(legacyGraph), 'stage:chip');

    expect(stage.width).toBe(232);
    expect(stage.height).toBe(188);
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

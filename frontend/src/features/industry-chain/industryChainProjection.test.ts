import { describe, expect, it } from 'vitest';

import { projectSemanticGraph, relatedNodeKeys, semanticNodeTone } from './industryChainProjection';
import type { IndustryChainGraph, IndustryChainNodeProfile } from './industryChainTypes';

const graph = {
  name: '人形机器人', summary: '材料、零部件、技术与应用构成产业链', limitations: '仅作结构展示',
  schemaVersion: 'INDUSTRY_CHAIN_V3', nodes: [
    node('stage:up', 'STAGE', '上游材料', 1),
    node('stage:mid', 'STAGE', '核心零部件', 2),
    node('stage:down', 'STAGE', '整机应用', 3),
    node('material:steel', 'MATERIAL', '特种钢'),
    node('equipment:cnc', 'EQUIPMENT', '精密机床'),
    node('component:reducer', 'COMPONENT', '减速器'),
    node('technology:harmonic', 'TECHNOLOGY', '谐波传动'),
    node('company:a', 'COMPANY', '样本公司')
  ], edges: [
    edge('flow:1', 'stage:up', 'stage:mid', 'FLOWS_TO'),
    edge('flow:2', 'stage:mid', 'stage:down', 'FLOWS_TO'),
    edge('belongs:steel', 'material:steel', 'stage:up', 'BELONGS_TO_STAGE'),
    edge('belongs:cnc', 'equipment:cnc', 'stage:up', 'BELONGS_TO_STAGE'),
    edge('belongs:reducer', 'component:reducer', 'stage:mid', 'BELONGS_TO_STAGE'),
    edge('enables:harmonic', 'technology:harmonic', 'component:reducer', 'ENABLES'),
    edge('company:reducer', 'company:a', 'component:reducer', 'PARTICIPATES_IN')
  ], evidence: []
} as IndustryChainGraph;

const materialProfile: IndustryChainNodeProfile = {
  nodeKey: 'material:steel', definition: '高强度结构材料', function: '承载与传动',
  inputs: [], outputs: [], costDrivers: [], valueDrivers: [], barriers: [], coreMetrics: [], risks: [],
  maturity: 'MATURE', valueLevel: 'MEDIUM', bottleneckLevel: 'HIGH', localizationLevel: 'HIGH'
};

describe('industryChainProjection', () => {
  it('shows core semantic nodes by default and keeps companies collapsed', () => {
    expect(projectSemanticGraph(graph, new Set(), 'STRUCTURE').nodes.map((item) => item.nodeKey))
      .toEqual([
        'stage:up', 'stage:mid', 'stage:down',
        'material:steel', 'equipment:cnc', 'component:reducer'
      ]);
    expect(projectSemanticGraph(graph, new Set(), 'STRUCTURE').nodes.map((item) => item.nodeKey))
      .not.toContain('company:a');
  });

  it('distributes the default semantic budget across stages', () => {
    const stages = ['stage:up', 'stage:mid', 'stage:down'];
    const semanticNodes = stages.flatMap((stageKey, stageIndex) => Array.from({ length: 12 }, (_, index) =>
      node(`component:${stageIndex}:${index}`, 'COMPONENT', `部件 ${stageIndex}-${index}`)));
    const largeGraph = {
      ...graph,
      nodes: [...graph.nodes.filter((item) => item.type === 'STAGE'), ...semanticNodes],
      edges: [
        edge('flow:1', 'stage:up', 'stage:mid', 'FLOWS_TO'),
        edge('flow:2', 'stage:mid', 'stage:down', 'FLOWS_TO'),
        ...semanticNodes.map((item, index) => edge(
          `belongs:${item.nodeKey}`, item.nodeKey, stages[Math.floor(index / 12)], 'BELONGS_TO_STAGE'
        ))
      ]
    } as IndustryChainGraph;
    const projected = projectSemanticGraph(largeGraph, new Set(), 'STRUCTURE');

    expect(projected.nodes.length).toBeLessThanOrEqual(25);
    stages.forEach((stageKey) => {
      expect(projected.edges.some((item) => item.type === 'BELONGS_TO_STAGE'
        && item.targetKey === stageKey)).toBe(true);
    });
  });

  it('reveals at most twelve direct semantic neighbors of an expanded node', () => {
    const projected = projectSemanticGraph(graph, new Set(['stage:up']), 'STRUCTURE');

    expect(projected.nodes.map((item) => item.nodeKey)).toEqual(expect.arrayContaining([
      'stage:up', 'material:steel', 'equipment:cnc'
    ]));
    expect(projected.edges.every((item) => projected.nodes.some((nodeValue) => nodeValue.nodeKey === item.sourceKey)
      && projected.nodes.some((nodeValue) => nodeValue.nodeKey === item.targetKey))).toBe(true);
  });

  it('does not alter topology when a topic layer changes', () => {
    const expanded = new Set(['stage:mid', 'component:reducer']);
    const structureKeys = projectSemanticGraph(graph, expanded, 'STRUCTURE').nodes.map((item) => item.nodeKey);

    expect(projectSemanticGraph(graph, expanded, 'TECHNOLOGY').nodes.map((item) => item.nodeKey))
      .toEqual(structureKeys);
  });

  it('maps profile dimensions to semantic tones', () => {
    expect(semanticNodeTone(materialProfile, 'BOTTLENECK')).toBe('high');
    expect(semanticNodeTone(materialProfile, 'VALUE')).toBe('medium');
    expect(semanticNodeTone(materialProfile, 'STRUCTURE')).toBe('neutral');
  });

  it('finds directly related nodes without duplicates', () => {
    expect(relatedNodeKeys(graph, 'technology:harmonic')).toContain('component:reducer');
  });
});

function node(nodeKey: string, type: string, name: string, stageOrder?: number) {
  return { nodeKey, type, name, description: name, stageOrder,
    confidence: 'HIGH', evidenceRefs: ['E1'] };
}

function edge(edgeKey: string, sourceKey: string, targetKey: string, type: string) {
  return { edgeKey, sourceKey, targetKey, type, nature: 'INDUSTRY_LOGIC', description: type,
    confidence: 'HIGH', strength: 'PRIMARY', directionNote: type, evidenceRefs: ['E1'] };
}

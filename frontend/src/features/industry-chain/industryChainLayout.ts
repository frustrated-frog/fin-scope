import type { IndustryChainEdge, IndustryChainGraph, IndustryChainNode } from './industryChainTypes';

const LANE_WIDTH = 292;
const NODE_WIDTH = 208;
const STAGE_Y = 72;
const PRODUCT_Y = 184;
const COMPANY_Y = 310;

export type PositionedIndustryNode = IndustryChainNode & {
  x: number;
  y: number;
  width: number;
  height: number;
  column: number;
};

export type PositionedIndustryEdge = IndustryChainEdge & { path: string };

export type IndustryGraphLayout = {
  width: number;
  height: number;
  stages: PositionedIndustryNode[];
  nodes: PositionedIndustryNode[];
  edges: PositionedIndustryEdge[];
  companyCounts: Map<string, number>;
};

export function layoutIndustryGraph(
  graph: IndustryChainGraph,
  options: { expandedCompanyKeys?: Set<string> } = {}
): IndustryGraphLayout {
  const stages = graph.nodes
    .filter((node) => node.type === 'STAGE')
    .sort((left, right) => (left.stageOrder ?? Number.MAX_SAFE_INTEGER)
      - (right.stageOrder ?? Number.MAX_SAFE_INTEGER));
  const columns = new Map(stages.map((stage, index) => [stage.nodeKey, index]));
  const stageForNode = parentStageMap(graph, columns);
  const companyParent = companyParentMap(graph);
  const companyCounts = new Map<string, number>();
  graph.nodes.filter((node) => node.type === 'COMPANY').forEach((company) => {
    const parent = companyParent.get(company.nodeKey);
    if (parent) companyCounts.set(parent, (companyCounts.get(parent) ?? 0) + 1);
  });
  const expanded = options.expandedCompanyKeys ?? new Set<string>();
  const rowCounters = new Map<string, number>();
  const visible = graph.nodes.filter((node) => (
    node.type !== 'INDUSTRY_CHAIN'
    && (node.type !== 'COMPANY' || expanded.has(companyParent.get(node.nodeKey) ?? ''))
  ));
  const nodes = visible.map((node): PositionedIndustryNode => {
    const stageKey = node.type === 'STAGE' ? node.nodeKey : stageForNode.get(node.nodeKey);
    const column = columns.get(stageKey ?? '') ?? 0;
    const counterKey = `${column}:${node.type}`;
    const row = rowCounters.get(counterKey) ?? 0;
    rowCounters.set(counterKey, row + 1);
    const y = node.type === 'STAGE' ? STAGE_Y
      : node.type === 'PRODUCT' ? PRODUCT_Y + row * 96
        : COMPANY_Y + row * 78;
    return { ...node, column, x: column * LANE_WIDTH + 42, y,
      width: NODE_WIDTH, height: node.type === 'STAGE' ? 72 : 64 };
  });
  const positioned = new Map(nodes.map((node) => [node.nodeKey, node]));
  const edges = graph.edges.flatMap((edge): PositionedIndustryEdge[] => {
    const source = positioned.get(edge.sourceKey);
    const target = positioned.get(edge.targetKey);
    if (!source || !target) return [];
    const sx = source.x + source.width;
    const sy = source.y + source.height / 2;
    const tx = target.x;
    const ty = target.y + target.height / 2;
    const distance = Math.max(36, Math.abs(tx - sx) * 0.46);
    return [{ ...edge, path: `M ${sx} ${sy} C ${sx + distance} ${sy}, ${tx - distance} ${ty}, ${tx} ${ty}` }];
  });
  return {
    width: Math.max(900, stages.length * LANE_WIDTH + 32),
    height: Math.max(520, ...nodes.map((node) => node.y + node.height + 80)),
    stages: nodes.filter((node) => node.type === 'STAGE'), nodes, edges, companyCounts
  };
}

export function focusNeighborhood(graph: IndustryChainGraph, startKey: string, depth: number) {
  const visited = new Set<string>([startKey]);
  const queue: Array<{ key: string; level: number }> = [{ key: startKey, level: 0 }];
  const adjacency = new Map<string, Set<string>>();
  graph.edges.forEach((edge) => {
    addNeighbor(adjacency, edge.sourceKey, edge.targetKey);
    addNeighbor(adjacency, edge.targetKey, edge.sourceKey);
  });
  while (queue.length) {
    const current = queue.shift()!;
    if (current.level >= Math.max(0, depth)) continue;
    adjacency.get(current.key)?.forEach((next) => {
      if (!visited.has(next)) {
        visited.add(next);
        queue.push({ key: next, level: current.level + 1 });
      }
    });
  }
  return [...visited];
}

function parentStageMap(graph: IndustryChainGraph, stageColumns: Map<string, number>) {
  const result = new Map<string, string>();
  graph.edges.forEach((edge) => {
    if (edge.type !== 'BELONGS_TO_STAGE') return;
    if (stageColumns.has(edge.targetKey)) result.set(edge.sourceKey, edge.targetKey);
    if (stageColumns.has(edge.sourceKey)) result.set(edge.targetKey, edge.sourceKey);
  });
  let changed = true;
  while (changed) {
    changed = false;
    graph.edges.forEach((edge) => {
      const sourceStage = result.get(edge.sourceKey);
      const targetStage = result.get(edge.targetKey);
      if (sourceStage && !targetStage && !stageColumns.has(edge.targetKey)) {
        result.set(edge.targetKey, sourceStage); changed = true;
      }
      if (targetStage && !sourceStage && !stageColumns.has(edge.sourceKey)) {
        result.set(edge.sourceKey, targetStage); changed = true;
      }
    });
  }
  return result;
}

function companyParentMap(graph: IndustryChainGraph) {
  const result = new Map<string, string>();
  const nodeTypes = new Map(graph.nodes.map((node) => [node.nodeKey, node.type]));
  graph.edges.forEach((edge) => {
    if (nodeTypes.get(edge.sourceKey) === 'COMPANY') result.set(edge.sourceKey, edge.targetKey);
    if (nodeTypes.get(edge.targetKey) === 'COMPANY') result.set(edge.targetKey, edge.sourceKey);
  });
  return result;
}

function addNeighbor(adjacency: Map<string, Set<string>>, source: string, target: string) {
  if (!adjacency.has(source)) adjacency.set(source, new Set());
  adjacency.get(source)!.add(target);
}

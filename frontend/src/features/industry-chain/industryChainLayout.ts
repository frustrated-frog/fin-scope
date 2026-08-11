import type { IndustryChainEdge, IndustryChainGraph, IndustryChainNode } from './industryChainTypes';

const LANE_WIDTH = 292;
const NODE_WIDTH = 208;
const STAGE_Y = 72;
const PRODUCT_Y = 184;
const STAGE_HEIGHT = 72;
const NODE_HEIGHT = 64;
const COMPANY_TOGGLE_HEIGHT = 28;
const COMPANY_GAP = 12;
const GROUP_GAP = 32;
const EDGE_GUTTER = 18;

export type PositionedIndustryNode = IndustryChainNode & {
  x: number;
  y: number;
  width: number;
  height: number;
  column: number;
};

export type IndustryEdgeRoute = 'stage-flow' | 'stage-membership' | 'company-link' | 'cross-link';

export type PositionedIndustryEdge = IndustryChainEdge & {
  path: string;
  route: IndustryEdgeRoute;
};

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
  const companiesByParent = new Map<string, IndustryChainNode[]>();
  graph.nodes.filter((node) => node.type === 'COMPANY').forEach((company) => {
    const parent = companyParent.get(company.nodeKey);
    if (!parent) return;
    companyCounts.set(parent, (companyCounts.get(parent) ?? 0) + 1);
    companiesByParent.set(parent, [...(companiesByParent.get(parent) ?? []), company]);
  });
  const expanded = options.expandedCompanyKeys ?? new Set<string>();
  const nodes: PositionedIndustryNode[] = stages.map((stage, column) => (
    positionNode(stage, column, STAGE_Y, STAGE_HEIGHT)
  ));
  const productsByColumn = new Map<number, IndustryChainNode[]>();
  graph.nodes.filter((node) => node.type === 'PRODUCT').forEach((product) => {
    const column = columns.get(stageForNode.get(product.nodeKey) ?? '') ?? 0;
    productsByColumn.set(column, [...(productsByColumn.get(column) ?? []), product]);
  });
  const columnBottoms: number[] = [];
  stages.forEach((_, column) => {
    let cursorY = PRODUCT_Y;
    (productsByColumn.get(column) ?? []).forEach((product) => {
      nodes.push(positionNode(product, column, cursorY, NODE_HEIGHT));
      const companies = companiesByParent.get(product.nodeKey) ?? [];
      cursorY += NODE_HEIGHT;
      if (companies.length > 0) cursorY += COMPANY_TOGGLE_HEIGHT;
      if (expanded.has(product.nodeKey)) {
        companies.forEach((company) => {
          nodes.push(positionNode(company, column, cursorY, NODE_HEIGHT));
          cursorY += NODE_HEIGHT + COMPANY_GAP;
        });
        if (companies.length > 0) cursorY -= COMPANY_GAP;
      }
      cursorY += GROUP_GAP;
    });
    columnBottoms.push(cursorY);
  });
  const positioned = new Map(nodes.map((node) => [node.nodeKey, node]));
  const routeCounts = new Map<IndustryEdgeRoute, number>();
  const edges = graph.edges.flatMap((edge): PositionedIndustryEdge[] => {
    const source = positioned.get(edge.sourceKey);
    const target = positioned.get(edge.targetKey);
    if (!source || !target) return [];
    const route = edgeRoute(edge, source, target);
    const routeIndex = routeCounts.get(route) ?? 0;
    routeCounts.set(route, routeIndex + 1);
    return [{ ...edge, route, path: edgePath(route, source, target, routeIndex) }];
  });
  return {
    width: Math.max(900, stages.length * LANE_WIDTH + 32),
    height: Math.max(520, ...columnBottoms.map((bottom) => bottom + 48)),
    stages: nodes.filter((node) => node.type === 'STAGE'), nodes, edges, companyCounts
  };
}

function positionNode(node: IndustryChainNode, column: number, y: number, height: number): PositionedIndustryNode {
  return { ...node, column, x: column * LANE_WIDTH + 42, y, width: NODE_WIDTH, height };
}

function edgeRoute(edge: IndustryChainEdge, source: PositionedIndustryNode,
                   target: PositionedIndustryNode): IndustryEdgeRoute {
  if (edge.type === 'FLOWS_TO' && source.type === 'STAGE' && target.type === 'STAGE') {
    return 'stage-flow';
  }
  if (edge.type === 'BELONGS_TO_STAGE' || edge.type === 'CONTAINS_STAGE') {
    return 'stage-membership';
  }
  if (source.type === 'COMPANY' || target.type === 'COMPANY') return 'company-link';
  return 'cross-link';
}

function edgePath(route: IndustryEdgeRoute, source: PositionedIndustryNode,
                  target: PositionedIndustryNode, routeIndex: number) {
  if (route === 'stage-flow') return horizontalPath(source, target);
  if (route === 'stage-membership') {
    const channelX = Math.min(source.x, target.x) - EDGE_GUTTER - (routeIndex % 3) * 5;
    return sameSidePath(source, target, channelX, 'left');
  }
  if (route === 'company-link' || source.column === target.column) {
    const channelX = Math.max(source.x + source.width, target.x + target.width)
      + EDGE_GUTTER + (routeIndex % 3) * 5;
    return sameSidePath(source, target, channelX, 'right');
  }
  return crossColumnPath(source, target);
}

function horizontalPath(source: PositionedIndustryNode, target: PositionedIndustryNode) {
  const forward = target.x >= source.x;
  const startX = forward ? source.x + source.width : source.x;
  const endX = forward ? target.x : target.x + target.width;
  return `M ${startX} ${centerY(source)} L ${endX} ${centerY(target)}`;
}

function sameSidePath(source: PositionedIndustryNode, target: PositionedIndustryNode,
                      channelX: number, side: 'left' | 'right') {
  const sourceX = side === 'left' ? source.x : source.x + source.width;
  const targetX = side === 'left' ? target.x : target.x + target.width;
  return `M ${sourceX} ${centerY(source)} L ${channelX} ${centerY(source)}`
    + ` L ${channelX} ${centerY(target)} L ${targetX} ${centerY(target)}`;
}

function crossColumnPath(source: PositionedIndustryNode, target: PositionedIndustryNode) {
  const forward = target.x >= source.x;
  const sourceX = forward ? source.x + source.width : source.x;
  const targetX = forward ? target.x : target.x + target.width;
  const channelX = sourceX + (targetX - sourceX) / 2;
  return `M ${sourceX} ${centerY(source)} L ${channelX} ${centerY(source)}`
    + ` L ${channelX} ${centerY(target)} L ${targetX} ${centerY(target)}`;
}

function centerY(node: PositionedIndustryNode) {
  return node.y + node.height / 2;
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

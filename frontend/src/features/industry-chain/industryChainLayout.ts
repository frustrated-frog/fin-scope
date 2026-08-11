import type { IndustryChainEdge, IndustryChainGraph, IndustryChainNode } from './industryChainTypes';
import { stageHighlightsForDisplay } from './industryChainProjection';

const LANE_WIDTH = 292;
const NODE_WIDTH = 208;
const STAGE_Y = 72;
const STAGE_HEIGHT = 72;
const RICH_STAGE_HEIGHT = 176;
const NODE_HEIGHT = 64;
const NODE_GAP = 28;
const TYPE_GAP = 14;
const EDGE_GUTTER = 18;
const TYPE_ORDER = ['MATERIAL', 'EQUIPMENT', 'COMPONENT', 'PRODUCT', 'TECHNOLOGY', 'APPLICATION', 'COMPANY'];

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
};

export function layoutIndustryGraph(graph: IndustryChainGraph): IndustryGraphLayout {
  const stages = graph.nodes
    .filter((node) => node.type === 'STAGE')
    .sort((left, right) => (left.stageOrder ?? Number.MAX_SAFE_INTEGER)
      - (right.stageOrder ?? Number.MAX_SAFE_INTEGER));
  const columns = new Map(stages.map((stage, index) => [stage.nodeKey, index]));
  const stageHeight = stages.some((stage) => stageHighlightsForDisplay(graph, stage.nodeKey).length > 0)
    ? RICH_STAGE_HEIGHT : STAGE_HEIGHT;
  const semanticY = STAGE_Y + stageHeight + 40;
  const stageForNode = parentStageMap(graph, columns);
  const nodes: PositionedIndustryNode[] = stages.map((stage, column) => (
    positionNode(stage, column, STAGE_Y, stageHeight)
  ));
  const semanticByColumn = new Map<number, IndustryChainNode[]>();
  graph.nodes.filter((node) => node.type !== 'STAGE' && node.type !== 'INDUSTRY_CHAIN').forEach((node) => {
    const column = columns.get(stageForNode.get(node.nodeKey) ?? '') ?? 0;
    semanticByColumn.set(column, [...(semanticByColumn.get(column) ?? []), node]);
  });
  const columnBottoms: number[] = [];
  stages.forEach((_, column) => {
    let cursorY = semanticY;
    let previousType = '';
    const semanticNodes = (semanticByColumn.get(column) ?? []).sort(compareSemanticNodes);
    semanticNodes.forEach((node) => {
      if (previousType && previousType !== node.type) cursorY += TYPE_GAP;
      nodes.push(positionNode(node, column, cursorY, NODE_HEIGHT));
      cursorY += NODE_HEIGHT + NODE_GAP;
      previousType = node.type;
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
    stages: nodes.filter((node) => node.type === 'STAGE'), nodes, edges
  };
}

function compareSemanticNodes(left: IndustryChainNode, right: IndustryChainNode) {
  return TYPE_ORDER.indexOf(left.type) - TYPE_ORDER.indexOf(right.type)
    || left.name.localeCompare(right.name, 'zh-CN')
    || left.nodeKey.localeCompare(right.nodeKey);
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

function addNeighbor(adjacency: Map<string, Set<string>>, source: string, target: string) {
  if (!adjacency.has(source)) adjacency.set(source, new Set());
  adjacency.get(source)!.add(target);
}

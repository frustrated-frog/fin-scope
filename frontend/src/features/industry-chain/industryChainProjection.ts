import type {
  IndustryChainGraph,
  IndustryChainLayer,
  IndustryChainNode,
  IndustryChainNodeProfile,
  IndustryChainNodeType
} from './industryChainTypes';

export type IndustryChainSemanticTone = 'high' | 'medium' | 'low' | 'neutral';
export type IndustryChainStageHighlight = {
  label: string;
  value: string;
  tone: 'critical' | 'value' | 'neutral';
};

const NODE_ORDER: Record<IndustryChainNodeType, number> = {
  INDUSTRY_CHAIN: 0,
  STAGE: 1,
  MATERIAL: 2,
  EQUIPMENT: 3,
  COMPONENT: 4,
  PRODUCT: 5,
  TECHNOLOGY: 6,
  APPLICATION: 7,
  COMPANY: 8
};

const MAX_DIRECT_CHILDREN = 12;
const MAX_DEFAULT_NODES = 25;

export function projectSemanticGraph(
  graph: IndustryChainGraph,
  expandedNodeKeys: Set<string>,
  activeLayer: IndustryChainLayer
): IndustryChainGraph {
  const orderedStages = graph.nodes
    .filter((node) => node.type === 'STAGE')
    .sort((left, right) => (left.stageOrder ?? Number.MAX_SAFE_INTEGER)
      - (right.stageOrder ?? Number.MAX_SAFE_INTEGER));
  const visibleKeys = new Set(orderedStages.map((node) => node.nodeKey));
  defaultSemanticNodes(graph, orderedStages).forEach((node) => visibleKeys.add(node.nodeKey));
  if (activeLayer === 'COMPANY') {
    graph.nodes.filter((node) => node.type === 'COMPANY').forEach((node) => visibleKeys.add(node.nodeKey));
  }
  const processed = new Set<string>();
  let changed = true;
  while (changed) {
    changed = false;
    [...visibleKeys].forEach((nodeKey) => {
      if (!expandedNodeKeys.has(nodeKey) || processed.has(nodeKey)) return;
      processed.add(nodeKey);
      directSemanticNeighbors(graph, nodeKey).slice(0, MAX_DIRECT_CHILDREN).forEach((node) => {
        if (!visibleKeys.has(node.nodeKey)) {
          visibleKeys.add(node.nodeKey);
          changed = true;
        }
      });
    });
  }
  const nodes = graph.nodes
    .filter((node) => visibleKeys.has(node.nodeKey))
    .sort(compareVisibleNodes);
  const edges = graph.edges.filter((edge) => visibleKeys.has(edge.sourceKey) && visibleKeys.has(edge.targetKey));
  return { ...graph, nodes, edges };
}

function defaultSemanticNodes(graph: IndustryChainGraph, stages: IndustryChainNode[]): IndustryChainNode[] {
  const remainingBudget = Math.max(0, MAX_DEFAULT_NODES - stages.length);
  const queues = stages.map((stage) => directSemanticNeighbors(graph, stage.nodeKey)
    .filter((node) => node.type !== 'COMPANY')
    .sort((left, right) => compareDefaultNodes(graph, left, right)));
  const selected: IndustryChainNode[] = [];
  const selectedKeys = new Set<string>();
  while (selected.length < remainingBudget && queues.some((queue) => queue.length > 0)) {
    queues.forEach((queue) => {
      while (queue.length > 0 && selectedKeys.has(queue[0].nodeKey)) queue.shift();
      const next = queue.shift();
      if (!next || selected.length >= remainingBudget) return;
      selected.push(next);
      selectedKeys.add(next.nodeKey);
    });
  }
  return selected;
}

function compareDefaultNodes(graph: IndustryChainGraph, left: IndustryChainNode, right: IndustryChainNode) {
  return defaultNodeScore(graph, right) - defaultNodeScore(graph, left) || compareSemanticNodes(left, right);
}

function defaultNodeScore(graph: IndustryChainGraph, node: IndustryChainNode) {
  const profile = graph.researchContent?.nodeProfiles?.find((item) => item.nodeKey === node.nodeKey);
  const primaryRelations = graph.edges.filter((edge) => (edge.sourceKey === node.nodeKey || edge.targetKey === node.nodeKey)
    && edge.strength !== 'SECONDARY').length;
  return primaryRelations * 2
    + (node.confidence === 'HIGH' ? 2 : node.confidence === 'MEDIUM' ? 1 : 0)
    + (profile?.bottleneckLevel === 'HIGH' ? 4 : profile?.bottleneckLevel === 'MEDIUM' ? 2 : 0)
    + (profile?.valueLevel === 'HIGH' ? 3 : profile?.valueLevel === 'MEDIUM' ? 1 : 0);
}

export function semanticNodeTone(
  profile: IndustryChainNodeProfile | undefined,
  activeLayer: IndustryChainLayer
): IndustryChainSemanticTone {
  if (!profile) return 'neutral';
  if (activeLayer === 'VALUE') return levelTone(profile.valueLevel);
  if (activeLayer === 'BOTTLENECK') return levelTone(profile.bottleneckLevel);
  if (activeLayer === 'LOCALIZATION') return localizationTone(profile.localizationLevel);
  if (activeLayer === 'TECHNOLOGY') return maturityTone(profile.maturity);
  return 'neutral';
}

export function relatedNodeKeys(graph: IndustryChainGraph, nodeKey: string): string[] {
  const result = new Set<string>();
  graph.edges.forEach((edge) => {
    if (edge.sourceKey === nodeKey) result.add(edge.targetKey);
    if (edge.targetKey === nodeKey) result.add(edge.sourceKey);
  });
  return [...result];
}

export function stageGraphHighlights(graph: IndustryChainGraph, stageKey: string): IndustryChainStageHighlight[] {
  const profile = graph.researchContent?.stageProfiles.find((item) => item.nodeKey === stageKey);
  if (!profile) return [];
  const candidates: IndustryChainStageHighlight[] = [
    { label: '核心瓶颈', value: profile.bottleneck, tone: 'critical' },
    { label: '价值获取', value: profile.valueCapture, tone: 'value' },
    { label: '关键指标', value: profile.coreMetrics[0], tone: 'neutral' },
    { label: '行业壁垒', value: profile.barriers[0], tone: 'neutral' },
    { label: '关键变量', value: profile.keyVariables[0], tone: 'neutral' }
  ];
  return candidates.filter((item) => Boolean(item.value?.trim())).slice(0, 3);
}

export function stageHighlightsForDisplay(
  graph: IndustryChainGraph,
  stageKey: string
): IndustryChainStageHighlight[] {
  const hasSemanticChildren = directSemanticNeighbors(graph, stageKey).some((node) => node.type !== 'COMPANY');
  return hasSemanticChildren ? [] : stageGraphHighlights(graph, stageKey);
}

export function directSemanticNeighbors(graph: IndustryChainGraph, nodeKey: string): IndustryChainNode[] {
  const current = graph.nodes.find((node) => node.nodeKey === nodeKey);
  if (!current) return [];
  const nodesByKey = new Map(graph.nodes.map((node) => [node.nodeKey, node]));
  const related = new Set<string>();
  graph.edges.forEach((edge) => {
    if (current.type === 'STAGE') {
      if (edge.type !== 'BELONGS_TO_STAGE') return;
      if (edge.targetKey === nodeKey) related.add(edge.sourceKey);
      if (edge.sourceKey === nodeKey) related.add(edge.targetKey);
      return;
    }
    if (edge.sourceKey === nodeKey) related.add(edge.targetKey);
    if (edge.targetKey === nodeKey) related.add(edge.sourceKey);
  });
  return [...related]
    .map((key) => nodesByKey.get(key))
    .filter((node): node is IndustryChainNode => Boolean(node) && node?.type !== 'STAGE')
    .sort(compareSemanticNodes);
}

function compareVisibleNodes(left: IndustryChainNode, right: IndustryChainNode) {
  if (left.type === 'STAGE' && right.type === 'STAGE') {
    return (left.stageOrder ?? Number.MAX_SAFE_INTEGER) - (right.stageOrder ?? Number.MAX_SAFE_INTEGER);
  }
  return compareSemanticNodes(left, right);
}

function compareSemanticNodes(left: IndustryChainNode, right: IndustryChainNode) {
  return NODE_ORDER[left.type] - NODE_ORDER[right.type]
    || left.name.localeCompare(right.name, 'zh-CN')
    || left.nodeKey.localeCompare(right.nodeKey);
}

function levelTone(value: 'HIGH' | 'MEDIUM' | 'LOW'): IndustryChainSemanticTone {
  return value.toLocaleLowerCase() as IndustryChainSemanticTone;
}

function localizationTone(value: IndustryChainNodeProfile['localizationLevel']): IndustryChainSemanticTone {
  if (value === 'LEADING' || value === 'HIGH') return 'high';
  if (value === 'MEDIUM') return 'medium';
  return 'low';
}

function maturityTone(value: IndustryChainNodeProfile['maturity']): IndustryChainSemanticTone {
  if (value === 'SCALING') return 'high';
  if (value === 'EMERGING') return 'medium';
  return 'low';
}

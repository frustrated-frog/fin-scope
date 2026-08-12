export type IndustryChainNodeType = 'INDUSTRY_CHAIN' | 'STAGE' | 'MATERIAL' | 'EQUIPMENT'
  | 'COMPONENT' | 'PRODUCT' | 'TECHNOLOGY' | 'APPLICATION' | 'COMPANY';
export type IndustryChainEdgeType = 'CONTAINS_STAGE' | 'FLOWS_TO' | 'BELONGS_TO_STAGE'
  | 'INPUT_TO' | 'PRODUCES' | 'PARTICIPATES_IN' | 'SUPPLIES_TO' | 'DEPENDS_ON'
  | 'ENABLES' | 'USED_IN' | 'SUBSTITUTES' | 'COMPETES_WITH';
export type IndustryChainEdgeNature = 'DISCLOSED' | 'INDUSTRY_LOGIC' | 'INFERRED';
export type IndustryChainConfidence = 'HIGH' | 'MEDIUM' | 'LOW';
export type IndustryChainEdgeStrength = 'PRIMARY' | 'SECONDARY';
export type IndustryChainLayer = 'STRUCTURE' | 'VALUE' | 'BOTTLENECK'
  | 'TECHNOLOGY' | 'LOCALIZATION' | 'COMPANY';
export type IndustryChainLifecycle = 'EMERGING' | 'GROWTH' | 'MATURE' | 'CONSOLIDATING' | 'DECLINING';
export type IndustryChainProsperity = 'RISING' | 'STABLE' | 'COOLING' | 'MIXED';
export type IndustryChainSupplyDemand = 'TIGHT' | 'BALANCED' | 'LOOSE' | 'STRUCTURAL';

export type IndustryChainResearchOverview = {
  lifecycle: IndustryChainLifecycle;
  prosperity: IndustryChainProsperity;
  supplyDemand: IndustryChainSupplyDemand;
  cycleType: string;
  demandDrivers: string[];
  supplyDrivers: string[];
  keyVariables: string[];
  bottlenecks: string[];
  overcapacityRisks: string[];
  trendTags: string[];
};

export type IndustryChainStageProfile = {
  nodeKey: string;
  roleSummary: string;
  businessModel: string;
  costStructure: string;
  valueCapture: string;
  bottleneck: string;
  prosperity: IndustryChainProsperity;
  supplyDemand: IndustryChainSupplyDemand;
  lifecycle: IndustryChainLifecycle;
  profitDrivers: string[];
  barriers: string[];
  coreMetrics: string[];
  risks: string[];
  keyVariables: string[];
  trendTags: string[];
};

export type IndustryChainCompanyProfile = {
  nodeKey: string;
  industryPosition: string;
  coreProducts: string[];
  downstreamMarkets: string[];
  competitiveAdvantages: string[];
  keyVariables: string[];
};

export type IndustryChainNodeProfile = {
  nodeKey: string;
  definition: string;
  function: string;
  inputs: string[];
  outputs: string[];
  costDrivers: string[];
  valueDrivers: string[];
  barriers: string[];
  coreMetrics: string[];
  risks: string[];
  maturity: 'EMERGING' | 'SCALING' | 'MATURE' | 'DECLINING';
  valueLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  bottleneckLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  localizationLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'LEADING';
};

export type IndustryChainResearchContent = {
  overview: IndustryChainResearchOverview;
  stageProfiles: IndustryChainStageProfile[];
  companyProfiles: IndustryChainCompanyProfile[];
  nodeProfiles?: IndustryChainNodeProfile[];
};

export type IndustryChain = {
  id: number;
  name: string;
  normalizedName: string;
  summary?: string;
  currentRevisionId?: number;
  createdAt?: string;
  updatedAt?: string;
};

export type IndustryChainRevision = {
  id: number;
  chainId: number;
  status: 'RUNNING' | 'READY' | 'FAILED';
  stage: 'QUEUED' | 'DISPATCHED' | 'COLLECTING_EVIDENCE' | 'SYNTHESIZING'
    | 'COMPLETING_STRUCTURE' | 'VALIDATING_STRUCTURE' | 'COMPLETED';
  message?: string;
  errorCode?: string;
  retryable?: boolean;
  createdAt?: string;
  completedAt?: string;
};

export type IndustryChainStructureAssessment = {
  status: 'BUILDING' | 'UPGRADE_AVAILABLE' | 'ENRICHMENT_RECOMMENDED' | 'COMPLETE';
  score: number;
  semanticNodeCount: number;
  coveredStageCount: number;
  stageCount: number;
  gaps: string[];
};

export type IndustryChainEvidence = {
  evidenceCode: string;
  title: string;
  url?: string;
  source?: string;
  sourceTier?: string;
  publishedAt?: string;
  excerpt?: string;
};

export type IndustryChainNode = {
  nodeKey: string;
  type: IndustryChainNodeType;
  name: string;
  description: string;
  stageOrder?: number;
  stockCode?: string;
  confidence: IndustryChainConfidence;
  evidenceRefs: string[];
};

export type IndustryChainEdge = {
  edgeKey: string;
  sourceKey: string;
  targetKey: string;
  type: IndustryChainEdgeType;
  nature: IndustryChainEdgeNature;
  description: string;
  confidence: IndustryChainConfidence;
  strength?: IndustryChainEdgeStrength;
  directionNote?: string;
  evidenceRefs: string[];
};

export type IndustryChainGraph = {
  chainId?: number;
  revisionId?: number;
  name: string;
  summary: string;
  limitations: string;
  schemaVersion: string;
  model?: string;
  generatedAt?: string;
  nodes: IndustryChainNode[];
  edges: IndustryChainEdge[];
  evidence: IndustryChainEvidence[];
  researchContent?: IndustryChainResearchContent;
};

export type IndustryChainWorkspace = {
  chain: IndustryChain;
  revision: IndustryChainRevision | null;
  graph: IndustryChainGraph | null;
  structure?: IndustryChainStructureAssessment;
};

export type IndustryChainEventImpact = {
  radarEventId: number;
  directNodeKey: string;
  direction: 'POSITIVE' | 'NEGATIVE' | 'MIXED' | 'UNCERTAIN';
  mechanism: 'SUPPLY' | 'DEMAND' | 'PRICE' | 'CAPACITY' | 'POLICY' | 'ORDER' | 'TECHNOLOGY';
  horizon: 'SHORT' | 'MEDIUM' | 'LONG';
  confidence: IndustryChainConfidence;
  impactSummary: string;
  analysisVersion: string;
  pathNodeKeys: string[];
};

export type IndustryChainEventItem = {
  eventId: number;
  title: string;
  summary?: string;
  categoryCode?: string;
  status?: string;
  firstSeenAt?: string;
  lastSeenAt?: string;
  sourceCount: number;
  signalCount: number;
  hotspotScore: number;
  impact: IndustryChainEventImpact;
};

export type IndustryChainEventFeed = {
  chainId: number;
  hours: number;
  refreshedAt: string;
  nodeEventCounts: Record<string, number>;
  events: IndustryChainEventItem[];
};

export type IndustryChainEventRefreshSummary = {
  scanned: number;
  added: number;
  updated: number;
  skipped: number;
  refreshedAt: string;
};

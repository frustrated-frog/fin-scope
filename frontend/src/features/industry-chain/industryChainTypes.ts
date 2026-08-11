export type IndustryChainNodeType = 'INDUSTRY_CHAIN' | 'STAGE' | 'PRODUCT' | 'COMPANY';
export type IndustryChainEdgeType = 'CONTAINS_STAGE' | 'FLOWS_TO' | 'BELONGS_TO_STAGE'
  | 'INPUT_TO' | 'PRODUCES' | 'PARTICIPATES_IN' | 'SUPPLIES_TO';
export type IndustryChainEdgeNature = 'DISCLOSED' | 'INDUSTRY_LOGIC' | 'INFERRED';
export type IndustryChainConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

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
  stage: 'QUEUED' | 'COLLECTING_EVIDENCE' | 'SYNTHESIZING' | 'COMPLETED';
  message?: string;
  errorCode?: string;
  retryable?: boolean;
  createdAt?: string;
  completedAt?: string;
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
};

export type IndustryChainWorkspace = {
  chain: IndustryChain;
  revision: IndustryChainRevision | null;
  graph: IndustryChainGraph | null;
};

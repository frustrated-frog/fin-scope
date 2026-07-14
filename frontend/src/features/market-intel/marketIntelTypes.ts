export type MarketIntelInstrument = {
  id: number;
  code: string;
  type: 'STOCK';
  name: string;
  market: string;
};

export type CapitalFlowPoint = {
  id: number;
  observedAt: string;
  price?: number;
  intervalTradeAmount?: number;
  cumulativeTradeAmount?: number;
  mainNetInflow?: number;
  turnoverRate?: number;
  volumeRatio?: number;
  qualityStatus?: string;
};

export type CapitalRuleExplanation = {
  summary: string;
  ruleVersion: string;
  items: Array<{
    level: string;
    text: string;
    metricRefs: string[];
  }>;
  dataGaps: string[];
};

export type MarketIntelCapitalOverview = {
  instrument: MarketIntelInstrument;
  snapshot: {
    id: number;
    instrumentId: number;
    asOf: string;
    fingerprint: string;
  } | null;
  intradayTimeline: CapitalFlowPoint[];
  dailyTrend: CapitalFlowPoint[];
  ruleExplanation: CapitalRuleExplanation | null;
  health: {
    status: 'EMPTY' | 'FRESH' | 'STALE';
    asOf: string | null;
    providerCode: string;
    warnings: string[];
  };
};

export type CapitalHypothesis = {
  type: string;
  claim: string;
  confidence: 'LOW' | 'MID' | 'HIGH';
  supportingMetricRefs: string[];
  counterEvidence: string[];
  dataGaps: string[];
};

export type CapitalInterpretation = {
  id: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FALLBACK' | 'FAILED';
  interpretationType: 'AGENT';
  plainSummary: string;
  facts: string[];
  hypotheses: CapitalHypothesis[];
  dataGaps: string[];
  observationPoints: string[];
  disclaimer: string;
  fallbackReason?: string;
  modelName?: string;
  updatedAt?: string;
};

export type MarketIntelRefreshRun = {
  id: number;
  instrumentId: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED';
  successCount: number;
  failureCount: number;
  finishedAt?: string;
};

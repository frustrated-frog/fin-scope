import type { MarketDataQualityStatus } from '../../shared/types';

export type MarketIntelInstrument = {
  id: number;
  code: string;
  type: 'STOCK';
  name: string;
  market: string;
};

export type CapitalFlowPoint = {
  id: number | null;
  observedAt: string;
  price?: number;
  tradeVolume?: number;
  intervalTradeAmount?: number;
  cumulativeTradeAmount?: number;
  mainNetInflow?: number;
  mainNetInflowSharePct?: number;
  turnoverRate?: number;
  volumeRatio?: number;
  qualityStatus?: string;
};

export type CapitalFlowStreak = {
  direction: 'INFLOW' | 'OUTFLOW' | 'FLAT';
  periods: number;
  granularity: string;
  since?: string;
  through?: string;
};

export type CapitalBehaviorMetrics = {
  latest: {
    tradeAmount?: number;
    tradeVolume?: number;
    turnoverRate?: number;
    volumeRatio?: number;
    mainNetInflow?: number;
    mainNetInflowSharePct?: number;
    observedAt?: string;
  } | null;
  intradayStreak: CapitalFlowStreak;
  dailyStreak: CapitalFlowStreak;
  objectiveTags: Array<{
    code: string;
    label: string;
    explanation: string;
    window: string;
    version: string;
    metricRefs: string[];
    actualValues?: Record<string, number>;
    thresholds?: Record<string, number>;
  }>;
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

export type CapitalFactorObservation = {
  factorRef?: string;
  factorCode: string;
  label: string;
  category: string;
  observedAt: string;
  window: string;
  value: number;
  baseline?: number;
  percentile?: number;
  zscore?: number;
  state?: string;
  sampleCount: number;
  metricRefs: string[];
  qualityStatus: string;
  calculationVersion: string;
  interpretationBoundary: string;
};

export type CapitalWatchCondition = {
  id: string;
  label: string;
  factorRef: string;
  operator: string;
  threshold: number;
  unit: string;
};

export type CapitalSignalEvaluation = {
  signalType: string;
  signalLabel: string;
  horizonDays: number;
  sampleCount: number;
  averageReturn?: number | null;
  medianReturn?: number | null;
  positiveRate?: number | null;
  averageMfe?: number | null;
  averageMae?: number | null;
  baselineAverageReturn?: number | null;
  baselineMedianReturn?: number | null;
  excessAverageReturn?: number | null;
  excessMedianReturn?: number | null;
  stabilityStatus: 'INSUFFICIENT_SAMPLE' | 'CONSISTENT' | 'MIXED';
  decayStatus?: 'INSUFFICIENT_SAMPLE' | 'BASELINE' | 'PERSISTENT' | 'DECAYING' | 'REVERSING';
  evaluationStatus: 'UNTESTED' | 'EXPLORATORY' | 'VALIDATED' | 'INVALIDATED';
  lastEventDate?: string | null;
};

export type CapitalBehaviorEvaluation = {
  id: number;
  snapshotId: number;
  asOf: string;
  dataFrom?: string | null;
  dataTo?: string | null;
  evaluationVersion: string;
  factorVersion: string;
  signalVersion: string;
  status: 'AVAILABLE' | 'INSUFFICIENT_DATA' | 'DATA_UNRELIABLE';
  dailySampleCount: number;
  evaluableEventCount: number;
  coverageRate: number;
  missingLossRate: number;
  historyQualityStatus?: 'RELIABLE' | 'DATA_UNRELIABLE' | null;
  priceCoverageRate?: number | null;
  amountCoverageRate?: number | null;
  signals: CapitalSignalEvaluation[];
  dataGaps: string[];
};

export type MarketIntelCapitalOverview = {
  instrument: MarketIntelInstrument;
  snapshot: {
    id: number;
    instrumentId: number;
    asOf: string;
    fingerprint: string;
    qualityStatus: 'COMPLETE' | 'PARTIAL';
    warnings: string[];
  } | null;
  intradayTimeline: CapitalFlowPoint[];
  dailyTrend: CapitalFlowPoint[];
  metrics: CapitalBehaviorMetrics | null;
  ruleExplanation: CapitalRuleExplanation | null;
  historicalEvaluation: CapitalBehaviorEvaluation | null;
  factorObservations: CapitalFactorObservation[];
  watchConditions: CapitalWatchCondition[];
  factorVersion?: string;
  signalVersion?: string;
  health: {
    status: MarketDataQualityStatus;
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
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FALLBACK' | 'INSUFFICIENT_DATA' | 'FAILED';
  interpretationType: 'AGENT';
  plainSummary: string;
  marketState?: string;
  executiveSummary?: string;
  facts: string[];
  hypotheses: CapitalHypothesis[];
  dataGaps: string[];
  observationPoints: string[];
  observations: Array<{
    dimension: string;
    claim: string;
    factorRefs: string[];
    metricRefs: string[];
    evaluationRefs?: string[];
  }>;
  counterEvidence: string[];
  watchConditionRefs: string[];
  confidence?: 'LOW' | 'MID';
  factorVersion?: string;
  signalVersion?: string;
  evidenceRefs: Array<{
    ref: string;
    label: string;
    category: string;
    value: number;
    unit: string;
    observedAt: string;
  }>;
  rejectedOutputCount: number;
  rejectionReasons: string[];
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
  errorType?: string;
  errorMessage?: string;
  finishedAt?: string;
};

export interface QuantDataset {
  id: number; name: string; market: string; dataKind: 'REAL' | 'LEARNING_SAMPLE';
  startDate?: string; endDate?: string; status: string; fingerprint?: string; qualitySummary?: string;
}

export interface QuantDatasetQuality {
  datasetId: number;
  status: string;
  summary?: string;
  fingerprint?: string;
  availableFactors: string[];
}

export interface QuantFactor {
  code: string; name: string; category: string; direction: 'HIGH' | 'LOW';
  description: string; lookbackDays: number; pointInTime: boolean;
}
export interface QuantFactorAnalysis { datasetId: number; datasetFingerprint: string; factorCode: string; sampleCount: number; icMean: number; icStd: number; icIr: number; positiveIcRatio: number }

export type FactorLifecycleStatus =
  | 'CANDIDATE' | 'DEFINITION_REVIEWED' | 'IMPLEMENTED' | 'CALCULATION_VERIFIED'
  | 'EXPLORATORY' | 'VALIDATED' | 'PRODUCTION_ELIGIBLE' | 'INVALIDATED' | 'RETIRED';

export type FactorResearchDirection = 'POSITIVE_HYPOTHESIS' | 'NEGATIVE_HYPOTHESIS';

export interface ResearchFactorDefinition {
  identity: { namespace: string; code: string; version: string };
  name: string;
  category: string;
  frequency: string;
  expectedDirection: FactorResearchDirection;
  plainMeaning: string;
  hypothesis: string;
  economicRationale: string;
  interpretationBoundary: string;
  requiredFields: string[];
  availableAtRule: string;
  missingPolicy: string;
  calculationKey: string;
  calculationVersion: string;
  sourceType: string;
  sourceRef: string;
  evaluationPolicyCode: string;
  evaluationPolicyVersion: string;
  status: FactorLifecycleStatus;
  validationEvidenceRef?: string;
}

export interface QuantStrategySpec {
  name: string; datasetId: number; benchmark: string; investmentHypothesis: string; riskBoundary: string;
  startDate?: string; endDate?: string;
  factors: Array<{ code: string; weight: number; direction: string }>;
  portfolio: { topN: number; rebalanceEvery: number; weighting: string };
  execution: { signalPrice: string; fillPrice: string; slippageBps: number };
  cost: { buyCommission: number; sellCommission: number; stampDuty: number; minimumCommission: number };
}

export interface QuantStrategyDraft {
  id: number; datasetId: number; status: string; model: string; spec: QuantStrategySpec; validationIssues: string[];
}

export interface QuantStrategyVersion {
  id: number; name: string; datasetId: number; version: number; specJson: string;
  strategyFingerprint: string; datasetFingerprint: string; engineVersion: string; source: string;
}

export interface BacktestMetrics {
  totalReturn: number; annualizedReturn: number; annualizedVolatility: number; maxDrawdown: number;
  sharpeRatio: number; calmarRatio: number; winRate: number; turnover: number; tradeCount: number;
  benchmarkReturn: number; excessReturn: number;
}

export interface QuantExperiment {
  id: number; strategyVersionId: number; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  datasetId?: number; datasetName?: string; dataKind?: 'REAL' | 'LEARNING_SAMPLE';
  datasetFingerprint: string; engineVersion: string; errorMessage?: string; createdAt?: string; completedAt?: string;
  result?: { metrics: BacktestMetrics; equityCurve: Array<{ tradeDate: string; portfolioNav: number; benchmarkNav: number; drawdown: number }>;
    annualPerformance: Array<{ year: number; portfolioReturn: number; benchmarkReturn: number; excessReturn: number; maxDrawdown: number }>;
    trades: Array<{ signalDate: string; tradeDate: string; instrumentCode: string; side: string; quantity: number; price: number; fee: number }>;
    positions: Array<{ tradeDate: string; instrumentCode: string; quantity: number; price: number; marketValue: number; weight: number }>;
    warnings: string[] };
  interpretation?: string;
}

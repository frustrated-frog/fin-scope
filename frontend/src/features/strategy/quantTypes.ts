export interface QuantDataset {
  id: number; name: string; market: string; dataKind: 'REAL' | 'LEARNING_SAMPLE';
  startDate?: string; endDate?: string; status: string; fingerprint?: string; qualitySummary?: string;
}

export interface QuantFactor {
  code: string; name: string; category: string; direction: 'HIGH' | 'LOW';
  description: string; lookbackDays: number; pointInTime: boolean;
}

export interface QuantStrategySpec {
  name: string; datasetId: number; benchmark: string; investmentHypothesis: string; riskBoundary: string;
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
  datasetFingerprint: string; engineVersion: string; errorMessage?: string; createdAt?: string; completedAt?: string;
  result?: { metrics: BacktestMetrics; equityCurve: Array<{ tradeDate: string; portfolioNav: number; benchmarkNav: number; drawdown: number }>; trades: unknown[] };
  interpretation?: string;
}

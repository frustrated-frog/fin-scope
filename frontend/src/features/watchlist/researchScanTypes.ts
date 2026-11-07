export type ResearchScanStatus = 'RUNNING' | 'READY' | 'PARTIAL' | 'FAILED';

export type StrengthReturnPoint = {
  date: string;
  actualReturnPct: number;
  baselineReturnPct: number;
};

export type StockStrength = {
  code: string;
  name: string;
  trainingFrom?: string;
  trainingThrough?: string;
  evaluatedFrom?: string;
  evaluatedThrough?: string;
  trainingSamples: number;
  evaluationSamples: number;
  beta?: number | null;
  fitQuality?: number | null;
  actualReturnPct?: number | null;
  baselineReturnPct?: number | null;
  excessReturnPp?: number | null;
  positiveResidualDays: number;
  returnPath?: StrengthReturnPoint[];
  gaps: string[];
};

export type StockRelationship = {
  leftCode: string;
  leftName: string;
  rightCode: string;
  rightName: string;
  from?: string;
  through?: string;
  samples: number;
  correlation?: number | null;
  residualCorrelation?: number | null;
  downsideCorrelation?: number | null;
  downsideSamples: number;
  strong: boolean;
};

export type StockGrowthQuality = {
  code: string;
  name: string;
  currency?: string;
  periodFrom?: string;
  periodThrough?: string;
  revenueTtm?: number | null;
  profitTtm?: number | null;
  operatingCashFlowTtm?: number | null;
  revenueGrowthPct?: number | null;
  profitGrowthPct?: number | null;
  cashConversion?: number | null;
  grossMarginPct?: number | null;
  grossMarginChangePp?: number | null;
  gaps: string[];
};

export type ResearchFinding = {
  key: string;
  kind: 'OUTPERFORMING' | 'UNDERPERFORMING' | 'RESIDUAL_LINK' | 'NEGATIVE_CASH_FLOW' | 'CASH_LAG' | 'MARGIN_EROSION';
  codes: string[];
  title: string;
  evidence: string[];
  newlyObserved: boolean;
};

export type ResearchSnapshot = {
  benchmarkName: string;
  marketDate?: string;
  observedAt?: string;
  totalStocks: number;
  marketCovered: number;
  financialCovered: number;
  strengths: StockStrength[];
  relationships: StockRelationship[];
  growthQuality: StockGrowthQuality[];
  findings: ResearchFinding[];
  gaps: string[];
};

export type ResearchScanRun = {
  id: string;
  scanDate: string;
  status: ResearchScanStatus;
  stage?: 'MARKET' | 'FINANCIAL' | 'COMPLETED';
  processedStocks: number;
  totalStocks: number;
  message?: string;
  startedAt?: string;
  finishedAt?: string;
  snapshot?: ResearchSnapshot | null;
};

export type ResearchScanView = {
  active?: ResearchScanRun | null;
  latest?: ResearchScanRun | null;
  lastAttempt?: ResearchScanRun | null;
  history: ResearchScanRun[];
};

export function isResearchScanView(value: ResearchScanView | null | undefined): value is ResearchScanView {
  return Boolean(value && !Array.isArray(value) && Array.isArray(value.history));
}

export function isCompletedResearchRun(value: ResearchScanRun | null | undefined): value is ResearchScanRun {
  return Boolean(value && (value.status === 'READY' || value.status === 'PARTIAL'));
}

export function formatResearchNumber(value: number | null | undefined, suffix = '', signed = false) {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${signed && value > 0 ? '+' : ''}${value.toFixed(2)}${suffix}`;
}

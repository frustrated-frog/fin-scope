export type FinancialReportType = 'Q1' | 'HALF_YEAR' | 'Q3' | 'ANNUAL';
export type FinancialStatementType = 'INCOME' | 'BALANCE_SHEET' | 'CASH_FLOW';
export type FinancialValueOrigin = 'REPORTED' | 'DERIVED' | 'CALCULATED' | 'AI_EXTRACTED';
export type FinancialQualityStatus = 'FRESH' | 'PARTIAL' | 'STALE' | 'CONFLICT' | 'UNAVAILABLE' | 'UNVERIFIED';

export type FinancialInstrument = {
  id: number;
  code: string;
  name: string;
  type: string;
  market?: string;
};

export type CompanySecurity = {
  symbol: string;
  exchange: string;
  market: string;
};

export type CompanySearchResult = {
  localInstrumentId?: number;
  providerCode: string;
  providerCompanyId: string;
  legalName: string;
  displayName: string;
  nativeName?: string;
  countryCode?: string;
  industry?: string;
  capabilityLevel: 'L1' | 'L2' | 'L3' | 'L4';
  securities: CompanySecurity[];
};

export type FinancialReport = {
  id: number;
  instrumentId: number;
  periodEnd: string;
  reportType: FinancialReportType;
  scope: string;
  currency: string;
  publishedAt?: string;
  audited?: boolean;
  qualityStatus: FinancialQualityStatus;
  sourceCode: string;
  warningMessage?: string;
  updatedAt?: string;
};

export type FinancialLineItem = {
  id: number;
  reportId: number;
  statementType: FinancialStatementType;
  sourceLabel: string;
  conceptCode?: string;
  periodRole: string;
  normalizedValue?: number | string | null;
  currency?: string;
  unitMultiplier?: number | string;
  valueOrigin: FinancialValueOrigin;
  sourceField?: string;
  sourceCode: string;
  displayOrder: number;
  qualityStatus: FinancialQualityStatus;
};

export type FinancialMetric = {
  id?: number;
  reportId?: number;
  metricCode: string;
  label: string;
  value?: number | string | null;
  unit?: string;
  formulaVersion?: string;
  inputRefs?: string;
  qualityStatus: FinancialQualityStatus;
};

export type FinancialFinding = {
  id?: number;
  reportId?: number;
  ruleCode: string;
  severity: string;
  direction: string;
  title: string;
  explanation: string;
  metricRefs?: string;
  limitations?: string;
};

export type FinancialReportView = {
  instrument: FinancialInstrument;
  report: FinancialReport;
  statements: Record<FinancialStatementType, FinancialLineItem[]>;
  metrics: FinancialMetric[];
  findings: FinancialFinding[];
  dataGaps: string[];
};

export type FinancialDocument = {
  id: number;
  instrumentId?: number;
  reportId?: number;
  originalFileName: string;
  mimeType?: string;
  fileSize?: number;
  fileHash?: string;
  pageCount?: number;
  parseStatus: string;
  errorMessage?: string;
  createdAt?: string;
};

export type FinancialUnit = 'YUAN' | 'WAN' | 'YI';

export type StockValuationSnapshot = {
  id?: number;
  instrumentId: number;
  observedDate: string;
  observedAt: string;
  name?: string;
  peTtm?: number | string | null;
  peMrq?: number | string | null;
  pbMrq?: number | string | null;
  psTtm?: number | string | null;
  pcfTtm?: number | string | null;
  sourceCode: string;
  qualityStatus: string;
};

export type ValuationMetricSummary = {
  metricCode: string;
  value?: number | string | null;
  percentile3y?: number | string | null;
  percentile5y?: number | string | null;
  sampleCount3y: number;
  sampleCount5y: number;
  historyStatus: 'READY' | 'ACCUMULATING';
};

export type StockCorporateAction = {
  id?: number;
  instrumentId: number;
  exDate: string;
  eventTypes: string[];
  dividendPerShare?: number | string | null;
  perShareBonus?: number | string | null;
  allotmentRatio?: number | string | null;
  allotmentPrice?: number | string | null;
  currency: string;
  sourceCode: string;
};

export type StockValuationView = {
  instrument: FinancialInstrument;
  latest?: StockValuationSnapshot;
  metrics: ValuationMetricSummary[];
  history: StockValuationSnapshot[];
  corporateActions: StockCorporateAction[];
  warnings: string[];
};

export type FinancialInterpretationStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'VALIDATING'
  | 'SUCCESS'
  | 'FALLBACK'
  | 'FAILED';

export type FinancialInterpretationClaim = {
  claim: string;
  claimType: 'FACT' | 'INFERENCE' | 'WATCHPOINT';
  refs: string[];
};

export type FinancialInterpretationDimension = {
  code: string;
  assessment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'INSUFFICIENT_EVIDENCE';
  summary: string;
  refs: string[];
  details?: FinancialInterpretationClaim[];
};

export type FinancialInterpretationResult = {
  operatingState: 'IMPROVING' | 'STABLE' | 'UNDER_PRESSURE' | 'INSUFFICIENT_EVIDENCE';
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  executiveSummary: FinancialInterpretationClaim[];
  periodChanges?: FinancialInterpretationClaim[];
  crossStatementInsights?: FinancialInterpretationClaim[];
  dimensions: FinancialInterpretationDimension[];
  positiveSignals: FinancialInterpretationClaim[];
  risks: FinancialInterpretationClaim[];
  turningPoints: FinancialInterpretationClaim[];
  watchpoints: FinancialInterpretationClaim[];
  limitations: string[];
  disclaimer: string;
};

export type FinancialInterpretation = {
  id: number;
  reportId: number;
  snapshotId: number;
  generationKey?: string;
  promptVersion: string;
  modelName?: string;
  status: FinancialInterpretationStatus;
  generationMode?: 'LLM' | 'REPAIRED' | 'DETERMINISTIC_FALLBACK';
  result?: FinancialInterpretationResult;
  validationErrors?: string[];
  failureCode?: string;
  failureMessage?: string;
  durationMs?: number;
  snapshotStale: boolean;
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
};

export type FinancialEvidence = {
  id: string;
  type: string;
  label: string;
  value?: string;
  unit?: string;
  period?: string;
  detail?: string;
  sourceRefs?: string[];
};

export type BrokerResearchReport = {
  id: number;
  instrumentId: number;
  linkedFinancialReportId?: number;
  title: string;
  institution?: string;
  analyst?: string;
  publishedDate?: string;
  reportType?: string;
  rating?: string;
  targetPrice?: number | string;
  targetPriceCurrency?: string;
  sourceType: string;
  sourceUrl?: string;
  originalFileName?: string;
  fileSize?: number;
  fileHash?: string;
  pageCount?: number;
  parseStatus: string;
  analysisStatus: string;
  qualityLevel: string;
  extractedText?: string;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type BrokerResearchAnalysis = {
  executiveSummary: string[];
  investmentThesis: string[];
  businessAnalysis: string[];
  industryAnalysis: string[];
  keyAssumptions: string[];
  catalysts: string[];
  risks: string[];
  learningNotes: string[];
  evidenceSections?: Record<string, Array<{
    text: string;
    sourceQuote: string;
    sourcePage?: number;
  }>>;
  glossary: Array<{ term: string; explanation: string }>;
  limitations: string[];
  disclaimer: string;
};

export type BrokerResearchForecast = {
  id?: number;
  metricCode: string;
  metricLabel: string;
  forecastPeriod: string;
  forecastValue?: number | string;
  unit?: string;
  sourceQuote?: string;
  sourcePage?: number;
  actualValue?: number | string;
  actualUnit?: string;
  actualPeriod?: string;
  variancePercent?: number | string;
  verificationStatus?: string;
  verificationReason?: string;
};

export type BrokerResearchClaim = {
  id?: number;
  category: string;
  title: string;
  detail: string;
  claimType: string;
  sourceQuote?: string;
  sourcePage?: number;
  financialMetricCode?: string;
  financialConceptCode?: string;
  verificationStatus?: string;
  verificationReason?: string;
  evidenceLabel?: string;
  evidenceValue?: string;
  evidenceUnit?: string;
  evidencePeriod?: string;
};

export type BrokerResearchReportView = {
  report: BrokerResearchReport;
  analysis: BrokerResearchAnalysis;
  forecasts: BrokerResearchForecast[];
  claims: BrokerResearchClaim[];
};

export type BrokerResearchCandidate = {
  sourceCode: string;
  externalId: string;
  sourceUrl: string;
  stockCode: string;
  title: string;
  institution?: string;
  analyst?: string;
  publishedDate?: string;
  rating?: string;
  reportType?: string;
  pageCount?: number;
  importedReportId?: number;
  availability: 'AVAILABLE' | 'IMPORTED' | 'FAILED' | 'UNAVAILABLE';
};

export type BrokerResearchSyncResult = {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
  sourceCode?: string;
  candidates: BrokerResearchCandidate[];
  importedReports: BrokerResearchReport[];
  importedCount: number;
  skippedCount: number;
  failedCount: number;
  errors: string[];
  completedAt?: string;
};

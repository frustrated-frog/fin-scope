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

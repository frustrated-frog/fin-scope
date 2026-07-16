import {
  FinancialLineItem,
  FinancialQualityStatus,
  FinancialReportType,
  FinancialUnit,
  FinancialValueOrigin
} from './financialTypes';

export const statementLabels = {
  INCOME: '利润表',
  BALANCE_SHEET: '资产负债表',
  CASH_FLOW: '现金流量表'
} as const;

export const reportTypeLabels: Record<FinancialReportType, string> = {
  Q1: '一季报',
  HALF_YEAR: '半年报',
  Q3: '三季报',
  ANNUAL: '年报'
};

export const originLabels: Record<FinancialValueOrigin, string> = {
  REPORTED: '披露',
  DERIVED: '单季派生',
  CALCULATED: '计算',
  AI_EXTRACTED: 'AI 提取'
};

export const qualityLabels: Record<FinancialQualityStatus, string> = {
  FRESH: '数据完整',
  PARTIAL: '部分可用',
  STALE: '待更新',
  CONFLICT: '存在冲突',
  UNAVAILABLE: '不可用',
  UNVERIFIED: '待核验'
};

const keyConcepts = new Set([
  'REVENUE',
  'OPERATING_COST',
  'NET_PROFIT',
  'NET_PROFIT_PARENT',
  'TOTAL_ASSETS',
  'TOTAL_LIABILITIES',
  'TOTAL_EQUITY',
  'ACCOUNTS_RECEIVABLE',
  'INVENTORY',
  'CASH_AND_EQUIVALENTS',
  'OPERATING_CASH_FLOW',
  'INVESTING_CASH_FLOW',
  'FINANCING_CASH_FLOW'
]);

export function keyFinancialLines(items: FinancialLineItem[]) {
  const selected = items.filter((item) => item.conceptCode && keyConcepts.has(item.conceptCode));
  return selected.length ? selected : items.slice(0, 8);
}

export function reportLabel(periodEnd: string, type: FinancialReportType) {
  return `${periodEnd.slice(0, 4)} ${reportTypeLabels[type]}`;
}

export function formatFinancialValue(value: number | string | null | undefined, unit: FinancialUnit) {
  if (value == null || value === '') return '—';
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return String(value);
  const divisor = unit === 'YI' ? 100_000_000 : unit === 'WAN' ? 10_000 : 1;
  const suffix = unit === 'YI' ? '亿' : unit === 'WAN' ? '万' : '';
  return `${new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: unit === 'YUAN' ? 0 : 2,
    maximumFractionDigits: unit === 'YUAN' ? 0 : 2
  }).format(parsed / divisor)}${suffix}`;
}

export function formatMetric(value: number | string | null | undefined, unit?: string) {
  if (value == null || value === '') return '—';
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return `${value}${unit ?? ''}`;
  const rendered = new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(parsed);
  return unit === 'pct' ? `${rendered} pct` : `${rendered}${unit ?? ''}`;
}

export function defaultReportPeriod(now = new Date()) {
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  if (month <= 4) return { periodEnd: `${year - 1}-12-31`, reportType: 'ANNUAL' as const };
  if (month <= 8) return { periodEnd: `${year}-03-31`, reportType: 'Q1' as const };
  if (month <= 10) return { periodEnd: `${year}-06-30`, reportType: 'HALF_YEAR' as const };
  return { periodEnd: `${year}-09-30`, reportType: 'Q3' as const };
}

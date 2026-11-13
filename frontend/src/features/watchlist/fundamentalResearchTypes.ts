export type FundamentalDimension = 'GROWTH' | 'EARNINGS_QUALITY' | 'WORKING_CAPITAL' | 'CAPITAL_ALLOCATION';
export type FundamentalWatchStatus = 'TRACKING' | 'SUPPORTED' | 'CHALLENGED' | 'CLOSED';
export interface BusinessNotes {
  revenueModel: string; customers: string; costDrivers: string; advantage: string; sourceUrl: string; revision: number; updatedAt?: string;
}
export interface FundamentalMetric {
  label: string; value: number | string; unit: string; conceptCode: string; reportId: number; periodEnd: string;
}
export interface FundamentalInsight {
  dimension: FundamentalDimension; title: string; conclusion: string; attention: boolean; nextCheck: string; metrics: FundamentalMetric[];
}
export interface FundamentalEvent {
  sampleId: number; title: string; subtype: string; dimension: FundamentalDimension; impact: string; nextCheck: string; sourceUrl?: string; publishedAt?: string;
}
export interface FundamentalFinancialBaseline {
  reportId: number; periodEnd: string; reportType: string; scope: string; currency: string; sourceCode: string;
  dimension: FundamentalDimension; metrics: FundamentalMetric[];
}
export interface FundamentalWatchComparison {
  state: 'WAITING' | 'UPDATED' | 'INCOMPARABLE' | 'LEGACY'; message: string; token?: string; reviewed: boolean;
  baseline?: FundamentalFinancialBaseline; current?: FundamentalFinancialBaseline;
  changes: Array<{ conceptCode: string; label: string; unit: string; baseline: number | string; current: number | string; change: number | string }>;
}
export interface FundamentalWatch {
  id?: number; originKey: string; title: string; hypothesis: string; baseline: string; supportCondition: string; challengeCondition: string;
  nextReviewDate: string; status: FundamentalWatchStatus; revision: number;
  baselineSnapshot?: FundamentalFinancialBaseline; comparison?: FundamentalWatchComparison; due?: boolean;
  reviews: Array<{ id: number; status: FundamentalWatchStatus; note: string; reviewedAt: string; nextReviewDate?: string; comparison?: FundamentalWatchComparison }>;
}

export function fundamentalChange(value: number | string, unit: string) {
  const sign = Number(value) > 0 ? '+' : '';
  return unit === '%' ? `${sign}${Number(value).toFixed(1)} 个百分点` : `${sign}${fundamentalValue(value, unit)}`;
}

export function comparisonReviewNote(comparison?: FundamentalWatchComparison) {
  if (comparison?.state !== 'UPDATED') {
    return '';
  }
  return `财报对照 ${comparison.baseline?.periodEnd} → ${comparison.current?.periodEnd}：\n`
    + comparison.changes.map(metric => `${metric.label}：${fundamentalValue(metric.baseline, metric.unit)} → ${fundamentalValue(metric.current, metric.unit)}（变化 ${fundamentalChange(metric.change, metric.unit)}）`).join('\n');
}
export interface FundamentalWorkspace {
  code: string; name: string;
  profile?: { companyName: string; industry: string; description: string; businessScope: string; sourceUrl: string; fetchedAt: string };
  notes: BusinessNotes; reportId?: number; periodEnd?: string; reportType?: string; scope?: string; currency?: string; sourceCode?: string;
  publishedAt?: string; summary: string; insights: FundamentalInsight[]; events: FundamentalEvent[];
  suggestions: FundamentalWatch[]; watches: FundamentalWatch[]; notices: string[]; assembledAt: string;
}
export const dimensionLabels: Record<FundamentalDimension, string> = {
  GROWTH: '增长来源', EARNINGS_QUALITY: '利润质量', WORKING_CAPITAL: '营运资金', CAPITAL_ALLOCATION: '资本配置',
};
export const watchStatusLabels: Record<FundamentalWatchStatus, string> = {
  TRACKING: '持续跟踪', SUPPORTED: '得到支持', CHALLENGED: '出现反证', CLOSED: '已结束',
};
export const emptyNotes: BusinessNotes = { revenueModel: '', customers: '', costDrivers: '', advantage: '', sourceUrl: '', revision: 0 };

export function fundamentalValue(value: number | string, unit: string) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return '—';
  }
  if (unit === '%') {
    return `${number.toFixed(1)}%`;
  }
  const currency = unit === 'CNY' ? '元' : ` ${unit}`;
  if (Math.abs(number) >= 100_000_000) {
    return `${(number / 100_000_000).toFixed(2)} 亿${currency}`;
  }
  if (Math.abs(number) >= 10_000) {
    return `${(number / 10_000).toFixed(2)} 万${currency}`;
  }
  return `${number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}${currency}`;
}
export function fundamentalSource(value?: string) {
  try {
    const url = new URL(value || '');
    return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}

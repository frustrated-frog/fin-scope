export type EventType = 'EARNINGS' | 'CONTRACT';
export type SampleState = 'DRAFT' | 'OBSERVING' | 'ARCHIVED';
export type WindowStatus = 'READY' | 'NOT_DUE' | 'MISSING_DATA' | 'SUSPENDED';
export type PathType = 'RECOVERED' | 'PEAK_GIVEBACK' | 'OBSERVING' | 'PERSISTENT_STRENGTH' | 'GIVEBACK' | 'DELAYED_STRENGTH' | 'RELATIVE_WEAKNESS' | 'NO_CLEAR_PATTERN';
export interface ReactionPoint {
  close?: number | null;
  adjustedClose?: number | null;
  volume?: number | null;
  amount?: number | null;
  session: number;
  tradeDate: string;
  status: WindowStatus;
  stockReturnPct?: number | null;
  benchmarkReturnPct?: number | null;
  relativeReturnPp?: number | null;
}
export interface ReactionWindow extends Omit<ReactionPoint, 'session' | 'tradeDate'> {
  sessions: number;
  endDate: string;
}
export interface ReactionCalculation {
  profile?: ReactionProfile;
  methodVersion: string;
  benchmarkCode: string;
  benchmarkName: string;
  adjustment: string;
  stockSource: string;
  benchmarkSource: string;
  stockQuality: string;
  benchmarkQuality: string;
  stockAsOf: string;
  benchmarkAsOf: string;
  baselineDate: string;
  firstSession: string;
  calculatedAt: string;
  pathType: PathType;
  warnings: string[];
  points: ReactionPoint[];
  windows: ReactionWindow[];
}
export interface ReactionSample {
  id: number;
  majorEventId?: number | null;
  sourceIdentity?: string;
  automatic?: boolean;
  eventSubtype?: string;
  ruleEvidence?: string;
  fact?: string;
  followed?: boolean;
  discoveryIssue?: string;
  sourceOriginType: string;
  sourceOriginKey: string;
  title: string;
  summary?: string;
  sourceUrl?: string;
  occurredDate?: string;
  firstCapturedAt?: string;
  registeredAt: string;
  instrumentCode: string;
  instrumentName?: string;
  eventType?: EventType;
  publishedAt?: string;
  relationNote?: string;
  state: SampleState;
  historicalBackfill: boolean;
  revision: number;
  calculation?: ReactionCalculation;
  lastAttemptAt?: string;
  refreshError?: string;
}
export interface ReactionCandidate {
  majorEventId: number;
  title: string;
  summary?: string;
  sourceUrl?: string;
  occurredDate?: string;
  suggestedType: EventType;
}
export const eventLabels: Record<EventType, string> = { EARNINGS: '业绩披露', CONTRACT: '合同与订单' };
export const statusLabels: Record<WindowStatus, string> = {
  READY: '已完成', NOT_DUE: '尚未到期', MISSING_DATA: '行情缺失', SUSPENDED: '无成交／可能停牌'
};
export const pathLabels: Record<PathType, string> = {
  RECOVERED: '下探后修复', PEAK_GIVEBACK: '阶段高点后回吐',
  OBSERVING: '等待完整窗口', PERSISTENT_STRENGTH: '首日优势保留', GIVEBACK: '首日走强后回吐',
  DELAYED_STRENGTH: '后续逐渐走强', RELATIVE_WEAKNESS: '相对走弱', NO_CLEAR_PATTERN: '未见明确路径'
};
export function signed(value?: number | null, unit = '') {
  return value == null || !Number.isFinite(value) ? '—' : `${value > 0 ? '+' : ''}${value.toFixed(2)}${unit}`;
}
export function dateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '未记录';
}
export function sourceHref(value?: string) {
  try {
    const url = new URL(value || '');
    return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}

export interface DiscoveryStatus {
  running: boolean;
  lastCompletedAt?: string;
  captured: number;
  resolved: number;
  message: string;
}

export function beforeEventReturn(sample: ReactionSample) {
  const start = sample.calculation?.points.find(point => point.session === -5)?.stockReturnPct;
  return start == null || start <= -100 ? undefined : (1 / (1 + start / 100) - 1) * 100;
}

export interface ReactionProfile {
  observedSessions: number;
  windowEnded: boolean;
  dataComplete: boolean;
  hasGaps: boolean;
  beforeStockPct?: number;
  beforeBenchmarkPct?: number;
  beforeRelativePp?: number;
  firstRelativePp?: number;
  currentRelativePp?: number;
  peakRelativePp?: number;
  peakSession?: number;
  givebackPp?: number;
  maxDrawdownPct?: number;
  firstVolumeRatio?: number;
  currentVolumeRatio?: number;
  summary: string;
}
export interface ReactionChange {
  id: number;
  sampleId: number;
  title: string;
  instrumentName: string;
  changeType: string;
  tradeDate: string;
  detectedAt: string;
  summary: string;
  followed: boolean;
}
export interface ReactionSource {
  originType: string;
  originKey: string;
  title: string;
  url?: string;
  publishedAt?: string;
  capturedAt?: string;
}
export interface ReactionComparisonGroup {
  criteria: string;
  relaxed: boolean;
  eventCount: number;
  sampleCount: number;
  completeCount: number;
  notDueCount: number;
  missingCount: number;
  median?: number;
  lowerQuartile?: number;
  upperQuartile?: number;
  cases: Array<{ sampleId: number; title: string; instrumentName: string; instrumentCode: string; publishedAt: string; matchReason: string; calculation: ReactionCalculation }>;
}
export interface ReactionHistoryComparison {
  sessions: number;
  sameCompany: ReactionComparisonGroup;
  otherCompanies: ReactionComparisonGroup;
}

export const subtypeLabels: Record<string, string> = {
  EARNINGS_FORECAST: '业绩预告', EARNINGS_REVISION: '业绩修正', EARNINGS_REPORT: '业绩报告',
  CONTRACT_SIGNED: '合同签署', CONTRACT_AWARDED: '正式中标', CONTRACT_TERMINATED: '合同终止',
  OPERATING_UPDATE: '经营进展', UNCLASSIFIED: '未分类'
};

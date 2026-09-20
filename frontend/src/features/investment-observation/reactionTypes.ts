export type EventType = 'EARNINGS' | 'CONTRACT';
export type SampleState = 'DRAFT' | 'OBSERVING' | 'ARCHIVED';
export type WindowStatus = 'READY' | 'NOT_DUE' | 'MISSING_DATA' | 'SUSPENDED';
export type PathType = 'OBSERVING' | 'PERSISTENT_STRENGTH' | 'GIVEBACK' | 'DELAYED_STRENGTH' | 'RELATIVE_WEAKNESS' | 'NO_CLEAR_PATTERN';
export interface ReactionPoint {
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
  majorEventId: number;
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
export const eventLabels: Record<EventType, string> = { EARNINGS: '业绩披露', CONTRACT: '正式合同' };
export const statusLabels: Record<WindowStatus, string> = {
  READY: '已完成', NOT_DUE: '尚未到期', MISSING_DATA: '行情缺失', SUSPENDED: '无成交／可能停牌'
};
export const pathLabels: Record<PathType, string> = {
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

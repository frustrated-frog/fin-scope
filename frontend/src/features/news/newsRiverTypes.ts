export type RiverCategory = 'ALL' | 'FINANCE' | 'TECHNOLOGY' | 'POLITICS' | 'OTHER';
export type RiverFrame = { id: string; publishedAt: string; eventCount: number };
export type RiverBucket = { start: string; finance: number; technology: number; politics: number; other: number };
export type RiverOverview = { date: string; earliestDate: string; frames: RiverFrame[]; buckets: RiverBucket[] };
export type RiverItem = {
  eventId: string; eventKey: string; title: string; summary: string; category: RiverCategory;
  observedAt?: string; rank?: number; hotspotScore?: number; sourceCount?: number; reportCount?: number;
  lifecycle?: string; change?: 'FIRST_ENTRY' | 'RETURNING' | 'RISING' | 'FALLING' | 'STEADY'; rankDelta?: number;
};
export type RiverPage = { frame: RiverFrame; bucket?: string; items: RiverItem[]; total: number; page: number };
export const riverCategories: { code: RiverCategory; name: string; field?: keyof Pick<RiverBucket, 'finance' | 'technology' | 'politics' | 'other'> }[] = [
  { code: 'ALL', name: '全部' }, { code: 'FINANCE', name: '财经', field: 'finance' },
  { code: 'TECHNOLOGY', name: '科技', field: 'technology' }, { code: 'POLITICS', name: '国际时事', field: 'politics' },
  { code: 'OTHER', name: '其他', field: 'other' },
];
export function shanghaiToday() { return new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }); }
export function timeLabel(value?: string) { return value?.slice(11, 16) ?? '—'; }
export function bucketTotal(bucket: RiverBucket) { return bucket.finance + bucket.technology + bucket.politics + bucket.other; }

export function lastFrame(frames?: RiverFrame[]) { return frames?.[frames.length - 1]; }

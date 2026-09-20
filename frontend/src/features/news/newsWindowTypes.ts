import type { ReactionSample } from '../investment-observation/reactionTypes';

export interface NewsReport {
  id: string;
  title: string;
  content: string;
  url?: string;
  providerCode: string;
  sourceName: string;
  sourceTier?: string;
  kind: string;
  publishedAt?: string;
  firstSeenAt: string;
  lastSeenAt: string;
  contentVersion: number;
  readVersion: number;
  unread: boolean;
  historicalBackfill: boolean;
  categoryCode?: string;
  categoryName?: string;
  classificationReason?: string;
  manuallyReviewed: boolean;
  manualReason?: string;
}
export interface NewsQuery {
  query: string;
  exclude: string;
  source: string;
  category: string;
  kind: string;
  unreadOnly: boolean;
  hours: number;
  page: number;
  size: number;
  asOfSequence: number;
  asOfTime?: string;
}
export interface NewsPage {
  items: NewsReport[];
  total: number;
  asOfSequence: number;
  asOfTime?: string;
  page: number;
  size: number;
  sources: string[];
  categoryCounts: Record<string, number>;
  sourceHealth?: Array<{
    providerCode: string;
    status: string;
    lastSuccessAt?: string;
  }>;
}
export interface NewsDetail {
  relatedReports?: NewsReport[];
  report: NewsReport;
  versions: Array<{
    reportId: string;
    version: number;
    title: string;
    content: string;
    detectedAt: string;
  }>;
  reactions: ReactionSample[];
}
export interface SavedFilter {
  unreadCount?: number;
  id: string;
  name: string;
  query: NewsQuery;
}
export const initialQuery: NewsQuery = {
  query: '',
  exclude: '',
  source: 'ALL',
  category: 'ALL',
  kind: 'ALL',
  unreadOnly: false,
  hours: 36,
  page: 0,
  size: 50,
  asOfSequence: 0,
};
export function queryParams(query: NewsQuery) {
  return new URLSearchParams(
    Object.entries(query)
      .filter(([, value]) => value != null)
      .map(([key, value]) => [key, String(value)]),
  ).toString();
}

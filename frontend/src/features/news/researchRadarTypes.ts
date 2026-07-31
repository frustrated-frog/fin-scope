export type RadarNewsItem = {
  id: string;
  kind: 'FLASH' | 'ARTICLE';
  title: string;
  content: string;
  url?: string;
  publishedAt?: string;
  providerCode: string;
  sourceName: string;
  sourceTier: string;
  categoryCode?: string;
};

export type RadarEvent = {
  id: number;
  title: string;
  summary: string;
  categoryCode?: string;
  priorityScore: number;
  recommendation: string;
  reasons: string[];
  watchlistRelated: boolean;
  watchlistExplanation: string;
  sourceCount: number;
  signalCount: number;
  uncertainty: string;
  nextObservation: string;
  suggestedResearchQuestion: string;
  lastSeenAt?: string;
};

export type RadarSignal = {
  id: number;
  title: string;
  content: string;
  url?: string;
  sourceName: string;
  sourceTier: string;
  publishedAt?: string;
  relationType?: string;
  matchScore: number;
  matchReason?: string;
};

export type RadarEventDetail = { event: RadarEvent; signals: RadarSignal[] };

export type ResearchRadarSnapshot = {
  overview: { eventCount: number; highPriorityCount: number; watchlistRelatedCount: number; sourceCount: number };
  events: RadarEvent[];
  liveItems: RadarNewsItem[];
  warnings: string[];
  refreshedAt: string;
};

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
  evidenceStatus?: string;
  evidenceSummary?: string;
  evidenceWarning?: string;
  evidenceCount?: number;
  evidenceSourceCount?: number;
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

export type RadarEvidence = {
  id?: number;
  toolCode: string;
  evidenceType?: string;
  title: string;
  summary?: string;
  url?: string;
  sourceName?: string;
  sourceTier?: string;
  publishedAt?: string;
};

export type RadarAgentTrace = {
  nodeName: string;
  status: string;
  summary?: string;
  errorType?: string;
  fallbackUsed: boolean;
  fallbackReason?: string;
  durationMs: number;
};

export type RadarEventDetail = {
  event: RadarEvent;
  signals: RadarSignal[];
  evidence?: RadarEvidence[];
  agentTrace?: RadarAgentTrace[];
};

export type ResearchRadarSnapshot = {
  overview: { eventCount: number; highPriorityCount: number; watchlistRelatedCount: number; sourceCount: number };
  events: RadarEvent[];
  liveItems: RadarNewsItem[];
  warnings: string[];
  refreshedAt: string;
};

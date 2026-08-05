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
  hotspotScore?: number;
  hotspotExplanation?: string;
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
  changeType?: string;
  changeSummary?: string;
  interpretationStatus?: string;
  suggestedResearchQuestion: string;
  lastSeenAt?: string;
  read?: boolean;
  followed?: boolean;
  disposition?: 'ACTIVE' | 'LATER' | 'IGNORED';
  observationCount?: number;
  openObservationCount?: number;
  researchRunCount?: number;
  unreadNotificationCount?: number;
};

export type RadarInterpretationResult = {
  factSummary: string;
  newDevelopment: string;
  whyItMatters: string;
  impactChain: string[];
  uncertainties: string[];
  nextObservations: string[];
  evidenceRefs: string[];
};

export type RadarInterpretation = {
  id?: number;
  eventId: number;
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'UNAVAILABLE';
  stale: boolean;
  failureCode?: string;
  failureMessage?: string;
  durationMs?: number;
  result?: RadarInterpretationResult;
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
  interpretation?: RadarInterpretation;
  workspaceState?: RadarWorkspaceState;
  observations?: RadarObservation[];
  timeline?: RadarTimelineEntry[];
  trust?: RadarTrust;
  researchLinks?: RadarResearchLink[];
};

export type RadarWorkspaceState = { eventId: number; read: boolean; followed: boolean; disposition: 'ACTIVE' | 'LATER' | 'IGNORED'; readAt?: string };
export type RadarObservation = { id: number; eventId: number; content: string; status: 'OPEN' | 'DONE'; source: 'SYSTEM' | 'USER'; createdAt: string; completedAt?: string };
export type RadarTimelineEntry = { id: number; eventId: number; eventType: string; title: string; summary?: string; referenceType?: string; referenceId?: number; occurredAt: string };
export type RadarTrust = { independentSourceCount: number; sourceTierCounts: Record<string, number>; citationCoveredCount: number; citationTotalCount: number; concentration: string; conflicts: string[]; limitation: string };
export type RadarResearchLink = { id: number; eventId: number; researchRunId: number; questionSnapshot?: string; status?: string; summary?: string; createdAt: string };
export type RadarNotification = { id?: number; eventId?: number; notificationType: string; title: string; message?: string; read: boolean; createdAt: string };
export type RadarNotificationCenter = { items: RadarNotification[]; unreadCount: number; todayCount: number; followedChangeCount?: number; openObservationCount?: number };
export type RadarStateFilter = 'ALL' | 'UNREAD' | 'FOLLOWED' | 'LATER' | 'IGNORED';

export type ResearchRadarSnapshot = {
  overview: { eventCount: number; highPriorityCount: number; watchlistRelatedCount: number; sourceCount: number };
  events: RadarEvent[];
  latestChanges?: RadarEvent[];
  liveItems?: RadarNewsItem[];
  warnings: string[];
  refreshedAt: string;
  productionStatus?: {
    running: boolean;
    status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'EMPTY';
    completedAt?: string;
    sourceCount: number;
    signalCount: number;
    eventCount: number;
    warning?: string;
  };
};

export type View =
  | 'dashboard'
  | 'sources'
  | 'intake'
  | 'article'
  | 'briefs'
  | 'briefReader'
  | 'research'
  | 'events'
  | 'eventDetail'
  | 'evidence'
  | 'knowledge'
  | 'topics'
  | 'topicReader'
  | 'learning'
  | 'contentStudio'
  | 'agents'
  | 'settings'
  | 'watchlist'
  | 'marketIntel'
  | 'financials'
  | 'strategy';

export type StrategyHolding = { id: number; instrumentId: number; code: string; type: 'FUND' | 'STOCK'; name: string; role: string; targetWeight: number; currentWeight: number; note?: string; revision: number; updatedAt?: string };
export type StrategyOverview = { holdings: StrategyHolding[]; targetWeight: number; currentWeight: number };
export type StrategyPlaybook = { code: string; title: string; scope: string; summary: string; cadence: string; riskBoundary: string; status: 'RESEARCHING' | 'ACTIVE' | 'PAUSED'; note?: string; revision: number };
export type StrategyStockThesis = { id: number; code: string; name: string; stage: string; thesis: string; buyConditions: string; invalidationConditions: string; watchFocus: string; note?: string; revision: number };
export type StrategyReview = { id: number; reviewDate: string; facts: string; reasoning: string; nextAction: string; createdAt: string };

export type Source = {
  id?: number;
  name: string;
  type: string;
  url: string;
  enabled: boolean;
  fetchFrequencyMinutes: number;
  credibility: number;
  tags?: string;
  maxItemsPerRun?: number;
  scheduleTimes?: string;
  scheduledEnabled?: boolean;
};

export type FetchBatch = {
  id: number;
  sourceId?: number;
  sourceName?: string;
  triggerType: string;
  status: string;
  startedAt?: string;
  endedAt?: string;
  lookbackDays?: number;
  maxItemsRequested?: number;
  rawItemCount?: number;
  candidateCount?: number;
  agentReviewedCount?: number;
  duplicateCount?: number;
  lowValueCount?: number;
  errorMessage?: string;
  batchSummaryJson?: string;
  batchSummaryText?: string;
};

export type IntakeCandidate = {
  id: number;
  batchId: number;
  sourceId?: number;
  sourceName?: string;
  sourceType?: string;
  originalTitle?: string;
  originalUrl?: string;
  originalSummary?: string;
  originalBody?: string;
  contentType?: string;
  extractionMethod?: string;
  extractionQualityScore?: number;
  publishedAt?: string;
  fetchedAt?: string;
  chineseTitle?: string;
  decisionSummary?: string;
  keyFactsJson?: string;
  whyItMatters?: string;
  noveltyJudgment?: string;
  riskFlagsJson?: string;
  agentScore?: number;
  agentRecommendation?: string;
  agentReason?: string;
  agentModel?: string;
  agentStatus?: string;
  agentErrorMessage?: string;
  agentReviewJson?: string;
  humanStatus?: string;
  humanNote?: string;
  promotedArticleId?: number;
  duplicateOfCandidateId?: number;
  duplicateOfArticleId?: number;
};

export type PromoteIntakeCandidateResponse = {
  candidateId: number;
  articleId: number;
  /** Candidate human status after promote — e.g. "PROMOTED" */
  status: 'PROMOTED' | 'PENDING' | 'SAVED_FOR_LATER' | 'SKIPPED' | 'REJECTED';
  eventId?: number;
  eventTitle?: string;
  evidenceCount?: number;
  learningTaskCount?: number;
  contentIdeaCount?: number;
  /** Workflow attach status — whether research package generation succeeded */
  workflowStatus?: 'SUCCESS' | 'FAILED';
  workflowSummary?: string;
  workflowErrorMessage?: string;
};

export type InsightCard = {
  id?: number;
  oneSentenceSummary?: string;
  coreEvent?: string;
  importance?: string;
  impactTargets?: string;
  followUpQuestions?: string;
  cardMarkdown?: string;
  analysisSections?: Array<{
    title: string;
    content: string;
  }>;
  background?: string;
  keyData?: string;
  timeline?: string;
  relatedParties?: string;
  riskFactors?: string;
  futureOutlook?: string;
  impactOnInvestment?: string;
  impactOnStartup?: string;
  professionalInsight?: string;
  facts?: string;
  reasoning?: string;
  opinions?: string;
};

export type Article = {
  id: number;
  title: string;
  url?: string;
  sourceName: string;
  category?: string;
  noveltyType?: string;
  noveltyReason?: string;
  summary?: string;
  body?: string;
  publishedAt?: string;
  fetchedAt?: string;
  insightCard?: InsightCard;
};

export type AsyncTask = {
  taskId: string;
  type?: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  phase?: 'QUEUED' | 'FETCHING' | 'PARSING' | 'PERSISTING' | 'LLM' | 'COMPLETED' | 'FAILED';
  message?: string;
  errorMessage?: string;
  articleId?: number;
  article?: Article;
};

export type TaskProgressEvent = {
  eventId: string;
  taskId: string;
  type: 'SNAPSHOT' | 'PHASE' | 'DONE' | 'ERROR' | 'HEARTBEAT';
  status?: AsyncTask['status'];
  phase?: AsyncTask['phase'];
  message?: string;
  errorMessage?: string;
  articleId?: number;
  occurredAt?: string;
};

export type Brief = {
  id: number;
  briefDate: string;
  title: string;
  markdownPath: string;
  content?: string;
};

export type EventCluster = {
  id: number;
  canonicalTitle: string;
  canonicalEventKey?: string;
  themeCode: string;
  summary?: string;
  status?: string;
  firstSeenAt?: string;
  lastSeenAt?: string;
  lastMeaningfulUpdateAt?: string;
  updatedAt?: string;
  importanceScore?: number;
  noveltyState?: string;
  evidenceCount?: number;
  articleCount?: number;
};

export type EventArticleLink = {
  eventId: number;
  articleId: number;
  noveltyType?: string;
  noveltyReason?: string;
  relationType?: string;
  matchScore?: number;
  createdAt?: string;
  articleTitle?: string;
  articleUrl?: string;
};

export type EvidenceItem = {
  id: number;
  eventId: number;
  articleId?: number;
  sourceTier: string;
  evidenceType: string;
  claim: string;
  claimKey?: string;
  confidence: number;
  createdAt?: string;
  articleTitle?: string;
  articleUrl?: string;
  articlePublishedAt?: string;
};

export type LearningTask = {
  id: number;
  eventId: number;
  themeCode: string;
  question: string;
  concepts?: string;
  difficulty?: string;
  status: string;
  whyNeeded?: string;
};

export type ContentIdea = {
  id: number;
  eventId: number;
  themeCode: string;
  title: string;
  angle?: string;
  format: string;
  audience?: string;
  score: number;
  scoreReason?: string;
  outline?: string;
  status?: string;
};

export type BriefResearchContext = {
  briefDate: string;
  events: EventCluster[];
  evidenceItems: EvidenceItem[];
  learningTasks: LearningTask[];
  contentIdeas: ContentIdea[];
};

export type SourceProfile = {
  sourceId?: number;
  sourceName: string;
  sourceTier?: string;
  adapterType?: string;
  credibility?: number;
  enabled?: boolean;
  themeCodes?: string[];
};

export type ResearchRunPlanStep = {
  id?: number;
  researchRunId?: number;
  stepId: string;
  title: string;
  stepType?: string;
  executor?: string;
  status: string;
  dependencies?: string[];
  inputSummary?: string;
  outputSummary?: string;
  errorType?: string;
  errorMessage?: string;
  fallbackUsed?: boolean;
  fallbackReason?: string;
  terminationReason?: string;
  attempt?: number;
  maxAttempts?: number;
  progressDelta?: number;
  startedAt?: string;
  endedAt?: string;
  metadataJson?: string;
};

export type ResearchRun = {
  id: number;
  thesisId?: number;
  runDate: string;
  themeCodes: string[];
  sourceCount: number;
  fetchedSourceCount?: number;
  articleCount?: number;
  eventCount?: number;
  evidenceCount?: number;
  learningTaskCount?: number;
  contentIdeaCount?: number;
  briefDate?: string;
  status: string;
  summary?: string;
  errorMessage?: string;
  plannedSources?: SourceProfile[];
};

export type ResearchReport = {
  id: number;
  researchRunId: number;
  thesisId?: number;
  reportType: string;
  status: string;
  title: string;
  conclusion: string;
  conclusionDirection: string;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  executiveSummary: string;
  contentMarkdown: string;
  markdownPath: string;
  generationMode: string;
  warningMessage?: string;
  evidenceCount: number;
  sourceCount: number;
  characterCount: number;
  createdAt?: string;
  updatedAt?: string;
};

export type ResearchThesis = {
  id: number;
  question: string;
  subjectType: 'COMPANY' | 'INDUSTRY' | 'WATCHLIST';
  subjectName: string;
  subjectCode?: string;
  status: 'OPEN' | 'CONCLUDED' | 'ARCHIVED';
  conclusion?: string;
  confidence?: 'HIGH' | 'MEDIUM' | 'LOW';
  nextValidation?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ThesisFinding = {
  id: number;
  thesisId: number;
  stance: 'SUPPORT' | 'COUNTER' | 'UNKNOWN';
  summary: string;
  evidenceId?: number;
  createdAt?: string;
  updatedAt?: string;
};
export type ResearchThesisDetail = { thesis: ResearchThesis; findings: ThesisFinding[]; runs: ResearchRun[]; outputs: { id: number; researchRunId: number; outputType: string; outputId: number }[] };

export type ResearchRunDetail = {
  run: ResearchRun;
  plannedSources: SourceProfile[];
  planSteps: ResearchRunPlanStep[];
  agentRuns: AgentRun[];
  reportAvailable: boolean;
  reportStatus?: string;
  reportGenerationMode?: string;
  canRegenerateReport: boolean;
};

export type Topic = {
  id: number;
  name: string;
  slug?: string;
  status: string;
  description?: string;
  markdownPath?: string;
  terms?: string;
  learningQuestions?: string;
  articleCount?: number;
  briefCount?: number;
};

export type TopicDetail = {
  topic: Topic;
  linkedArticles: Article[];
  linkedBriefs: Brief[];
  markdown: string;
};

export type AgentRun = {
  id: number;
  researchRunId?: number;
  eventId?: number;
  articleId?: number;
  nodeName: string;
  status: string;
  input?: string;
  output?: string;
  durationMs: number;
  errorMessage?: string;
  createdAt?: string;
  stepId?: string;
  attempt?: number;
  actionFingerprint?: string;
  inputHash?: string;
  outputHash?: string;
  errorType?: string;
  fallbackUsed?: boolean;
  fallbackReason?: string;
  terminationReason?: string;
  progressDelta?: number;
  budgetSnapshot?: string;
  metadataJson?: string;
};

export type Dashboard = {
  sourceCount: number;
  articleCount: number;
  briefCount: number;
  latestFetchRuns: Array<{
    id: number;
    sourceName: string;
    status: string;
    successCount: number;
    duplicateCount: number;
  }>;
};

export type PageResponse<T> = {
  items: T[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
};

export type ToastItem = {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
};

export type AttributionDriver = {
  claim: string;
  impactLevel?: string;
  confidence?: string;
  detail?: string;
  facts?: string[];
  transmissionPath?: string;
  counterEvidence?: string;
  observationWindow?: string;
  evidenceUrls?: string[];
};

export type AttributionEvidence = {
  id?: number;
  origin?: string;
  title?: string;
  url?: string;
  snippet?: string;
  sourceDomain?: string;
  sourceTier?: string;
  relevance?: number;
  eventType?: string;
  stance?: string;
  directness?: string;
  publishedAt?: string;
  eventKey?: string;
  historicalContext?: boolean;
};

export type AttributionReport = {
  id: number;
  instrumentCode: string;
  instrumentName?: string;
  instrumentType?: string;
  reportDate?: string;
  changePct?: number;
  status: 'GENERATING' | 'COMPLETED' | 'FAILED';
  summary?: string;
  drivers?: AttributionDriver[];
  primaryDriver?: AttributionDriver;
  uncertainties?: string[];
  observationWindows?: string[];
  disclaimer?: string;
  evidences?: AttributionEvidence[];
  errorMessage?: string;
  warningMessage?: string;
  durationMs?: number;
  createdAt?: string;
};

export type AttributionProgress = {
  type: 'STAGE' | 'CLUE' | 'DONE' | 'ERROR';
  stage?: string;
  message?: string;
  reportId?: number;
};

export type AttributionResearchStep = {
  id?: number;
  stepId: string;
  track?: string;
  status: 'PLANNED' | 'PENDING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED' | 'SKIPPED';
  inputSummary?: string;
  outputSummary?: string;
  attempt?: number;
  maxAttempts?: number;
  errorMessage?: string;
};

export type AttributionResearchRunView = {
  run: {
    id: number;
    reportId: number;
    status: 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';
    currentStep?: string;
    terminationReason?: string;
    errorMessage?: string;
    planJson?: string;
    budgetJson?: string;
  };
  steps: AttributionResearchStep[];
  progress?: {
    plannedTracks: number;
    activatedTracks: number;
    settledTracks: number;
    currentTrack?: string;
    currentStep?: string;
  };
};

export type MarketDataQualityStatus =
  | 'FRESH_PRIMARY'
  | 'FRESH_FALLBACK'
  | 'PARTIAL_FRESH'
  | 'STALE_FALLBACK'
  | 'UNAVAILABLE';

export type MarketDataQuality = {
  qualityStatus?: MarketDataQualityStatus;
  sourceCode?: string;
  asOf?: string;
  retrievedAt?: string;
  staleAgeSeconds?: number;
  warning?: string;
  refreshId?: string;
};

export type WatchlistItem = MarketDataQuality & {
  id: number;
  code: string;
  type: 'STOCK' | 'FUND' | 'SECTOR';
  name?: string;
  market?: string;
  groupName?: string;
  price?: number;
  confirmedNav?: number;
  confirmedNavDate?: string;
  confirmedNavChangePct?: number;
  changePct?: number;
  changeAmount?: number;
  turnover?: number;
  open?: number;
  high?: number;
  low?: number;
  amplitude?: number;
  quoteValid: boolean;
  quoteNote?: string;
  quoteDate?: string;
  attributionSummary?: string;
  attributionReportId?: number;
  attributionReportDate?: string;
  attributionChangePct?: number;
};

export type MarketIndexQuote = MarketDataQuality & {
  code: string;
  name: string;
  price?: number;
  changeAmount?: number;
  changePct?: number;
  quoteValid: boolean;
  quoteNote?: string;
};

export type SectorCategory = 'INDUSTRY' | 'CONCEPT';

export type SectorMarketEntry = {
  code: string;
  name: string;
  category: SectorCategory;
  price?: number;
  changeAmount?: number;
  changePct?: number;
  turnover?: number;
  leaderStockCode?: string;
  leaderStockName?: string;
  leaderStockChangePct?: number;
  quoteTime?: string;
};

export type SectorMarketOverview = MarketDataQuality & {
  category: SectorCategory;
  qualityStatus: MarketDataQualityStatus;
  leaders: SectorMarketEntry[];
  laggards: SectorMarketEntry[];
};

export type SectorMarketSearchResult = MarketDataQuality & {
  qualityStatus: MarketDataQualityStatus;
  items: SectorMarketEntry[];
};

export type FollowedSector = MarketDataQuality & {
  id: number;
  code: string;
  name?: string;
  price?: number;
  changePct?: number;
  changeAmount?: number;
  turnover?: number;
  quoteValid: boolean;
  quoteNote?: string;
  quoteDate?: string;
  attributionSummary?: string;
  attributionReportId?: number;
  attributionReportDate?: string;
  attributionChangePct?: number;
};

export type ResourceState<T> = {
  data: T;
  phase: 'idle' | 'loading' | 'ready' | 'refreshing' | 'error';
  error?: string;
  warning?: string;
  updatedAt?: string;
};

export type View =
  | 'dashboard'
  | 'sources'
  | 'intake'
  | 'article'
  | 'briefs'
  | 'briefReader'
  | 'research'
  | 'events'
  | 'evidence'
  | 'topics'
  | 'topicReader'
  | 'learning'
  | 'contentStudio'
  | 'agents'
  | 'settings'
  | 'watchlist';

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
  confidence: number;
  createdAt?: string;
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

export type ResearchRunDetail = {
  run: ResearchRun;
  plannedSources: SourceProfile[];
  planSteps: ResearchRunPlanStep[];
  agentRuns: AgentRun[];
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
  disclaimer?: string;
  evidences?: AttributionEvidence[];
  errorMessage?: string;
  durationMs?: number;
};

export type AttributionProgress = {
  type: 'STAGE' | 'CLUE' | 'DONE' | 'ERROR';
  stage?: string;
  message?: string;
  reportId?: number;
};

export type WatchlistItem = {
  id: number;
  code: string;
  type: 'STOCK' | 'FUND' | 'SECTOR';
  name?: string;
  market?: string;
  groupName?: string;
  price?: number;
  changePct?: number;
  changeAmount?: number;
  turnover?: number;
  open?: number;
  high?: number;
  low?: number;
  amplitude?: number;
  quoteValid: boolean;
  quoteNote?: string;
};

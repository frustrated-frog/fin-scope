import { PageResponse } from '../../shared/types';

export type KnowledgeSection = 'home' | 'topics' | 'learning' | 'review';

export type KnowledgeAction = {
  type: 'CONTINUE_TASK' | 'REVIEW_TOPIC' | 'START_TASK' | 'CHECK_NEW_EVIDENCE';
  title: string;
  reason: string;
  routeTarget: string;
  sourceLabel?: string;
  topicId?: number;
  taskId?: number;
};

export type KnowledgeTopic = {
  id: number;
  name: string;
  slug?: string;
  description?: string;
  status?: string;
  lifecycleStatus: 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
  masteryStatus: 'EXPLORING' | 'BUILDING' | 'REVIEWING' | 'MATURE';
  revision: number;
  articleCount?: number;
  briefCount?: number;
  updatedAt?: string;
};

export type KnowledgeEntry = {
  id: number;
  topicId: number;
  learningTaskId?: number;
  entryType: string;
  entryStatus: 'DRAFT' | 'FINAL';
  questionSnapshot?: string;
  contentMarkdown: string;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  revision: number;
  createdAt?: string;
  updatedAt?: string;
};

export type KnowledgeTask = {
  id: number;
  eventId?: number;
  topicId?: number;
  themeCode?: string;
  question: string;
  concepts?: string;
  difficulty?: string;
  status: 'SUGGESTED' | 'TODO' | 'IN_PROGRESS' | 'DONE' | 'DISMISSED';
  whyNeeded?: string;
  origin?: string;
  priority?: number;
  acceptedAt?: string;
  dismissedReason?: string;
  completionMode?: string;
  revision: number;
  updatedAt?: string;
};

export type KnowledgeOverview = {
  acceptedTaskCount: number;
  suggestedTaskCount: number;
  dueReviewCount: number;
  activeTopicCount: number;
  actions: KnowledgeAction[];
  activeTopics: KnowledgeTopic[];
  recentEntries: KnowledgeEntry[];
};

export type KnowledgePage<T> = PageResponse<T>;

export type KnowledgeEntryInput = {
  topicId: number;
  markdown: string;
  confidence: KnowledgeEntry['confidence'];
  evidenceIds: number[];
  expectedTaskRevision: number;
  expectedEntryRevision?: number;
};

export type KnowledgeEvidence = {
  id: number;
  eventId: number;
  articleId?: number;
  claim: string;
  sourceTier: string;
  evidenceType?: string;
  confidence: number;
  articleTitle?: string;
  articleUrl?: string;
};

export type KnowledgeEvent = {
  id: number;
  canonicalTitle: string;
  summary?: string;
  importanceScore?: number;
  noveltyState?: string;
  lastMeaningfulUpdateAt?: string;
};

export type TopicReviewState = {
  topicId: number;
  lastReviewedAt?: string;
  nextReviewAt?: string;
  intervalDays: number;
  reviewCount: number;
  revision: number;
};

export type KnowledgeTopicWorkspace = {
  topic: KnowledgeTopic;
  reviewState?: TopicReviewState;
  events: KnowledgeEvent[];
  evidence: KnowledgeEvidence[];
  tasks: KnowledgeTask[];
  entries: KnowledgeEntry[];
};

export type KnowledgeReviewInput = {
  conclusion: string;
  confidence: KnowledgeEntry['confidence'];
  evidenceIds: number[];
  intervalDays: 7 | 14 | 30 | 90;
  expectedRevision: number;
};

export type KnowledgeReviewResult = {
  entry: KnowledgeEntry;
  reviewedAt: string;
  nextReviewAt: string;
  intervalDays: number;
  reviewCount: number;
  revision: number;
};

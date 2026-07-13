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
  masteryStatus: 'EXPLORING' | 'BUILDING' | 'STABLE' | 'MASTERED';
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
  expectedRevision: number;
};

import { api } from '../../shared/api/client';
import {
  KnowledgeEntry,
  KnowledgeEntryInput,
  KnowledgeEvidence,
  KnowledgeOverview,
  KnowledgePage,
  KnowledgeReviewInput,
  KnowledgeReviewResult,
  KnowledgeTask,
  KnowledgeTopic,
  KnowledgeTopicWorkspace
} from './knowledgeTypes';

function queryString(values: Record<string, string | number | boolean | null | undefined>) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  });
  return params.toString();
}

export const knowledgeApi = {
  overview: () => api<KnowledgeOverview>('/api/knowledge/overview'),
  topics: (filters: {
    lifecycle?: string;
    mastery?: string;
    dueOnly?: boolean;
    query?: string;
    page?: number;
    size?: number;
  } = {}) => api<KnowledgePage<KnowledgeTopic>>(
    `/api/knowledge/topics?${queryString({ page: 0, size: 20, ...filters })}`
  ),
  tasks: (filters: {
    status?: string;
    topicId?: number;
    query?: string;
    page?: number;
    size?: number;
  } = {}) => api<KnowledgePage<KnowledgeTask>>(
    `/api/knowledge/tasks?${queryString({ page: 0, size: 20, ...filters })}`
  ),
  dueReviews: (page = 0, size = 20) => api<KnowledgePage<KnowledgeTopic>>(
    `/api/knowledge/reviews/due?${queryString({ page, size })}`
  ),
  taskEvidence: (taskId: number) => api<KnowledgeEvidence[]>(`/api/knowledge/tasks/${taskId}/evidence`),
  topicWorkspace: (topicId: number) => api<KnowledgeTopicWorkspace>(`/api/knowledge/topics/${topicId}`),
  reviewTopic: (topicId: number, input: KnowledgeReviewInput) =>
    api<KnowledgeReviewResult>(`/api/knowledge/topics/${topicId}/reviews`, {
      method: 'POST', body: JSON.stringify(input)
    }),
  acceptTask: (taskId: number, topicId: number, expectedRevision: number) =>
    api<KnowledgeTask>(`/api/knowledge/tasks/${taskId}/accept`, {
      method: 'POST', body: JSON.stringify({ topicId, expectedRevision })
    }),
  startTask: (taskId: number, expectedRevision: number) =>
    api<KnowledgeTask>(`/api/knowledge/tasks/${taskId}/start`, {
      method: 'POST', body: JSON.stringify({ expectedRevision })
    }),
  saveDraft: (taskId: number, input: KnowledgeEntryInput) =>
    api<KnowledgeEntry>(`/api/knowledge/tasks/${taskId}/draft`, {
      method: 'PUT', body: JSON.stringify(input)
    }),
  completeTask: (taskId: number, input: KnowledgeEntryInput) =>
    api<KnowledgeEntry>(`/api/knowledge/tasks/${taskId}/complete`, {
      method: 'POST', body: JSON.stringify(input)
    }),
  dismissTask: (taskId: number, reason: string, expectedRevision: number) =>
    api<KnowledgeTask>(`/api/knowledge/tasks/${taskId}/dismiss`, {
      method: 'POST', body: JSON.stringify({ reason, expectedRevision })
    })
};

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { KnowledgeView } from './KnowledgeView';

const overview = {
  acceptedTaskCount: 2,
  suggestedTaskCount: 5,
  dueReviewCount: 1,
  activeTopicCount: 3,
  actions: [],
  activeTopics: [],
  recentEntries: []
};

beforeEach(() => {
  window.history.replaceState({}, '', '/?section=topics&topic=11');
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const value = url === '/api/knowledge/topics/11'
      ? {
        topic: { id: 11, name: 'Agent 工程化', lifecycleStatus: 'ACTIVE', masteryStatus: 'BUILDING', revision: 1 },
        events: [], evidence: [], tasks: [], entries: []
      }
      : url.startsWith('/api/knowledge/topics')
      ? { items: [], totalCount: 0, page: 0, pageSize: 20, totalPages: 0 }
      : overview;
    return { ok: true, status: 200, text: async () => JSON.stringify(value) } as Response;
  }));
});

test('restores a valid knowledge section and selected topic from the URL', async () => {
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: 'Agent 工程化' })).toBeInTheDocument();
  expect(screen.getByRole('navigation', { name: '知识工作台' })).toBeInTheDocument();
  expect(screen.getByTestId('knowledge-view')).toHaveAttribute('data-topic-id', '11');
  expect(fetch).toHaveBeenCalledWith(
    '/api/knowledge/topics/11',
    expect.anything()
  );
});

test('rejects unknown sections and keeps navigation state in the URL', async () => {
  window.history.replaceState({}, '', '/?section=unknown');
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '今天从这里继续' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '学习队列' }));

  await waitFor(() => {
    expect(new URLSearchParams(window.location.search).get('section')).toBe('learning');
  });
});

test('restores the workbench when browser history changes', async () => {
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(await screen.findByRole('heading', { name: 'Agent 工程化' })).toBeInTheDocument();

  window.history.replaceState({}, '', '/?section=home');
  window.dispatchEvent(new PopStateEvent('popstate'));

  expect(await screen.findByRole('heading', { name: '今天从这里继续' })).toBeInTheDocument();
  expect(screen.getByTestId('knowledge-view')).not.toHaveAttribute('data-topic-id');
});

test('top-level navigation clears stale topic and task selection', async () => {
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(await screen.findByRole('heading', { name: 'Agent 工程化' })).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '学习队列' }));

  await waitFor(() => {
    const params = new URLSearchParams(window.location.search);
    expect(params.get('topic')).toBeNull();
    expect(params.get('task')).toBeNull();
    expect(screen.getByTestId('knowledge-view')).not.toHaveAttribute('data-topic-id');
  });
});

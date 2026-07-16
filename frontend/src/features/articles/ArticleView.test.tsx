import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { ArticleView } from './ArticleView';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

const firstArticle = {
  id: 1,
  title: '已有文章',
  url: 'https://example.com/old',
  sourceName: '测试来源',
  category: '市场',
  noveltyType: 'NEW',
  summary: '已有摘要'
};

const generatedArticle = {
  id: 2,
  title: '新生成的情报卡片',
  url: 'https://example.com/new-card',
  sourceName: '手动研究',
  category: '市场',
  noveltyType: 'NEW',
  summary: '生成摘要',
  insightCard: {
    oneSentenceSummary: '网页已整理成情报卡片。',
    coreEvent: '用户导入 URL。',
    importance: '提升信息整理效率。'
  }
};

const secondGeneratedArticle = {
  ...generatedArticle,
  id: 3,
  title: '第二张情报卡片',
  url: 'https://example.com/second-card'
};

beforeEach(() => {
  vi.mocked(api).mockReset();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

class ArticleTaskEventSource {
  onerror: (() => void) | null = null;
  private listener?: (event: MessageEvent) => void;
  addEventListener(_name: string, listener: (event: MessageEvent) => void) { this.listener = listener; }
  close = vi.fn();
  emit(data: unknown) { this.listener?.(new MessageEvent('progress', { data: JSON.stringify(data) })); }
}

test('updates article ingest phase from SSE and confirms completion from task API', async () => {
  const source = new ArticleTaskEventSource();
  vi.stubGlobal('EventSource', vi.fn(() => source));
  let taskReads = 0;
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) return {
      items: taskReads >= 2 ? [generatedArticle] : [], totalCount: taskReads >= 2 ? 1 : 0,
      page: 0, pageSize: 20, totalPages: 1
    };
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') {
      return { taskId: 'task-sse', status: 'QUEUED', phase: 'QUEUED' };
    }
    if (path === '/api/tasks/task-sse') {
      taskReads += 1;
      return taskReads >= 2
        ? { taskId: 'task-sse', status: 'COMPLETED', phase: 'COMPLETED', articleId: generatedArticle.id }
        : { taskId: 'task-sse', status: 'RUNNING', phase: 'FETCHING', message: '正在抓取网页' };
    }
    return {};
  });
  render(<ArticleView setView={vi.fn()} onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)} addToast={vi.fn()} />);
  await userEvent.type(await screen.findByPlaceholderText('输入文章URL...'), generatedArticle.url);
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  source.emit({ eventId: '2', taskId: 'task-sse', type: 'PHASE', status: 'RUNNING', phase: 'LLM', message: '正在生成情报卡片' });
  expect(await screen.findByText('正在生成情报卡片')).toBeInTheDocument();
  source.emit({ eventId: '3', taskId: 'task-sse', type: 'DONE', status: 'COMPLETED', phase: 'COMPLETED' });

  expect(await screen.findByText('新生成的情报卡片')).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/tasks/task-sse');
});

test('presents article workspace as a signal command center', async () => {
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path.startsWith('/api/articles/paged')) {
      return {
        items: [firstArticle],
        totalCount: 1,
        page: 0,
        pageSize: 20,
        totalPages: 1
      };
    }
    return {};
  });

  const { container } = render(
    <ArticleView setView={vi.fn()} onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)} addToast={vi.fn()} />
  );

  expect(await screen.findByRole('heading', { name: '文章情报台' })).toBeInTheDocument();
  expect(container.querySelector('.article-command-center')).toBeTruthy();
  expect(container.querySelector('.article-signal-panel')).toBeTruthy();
  expect(container.querySelector('.article-stream-panel')).toBeTruthy();
  expect(screen.getByText('1 active signals')).toBeInTheDocument();
});

test('article stylesheet keeps the command center responsive', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.article-command-center\s*{[^}]*grid-template-columns:\s*minmax\(280px,\s*360px\)\s+minmax\(0,\s*1fr\)/s);
  expect(styles).toMatch(/\.article-stream-panel\s*{[^}]*min-width:\s*0;/s);
  expect(styles).toMatch(/@media\s*\(max-width:\s*1120px\)[\s\S]*\.article-command-center[\s\S]*grid-template-columns:\s*1fr;/);
});

test('does not create an SSE channel when the page unmounts during task submission', async () => {
  let resolveSubmit!: (value: unknown) => void;
  const pendingSubmit = new Promise((resolve) => { resolveSubmit = resolve; });
  const eventSource = vi.fn();
  vi.stubGlobal('EventSource', eventSource);
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) return {
      items: [], totalCount: 0, page: 0, pageSize: 20, totalPages: 1
    };
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') return pendingSubmit;
    return {};
  });
  const rendered = render(
    <ArticleView setView={vi.fn()} onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)} addToast={vi.fn()} />
  );
  await userEvent.type(await screen.findByPlaceholderText('输入文章URL...'), 'https://example.com/slow-submit');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));
  rendered.unmount();

  resolveSubmit({ taskId: 'task-late', status: 'QUEUED', phase: 'QUEUED' });
  await pendingSubmit;
  await Promise.resolve();

  expect(eventSource).not.toHaveBeenCalled();
});

test('shows ingest progress while a pasted url is generating and highlights the new card after success', async () => {
  let listRequestCount = 0;
  let taskRequestCount = 0;
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) {
      listRequestCount += 1;
      return {
        items: listRequestCount === 1 ? [firstArticle] : [generatedArticle, firstArticle],
        totalCount: listRequestCount === 1 ? 1 : 2,
        page: 0,
        pageSize: 20,
        totalPages: 1
      };
    }
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') {
      return { taskId: 'task-1', status: 'QUEUED', phase: 'QUEUED', message: '等待开始' };
    }
    if (path === '/api/tasks/task-1') {
      taskRequestCount += 1;
      if (taskRequestCount === 1) {
        return { taskId: 'task-1', status: 'RUNNING', phase: 'LLM', message: '正在生成情报卡片' };
      }
      return {
        taskId: 'task-1',
        status: 'COMPLETED',
        phase: 'COMPLETED',
        message: '情报卡片已生成，已加入文章列表',
        articleId: generatedArticle.id,
        article: generatedArticle
      };
    }
    return {};
  });

  render(
    <ArticleView
      setView={vi.fn()}
      onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)}
      addToast={vi.fn()}
    />
  );

  await userEvent.type(await screen.findByPlaceholderText('输入文章URL...'), 'https://example.com/new-card');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(screen.getByRole('button', { name: '生成中...' })).toBeDisabled();
  expect(await screen.findByText('正在生成情报卡片')).toBeInTheDocument();
  expect(screen.getByText('抓取网页')).toBeInTheDocument();
  expect(screen.getByText('提取正文')).toBeInTheDocument();
  expect(screen.getByText('生成卡片')).toBeInTheDocument();

  expect(await screen.findByText('新生成的情报卡片')).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/tasks/task-1');
  expect(screen.getByText('AI 解读')).toBeInTheDocument();
  expect(screen.getByText('网页已整理成情报卡片。')).toBeInTheDocument();
  await waitFor(() => {
    expect(screen.getByText('新生成的情报卡片').closest('.article-card')).toHaveClass('article-card-highlight');
  });
});

test('submits the selected article category when generating an intelligence card', async () => {
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) {
      return {
        items: [],
        totalCount: 0,
        page: 0,
        pageSize: 20,
        totalPages: 1
      };
    }
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') {
      return { taskId: 'task-tech', status: 'QUEUED', phase: 'QUEUED', message: '等待开始' };
    }
    if (path === '/api/tasks/task-tech') {
      return {
        taskId: 'task-tech',
        status: 'COMPLETED',
        phase: 'COMPLETED',
        articleId: generatedArticle.id,
        article: { ...generatedArticle, category: '前沿技术' }
      };
    }
    return {};
  });

  render(
    <ArticleView
      setView={vi.fn()}
      onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)}
      addToast={vi.fn()}
    />
  );

  const categoryControl = await screen.findByLabelText('文章类型');
  await userEvent.click(within(categoryControl).getByRole('button', { name: '前沿技术' }));
  await userEvent.type(screen.getByPlaceholderText('输入文章URL...'), 'https://example.com/frontier-tech');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  await waitFor(() => {
    expect(api).toHaveBeenCalledWith('/api/articles/ingest-url', {
      method: 'POST',
      body: JSON.stringify({
        url: 'https://example.com/frontier-tech',
        sourceName: '手动研究',
        tags: '市场',
        category: '前沿技术'
      })
    });
  });
});

test('keeps the pasted url and offers retry when generation fails', async () => {
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) {
      return {
        items: [firstArticle],
        totalCount: 1,
        page: 0,
        pageSize: 20,
        totalPages: 1
      };
    }
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') {
      return { taskId: 'task-fail', status: 'QUEUED', phase: 'QUEUED', message: '等待开始' };
    }
    if (path === '/api/tasks/task-fail') {
      return {
        taskId: 'task-fail',
        status: 'FAILED',
        phase: 'FAILED',
        errorMessage: '未能读取到可用正文'
      };
    }
    return {};
  });

  render(
    <ArticleView
      setView={vi.fn()}
      onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)}
      addToast={vi.fn()}
    />
  );

  const urlInput = await screen.findByPlaceholderText('输入文章URL...');
  await userEvent.type(urlInput, 'https://example.com/fail');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(await screen.findByText('生成失败')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  expect(urlInput).toHaveValue('https://example.com/fail');
});

test('keeps generation successful when workspace refresh fails after task completion', async () => {
  let listRequestCount = 0;
  const addToast = vi.fn();
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) {
      listRequestCount += 1;
      return {
        items: listRequestCount === 1 ? [firstArticle] : [generatedArticle, firstArticle],
        totalCount: listRequestCount === 1 ? 1 : 2,
        page: 0,
        pageSize: 20,
        totalPages: 1
      };
    }
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') {
      return { taskId: 'task-refresh-fail', status: 'QUEUED', phase: 'QUEUED', message: '等待开始' };
    }
    if (path === '/api/tasks/task-refresh-fail') {
      return {
        taskId: 'task-refresh-fail',
        status: 'COMPLETED',
        phase: 'COMPLETED',
        message: '情报卡片已生成，已加入文章列表',
        articleId: generatedArticle.id,
        article: generatedArticle
      };
    }
    return {};
  });

  render(
    <ArticleView
      setView={vi.fn()}
      onWorkspaceChanged={vi.fn().mockRejectedValue(new Error('Agent Runs 刷新失败'))}
      addToast={addToast}
    />
  );

  const urlInput = await screen.findByPlaceholderText('输入文章URL...');
  await userEvent.type(urlInput, generatedArticle.url);
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(await screen.findByText('生成完成')).toBeInTheDocument();
  expect(screen.getByText('情报卡片已生成，已加入文章列表')).toBeInTheDocument();
  expect(screen.queryByText('生成失败')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '重试' })).not.toBeInTheDocument();
  expect(screen.getByText('新生成的情报卡片')).toBeInTheDocument();
});

test('does not treat a polling timeout as successful without a completed task confirmation', async () => {
  let listRequestCount = 0;
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) {
      listRequestCount += 1;
      return {
        items: listRequestCount === 1 ? [firstArticle] : [generatedArticle, firstArticle],
        totalCount: listRequestCount === 1 ? 1 : 2,
        page: 0,
        pageSize: 20,
        totalPages: 1
      };
    }
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') {
      return { taskId: 'task-slow', status: 'QUEUED', phase: 'QUEUED', message: '等待开始' };
    }
    if (path === '/api/tasks/task-slow') {
      return { taskId: 'task-slow', status: 'RUNNING', phase: 'LLM', message: '正在生成情报卡片' };
    }
    return {};
  });

  render(
    <ArticleView
      setView={vi.fn()}
      onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)}
      addToast={vi.fn()}
    />
  );

  const urlInput = await screen.findByPlaceholderText('输入文章URL...');
  const form = urlInput.closest('form');
  expect(form).not.toBeNull();
  fireEvent.change(urlInput, { target: { value: generatedArticle.url } });
  vi.useFakeTimers();
  fireEvent.submit(form!);

  await act(async () => {
    await vi.advanceTimersByTimeAsync(60 * 800 + 1);
  });
  await act(async () => {
    await Promise.resolve();
  });

  expect(screen.getByText('生成失败')).toBeInTheDocument();
  expect(screen.getByText('生成任务超时，请稍后重试')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  expect(screen.queryByText('新生成的情报卡片')).not.toBeInTheDocument();
});

test('keeps the latest generated card highlighted when generations finish close together', async () => {
  let ingestCount = 0;
  let listRequestCount = 0;
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.startsWith('/api/articles/paged')) {
      listRequestCount += 1;
      return {
        items: [
          ...(listRequestCount >= 3 ? [secondGeneratedArticle] : []),
          ...(listRequestCount >= 2 ? [generatedArticle] : []),
          firstArticle
        ],
        totalCount: listRequestCount,
        page: 0,
        pageSize: 20,
        totalPages: 1
      };
    }
    if (path === '/api/articles/ingest-url' && options?.method === 'POST') {
      ingestCount += 1;
      return { taskId: ingestCount === 1 ? 'task-1' : 'task-2', status: 'QUEUED', phase: 'QUEUED' };
    }
    if (path === '/api/tasks/task-1') {
      return {
        taskId: 'task-1',
        status: 'COMPLETED',
        phase: 'COMPLETED',
        articleId: generatedArticle.id,
        article: generatedArticle
      };
    }
    if (path === '/api/tasks/task-2') {
      return {
        taskId: 'task-2',
        status: 'COMPLETED',
        phase: 'COMPLETED',
        articleId: secondGeneratedArticle.id,
        article: secondGeneratedArticle
      };
    }
    return {};
  });

  render(
    <ArticleView
      setView={vi.fn()}
      onWorkspaceChanged={vi.fn().mockResolvedValue(undefined)}
      addToast={vi.fn()}
    />
  );

  const urlInput = await screen.findByPlaceholderText('输入文章URL...');
  const form = urlInput.closest('form');
  expect(form).not.toBeNull();
  vi.useFakeTimers();

  fireEvent.change(urlInput, { target: { value: generatedArticle.url } });
  await act(async () => {
    fireEvent.submit(form!);
  });
  expect(screen.getByText('新生成的情报卡片')).toBeInTheDocument();

  act(() => {
    vi.advanceTimersByTime(4000);
  });

  fireEvent.change(screen.getByPlaceholderText('输入文章URL...'), {
    target: { value: secondGeneratedArticle.url }
  });
  await act(async () => {
    fireEvent.submit(form!);
  });
  expect(screen.getByText('第二张情报卡片')).toBeInTheDocument();

  act(() => {
    vi.advanceTimersByTime(1000);
  });

  expect(screen.getByText('第二张情报卡片').closest('.article-card')).toHaveClass('article-card-highlight');
});

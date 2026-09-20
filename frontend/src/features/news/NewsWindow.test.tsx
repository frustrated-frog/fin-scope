import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { LiveNewsPanel } from './LiveNewsPanel';
import type { NewsPage, NewsReport } from './newsWindowTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
let onRevision: () => void;
vi.mock('../../shared/api/useViewRevision', () => ({
  useViewRevision: (_: unknown, callback: () => void) => {
    onRevision = callback;
  },
}));
const report: NewsReport = {
  id: 'CLS:1',
  title: '开普检测中标334万元合同',
  content: '公司公告中标设备采购项目。',
  providerCode: 'CLS',
  sourceName: '财联社',
  kind: 'FLASH',
  publishedAt: '2026-09-20T15:34:00',
  firstSeenAt: '2026-09-21T08:00:00',
  lastSeenAt: '2026-09-21T08:00:00',
  contentVersion: 2,
  readVersion: 1,
  unread: true,
  historicalBackfill: true,
  manuallyReviewed: false,
};
const page: NewsPage = {
  items: [report],
  total: 251,
  asOfSequence: 251,
  asOfTime: '2026-09-21T08:00:00',
  page: 0,
  size: 50,
  sources: ['财联社'],
  categoryCounts: { ALL: 251 },
};
let latest = page;
beforeEach(() => {
  latest = page;
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/news/window/filters') {
      return [];
    }
    if (path === '/api/news/categories') {
      return [{ code: 'COMPANY', name: '公司动态' }];
    }
    if (path.startsWith('/api/news/window?')) {
      return latest;
    }
    if (path.startsWith('/api/news/window/detail')) {
      return {
        report,
        reactions: [],
        versions: [
          {
            reportId: report.id,
            version: 2,
            title: report.title,
            content: report.content,
            detectedAt: report.lastSeenAt,
          },
          {
            reportId: report.id,
            version: 1,
            title: report.title,
            content: '金额待披露',
            detectedAt: report.firstSeenAt,
          },
        ],
      };
    }
    return true;
  });
});
function setup() {
  return render(<LiveNewsPanel setMessage={vi.fn()} addToast={vi.fn()} />);
}

test('queries the server beyond the rendered page and keeps the pagination watermark', async () => {
  setup();
  await screen.findByText('共 251 条 · 第 1 / 6 页');
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  await waitFor(() =>
    expect(api).toHaveBeenCalledWith(expect.stringMatching(/page=1.*asOfSequence=251.*asOfTime=2026/)),
  );
  fireEvent.change(screen.getByRole('searchbox'), {
    target: { value: '芯片' },
  });
  fireEvent.click(screen.getByRole('button', { name: '检索' }));
  await waitFor(() =>
    expect(api).toHaveBeenCalledWith(expect.stringMatching(/query=%E8%8A%AF%E7%89%87.*page=0.*asOfSequence=0/)),
  );
});

test('defers background changes until the reader accepts them', async () => {
  setup();
  await screen.findByRole('heading', { name: report.title });
  latest = { ...page, items: [{ ...report, id: 'CLS:2', title: '新公告' }] };
  await act(async () => {
    onRevision();
  });
  expect(screen.getByRole('heading', { name: report.title })).toBeInTheDocument();
  expect(screen.queryByText('新公告')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '有新报道或内容更新，点击查看' }));
  expect(await screen.findByRole('heading', { name: '新公告' })).toBeInTheDocument();
});

test('reads the displayed version, shows source revisions and does not invent stock associations', async () => {
  setup();
  fireEvent.click(await screen.findByRole('button', { name: /阅读资讯：开普检测中标/ }));
  await waitFor(() =>
    expect(api).toHaveBeenCalledWith('/api/news/window/read?id=CLS%3A1&version=2', { method: 'POST' }),
  );
  fireEvent.click(screen.getByRole('button', { name: /内容变化/ }));
  expect((await screen.findAllByText('金额待披露')).length).toBeGreaterThan(0);
  fireEvent.click(screen.getByRole('button', { name: '股票反应' }));
  expect(screen.getByText(/尚未建立可靠的股票关联/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '关闭新闻详情' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

test('failed detail retrieval does not mark the report read', async () => {
  const original = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation((path) =>
    path.startsWith('/api/news/window/detail') ? Promise.reject(new Error('详情暂不可用')) : original(path),
  );
  setup();
  fireEvent.click(await screen.findByRole('button', { name: /阅读资讯：开普检测中标/ }));
  expect(await screen.findByRole('alert')).toHaveTextContent('详情暂不可用');
  expect(vi.mocked(api).mock.calls.some(([path]) => path.startsWith('/api/news/window/read'))).toBe(false);
});

test('allows manual classification of an unclassified report and keeps content after a failure', async () => {
  const original = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation((path) =>
    path.startsWith('/api/news/window/review') ? Promise.reject(new Error('保存暂不可用')) : original(path),
  );
  const toast = vi.fn();
  render(<LiveNewsPanel setMessage={vi.fn()} addToast={toast} />);
  fireEvent.click(await screen.findByRole('button', { name: /阅读资讯：开普检测中标/ }));
  await screen.findByText('分类依据');
  fireEvent.change(screen.getByLabelText('调整分类'), {
    target: { value: 'COMPANY' },
  });
  fireEvent.click(screen.getByRole('button', { name: '保存分类' }));
  await waitFor(() => expect(toast).toHaveBeenCalledWith('保存暂不可用', 'error'));
  expect(screen.getByRole('dialog')).toHaveTextContent(report.content);
});

test('saves reusable filters with current applied conditions', async () => {
  setup();
  await screen.findByRole('heading', { name: report.title });
  fireEvent.click(screen.getByText('保存当前筛选'));
  fireEvent.change(screen.getByLabelText('筛选名称'), {
    target: { value: '合同跟踪' },
  });
  const original = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation((path, options) =>
    options?.method === 'POST' && path.endsWith('/filters')
      ? Promise.resolve({ id: 'f1', name: '合同跟踪', query: {} })
      : original(path, options),
  );
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  expect(await screen.findByRole('button', { name: '合同跟踪' })).toBeInTheDocument();
});

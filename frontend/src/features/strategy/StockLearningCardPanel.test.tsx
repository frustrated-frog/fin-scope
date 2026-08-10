import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StockLearningCardPanel } from './StockLearningCardPanel';

test('restores persisted stock cards and opens the latest six-dimension detail', async () => {
  const requests: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input); requests.push(path);
    if (path === '/api/stock-learning-cards') return apiResponse([{
      code: '603618', name: '杭电股份', status: 'DEGRADED', stage: 'COMPLETED',
      summary: '已生成5个维度，1个维度需要重试', completedDimensions: 5, totalDimensions: 6,
      completedAt: '2026-08-09T20:51:59'
    }]);
    if (path === '/api/stock-learning-cards/603618') return apiResponse({
      card: { code: '603618', name: '杭电股份' },
      latestRun: {
        id: 3, status: 'DEGRADED', stage: 'COMPLETED', summary: '已生成5个维度，1个维度需要重试',
        claims: [{
          dimensionCode: 'SPACE', status: 'READY', headline: '空间判断', ratingLabel: '成长空间', ratingValue: 'MEDIUM_HIGH', confidence: 'MEDIUM', sortOrder: 1,
          sections: [
            { key: 'growth_drivers', title: '增量引擎', content: '高端产品提供新增量', evidenceRefs: ['E1'], verificationStatus: 'SUPPORTED', sortOrder: 1 },
            { key: 'milestones', title: '兑现路径', content: '等待客户认证与产能释放', evidenceRefs: [], verificationStatus: 'UNVERIFIED', sortOrder: 2 }
          ]
        }],
        evidence: [], watchItems: []
      }
    });
    return apiResponse({});
  }));
  const user = userEvent.setup();

  render(<StockLearningCardPanel addToast={vi.fn()} setMessage={vi.fn()} />);

  const card = await screen.findByRole('button', { name: '查看杭电股份 603618 学习卡' });
  expect(screen.queryByText('空间判断')).not.toBeInTheDocument();
  await user.click(card);
  expect(await screen.findByText('空间判断')).toBeInTheDocument();
  expect(screen.getByText('成长空间 · 中高')).toBeInTheDocument();
  expect(screen.getByText('增量引擎')).toBeInTheDocument();
  expect(screen.getByText('已证实')).toBeInTheDocument();
  expect(screen.getByText('兑现路径')).toBeInTheDocument();
  expect(screen.queryByText('为什么')).not.toBeInTheDocument();
  expect(screen.queryByText('反方')).not.toBeInTheDocument();
  expect(screen.queryByText('未知')).not.toBeInTheDocument();
  expect(requests).toContain('/api/stock-learning-cards/603618');
  await user.click(screen.getByRole('button', { name: '返回全部股票' }));
  expect(await screen.findByRole('button', { name: '查看杭电股份 603618 学习卡' })).toBeInTheDocument();
  expect(screen.queryByText('空间判断')).not.toBeInTheDocument();
});

test('lets the learner select a stock and starts the agent without requiring a thesis', async () => {
  const requests: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input); requests.push(`${init?.method ?? 'GET'} ${path}`);
    if (path === '/api/stock-learning-cards' && !init?.method) return apiResponse([]);
    if (path === '/api/stock-learning-cards/600519' && !init?.method) {
      return apiResponse({ card: { code: '600519', name: '贵州茅台' }, latestRun: null });
    }
    if (path === '/api/stock-learning-cards/600519/runs' && init?.method === 'POST') {
      return apiResponse({ id: 5, status: 'RUNNING', stage: 'COLLECTING_EVIDENCE', claims: [], watchItems: [] });
    }
    return apiResponse({});
  }));
  const user = userEvent.setup();
  render(<StockLearningCardPanel addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.type(screen.getByLabelText('股票代码'), '600519');
  await user.click(screen.getByRole('button', { name: '生成学习卡' }));

  expect(requests).toContain('POST /api/stock-learning-cards/600519/runs');
  expect(await screen.findByText('正在按六个学习维度收集公开资料…')).toBeInTheDocument();
  expect(screen.queryByText('买入条件')).not.toBeInTheDocument();
});

test('shows a failed dimension beside successful cards and explains that it can be retried', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input) === '/api/stock-learning-cards' && !init?.method) return apiResponse([]);
    if (init?.method === 'POST') return apiResponse({
      id: 6,
      status: 'DEGRADED',
      stage: 'COMPLETED',
      userMessage: '部分学习维度未能生成，其他结果已保留，可以重新生成补全',
      retryable: true,
      summary: '已生成5个维度，1个维度需要重试',
      claims: [
        { dimensionCode: 'SPACE', status: 'READY', headline: '空间判断', ratingLabel: '成长空间', ratingValue: 'MEDIUM_HIGH', sections: [{ key: 'growth_drivers', title: '增量引擎', content: '公开资料显示存在新增量', evidenceRefs: ['E1'], verificationStatus: 'SUPPORTED', sortOrder: 1 }], confidence: 'MEDIUM', sortOrder: 1 },
        { dimensionCode: 'COMPETITION', status: 'FAILED', failureMessage: '该维度生成失败，可以重新生成学习卡', headline: '暂未形成判断', ratingLabel: '竞争位置', ratingValue: 'UNKNOWN', sections: [{ key: 'industry_structure', title: '行业格局', content: '该维度当前保持未知', evidenceRefs: [], verificationStatus: 'UNVERIFIED', sortOrder: 1 }], confidence: 'LOW', sortOrder: 3 }
      ],
      evidence: [
        { dimensionCode: 'SPACE', evidenceCode: 'E1', title: '公司年度报告', url: 'https://example.com/report', source: 'example.com', sortOrder: 1 }
      ],
      watchItems: []
    });
    return apiResponse({ card: { code: '603618', name: '杭电股份' }, latestRun: null });
  }));
  const user = userEvent.setup();
  render(<StockLearningCardPanel addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.type(screen.getByLabelText('股票代码'), '603618');
  await user.click(screen.getByRole('button', { name: '生成学习卡' }));

  expect(await screen.findByText('部分学习维度未能生成，其他结果已保留，可以重新生成补全')).toBeInTheDocument();
  expect(screen.getByText('该维度生成失败，可以重新生成学习卡')).toBeInTheDocument();
  expect(screen.getByText('空间判断')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '[E1] 公司年度报告' })).toHaveAttribute('href', 'https://example.com/report');
  expect(screen.queryByText('研究主题')).not.toBeInTheDocument();
});

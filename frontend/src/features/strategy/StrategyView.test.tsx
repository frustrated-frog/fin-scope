import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StrategyView } from './StrategyView';

test('shows empty workspace and creates the first fund', async () => {
  const requests: Array<{ url: string; method: string }> = [];
  const overview = { holdings: [], targetWeight: 0, currentWeight: 0 };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input); const method = init?.method ?? 'GET'; requests.push({ url, method });
    if (url.startsWith('/api/quant/')) return apiResponse([], { status: 200 });
    if (url === '/api/strategy/overview' && method === 'GET') return apiResponse(overview, { status: 200 });
    if (url === '/api/strategy/playbooks') return apiResponse([], { status: 200 });
    if (url === '/api/strategy/stock-theses') return apiResponse([], { status: 200 });
    if (url === '/api/strategy/reviews') return apiResponse([], { status: 200 });
    if (url === '/api/strategy/holdings' && method === 'POST') {
      overview.holdings = [{ id: 1, code: '020608', name: '测试基金', type: 'FUND', role: 'CORE', targetWeight: 60, currentWeight: 0, revision: 0 }] as never[];
      overview.targetWeight = 60;
      return apiResponse(overview.holdings[0], { status: 200 });
    }
    return apiResponse({}, { status: 200 });
  }));
  const user = userEvent.setup();
  render(<StrategyView addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: /长期投资工作台/ }));
  expect(await screen.findByText('还没有组合资产')).toBeInTheDocument();
  await user.click(screen.getAllByRole('button', { name: '添加资产' })[0]);
  await user.type(screen.getByLabelText('标的代码'), '020608');
  await user.type(screen.getByLabelText('目标权重'), '60');
  await user.click(screen.getByRole('button', { name: '保存资产' }));
  expect(requests).toContainEqual({ url: '/api/strategy/holdings', method: 'POST' });
  expect(await screen.findByText('测试基金')).toBeInTheDocument();
});

test('shows a database-backed author strategy and opens its sourced rules', async () => {
  const code = 'STOCK_QUALITY_TREND_CHEN_XIAO_2020';
  const playbook = {
    id: 9, code, title: '质量趋势中长线', scope: '股票', summary: '基本面与趋势结合',
    cadence: '周线观察，财报期复核', riskBoundary: '不抄底、不逆势补仓',
    author: '陈潇', sourceTitle: '《中长线股票策略基础》', sourceType: 'BOOK',
    sourceRef: 'local-pdf:chen-xiao', sourcePublishedAt: '2020',
    validationStatus: 'UNVALIDATED', status: 'RESEARCHING', revision: 0
  };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.startsWith('/api/quant/')) return apiResponse([], { status: 200 });
    if (url === '/api/strategy/overview') return apiResponse({ holdings: [], targetWeight: 0, currentWeight: 0 }, { status: 200 });
    if (url === '/api/strategy/playbooks') return apiResponse([playbook], { status: 200 });
    if (url === `/api/strategy/playbooks/${code}`) return apiResponse({
      ...playbook,
      rules: [
        { id: 1, sectionCode: 'FUNDAMENTAL', sectionTitle: '基本面筛选', ruleType: 'FILTER', ruleText: '扣非盈利应具备持续性，并与经营现金流相互印证。', testability: 'CANDIDATE_RULE', sourcePage: 10, sortOrder: 1 },
        { id: 2, sectionCode: 'TREND', sectionTitle: '趋势过滤', ruleType: 'FILTER', ruleText: '仅在中长期趋势向上时考虑买入。', testability: 'CANDIDATE_RULE', sourcePage: 34, sortOrder: 2 }
      ]
    }, { status: 200 });
    if (url === '/api/strategy/stock-theses' || url === '/api/strategy/reviews') return apiResponse([], { status: 200 });
    return apiResponse({}, { status: 200 });
  }));

  const user = userEvent.setup();
  render(<StrategyView addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: /长期投资工作台/ }));
  await user.click(await screen.findByRole('tab', { name: '策略库' }));

  expect(await screen.findByText('质量趋势中长线')).toBeInTheDocument();
  expect(screen.getByText('陈潇 · 《中长线股票策略基础》')).toBeInTheDocument();
  expect(screen.getByText('尚未验证')).toBeInTheDocument();

  const status = screen.getByText('研究中');
  expect(status).toHaveClass('strategy-playbook-status');
  expect(status.closest('header')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '查看详情' })).toHaveClass('strategy-card-detail');
  expect(screen.getByRole('button', { name: '开始使用' })).toHaveClass('strategy-card-use');
  expect(screen.getByRole('button', { name: '暂停' })).toHaveClass('strategy-card-state');

  await user.click(screen.getByRole('button', { name: '查看详情' }));
  const dialog = await screen.findByRole('dialog', { name: '质量趋势中长线' });
  const detailOverlay = dialog.closest('.strategy-detail-overlay');
  expect(detailOverlay).toBeInTheDocument();
  expect(detailOverlay?.parentElement).toBe(document.body);
  expect(screen.getByRole('heading', { name: '基本面筛选' })).toBeInTheDocument();
  expect(screen.getByText('第 10 页')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '趋势过滤' })).toBeInTheDocument();

  const closeButton = screen.getByRole('button', { name: '关闭策略详情' });
  expect(closeButton).toHaveClass('strategy-detail-close');
  expect(closeButton).toHaveTextContent('×');
  await user.click(closeButton);
  expect(screen.queryByRole('dialog', { name: '质量趋势中长线' })).not.toBeInTheDocument();
});

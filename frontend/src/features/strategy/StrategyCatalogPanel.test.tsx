import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StrategyCatalogPanel } from './StrategyCatalogPanel';

const datasets = [
  { id: 3, name: 'A股真实研究集', market: 'A_SHARE', dataKind: 'REAL' as const, status: 'READY' },
  { id: 4, name: '虚拟学习样本', market: 'A_SHARE', dataKind: 'LEARNING_SAMPLE' as const, status: 'READY' }
];

const cards = [
  {
    candidateId: 7, title: '质量价值策略', datasetId: 3, datasetName: 'A股真实研究集',
    strategyVersionId: 21, experimentId: 31, experimentStatus: 'SUCCEEDED',
    evidenceLevel: 'HISTORICAL_EVIDENCE', shelf: 'APPLICATION_CANDIDATE', evidenceScore: 86,
    evidenceSummary: '本地历史证据相对完整，可进入影子组合继续观察',
    earningLogic: '利用“ROE、BP”在股票之间形成的相对差异',
    rationale: '规则只使用当时可获得的数据，对候选股票排序并低频等权持有',
    suitableRegime: '更适合流动性正常、横截面差异能够持续体现的市场阶段',
    invalidationRisk: '因子拥挤、市场风格切换或交易成本上升可能使策略失效',
    mappedFactors: ['ROE', 'BP'], adaptationNote: '使用披露时点质量与价值因子形成 A 股多头版本',
    paperUrl: 'https://example.com/paper', implementationUrl: 'https://example.com/code',
    metrics: { annualizedReturn: .16, excessReturn: .07, maxDrawdown: .21, sharpeRatio: 1.08,
      calmarRatio: .76, turnover: 2.4, tradeCount: 48, yearCount: 4, positiveExcessYearRatio: .75 },
    dimensions: [
      { code: 'COVERAGE', label: '样本覆盖', score: 20, maxScore: 20, explanation: '4 个年度 · 48 笔成交' },
      { code: 'BENCHMARK', label: '基准比较', score: 25, maxScore: 25, explanation: '年化收益与等权基准进行同口径比较' }
    ],
    annualEvidence: [{ year: 2025, portfolioReturn: .15, benchmarkReturn: .08, excessReturn: .07, maxDrawdown: .18 }],
    limitations: ['本地历史回测不等同于实盘或未来收益证明']
  },
  {
    candidateId: 8, title: '低波动策略', evidenceLevel: 'HISTORICAL_EVIDENCE', shelf: 'OBSERVATION',
    evidenceScore: 62, evidenceSummary: '已有本地历史证据，但仍需观察稳定性与风险', mappedFactors: ['VOLATILITY_20D'],
    dimensions: [], annualEvidence: [], limitations: [], earningLogic: '利用低波动差异', rationale: '低频排序',
    suitableRegime: '震荡市场', invalidationRisk: '风格切换'
  },
  {
    candidateId: 9, title: '短期反转策略', evidenceLevel: 'LEARNING_CASE', shelf: 'LEARNING_CASE',
    evidenceScore: 38, evidenceSummary: '历史实验已完成，但综合证据偏弱，保留为学习案例', mappedFactors: ['REVERSAL_5D'],
    dimensions: [], annualEvidence: [], limitations: ['交易成本吞噬收益'], earningLogic: '利用短期反转',
    rationale: '低频排序', suitableRegime: '过度反应阶段', invalidationRisk: '趋势延续'
  }
];

test('presents evidence staircase, three shelves, and a beginner-first dossier', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path.startsWith('/api/quant/academy/cards')) {
      return apiResponse(cards);
    }
    if (path === '/api/quant/catalog/source') {
      return apiResponse({ code: 'AWESOME_SYSTEMATIC_TRADING', commitSha: 'abc123456789', status: 'READY', lastSyncedAt: '2026-08-19T09:00:00' });
    }
    return apiResponse([]);
  }));

  render(<StrategyCatalogPanel datasets={datasets} addToast={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '自动策略学院' })).toBeInTheDocument();
  expect(screen.getByText('公开研究复现')).toBeInTheDocument();
  expect(screen.getByText('本地历史验证')).toBeInTheDocument();
  expect(screen.getByText('前向观察')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '应用候选' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '验证队列' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '当前观察' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '学习案例' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /质量价值策略/ })).toBeInTheDocument();
  expect(screen.getByText('它赚的是什么钱？')).toBeInTheDocument();
  expect(screen.getByText('利用“ROE、BP”在股票之间形成的相对差异')).toBeInTheDocument();
  expect(screen.getAllByText('86')).toHaveLength(2);
  expect(screen.getByRole('table', { name: '逐年历史证据' })).toBeInTheDocument();
  expect(screen.getByText('2025')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '查看公开研究' })).toHaveAttribute('href', 'https://example.com/paper');
});

test('builds a bounded academy only from the selected real dataset', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    requests.push({ path, init });
    if (path.startsWith('/api/quant/academy/cards')) {
      return apiResponse([]);
    }
    if (path === '/api/quant/catalog/source') {
      return apiResponse({ code: 'AWESOME_SYSTEMATIC_TRADING', commitSha: 'abc123456789', status: 'READY', lastSyncedAt: '2026-08-19T09:00:00' });
    }
    if (path === '/api/quant/academy/build') {
      return apiResponse({ scannedCount: 6, draftCreatedCount: 5, versionConfirmedCount: 5,
        experimentStartedCount: 5, reusedCount: 1, failedCount: 0, items: [] });
    }
    return apiResponse([]);
  }));
  const addToast = vi.fn();
  const user = userEvent.setup();

  render(<StrategyCatalogPanel datasets={datasets} addToast={addToast} />);

  const build = await screen.findByRole('button', { name: '自动构建本期学院' });
  expect(screen.queryByRole('option', { name: '虚拟学习样本' })).not.toBeInTheDocument();
  expect(requests.some(request => request.path === '/api/quant/academy/cards?datasetId=3')).toBe(true);
  await user.click(build);

  await waitFor(() => expect(requests.some(request => request.path === '/api/quant/academy/build'
    && request.init?.method === 'POST' && request.init.body === JSON.stringify({ datasetId: 3 }))).toBe(true));
  expect(addToast).toHaveBeenCalledWith('已启动 5 个历史验证，复用 1 个已有结果', 'success');
});

test('keeps source recovery available when the strategy directory has not been synchronized', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    if (String(input).startsWith('/api/quant/academy/cards')) {
      return apiResponse([]);
    }
    return new Response(JSON.stringify({ code: 'RESOURCE_NOT_FOUND', message: '策略素材库尚未同步' }), {
      status: 404, headers: { 'Content-Type': 'application/json' }
    });
  }));

  render(<StrategyCatalogPanel datasets={datasets} addToast={vi.fn()} />);

  expect(await screen.findByText('先同步公开策略目录')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '同步公开目录' })).toBeInTheDocument();
});

test('keeps the newest dataset cards when an older request finishes late', async () => {
  let resolveOlder: ((response: Response) => void) | undefined;
  const olderResponse = new Promise<Response>(resolve => {
    resolveOlder = resolve;
  });
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path === '/api/quant/academy/cards?datasetId=3') {
      return olderResponse;
    }
    if (path === '/api/quant/academy/cards?datasetId=5') {
      return apiResponse([{ ...cards[0], candidateId: 15, title: '数据集 B 策略', datasetId: 5, datasetName: '数据集 B' }]);
    }
    if (path === '/api/quant/catalog/source') {
      return apiResponse({ code: 'AWESOME_SYSTEMATIC_TRADING', commitSha: 'abc123456789', status: 'READY' });
    }
    return apiResponse([]);
  }));
  const user = userEvent.setup();

  render(<StrategyCatalogPanel datasets={[datasets[0],
    { id: 5, name: '数据集 B', market: 'A_SHARE', dataKind: 'REAL', status: 'READY' }]} addToast={vi.fn()} />);
  await user.selectOptions(screen.getByRole('combobox', { name: '验证数据' }), '5');

  expect(await screen.findByRole('button', { name: /数据集 B 策略/ })).toBeInTheDocument();
  resolveOlder?.(apiResponse([{ ...cards[0], title: '迟到的数据集 A 策略' }]));

  await waitFor(() => expect(screen.queryByRole('button', { name: /迟到的数据集 A 策略/ })).not.toBeInTheDocument());
  expect(screen.getByRole('button', { name: /数据集 B 策略/ })).toBeInTheDocument();
});

test('locks the dataset while an academy build is in flight', async () => {
  let resolveBuild: ((response: Response) => void) | undefined;
  const buildResponse = new Promise<Response>(resolve => {
    resolveBuild = resolve;
  });
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path === '/api/quant/academy/build') {
      return buildResponse;
    }
    if (path.startsWith('/api/quant/academy/cards')) {
      return apiResponse([]);
    }
    if (path === '/api/quant/catalog/source') {
      return apiResponse({ code: 'AWESOME_SYSTEMATIC_TRADING', commitSha: 'abc123456789', status: 'READY' });
    }
    return apiResponse([]);
  }));
  const user = userEvent.setup();

  render(<StrategyCatalogPanel datasets={datasets} addToast={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '自动构建本期学院' }));

  expect(screen.getByRole('combobox', { name: '验证数据' })).toBeDisabled();
  resolveBuild?.(apiResponse({ scannedCount: 0, draftCreatedCount: 0, versionConfirmedCount: 0,
    experimentStartedCount: 0, reusedCount: 0, failedCount: 0, items: [] }));
  await waitFor(() => expect(screen.getByRole('combobox', { name: '验证数据' })).not.toBeDisabled());
});

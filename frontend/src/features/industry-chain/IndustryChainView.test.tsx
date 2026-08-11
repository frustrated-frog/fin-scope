import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { IndustryChainView } from './IndustryChainView';
import type { IndustryChainGraph, IndustryChainWorkspace } from './industryChainTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const graph: IndustryChainGraph = {
  chainId: 7, revisionId: 11, name: 'AI算力', summary: '算力从芯片流向服务器与数据中心。',
  limitations: '具体供销关系以公司公告为准。', schemaVersion: 'INDUSTRY_CHAIN_V1',
  nodes: [
    { nodeKey: 'stage:chip', type: 'STAGE', name: '算力芯片', description: '计算核心', stageOrder: 1, confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'stage:server', type: 'STAGE', name: '服务器', description: '系统集成', stageOrder: 2, confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'stage:dc', type: 'STAGE', name: '数据中心', description: '算力交付', stageOrder: 3, confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'product:gpu', type: 'PRODUCT', name: 'AI芯片', description: 'GPU 与 ASIC', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'company:nvidia', type: 'COMPANY', name: '英伟达', description: '代表公司', stockCode: 'NVDA.O', confidence: 'HIGH', evidenceRefs: ['E1'] }
  ],
  edges: [
    { edgeKey: 'e1', sourceKey: 'stage:chip', targetKey: 'stage:server', type: 'FLOWS_TO', nature: 'INDUSTRY_LOGIC', description: '进入整机', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e2', sourceKey: 'stage:server', targetKey: 'stage:dc', type: 'FLOWS_TO', nature: 'INDUSTRY_LOGIC', description: '部署交付', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e3', sourceKey: 'product:gpu', targetKey: 'stage:chip', type: 'BELONGS_TO_STAGE', nature: 'INDUSTRY_LOGIC', description: '属于上游', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e4', sourceKey: 'company:nvidia', targetKey: 'product:gpu', type: 'PARTICIPATES_IN', nature: 'DISCLOSED', description: '参与 AI 芯片', confidence: 'HIGH', evidenceRefs: ['E1'] }
  ],
  evidence: [{ evidenceCode: 'E1', title: 'AI 算力产业白皮书', url: 'https://example.com/report', source: 'example.com', sourceTier: 'T2', excerpt: '产业链证据' }]
};

const workspace: IndustryChainWorkspace = {
  chain: { id: 7, name: 'AI算力', normalizedName: 'ai算力', summary: graph.summary },
  revision: { id: 11, chainId: 7, status: 'READY', stage: 'COMPLETED', message: '产业链图谱已更新' },
  graph
};

beforeEach(() => {
  vi.mocked(api).mockReset();
});

test('shows research prompts and creates a suggested industry chain', async () => {
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === '/api/industry-chains' && !options?.method) return [];
    if (path === '/api/industry-chains' && options?.method === 'POST') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: 'AI 算力' }));

  expect(api).toHaveBeenCalledWith('/api/industry-chains', expect.objectContaining({ method: 'POST' }));
  expect(await screen.findByRole('region', { name: 'AI算力产业链图谱' })).toBeInTheDocument();
  expect(screen.getByText('AI 算力产业白皮书')).toBeInTheDocument();
});

test('presents industry chains as a status-aware research library', async () => {
  const readyChain = { ...workspace.chain, currentRevisionId: 11 };
  const pendingChain = { id: 8, name: '半导体', normalizedName: '半导体' };
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [readyChain, pendingChain];
    if (path === '/api/industry-chains/7') return { ...workspace, chain: readyChain };
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: 'AI算力 产业链' }));

  expect(screen.getByText('2 个图谱')).toBeInTheDocument();
  expect(screen.getByText('我的图谱')).toBeInTheDocument();
  expect(screen.getByText('可查看链上动态')).toBeInTheDocument();
  expect(screen.getByText('等待首次生成')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'AI算力 产业链' })).toHaveAttribute('aria-current', 'page');
});

test('selects graph nodes, searches and switches focus mode', async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));
  await userEvent.click(await screen.findByRole('button', { name: /算力芯片/ }));
  expect(screen.getByRole('complementary', { name: '图谱详情' })).toHaveTextContent('计算核心');

  fireEvent.change(screen.getByRole('searchbox', { name: '搜索图谱节点' }), { target: { value: '数据中心' } });
  await waitFor(() => expect(screen.getByRole('button', { name: /数据中心/ })).toHaveAttribute('data-search-match', 'true'));
  await userEvent.click(screen.getByRole('button', { name: '聚焦链路' }));
  expect(screen.getByRole('button', { name: '查看全图' })).toBeInTheDocument();
  expect(screen.getByLabelText('移动端产业链阅读器')).toBeInTheDocument();
});

test('renders dedicated edge routes and expands a company below its product', async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  const { container } = render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));
  await userEvent.click(screen.getByRole('button', { name: '展开 AI芯片 的 1 家公司' }));

  expect(container.querySelector('.ic-edge--stage-flow')).toBeInTheDocument();
  expect(container.querySelector('.ic-edge--stage-membership')).toBeInTheDocument();
  expect(container.querySelector('.ic-edge--company-link')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '英伟达 · 代表公司' })).toBeInTheDocument();
});

test('shows chain dynamics, changes time window and opens the reused News Wire event', async () => {
  const onOpenNewsEvent = vi.fn();
  const feed = {
    chainId: 7, hours: 168, refreshedAt: '2026-08-11T12:00:00',
    nodeEventCounts: { 'product:gpu': 1, 'stage:chip': 1 },
    events: [{
      eventId: 91, title: 'AI 芯片订单增长', summary: '云厂商增加 GPU 采购', categoryCode: 'INDUSTRY',
      status: 'ACTIVE', firstSeenAt: '2026-08-11T08:00:00', lastSeenAt: '2026-08-11T11:00:00',
      sourceCount: 3, signalCount: 5, hotspotScore: 82,
      impact: {
        radarEventId: 91, directNodeKey: 'product:gpu', direction: 'POSITIVE', mechanism: 'ORDER',
        horizon: 'SHORT', confidence: 'HIGH', impactSummary: '订单增长直接作用于 AI 芯片，并向服务器传导。',
        analysisVersion: 'RULES_V1', pathNodeKeys: ['product:gpu', 'stage:chip', 'stage:server']
      }
    }]
  };
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    if (path.startsWith('/api/industry-chains/7/events?hours=')) return { ...feed, hours: Number(path.split('=').pop()) };
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView onOpenNewsEvent={onOpenNewsEvent} />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));
  await userEvent.click(screen.getByRole('button', { name: '链上动态' }));
  expect(await screen.findByText('AI 芯片订单增长')).toBeInTheDocument();
  expect(screen.getByText('订单增长直接作用于 AI 芯片，并向服务器传导。')).toBeInTheDocument();
  expect(screen.getByLabelText('AI芯片关联 1 条动态')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '24 小时' }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/industry-chains/7/events?hours=24'));
  await userEvent.click(screen.getByRole('button', { name: '在 News Wire 查看' }));
  expect(onOpenNewsEvent).toHaveBeenCalledWith(91);
});

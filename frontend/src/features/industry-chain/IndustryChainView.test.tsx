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
    { nodeKey: 'stage:dc', type: 'STAGE', name: '数据中心', description: '算力交付', stageOrder: 3, confidence: 'HIGH', evidenceRefs: ['E1'] }
  ],
  edges: [
    { edgeKey: 'e1', sourceKey: 'stage:chip', targetKey: 'stage:server', type: 'FLOWS_TO', nature: 'INDUSTRY_LOGIC', description: '进入整机', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e2', sourceKey: 'stage:server', targetKey: 'stage:dc', type: 'FLOWS_TO', nature: 'INDUSTRY_LOGIC', description: '部署交付', confidence: 'HIGH', evidenceRefs: ['E1'] }
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

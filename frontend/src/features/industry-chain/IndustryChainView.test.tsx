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
  researchContent: {
    overview: {
      lifecycle: 'GROWTH', prosperity: 'RISING', supplyDemand: 'STRUCTURAL', cycleType: '资本开支驱动的成长周期',
      demandDrivers: ['云厂商资本开支'], supplyDrivers: ['先进制程产能'], keyVariables: ['GPU 交付周期'],
      bottlenecks: ['先进算力芯片'], overcapacityRisks: ['低端服务器组装'], trendTags: ['算力升级', '国产替代']
    },
    stageProfiles: [{
      nodeKey: 'stage:chip', roleSummary: '提供计算核心', businessModel: '芯片销售与软件生态',
      costStructure: '研发、晶圆制造与先进封装', valueCapture: '性能与生态溢价', bottleneck: '先进制程与 HBM 供给',
      prosperity: 'RISING', supplyDemand: 'TIGHT', lifecycle: 'GROWTH', profitDrivers: ['产品代际升级'],
      barriers: ['软硬件生态'], coreMetrics: ['出货量', '平均售价'], risks: ['出口限制'],
      keyVariables: ['良率'], trendTags: ['高性能计算']
    }],
    companyProfiles: [{
      nodeKey: 'company:nvidia', industryPosition: '全球 AI 加速芯片与计算生态龙头', coreProducts: ['GPU', 'CUDA'],
      downstreamMarkets: ['云计算', '企业 AI'], competitiveAdvantages: ['软硬件生态', '产品迭代'], keyVariables: ['云厂商资本开支']
    }],
    nodeProfiles: [{
      nodeKey: 'material:copper', definition: '高速互连所需的高纯导体材料', function: '承担高速信号传输',
      inputs: ['精炼铜'], outputs: ['高纯铜材'], costDrivers: ['铜价'], valueDrivers: ['纯度'], barriers: ['提纯工艺'],
      coreMetrics: ['纯度'], risks: ['原料波动'], maturity: 'MATURE', valueLevel: 'MEDIUM',
      bottleneckLevel: 'HIGH', localizationLevel: 'HIGH'
    }, {
      nodeKey: 'technology:advanced-package', definition: '面向高算力芯片的先进封装路线', function: '提升互连密度与带宽',
      inputs: ['晶圆', '封装基板'], outputs: ['算力模组'], costDrivers: ['设备折旧'], valueDrivers: ['良率'], barriers: ['工艺协同'],
      coreMetrics: ['良率'], risks: ['产能爬坡'], maturity: 'SCALING', valueLevel: 'HIGH',
      bottleneckLevel: 'HIGH', localizationLevel: 'MEDIUM'
    }]
  },
  nodes: [
    { nodeKey: 'stage:chip', type: 'STAGE', name: '算力芯片', description: '计算核心', stageOrder: 1, confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'stage:server', type: 'STAGE', name: '服务器', description: '系统集成', stageOrder: 2, confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'stage:dc', type: 'STAGE', name: '数据中心', description: '算力交付', stageOrder: 3, confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'material:copper', type: 'MATERIAL', name: '高纯铜', description: '高速互连材料', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'equipment:lithography', type: 'EQUIPMENT', name: '光刻设备', description: '芯片制造设备', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'technology:advanced-package', type: 'TECHNOLOGY', name: '先进封装', description: '提高互连密度', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'product:gpu', type: 'PRODUCT', name: 'AI芯片', description: 'GPU 与 ASIC', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { nodeKey: 'company:nvidia', type: 'COMPANY', name: '英伟达', description: '代表公司', stockCode: 'NVDA.O', confidence: 'HIGH', evidenceRefs: ['E1'] }
  ],
  edges: [
    { edgeKey: 'e1', sourceKey: 'stage:chip', targetKey: 'stage:server', type: 'FLOWS_TO', nature: 'INDUSTRY_LOGIC', description: '进入整机', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e2', sourceKey: 'stage:server', targetKey: 'stage:dc', type: 'FLOWS_TO', nature: 'INDUSTRY_LOGIC', description: '部署交付', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e-material', sourceKey: 'material:copper', targetKey: 'stage:chip', type: 'BELONGS_TO_STAGE', nature: 'INDUSTRY_LOGIC', description: '属于上游', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e-equipment', sourceKey: 'equipment:lithography', targetKey: 'stage:chip', type: 'BELONGS_TO_STAGE', nature: 'INDUSTRY_LOGIC', description: '支撑制造', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e-technology', sourceKey: 'technology:advanced-package', targetKey: 'product:gpu', type: 'ENABLES', nature: 'INDUSTRY_LOGIC', description: '提升集成性能', confidence: 'HIGH', strength: 'PRIMARY', directionNote: '先进封装提升算力芯片带宽', evidenceRefs: ['E1'] },
    { edgeKey: 'e3', sourceKey: 'product:gpu', targetKey: 'stage:chip', type: 'BELONGS_TO_STAGE', nature: 'INDUSTRY_LOGIC', description: '属于上游', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { edgeKey: 'e4', sourceKey: 'company:nvidia', targetKey: 'product:gpu', type: 'PARTICIPATES_IN', nature: 'DISCLOSED', description: '参与 AI 芯片', confidence: 'HIGH', evidenceRefs: ['E1'] }
  ],
  evidence: [{ evidenceCode: 'E1', title: 'AI 算力产业白皮书', url: 'https://example.com/report', source: 'example.com', sourceTier: 'T2', excerpt: '产业链证据' }]
};

const workspace: IndustryChainWorkspace = {
  chain: { id: 7, name: 'AI算力', normalizedName: 'ai算力', summary: graph.summary },
  revision: { id: 11, chainId: 7, status: 'READY', stage: 'COMPLETED', message: '产业链图谱已更新' },
  graph,
  structure: {
    status: 'UPGRADE_AVAILABLE', score: 46, semanticNodeCount: 5,
    coveredStageCount: 2, stageCount: 3,
    gaps: ['升级为可展开的 V3 语义图谱', '补齐尚未展开的产业环节']
  }
};

beforeEach(() => {
  vi.unstubAllGlobals();
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

test('collapses the industry chain library into a compact rail and restores it', async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  expect(await screen.findByRole('textbox', { name: '产业链名称' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '收起产业链目录' }));

  expect(screen.queryByRole('textbox', { name: '产业链名称' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '展开产业链目录' })).toHaveAttribute('aria-expanded', 'false');

  await userEvent.click(screen.getByRole('button', { name: '展开产业链目录' }));
  expect(screen.getByRole('textbox', { name: '产业链名称' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '收起产业链目录' })).toHaveAttribute('aria-expanded', 'true');
});

test('selects graph nodes, searches and switches focus mode', async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));
  await userEvent.click(await screen.findByRole('button', { name: '算力芯片 · 计算核心' }));
  expect(screen.getByRole('complementary', { name: '图谱详情' })).toHaveTextContent('计算核心');

  fireEvent.change(screen.getByRole('searchbox', { name: '搜索图谱节点' }), { target: { value: '数据中心' } });
  await waitFor(() => expect(screen.getByRole('button', { name: /数据中心/ })).toHaveAttribute('data-search-match', 'true'));
  await userEvent.click(screen.getByRole('button', { name: '聚焦链路' }));
  expect(screen.getByRole('button', { name: '查看全图' })).toBeInTheDocument();
  expect(screen.getByLabelText('移动端产业链阅读器')).toBeInTheDocument();
});

test('presents structure depth and offers a semantic completion action', async () => {
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7' && !options?.method) return workspace;
    if (path === '/api/industry-chains/7/refresh' && options?.method === 'POST') {
      return { ...workspace.revision, status: 'RUNNING', stage: 'QUEUED' };
    }
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));

  expect(screen.getByLabelText('图谱结构完整度 46 分')).toBeInTheDocument();
  expect(screen.getByText('结构待升级')).toBeInTheDocument();
  expect(screen.getByText('2 / 3 环节 · 5 个语义节点')).toBeInTheDocument();
  expect(screen.getByText('升级为可展开的 V3 语义图谱')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '升级为 V3' }));
  expect(api).toHaveBeenCalledWith('/api/industry-chains/7/refresh', { method: 'POST' });
});

test('names the active structure completion stage while preserving the old graph', async () => {
  const completing = {
    ...workspace,
    revision: {
      ...workspace.revision!, status: 'RUNNING' as const, stage: 'COMPLETING_STRUCTURE' as const,
      message: '正在补齐材料、设备、部件、技术与应用节点'
    }
  };
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return completing;
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));

  expect(screen.getByText('正在补全结构')).toBeInTheDocument();
  expect(screen.getByText('正在补齐材料、设备、部件、技术与应用节点')).toBeInTheDocument();
  expect(screen.getByRole('region', { name: 'AI算力产业链图谱' })).toBeInTheDocument();
});

test('divides the measured desktop canvas equally across its industry stages', async () => {
  const clientWidth = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(1500);
  vi.stubGlobal('ResizeObserver', class {
    observe() { return undefined; }
    disconnect() { return undefined; }
  });
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  const { container } = render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));

  await waitFor(() => {
    const lanes = [...container.querySelectorAll<HTMLElement>('.ic-lane')];
    expect(lanes).toHaveLength(3);
    expect(lanes.map((lane) => lane.style.left)).toEqual(['0px', '500px', '1000px']);
    expect(lanes.map((lane) => lane.style.width)).toEqual(['500px', '500px', '500px']);
  });
  clientWidth.mockRestore();
});

test('progressively expands and collapses semantic nodes while preserving dedicated edge routes', async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  const { container } = render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));
  expect(container.querySelector('.ic-node--material')).toBeInTheDocument();
  expect(container.querySelector('.ic-node--equipment')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '展开 AI芯片 的 2 个关联节点' }));

  expect(container.querySelector('.ic-edge--stage-flow')).toBeInTheDocument();
  expect(container.querySelector('.ic-edge--stage-membership')).toBeInTheDocument();
  expect(container.querySelector('.ic-edge--company-link')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '英伟达 · 代表公司' })).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '收起 AI芯片 的 2 个关联节点' }));
  expect(container.querySelector('.ic-node--material')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '英伟达 · 代表公司' })).not.toBeInTheDocument();
});

test('shows legacy stage operating highlights directly on the graph', async () => {
  const legacyGraph: IndustryChainGraph = {
    ...graph,
    schemaVersion: 'INDUSTRY_CHAIN_V2',
    nodes: graph.nodes.filter((item) => item.type === 'STAGE'),
    edges: graph.edges.filter((item) => item.type === 'FLOWS_TO'),
    researchContent: {
      ...graph.researchContent!,
      nodeProfiles: [],
      companyProfiles: [],
      stageProfiles: graph.researchContent!.stageProfiles.map((profile) => ({
        ...profile,
        bottleneck: '先进制程与 HBM 供给',
        valueCapture: '性能与生态溢价',
        coreMetrics: ['出货量']
      }))
    }
  };
  const legacyWorkspace = { ...workspace, graph: legacyGraph };
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return legacyWorkspace;
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));

  expect(screen.getAllByText('核心瓶颈').length).toBeGreaterThan(0);
  expect(screen.getAllByText('先进制程与 HBM 供给').length).toBeGreaterThan(0);
  expect(screen.getAllByText('性能与生态溢价').length).toBeGreaterThan(0);
  expect(screen.getAllByText('出货量').length).toBeGreaterThan(0);
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

test('switches to the research panel and presents industry operating content', async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));
  await userEvent.click(screen.getByRole('button', { name: '研究面板' }));

  expect(screen.getByRole('region', { name: 'AI算力产业研究面板' })).toBeInTheDocument();
  expect(screen.getAllByText('成长扩张')).not.toHaveLength(0);
  expect(screen.getAllByText('景气上行')).not.toHaveLength(0);
  expect(screen.getByText('云厂商资本开支')).toBeInTheDocument();
  expect(screen.getByText('先进算力芯片')).toBeInTheDocument();
  expect(screen.getByText('芯片销售与软件生态')).toBeInTheDocument();
  expect(screen.getByText('全球 AI 加速芯片与计算生态龙头')).toBeInTheDocument();
});

test('explains semantic layers, labels node ratings and reveals companies automatically', async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/api/industry-chains') return [workspace.chain];
    if (path === '/api/industry-chains/7') return workspace;
    throw new Error(`unexpected ${path}`);
  });
  const { container } = render(<IndustryChainView />);

  await userEvent.click(await screen.findByRole('button', { name: /AI算力/ }));
  expect(screen.getByRole('group', { name: '产业专题图层' })).toBeInTheDocument();
  expect(screen.getAllByRole('button', { name: /产业结构|价值分配|产业瓶颈|技术路线|国产替代|公司生态/ })).toHaveLength(6);
  fireEvent.doubleClick(screen.getByRole('button', { name: '算力芯片 · 计算核心' }));
  const visibleCount = container.querySelectorAll('.ic-node').length;

  await userEvent.click(screen.getByRole('button', { name: /产业瓶颈/ }));

  expect(container.querySelectorAll('.ic-node')).toHaveLength(visibleCount);
  expect(container.querySelector('.ic-node--material')).toHaveClass('ic-tone--high');
  expect(screen.getByText('突出制约扩产、交付或性能的关键卡点。')).toBeInTheDocument();
  expect(screen.getAllByText('关键卡点').length).toBeGreaterThan(0);
  expect(screen.getAllByText('暂无评级').length).toBeGreaterThan(0);

  await userEvent.click(screen.getByRole('button', { name: /公司生态/ }));
  expect(screen.getByRole('button', { name: '英伟达 · 代表公司' })).toBeInTheDocument();
  expect(screen.getByText('自动展示代表公司及其所在产业环节。')).toBeInTheDocument();
});

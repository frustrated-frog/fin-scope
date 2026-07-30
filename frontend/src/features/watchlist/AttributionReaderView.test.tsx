import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { AttributionReaderView } from './AttributionReaderView';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

class MockEventSource {
  private listeners: Record<string, (event: MessageEvent) => void> = {};
  close = vi.fn();
  onerror: (() => void) | null = null;

  addEventListener(name: string, listener: (event: MessageEvent) => void) {
    this.listeners[name] = listener;
  }

  emit(name: string, data: unknown) {
    this.listeners[name]?.(new MessageEvent(name, { data: JSON.stringify(data) }));
  }
}

let eventSource: MockEventSource;

beforeEach(() => {
  vi.mocked(api).mockReset();
  eventSource = new MockEventSource();
  vi.stubGlobal('EventSource', vi.fn(() => eventSource));
});

test('uses a dedicated compact control to return to the watchlist', () => {
  vi.mocked(api).mockResolvedValue({
    id: 200,
    status: 'COMPLETED',
    summary: '归因完成',
    drivers: []
  });
  const onBack = vi.fn();

  render(<AttributionReaderView reportId={200} code="600519" name="贵州茅台" onBack={onBack} />);

  const backButton = screen.getByRole('button', { name: '返回自选' });
  expect(backButton).toHaveClass('attribution-back-button');
  expect(backButton.querySelector('.attribution-back-icon')).toHaveAttribute('aria-hidden', 'true');

  fireEvent.click(backButton);
  expect(onBack).toHaveBeenCalledTimes(1);
});

test('marks web search as reached when a live search clue arrives', async () => {
  render(
    <AttributionReaderView
      taskId="task-1"
      reportId={101}
      code="600343"
      name="测试标的"
      onBack={vi.fn()}
    />
  );

  const webSearchStep = screen.getByText('全网搜索线索').closest('li');
  expect(webSearchStep).not.toHaveClass('reached');

  eventSource.emit('progress', {
    type: 'CLUE',
    stage: 'web-search',
    message: '找到：测试新闻（T3）'
  });

  await waitFor(() => expect(webSearchStep).toHaveClass('reached'));
  expect(screen.getAllByText(/找到：测试新闻（T3）/).length).toBeGreaterThan(0);
});

test('falls back to report polling when the SSE connection breaks', async () => {
  vi.mocked(api).mockResolvedValue({ id: 101, status: 'COMPLETED', summary: '已完成归因', drivers: [] });
  render(
    <AttributionReaderView
      taskId="task-1"
      reportId={101}
      code="600343"
      name="测试标的"
      onBack={vi.fn()}
    />
  );

  eventSource.onerror?.();

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/attribution/reports/101'));
  expect(await screen.findByText('已完成归因')).toBeInTheDocument();
});

test('renders rich driver reasoning and uncertainty sections', async () => {
  vi.mocked(api).mockResolvedValue({
    id: 102,
    status: 'COMPLETED',
    summary: '多因素共同驱动',
    primaryDriver: { claim: '公司催化', transmissionPath: '公告 → 盈利预期 → 重估' },
    drivers: [{
      claim: '公司催化', detail: '公告信息得到市场关注', facts: ['公司发布正式公告'],
      transmissionPath: '公告 → 盈利预期 → 重估', counterEvidence: '成交结构尚未确认', observationWindow: '未来 3 日'
    }],
    uncertainties: ['公开信息不能解释全部波动'],
    observationWindows: ['观察成交额是否延续']
  });
  render(<AttributionReaderView taskId="task-2" reportId={102} code="600519" onBack={vi.fn()} />);
  eventSource.onerror?.();

  expect(await screen.findByText(/首要驱动/)).toBeInTheDocument();
  expect(screen.getByText('事实依据')).toBeInTheDocument();
  expect(screen.getByText('公司发布正式公告')).toBeInTheDocument();
  expect(screen.getByText(/不确定性/)).toBeInTheDocument();
  expect(screen.getByText(/后续验证/)).toBeInTheDocument();
  expect(screen.queryByLabelText('归因证据与验证')).not.toBeInTheDocument();
});

test('restores persisted harness tracks after SSE disconnects', async () => {
  vi.mocked(api)
    .mockResolvedValueOnce({ id: 103, status: 'GENERATING' })
    .mockResolvedValueOnce({
      run: { id: 7, reportId: 103, status: 'RUNNING' },
      steps: [{ stepId: 'company', track: 'COMPANY', status: 'COMPLETED', outputSummary: '新增证据 2 条' }]
    })
    .mockResolvedValue({ id: 103, status: 'COMPLETED', summary: '恢复完成', drivers: [] });
  render(<AttributionReaderView taskId="task-3" reportId={103} code="600000" onBack={vi.fn()} />);
  eventSource.onerror?.();

  expect(await screen.findByText(/Harness 轨道恢复/)).toBeInTheDocument();
  expect(screen.getByText(/公司事件 · COMPLETED/)).toBeInTheDocument();
});

test('renders a research dashboard side panel while attribution is running', async () => {
  vi.mocked(api)
    .mockResolvedValueOnce({ id: 104, status: 'GENERATING' })
    .mockResolvedValueOnce({
      run: { id: 8, reportId: 104, status: 'RUNNING' },
      steps: [
        { stepId: 'company', track: 'COMPANY', status: 'COMPLETED', outputSummary: '公司线索 2 条' },
        { stepId: 'industry', track: 'INDUSTRY', status: 'PENDING' }
      ]
    })
    .mockResolvedValue({ id: 104, status: 'GENERATING' });

  render(
    <AttributionReaderView
      taskId="task-4"
      reportId={104}
      code="021894"
      name="易方达半导体设备ETF联接C"
      changePct={-6.5}
      onBack={vi.fn()}
    />
  );

  eventSource.emit('progress', {
    type: 'CLUE',
    stage: 'evidence-rank',
    message: '找到：半导体设备链波动扩大（T2）'
  });

  expect(await screen.findByText('研究态势')).toBeInTheDocument();
  expect(screen.getByText('当前焦点')).toBeInTheDocument();
  expect(screen.getAllByText('整理证据').length).toBeGreaterThan(0);
  expect(screen.getByText('已发现线索')).toBeInTheDocument();
  expect(screen.getByText('1 条')).toBeInTheDocument();
  expect(screen.getByText('轨道进度')).toBeInTheDocument();
  expect(screen.getByText('已启动 1/2，已结算 1/2')).toBeInTheDocument();
  expect(screen.getAllByText(/半导体设备链波动扩大/).length).toBeGreaterThan(0);
});

test('treats pending tracks as an unstarted plan instead of zero progress', async () => {
  vi.mocked(api)
    .mockResolvedValueOnce({ id: 105, status: 'GENERATING' })
    .mockResolvedValueOnce({
      run: { id: 9, reportId: 105, status: 'RUNNING', currentStep: 'web-search' },
      steps: [
        { stepId: 'company', track: 'COMPANY', status: 'PENDING' },
        { stepId: 'industry', track: 'INDUSTRY', status: 'PENDING' },
        { stepId: 'macro', track: 'MACRO', status: 'PENDING' },
        { stepId: 'market', track: 'MARKET', status: 'PENDING' },
        { stepId: 'counter', track: 'COUNTER', status: 'PENDING' }
      ]
    })
    .mockResolvedValue({ id: 105, status: 'GENERATING' });

  render(<AttributionReaderView taskId="task-5" reportId={105} code="021894" onBack={vi.fn()} />);

  expect(await screen.findByText('轨道准备中')).toBeInTheDocument();
  expect(screen.queryByText('0/5')).not.toBeInTheDocument();
  expect(screen.queryByText('等待中')).not.toBeInTheDocument();
});

test('loads a persisted report and history without opening an event stream', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path.includes('/history') ? [{
      id: 201, instrumentCode: '600519', instrumentType: 'STOCK', status: 'COMPLETED',
      reportDate: '2026-07-13', createdAt: '2026-07-13T15:20:00', summary: '收盘归因', changePct: 2.1
    }] : {
      id: 201, instrumentCode: '600519', instrumentType: 'STOCK', status: 'COMPLETED',
      reportDate: '2026-07-13', createdAt: '2026-07-13T15:20:00', summary: '收盘归因', drivers: []
    }
  ) as never);

  render(<AttributionReaderView reportId={201} code="600519" type="STOCK" onBack={vi.fn()} />);

  expect(await screen.findByText('历史归因')).toBeInTheDocument();
  expect(screen.getAllByText('收盘归因')).toHaveLength(2);
  expect(EventSource).not.toHaveBeenCalled();
  expect(api).toHaveBeenCalledWith('/api/attribution/history?code=600519&type=STOCK&limit=50');
});

test('loads history when a live attribution becomes completed', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path.includes('/history') ? [
      {
        id: 301, instrumentCode: '603618', instrumentType: 'STOCK', status: 'COMPLETED',
        reportDate: '2026-07-30', createdAt: '2026-07-30T15:20:00', summary: '本次归因', changePct: -5.76
      },
      {
        id: 299, instrumentCode: '603618', instrumentType: 'STOCK', status: 'COMPLETED',
        reportDate: '2026-07-29', createdAt: '2026-07-29T15:10:00', summary: '上次归因', changePct: 1.25
      }
    ] : {
      id: 301, instrumentCode: '603618', instrumentType: 'STOCK', status: 'COMPLETED',
      reportDate: '2026-07-30', createdAt: '2026-07-30T15:20:00', summary: '本次归因', drivers: []
    }
  ) as never);

  render(
    <AttributionReaderView
      taskId="live-task"
      reportId={301}
      code="603618"
      type="STOCK"
      onBack={vi.fn()}
    />
  );

  expect(await screen.findByText('本次归因')).toBeInTheDocument();
  await waitFor(() => expect(api).toHaveBeenCalledWith(
    '/api/attribution/history?code=603618&type=STOCK&limit=50'
  ));
  expect(screen.getByText('上次归因')).toBeInTheDocument();
});

test('renders the plain-language causal narrative', async () => {
  vi.mocked(api).mockResolvedValue({
    id: 401,
    instrumentCode: '603618',
    instrumentType: 'STOCK',
    status: 'COMPLETED',
    summary: '多重因素共同造成下跌',
    narrative: {
      plainSummary: '延期传闻触发需求担忧，板块走弱和前期涨幅进一步放大了抛压。',
      event: '英伟达机架延期传闻出现',
      instrumentLink: '公司处于算力硬件相关链条，市场会据此调整短期需求预期。',
      whyToday: '传闻、板块回撤与获利盘卖出在今天集中出现。',
      causalSteps: ['延期传闻', '需求预期下调', '算力硬件板块承压', '杭电股份下跌'],
      amplifiers: ['前期累计涨幅较大', '板块龙头集体走弱'],
      dampeners: ['公司尚未确认实际订单受到影响']
    },
    drivers: [{
      claim: '机架延期传闻',
      role: 'TRIGGER',
      plainExplanation: '市场担心相关硬件需求后移。',
      impactLevel: 'HIGH',
      confidence: 'MID'
    }]
  });

  render(
    <AttributionReaderView
      reportId={401}
      code="603618"
      type="STOCK"
      name="杭电股份"
      changePct={-5.76}
      onBack={vi.fn()}
    />
  );

  expect(await screen.findByText('今天为什么跌')).toBeInTheDocument();
  expect(screen.getByText('延期传闻触发需求担忧，板块走弱和前期涨幅进一步放大了抛压。')).toBeInTheDocument();
  expect(screen.getByText('为什么是它')).toBeInTheDocument();
  expect(screen.getByText('为什么是今天')).toBeInTheDocument();
  expect(screen.getByText('放大跌幅的因素')).toBeInTheDocument();
  expect(screen.getByText('缓冲或反方因素')).toBeInTheDocument();
  expect(screen.getByText('直接触发')).toBeInTheDocument();
  expect(screen.getByText('市场担心相关硬件需求后移。')).toBeInTheDocument();
});

test('separates AI market interpretation from factual evidence', async () => {
  const report = {
    id: 402,
    instrumentCode: '600343',
    instrumentType: 'STOCK',
    status: 'COMPLETED',
    summary: '公告否定题材预期后股价承压',
    drivers: [{
      claim: '公司澄清不涉及商业航天核心业务',
      role: 'TRIGGER',
      plainExplanation: '市场此前炒作的成长故事被公司公告否定。',
      marketInterpretation: '市场在交易商业航天订单预期落空。',
      expectationShift: '原本预期切入商业航天 → 现在确认仍以传统业务为主。',
      priceImpact: '成长想象空间收缩，可能提高风险溢价并压低估值。',
      explanatoryPower: 'HIGH',
      explanatoryPowerReason: '公告直接否定核心题材，且与当日下跌方向一致。',
      facts: ['公司公告明确否认商业航天主营业务']
    }]
  };
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path.includes('/history') ? [] : report
  ) as never);

  render(
    <AttributionReaderView
      reportId={402}
      code="600343"
      type="STOCK"
      name="航天动力"
      changePct={-5.2}
      onBack={vi.fn()}
    />
  );

  expect(await screen.findByText('AI 解读')).toBeInTheDocument();
  expect(screen.getByLabelText('AI 市场解读')).toBeInTheDocument();
  expect(screen.getByText('市场在交易什么')).toBeInTheDocument();
  expect(screen.getByText('预期发生了什么变化')).toBeInTheDocument();
  expect(screen.getByText('为什么会影响股价')).toBeInTheDocument();
  expect(screen.getByText('解释力度')).toBeInTheDocument();
  expect(screen.getByText('事实依据')).toBeInTheDocument();
  expect(screen.getByText('公司公告明确否认商业航天主营业务')).toBeInTheDocument();
});

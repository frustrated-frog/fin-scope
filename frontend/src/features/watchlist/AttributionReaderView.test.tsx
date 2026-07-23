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
  expect(screen.getByText(/事实：公司发布正式公告/)).toBeInTheDocument();
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

import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { StockSupplyChainPanel } from './StockSupplyChainPanel';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const snapshot = {
  companyCode: '688012',
  companyName: '中微公司',
  summary: '公司连接上游关键零部件与下游晶圆制造扩产。',
  position: '半导体前道设备',
  limitations: '部分客户以匿名方式披露，不能据此猜测具体公司。',
  schemaVersion: 'SUPPLY_CHAIN_V1',
  model: 'test-model',
  evidenceAsOf: '2026-03-31',
  generatedAt: '2026-08-11T09:00:00',
  nodes: [
    { layer: 'UPSTREAM', name: '真空与射频零部件', relationType: 'SUPPLY', description: '设备关键供给环节', confidence: 'HIGH', evidenceRefs: ['E1'] },
    { layer: 'COMPANY', name: '刻蚀与薄膜设备', relationType: 'CORE_BUSINESS', description: '公司核心产品位置', confidence: 'HIGH', evidenceRefs: ['E1', 'E2'] },
    { layer: 'DOWNSTREAM', name: '晶圆制造', relationType: 'CUSTOMER_INDUSTRY', description: '下游扩产形成设备需求', confidence: 'MEDIUM', evidenceRefs: ['E2'] }
  ],
  evidence: [
    { evidenceCode: 'E1', title: '2025 年年度报告', url: 'https://example.com/report', source: 'example.com', sourceTier: 'T1', publishedAt: '2026-03-31', excerpt: '公司主营刻蚀与薄膜设备。' },
    { evidenceCode: 'E2', title: '投资者交流纪要', url: 'https://example.com/ir', source: 'example.com', sourceTier: 'T2', publishedAt: '2026-06-01', excerpt: '下游晶圆厂扩产推动需求。' }
  ]
};

describe('StockSupplyChainPanel', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  test('renders the persisted three-layer evidence map without refreshing it', async () => {
    vi.mocked(api).mockResolvedValue({
      code: '688012', name: '中微公司', snapshot,
      refreshRun: { id: 9, status: 'READY', stage: 'COMPLETED' }
    } as never);

    render(<StockSupplyChainPanel code="688012" name="中微公司" />);

    expect(await screen.findByText('真空与射频零部件')).toBeInTheDocument();
    expect(screen.getByText('刻蚀与薄膜设备')).toBeInTheDocument();
    expect(screen.getByText('晶圆制造')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /2025 年年度报告/ })).toHaveAttribute(
      'href', 'https://example.com/report'
    );
    expect(api).toHaveBeenCalledTimes(1);
    expect(api).toHaveBeenCalledWith('/api/stocks/688012/supply-chain');
  });

  test('automatically creates and polls the first evidence refresh', async () => {
    vi.useFakeTimers();
    vi.mocked(api)
      .mockResolvedValueOnce({ code: '688012', name: '中微公司', snapshot: null, refreshRun: null } as never)
      .mockResolvedValueOnce({ id: 10, status: 'RUNNING', stage: 'QUEUED' } as never)
      .mockResolvedValueOnce({
        code: '688012', name: '中微公司', snapshot,
        refreshRun: { id: 10, status: 'READY', stage: 'COMPLETED' }
      } as never);

    render(<StockSupplyChainPanel code="688012" name="中微公司" />);
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });

    expect(api).toHaveBeenNthCalledWith(2, '/api/stocks/688012/supply-chain/refresh', { method: 'POST' });
    expect(screen.getByText(/正在建立产业链证据图谱/)).toBeInTheDocument();
    await act(async () => { await vi.advanceTimersByTimeAsync(1200); });

    expect(screen.getByText('真空与射频零部件')).toBeInTheDocument();
  });

  test('keeps the old map visible while manually updating evidence', async () => {
    vi.mocked(api)
      .mockResolvedValueOnce({
        code: '688012', name: '中微公司', snapshot,
        refreshRun: { id: 9, status: 'READY', stage: 'COMPLETED' }
      } as never)
      .mockResolvedValueOnce({ id: 11, status: 'RUNNING', stage: 'QUEUED' } as never);
    render(<StockSupplyChainPanel code="688012" name="中微公司" />);

    await screen.findByText('真空与射频零部件');
    await userEvent.click(screen.getByRole('button', { name: '更新产业链证据' }));

    expect(screen.getByText('真空与射频零部件')).toBeInTheDocument();
    expect(screen.getByText(/正在更新公开证据/)).toBeInTheDocument();
  });

  test('shows a concrete retry state when the first evidence build failed', async () => {
    vi.mocked(api).mockResolvedValue({
      code: '688012', name: '中微公司', snapshot: null,
      refreshRun: {
        id: 12, status: 'FAILED', stage: 'COMPLETED', errorCode: 'SYNTHESIS_FAILED',
        message: '产业链证据刷新失败，可以稍后重试', retryable: true
      }
    } as never);

    render(<StockSupplyChainPanel code="688012" name="中微公司" />);

    expect(await screen.findByText('产业链生成失败')).toBeInTheDocument();
    expect(screen.getByText('产业链证据刷新失败，可以稍后重试')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新生成产业链' })).toBeInTheDocument();
  });
});

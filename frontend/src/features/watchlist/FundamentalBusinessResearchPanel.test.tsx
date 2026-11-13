import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { FundamentalBusinessResearchPanel } from './FundamentalBusinessResearchPanel';
import type { FundamentalBusinessResearch } from './fundamentalResearchTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
const base = '/api/watchlist/stocks/603618/fundamental-research/business-research';
const idle: FundamentalBusinessResearch = { status: 'IDLE', message: '准备整理经营资料', showingPrevious: false, findings: [] };
const ready: FundamentalBusinessResearch = {
  status: 'PARTIAL', message: '已有带来源的经营认识', latestRunId: 10, showingPrevious: false,
  findings: [{ aspect: 'REVENUE_MODEL', label: '收入来源', noteField: 'revenueModel', content: '电缆产品构成主营业务', support: 'PARTIALLY_SUPPORTED', runId: 10,
    generatedAt: '2026-11-14T12:00:00', sources: [{ evidenceRef: 'E1', title: '公司经营范围', url: 'https://example.com/company' }] }],
};
beforeEach(() => vi.mocked(api).mockReset());
afterEach(() => vi.useRealTimers());

test('reads existing research and passes cited findings into the editable notes flow', async () => {
  vi.mocked(api).mockResolvedValue(ready);
  const adopt = vi.fn();
  render(<FundamentalBusinessResearchPanel code="603618" disabled={false} onAdopt={adopt} />);
  expect(await screen.findByText('电缆产品构成主营业务')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '公司经营范围 ↗' })).toHaveAttribute('href', 'https://example.com/company');
  await userEvent.click(screen.getByRole('button', { name: '补入经营笔记 →' }));
  expect(adopt).toHaveBeenCalledWith(ready.findings[0]);
  expect(api).toHaveBeenCalledTimes(1);
});

test('starts a run only after explicit action', async () => {
  vi.mocked(api).mockResolvedValueOnce(idle).mockResolvedValueOnce({ ...idle, status: 'RUNNING', message: '正在整理经营资料' });
  render(<FundamentalBusinessResearchPanel code="603618" disabled={false} onAdopt={vi.fn()} />);
  await screen.findByText('准备整理经营资料');
  expect(api).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole('button', { name: '自动整理经营' }));
  expect(api).toHaveBeenLastCalledWith(`${base}/runs`, { method: 'POST' });
  expect(await screen.findByRole('button', { name: '正在整理经营…' })).toBeDisabled();
});

test('polling failure retains previous material and pauses until the user retries', async () => {
  vi.useFakeTimers();
  vi.mocked(api).mockResolvedValueOnce({ ...ready, status: 'RUNNING', showingPrevious: true, latestRunId: 11 }).mockRejectedValueOnce(new Error('进度读取失败'));
  render(<FundamentalBusinessResearchPanel code="603618" disabled={false} onAdopt={vi.fn()} />);
  await act(async () => { await Promise.resolve(); });
  await act(async () => { await vi.advanceTimersByTimeAsync(2500); });
  expect(screen.getByRole('alert')).toHaveTextContent('进度读取失败');
  expect(screen.getByText('电缆产品构成主营业务')).toBeInTheDocument();
  await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
  expect(api).toHaveBeenCalledTimes(2);
});

test('does not show a late response for the previous stock', async () => {
  let finish!: (value: FundamentalBusinessResearch) => void;
  vi.mocked(api).mockReturnValueOnce(new Promise(resolve => { finish = resolve; })).mockResolvedValueOnce({ ...idle, message: '当前股票的经营研究' });
  const { rerender } = render(<FundamentalBusinessResearchPanel code="603618" disabled={false} onAdopt={vi.fn()} />);
  rerender(<FundamentalBusinessResearchPanel code="688012" disabled={false} onAdopt={vi.fn()} />);
  expect(await screen.findByText('当前股票的经营研究')).toBeInTheDocument();
  await act(async () => finish(ready));
  expect(screen.queryByText('电缆产品构成主营业务')).not.toBeInTheDocument();
});

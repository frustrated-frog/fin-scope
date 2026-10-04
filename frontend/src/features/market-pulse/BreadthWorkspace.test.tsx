import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { BreadthWorkspace } from './BreadthWorkspace';
import type { MarketPulseWorkspace } from './marketPulseTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
const dates = ['2026-09-30', '2026-09-29', '2026-09-28', '2026-09-25', '2026-09-24', '2026-09-23'];
function snapshot(date: string, sample = 100): MarketPulseWorkspace {
  return {
    businessDate: date, qualityStatus: 'PARTIAL', generatedAt: `${date}T15:30:00`,
    breadth: {
      businessDate: date, validCount: sample, advanceCount: sample * .4, declineCount: sample * .1, flatCount: sample * .5,
      advanceRatio: .4, medianChangePct: -.1, totalAmount: 1e12, netAdvances: 30,
      volumePressure: { advanceAmountRatio: .8, advanceAmount: 8e11, declineAmount: 2e11, flatAmount: 0, trin: 1 },
      trendBreadth: { ma20Ratio: .6, ma20ValidCount: 10, ma60Ratio: .3, ma60ValidCount: 10 },
      newHighLow: { high20Count: 3, low20Count: 1, valid20Count: 10 },
      returnDistribution: [
        { code: 'DOWN_7', label: '≤ -7%', count: 0, ratio: 0 }, { code: 'DOWN_3_7', label: '-7% ~ -3%', count: sample * .1, ratio: .1 },
        { code: 'DOWN_0_3', label: '-3% ~ 0', count: 0, ratio: 0 }, { code: 'FLAT', label: '0', count: sample * .5, ratio: .5 },
        { code: 'UP_0_3', label: '0 ~ 3%', count: sample * .2, ratio: .2 }, { code: 'UP_3_7', label: '3% ~ 7%', count: sample * .1, ratio: .1 },
        { code: 'UP_7', label: '≥ 7%', count: sample * .1, ratio: .1 }
      ],
      indices: [
        { code: '000300.SH', name: '沪深300', businessDate: date, return1d: .3 },
        { code: '399001.SZ', name: '深证成指', businessDate: date, return1d: -.2 },
        { code: 'old', name: '过期指数', businessDate: '2026-08-01', return1d: 8 }
      ]
    }
  };
}
function show(workspace = snapshot(dates[0]), available = dates) {
  const onLoad = vi.fn();
  const onRefresh = vi.fn();
  return { ...render(<BreadthWorkspace key={workspace.businessDate} workspace={workspace} dates={available} refreshing={false} onLoad={onLoad} onRefresh={onRefresh} />), onLoad, onRefresh };
}
beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation(async path => snapshot(path.split('/').pop()!, 200));
});

test('loads one shared comparison and keeps price and participation denominators correct', async () => {
  show();
  expect(screen.getByLabelText('对照日期')).toHaveValue('2026-09-29');
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('2026-09-30 对照 2026-09-29'));
  expect(api).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('region', { name: '指数与个股温差' })).toHaveTextContent('+0.4 个百分点');
  expect(screen.getByRole('region', { name: '参与与成交配合' })).toHaveTextContent('成交参与差 0.0 个百分点');
  expect(screen.getByRole('region', { name: '强弱两端分析' })).toHaveTextContent('30.0%');
  expect(screen.queryByRole('option', { name: '过期指数' })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('宽度对照指数'), { target: { value: '399001.SZ' } });
  expect(screen.getByRole('region', { name: '指数与个股温差' })).toHaveTextContent('-0.1 个百分点');
});

test('distribution mode, bin selection and comparison shortcuts update the shared analysis', async () => {
  show();
  await screen.findByText(/彩色为所选日/);
  fireEvent.click(screen.getByRole('button', { name: '家数' }));
  const bucket = screen.getByRole('button', { name: '≥ 7%，10 家' });
  fireEvent.click(bucket);
  expect(bucket).toHaveAttribute('aria-pressed', 'true');
  expect(document.querySelector('.mbw-bucket-detail')).toHaveTextContent('10 家 · 10.0%');
  fireEvent.click(screen.getByRole('button', { name: '前5个观测日' }));
  expect(screen.getByLabelText('对照日期')).toHaveValue('2026-09-23');
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('2026-09-23'));
  fireEvent.change(screen.getByLabelText('对照日期'), { target: { value: 'none' } });
  expect(screen.getByRole('status')).toHaveTextContent('仅显示所选日');
  expect(document.querySelectorAll('.mbw-prior-tick')).toHaveLength(0);
  expect(document.querySelectorAll('.mbw-histogram-bar > i')).toHaveLength(0);
  expect(screen.queryByText(/较对照/)).not.toBeInTheDocument();
});

test('sorts and filters both improving and weakening proportions without mixing units', async () => {
  const previous = snapshot(dates[1]);
  previous.breadth!.advanceRatio = .8;
  previous.breadth!.trendBreadth!.ma20Ratio = .5;
  vi.mocked(api).mockResolvedValue(previous);
  show();
  const changes = screen.getByRole('region', { name: '结构变化排序' });
  await waitFor(() => expect(within(changes).getAllByRole('listitem')[0]).toHaveTextContent('上涨家数占比'));
  fireEvent.change(screen.getByLabelText('结构变化筛选'), { target: { value: 'up' } });
  expect(within(changes).getAllByRole('listitem')).toHaveLength(1);
  expect(changes).toHaveTextContent('MA20 参与度');
  fireEvent.change(screen.getByLabelText('结构变化筛选'), { target: { value: 'down' } });
  expect(within(changes).getAllByRole('listitem')).toHaveLength(1);
  expect(changes).toHaveTextContent('-40.0 pp');
});

test('rejects wrong-date comparison and retries without hiding the current analysis', async () => {
  vi.mocked(api).mockResolvedValueOnce(snapshot(dates[0]));
  show();
  expect(await screen.findByText('对照日暂不可用')).toBeInTheDocument();
  expect(screen.getByText('指数上涨，中位数下跌')).toBeInTheDocument();
  expect(document.querySelectorAll('.mbw-prior-tick')).toHaveLength(0);
  fireEvent.click(screen.getByRole('button', { name: '重试对照' }));
  await screen.findByText(/彩色为所选日/);
  expect(screen.queryByText('对照日暂不可用')).not.toBeInTheDocument();
});

test('ignores late replies after comparison selection changes', async () => {
  let resolveOld: (value: MarketPulseWorkspace) => void = () => {};
  vi.mocked(api).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
  show();
  await waitFor(() => expect(api).toHaveBeenCalledTimes(1));
  const oldSignal = vi.mocked(api).mock.calls[0][1]?.signal;
  fireEvent.change(screen.getByLabelText('对照日期'), { target: { value: dates[2] } });
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('2026-09-30 对照 2026-09-28'));
  expect(oldSignal?.aborted).toBe(true);
  await act(async () => resolveOld(snapshot(dates[1], 9999)));
  expect(screen.getByRole('status')).toHaveTextContent('2026-09-30 对照 2026-09-28');
  expect(document.querySelector('.mbw-scope')).not.toHaveTextContent('9,999');
});

test('supports missing breadth and disables unavailable shortcuts without inventing zero readings', () => {
  show({ businessDate: dates[0], qualityStatus: 'PARTIAL' }, [dates[0]]);
  expect(screen.getByRole('status')).toHaveTextContent('仅显示所选日');
  expect(screen.getByRole('button', { name: '上一观测日' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '前5个观测日' })).toBeDisabled();
  expect(screen.getByText('所选日暂无涨跌分档数据。')).toBeInTheDocument();
  expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
  expect(api).not.toHaveBeenCalled();
});

test('delegates current-date navigation and refresh, resetting comparison after a date change', async () => {
  const { onLoad, onRefresh, rerender } = show();
  fireEvent.change(screen.getByLabelText('历史截面'), { target: { value: dates[1] } });
  expect(onLoad).toHaveBeenCalledWith(dates[1]);
  fireEvent.click(screen.getByRole('button', { name: '立即补刷新' }));
  expect(onRefresh).toHaveBeenCalledOnce();
  fireEvent.change(screen.getByLabelText('对照日期'), { target: { value: 'none' } });
  rerender(<BreadthWorkspace key={dates[1]} workspace={snapshot(dates[1])} dates={dates} refreshing={false} onLoad={onLoad} onRefresh={onRefresh} />);
  expect(screen.getByLabelText('对照日期')).toHaveValue(dates[2]);
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('2026-09-29 对照 2026-09-28'));
});

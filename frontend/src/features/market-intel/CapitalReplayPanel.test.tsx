import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { CapitalReplayPanel } from './CapitalReplayPanel';
import { minuteLabel } from './capitalReplaySeries';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const instrument = { id: 7, code: '600519', type: 'STOCK' as const, name: '贵州茅台', market: 'SH' };

function minutes(from: number) {
  return [100, 100.5, 101.2, 100.5, ...Array(12).fill(100)].map((price, index) => ({
    time: `${minuteLabel(from + index)}:00`, price, tradeAmount: 100_000, largeNet: -20_000, superLargeNet: 0
  }));
}

beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockResolvedValue({
    sessions: [{
      date: '2026-11-10', priceProvider: 'EASTMONEY', flowProvider: 'EASTMONEY_CAPITAL_FLOW',
      minutes: [...minutes(571), ...minutes(871)]
    }], warnings: []
  });
});

test('links chronological cards to the selected price range and subsequent performance', async () => {
  const user = userEvent.setup();
  render(<CapitalReplayPanel instrument={instrument} dragonTiger={null} />);
  const timeline = await screen.findByLabelText('关键转折时间线');
  await user.click(within(timeline).getByRole('button', { name: /全部区间/ }));
  const cards = within(screen.getByRole('list', { name: '全部区间列表' })).getAllByRole('button');
  expect(cards).toHaveLength(4);
  expect(cards[0]).toHaveTextContent('09:31–09:33');
  expect(cards[0]).toHaveTextContent('涨幅全部回吐');
  await user.click(cards[2]);
  expect(cards[2]).toHaveAttribute('aria-pressed', 'true');
  const detail = screen.getByLabelText('区间数据');
  expect(detail).toHaveTextContent('14:31–14:33');
  expect(detail).toHaveTextContent('涨幅全部回吐');
  expect(detail).toHaveTextContent('截至 14:43');
  await user.click(within(detail).getByText('查看区间分钟明细'));
  expect(within(detail).getAllByRole('row')).toHaveLength(4);

  const chart = screen.getByRole('group', { name: '分时价格、成交额和大额净额图' });
  chart.focus();
  await user.keyboard('{ArrowRight}');
  expect(detail).toHaveTextContent('14:34');
  expect(within(detail).queryByText('涨幅全部回吐')).not.toBeInTheDocument();
});

test('keeps afternoon turns available after changing the time span', async () => {
  const user = userEvent.setup();
  render(<CapitalReplayPanel instrument={instrument} dragonTiger={null} />);
  const timeline = await screen.findByLabelText('关键转折时间线');
  await user.click(screen.getByRole('button', { name: '午后' }));
  expect(within(screen.getByRole('list', { name: '盘面阶段列表' })).getAllByRole('button', { name: /^\d{2} / })).toHaveLength(1);
  await user.click(within(timeline).getByRole('button', { name: /全部区间/ }));
  const cards = within(screen.getByRole('list', { name: '全部区间列表' })).getAllByRole('button');
  expect(cards).toHaveLength(2);
  expect(cards[0]).toHaveTextContent('14:31–14:33');
  expect(within(timeline).queryByText('09:31–09:33')).not.toBeInTheDocument();
  await user.click(cards[0]);
  expect(screen.getByLabelText('区间数据')).toHaveTextContent('14:31–14:33');
});

test('shows price turns with explicit missing fund coverage and a pending follow-up', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockResolvedValue({ sessions: [{
    date: '2026-11-10', priceProvider: 'EASTMONEY', flowProvider: null,
    minutes: minutes(571).slice(0, 3).map((point) => ({ ...point, largeNet: null, superLargeNet: null }))
  }], warnings: [] });
  render(<CapitalReplayPanel instrument={instrument} dragonTiger={null} />);
  const timeline = await screen.findByLabelText('关键转折时间线');
  await user.click(within(timeline).getByRole('button', { name: /全部区间/ }));
  const card = within(screen.getByRole('list', { name: '全部区间列表' })).getByRole('button');
  expect(card).toHaveTextContent('快速上涨');
  expect(card).toHaveTextContent('资金覆盖 0/3 分钟');
  expect(card).toHaveTextContent('待观察');
  expect(screen.getByText('该交易日暂未取得分钟资金净额。')).toBeInTheDocument();
});

test('explains missing minute prices instead of claiming there were no turning points', async () => {
  vi.mocked(api).mockResolvedValue({ sessions: [{
    date: '2026-11-11', priceProvider: 'EASTMONEY_DIRECT', flowProvider: 'EASTMONEY_DIRECT',
    minutes: minutes(571).map((point, index) => ({
      ...point, price: index === 15 ? 100 : null, tradeAmount: index === 15 ? 100_000 : null
    }))
  }], warnings: ['INTRADAY_MARKET_UNAVAILABLE'] });
  render(<CapitalReplayPanel instrument={instrument} dragonTiger={null} />);
  const timeline = await screen.findByLabelText('关键转折时间线');
  expect(timeline).toHaveTextContent('连续分钟价格缺失，暂不能识别转折');
  expect(screen.getByText(/价格覆盖 1 个，成交额覆盖 1 个/)).toBeInTheDocument();
  expect(within(timeline).queryByRole('button')).not.toBeInTheDocument();
});

test('defaults to a coarse stage view and expands every constituent interval with chart selection intact', async () => {
  const user = userEvent.setup();
  const { container } = render(<CapitalReplayPanel instrument={instrument} dragonTiger={null} />);
  const stages = await screen.findByRole('list', { name: '盘面阶段列表' });
  expect(within(stages).getAllByRole('button', { name: /^\d{2} / })).toHaveLength(2);
  expect(container.querySelectorAll('.event-marker')).toHaveLength(2);
  expect(within(stages).getAllByText('冲高回落')).toHaveLength(2);
  await user.click(screen.getAllByText('展开 2 个细区间')[0]);
  const intervals = screen.getByRole('list', { name: '09:31–09:46 的细分区间' });
  const card = within(intervals).getAllByRole('button')[0];
  await user.click(card);
  expect(card).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByLabelText('区间数据')).toHaveTextContent('09:31–09:33');
  expect(screen.getByLabelText('区间数据')).toHaveTextContent('最大回撤');
  expect(screen.getByRole('button', { name: '盘面阶段' })).toHaveAttribute('aria-pressed', 'true');
});

test('does not include an afternoon stage that only touches the tail boundary', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockResolvedValue({ sessions: [{
    date: '2026-11-10', priceProvider: 'EASTMONEY', flowProvider: 'EASTMONEY_CAPITAL_FLOW', minutes: minutes(860)
  }], warnings: [] });
  render(<CapitalReplayPanel instrument={instrument} dragonTiger={null} />);
  await screen.findByRole('list', { name: '盘面阶段列表' });
  await user.click(screen.getByRole('button', { name: '尾盘' }));
  const stages = screen.getByRole('list', { name: '盘面阶段列表' });
  expect(within(stages).getAllByRole('button', { name: /^\d{2} / })).toHaveLength(1);
  expect(stages).toHaveTextContent('14:30–14:35');
  expect(stages).not.toHaveTextContent('14:20–14:30');
});

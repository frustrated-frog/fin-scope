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
  const cards = within(timeline).getAllByRole('button');
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
  const cards = within(timeline).getAllByRole('button');
  expect(cards).toHaveLength(2);
  expect(cards[0]).toHaveTextContent('14:31–14:33');
  expect(within(timeline).queryByText('09:31–09:33')).not.toBeInTheDocument();
  await user.click(cards[0]);
  expect(screen.getByLabelText('区间数据')).toHaveTextContent('14:31–14:33');
});

test('shows price turns with explicit missing fund coverage and a pending follow-up', async () => {
  vi.mocked(api).mockResolvedValue({ sessions: [{
    date: '2026-11-10', priceProvider: 'EASTMONEY', flowProvider: null,
    minutes: minutes(571).slice(0, 3).map((point) => ({ ...point, largeNet: null, superLargeNet: null }))
  }], warnings: [] });
  render(<CapitalReplayPanel instrument={instrument} dragonTiger={null} />);
  const timeline = await screen.findByLabelText('关键转折时间线');
  const card = within(timeline).getByRole('button');
  expect(card).toHaveTextContent('快速上涨');
  expect(card).toHaveTextContent('资金覆盖 0/3 分钟');
  expect(card).toHaveTextContent('待观察');
  expect(screen.getByText('该交易日暂未取得分钟资金净额。')).toBeInTheDocument();
});

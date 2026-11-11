import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { ReplayFollowup } from './CapitalReplayFollowup';
import { replayOutcome } from './capitalReplayOutcome';
import { minuteLabel } from './capitalReplaySeries';
import type { ReplayPoint } from './capitalReplaySeries';

function series(prices: Array<number | null>, from = 571): ReplayPoint[] {
  return prices.map((price, index) => ({ minute: from + index, time: `${minuteLabel(from + index)}:00`,
    price, tradeAmount: null, largeNet: null, superLargeNet: null, net: null }));
}

test('shows the interim surge, running drawdown and closing retention together', () => {
  const points = [...series([100, 101, 102, 103, 105, 104, ...Array(7).fill(102.1)]), ...series([100.5], 900)];
  render(<ReplayFollowup outcome={replayOutcome(points, { from: 571, to: 573 })} />);
  expect(screen.getByText('冲高后回落')).toBeInTheDocument();
  expect(screen.getByTitle('后续观察窗口内，相对区间末价的最高涨幅')).toHaveTextContent('+2.94%');
  expect(screen.getByText(/最大回撤/)).toHaveTextContent('−2.76%');
  expect(screen.getByText('原涨幅保留 25%')).toBeInTheDocument();
});

test('does not display path extrema across a gap while still displaying a known closing return', () => {
  const points = [...series([100, 101, 102, null, ...Array(9).fill(103)]), ...series([101], 900)];
  render(<ReplayFollowup outcome={replayOutcome(points, { from: 571, to: 573 })} />);
  expect(screen.getByText('后续分钟缺失，暂不判断')).toBeInTheDocument();
  expect(screen.queryByText(/最大回撤/)).not.toBeInTheDocument();
  expect(screen.getByText(/收盘较末价/)).toHaveTextContent('−0.98%');
  expect(screen.getByText('原涨幅保留 50%')).toBeInTheDocument();
});

test('shows an interim return to the starting price and an explicit missing close', () => {
  render(<ReplayFollowup outcome={replayOutcome(series([100, 101, 102, 100, ...Array(9).fill(103)]),
    { from: 571, to: 573 })} />);
  expect(screen.getByText('期间曾跌回起涨点')).toBeInTheDocument();
  expect(screen.getByText('收盘价未取得')).toBeInTheDocument();
});

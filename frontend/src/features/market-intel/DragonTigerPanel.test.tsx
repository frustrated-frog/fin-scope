import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';

import { DragonTigerPanel } from './DragonTigerPanel';
import type { DragonTigerView } from './marketIntelTypes';

const dragonTigerView: DragonTigerView = {
  instrument: { id: 7, code: '000021', type: 'STOCK', name: '深科技', market: 'SZ' },
  range: { days: 120, from: '2026-03-19', to: '2026-07-16' },
  records: [{
    id: 101,
    tradeDate: '2026-07-15',
    externalId: '100373909',
    reason: '日跌幅偏离值达到7%的前5只证券',
    closePrice: 47.26,
    changeRate: -9.9981,
    buyAmount: 909610681.71,
    sellAmount: 1305481357.84,
    netAmount: -395870676.13,
    turnoverRate: 11.6572,
    qualityStatus: 'COMPLETE',
    buySeats: [{
      id: 1,
      direction: 'BUY',
      rank: 1,
      seatName: '机构专用',
      buyAmount: 206268197.34,
      sellAmount: 202192125.75,
      netAmount: 4076071.59,
      institutional: true,
      northbound: false
    }],
    sellSeats: [{
      id: 2,
      direction: 'SELL',
      rank: 1,
      seatName: '深股通专用',
      buyAmount: 247633752.43,
      sellAmount: 599113993.77,
      netAmount: -351480241.34,
      institutional: false,
      northbound: true
    }]
  }],
  health: {
    status: 'FRESH_PRIMARY',
    providerCode: 'EASTMONEY_DRAGON_TIGER',
    asOf: '2026-07-16T16:00:00',
    warnings: []
  }
};

test('shows summary facts and expands buy and sell seats', async () => {
  const user = userEvent.setup();
  render(<DragonTigerPanel view={dragonTigerView} />);

  expect(screen.getByText('近120日上榜 1 次')).toBeInTheDocument();
  expect(screen.getByText('日跌幅偏离值达到7%的前5只证券')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '查看席位明细' }));
  expect(screen.getByText('买入席位 TOP5')).toBeInTheDocument();
  expect(screen.getByText('卖出席位 TOP5')).toBeInTheDocument();
  expect(screen.getByText('机构专用')).toBeInTheDocument();
  expect(screen.getByText('深股通专用')).toBeInTheDocument();
});

test('explains a confirmed empty window without treating it as an error', () => {
  render(<DragonTigerPanel view={{ ...dragonTigerView, records: [] }} />);

  expect(screen.getByText(/近 120 日没有公开龙虎榜记录/)).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('does not describe an unrefreshed dimension as a confirmed empty window', () => {
  render(<DragonTigerPanel view={{
    ...dragonTigerView,
    records: [],
    health: {
      status: 'NOT_REFRESHED',
      providerCode: '',
      asOf: null,
      warnings: ['尚未刷新龙虎榜数据']
    }
  }} />);

  expect(screen.getByText(/尚未刷新龙虎榜事实/)).toBeInTheDocument();
  expect(screen.queryByText(/没有公开龙虎榜记录/)).not.toBeInTheDocument();
});

test('shows stale and unavailable states', () => {
  const { rerender } = render(<DragonTigerPanel view={{
    ...dragonTigerView,
    health: {
      status: 'STALE_FALLBACK',
      providerCode: 'EASTMONEY_DRAGON_TIGER',
      asOf: '2026-07-15T16:00:00',
      warnings: ['龙虎榜在线刷新失败，正在显示最近成功数据']
    }
  }} />);
  expect(screen.getByRole('alert')).toHaveTextContent('最近成功数据');

  rerender(<DragonTigerPanel view={{
    ...dragonTigerView,
    records: [],
    health: {
      status: 'UNAVAILABLE',
      providerCode: 'EASTMONEY_DRAGON_TIGER',
      asOf: null,
      warnings: ['龙虎榜数据源暂不可用']
    }
  }} />);
  expect(screen.getByRole('alert')).toHaveTextContent('龙虎榜数据源暂不可用');
});

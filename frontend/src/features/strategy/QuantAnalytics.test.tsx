import { render, screen, within } from '@testing-library/react';
import { expect, test } from 'vitest';
import { BacktestOverview, PortfolioOverview } from './QuantAnalytics';

test('uses daily closes for drawdown and omits unavailable target weights', () => {
  render(<BacktestOverview capital={100} history={{fills:0,fees:0,curve:[
    {at:'2026-11-23T10:00:00',equity:140},{at:'2026-11-23T15:00:00',equity:110},
    {at:'2026-11-24T15:00:00',equity:99}
  ]}}>净值</BacktestOverview>);
  expect(screen.getByText('-10.00%')).toBeInTheDocument();
  expect(screen.queryByText('调仓目标股票仓位')).toBeNull();
});

test('computes stock exposure separately from receivables and cash', () => {
  render(<PortfolioOverview capital={1000} book={{equity:1000,cash:350,dividendReceivable:50,
    realized:0,fills:1,fees:0,positions:{one:{lots:[['2026-11-23',100]],price:6,cost:595}}}}
    daily={[]} name={() => '样本股票'}/>);
  expect(screen.getByText('60.0%')).toBeInTheDocument();
  expect(within(screen.getByText('应收股息').closest('li')!).getByText('5.00%')).toBeInTheDocument();
  expect(screen.getByText('样本不足')).toBeInTheDocument();
});

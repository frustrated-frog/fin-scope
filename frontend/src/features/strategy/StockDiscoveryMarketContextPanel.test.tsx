import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { StockDiscoveryMarketContextPanel } from './StockDiscoveryMarketContextPanel';
import { buildResearchContext } from '../market-pulse/marketResearch';

test('sector filtering context does not invent a market risk judgment', () => {
  render(<StockDiscoveryMarketContextPanel context={buildResearchContext([{ sectorCode: '1', sectorName: '半导体', rotationScore: 70 }], '2026-09-11')} />);
  expect(screen.getByText('来自行业机会筛选器')).toBeInTheDocument();
  expect(screen.getByText('半导体')).toBeInTheDocument();
  expect(screen.queryByText('均衡试错')).not.toBeInTheDocument();
  expect(screen.queryByText('风险姿态')).not.toBeInTheDocument();
});

import { fireEvent, render, screen, within } from '@testing-library/react';
import { expect, test, vi } from 'vitest';

import { SectorOpportunityMap } from './SectorOpportunityMap';

const sectors = [
  {
    sectorCode: 'BK1040', sectorName: '创新药', return1d: 2.1, return5d: 5.8,
    return20d: 9.6, excessReturn5d: 4.2, mainNetInflow: 3200000000,
    flowRank: 2, previousFlowRank: 8, breadthRatio: 0.76, persistenceDays: 4,
    crowdingScore: 58, rotationScore: 78, stage: 'PERSISTENT',
    explanations: ['5日收益 5.80%', '行业上涨家数占比 76%']
  },
  {
    sectorCode: 'BK0737', sectorName: '贵金属', return1d: 0.8, return5d: 2.2,
    return20d: 4.1, excessReturn5d: 0.6, mainNetInflow: 1100000000,
    flowRank: 5, previousFlowRank: 6, breadthRatio: 0.61, persistenceDays: 2,
    crowdingScore: 31, rotationScore: 61, stage: 'EMERGING', explanations: ['资金排名改善']
  },
  {
    sectorCode: 'BK1036', sectorName: '半导体', return1d: -1.4, return5d: -3.5,
    return20d: 1.1, excessReturn5d: -5.1, mainNetInflow: -2800000000,
    flowRank: 28, previousFlowRank: 12, breadthRatio: 0.24, persistenceDays: 0,
    crowdingScore: 19, rotationScore: 27, stage: 'FADING', explanations: ['5日走势转弱']
  }
];

test('explores sector heatmap without reproducing stock discovery', () => {
  const onOpenStockDiscovery = vi.fn();
  render(<SectorOpportunityMap sectors={sectors} onOpenStockDiscovery={onOpenStockDiscovery} />);

  expect(screen.getByRole('heading', { name: '行业机会地图' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /贵金属，5日/ }));

  const detail = screen.getByRole('region', { name: '行业详情' });
  expect(within(detail).getByRole('heading', { name: '贵金属' })).toBeInTheDocument();
  expect(within(detail).getByText('萌芽')).toBeInTheDocument();
  expect(within(detail).getByText('61%')).toBeInTheDocument();
  expect(screen.queryByText('股票候选')).not.toBeInTheDocument();

  fireEvent.click(within(detail).getByRole('button', { name: '到股票发现筛选该方向' }));
  expect(onOpenStockDiscovery).toHaveBeenCalledOnce();
});

test('switches to a relative-strength rotation map', () => {
  render(<SectorOpportunityMap sectors={sectors} onOpenStockDiscovery={vi.fn()} />);

  fireEvent.click(screen.getByRole('button', { name: '轮动地图' }));

  expect(screen.getByText('领先')).toBeInTheDocument();
  expect(screen.getByText('改善')).toBeInTheDocument();
  expect(screen.getByText('减弱')).toBeInTheDocument();
  expect(screen.getByText('落后')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /创新药，相对强度/ })).toBeInTheDocument();
});

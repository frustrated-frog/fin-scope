import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import type { WatchlistItem } from '../../shared/types';
import { WatchlistMatters } from './WatchlistMatters';

test('matches verified matters by stock code and excludes funds and removed stocks', () => {
  const stock: WatchlistItem = { id: 1, code: '603042', type: 'STOCK', name: '华脉科技', quoteValid: false };
  const fund: WatchlistItem = { id: 2, code: '600667', type: 'FUND', quoteValid: false };
  const uncovered: WatchlistItem = { id: 3, code: '600519', type: 'STOCK', quoteValid: false };
  const { rerender } = render(<WatchlistMatters items={[stock, fund, uncovered]} />);

  expect(screen.getByRole('button', { name: '查看全部 2' })).toBeInTheDocument();
  expect(screen.getByText('敏捷智慧绿色数据中心项目')).toBeInTheDocument();
  expect(screen.getByText('5G 无线射频器件研发及产业化')).toBeInTheDocument();
  expect(screen.queryByText('华虹 FAB9B 工程总承包')).not.toBeInTheDocument();

  rerender(<WatchlistMatters items={[fund, uncovered]} />);
  expect(screen.getByText('首批核对样本暂未覆盖当前自选股票。')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /查看全部/ })).not.toBeInTheDocument();
});

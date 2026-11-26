import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { DashboardIndexBoard } from './DashboardIndexBoard';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

test('keeps independent index quotes when one feed fails and identifies cached prices', async () => {
  vi.mocked(api).mockImplementation(async path => {
    if (path.endsWith('IXIC')) {
      throw new Error('offline');
    }
    return { series: { asOf: '2024-01-02T21:00:00Z', price: 5050, reference: 5000 }, fallback: path.endsWith('DJI') };
  });
  const onChangeView = vi.fn();
  render(<DashboardIndexBoard indices={{ phase: 'ready', data: [] }} refreshRevision={0}
    onChangeView={onChangeView} reloadIndices={vi.fn()} />);
  const us = within(screen.getByRole('region', { name: '美股指数' }));
  expect(await us.findByText('缓存行情')).toBeVisible();
  expect(us.getByText('暂不可用')).toBeVisible();
  expect(us.getAllByText('+1.00%')).toHaveLength(2);
  expect(us.getByText('01-03 05:00')).toBeVisible();
  await userEvent.click(us.getByRole('button'));
  expect(onChangeView).toHaveBeenCalledWith('globalMarkets');
});

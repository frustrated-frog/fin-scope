import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import type { WatchlistItem } from '../../shared/types';
import { WatchlistStockChanges } from './WatchlistStockChanges';

const item: WatchlistItem = { id: 1, code: '600519', name: '贵州茅台', type: 'STOCK', quoteValid: true, price: 100, changePct: 1, quoteDate: '2026-11-05' };

beforeEach(() => {
  localStorage.removeItem('watchlist.stockLastVisit.v1');
});

test('automatically establishes a baseline and shows changes on the next page visit', async () => {
  const first = render(<WatchlistStockChanges items={[item]} ready onOpen={vi.fn()} />);
  expect(screen.getByText('首次查看，建立本地基线')).toBeInTheDocument();
  await waitFor(() => expect(localStorage.getItem('watchlist.stockLastVisit.v1')).toContain('600519'));
  first.unmount();
  const onOpen = vi.fn();
  const current = { ...item, price: 106, changePct: 6, attributionReportId: 12, attributionReportDate: '2026-11-06' };
  render(<WatchlistStockChanges items={[current]} ready onOpen={onOpen} />);
  expect(screen.getByText('3 项变化')).toBeInTheDocument();
  await userEvent.click(screen.getByText('股票变化回顾'));
  await userEvent.click(screen.getByRole('button', { name: /贵州茅台 有新的归因报告/ }));
  expect(onOpen).toHaveBeenCalledWith(current, true);
});

test('does not add a stock change panel to a fund-only list or overwrite baseline while loading', () => {
  const { rerender, container } = render(<WatchlistStockChanges items={[item]} ready={false} onOpen={vi.fn()} />);
  expect(container).toBeEmptyDOMElement();
  expect(localStorage.getItem('watchlist.stockLastVisit.v1')).toBeNull();
  rerender(<WatchlistStockChanges items={[{ ...item, type: 'FUND' }]} ready onOpen={vi.fn()} />);
  expect(container).toBeEmptyDOMElement();
  expect(localStorage.getItem('watchlist.stockLastVisit.v1')).toBeNull();
});

test('an invalid refresh cannot regress an already accepted quote or report in this visit', async () => {
  const first = render(<WatchlistStockChanges items={[item]} ready onOpen={vi.fn()} />);
  first.unmount();
  const current = { ...item, price: 110, quoteTime: '2026-11-06T15:00:00', quoteDate: '2026-11-06', attributionReportId: 8, attributionReportDate: '2026-11-06' };
  const { rerender } = render(<WatchlistStockChanges items={[current]} ready onOpen={vi.fn()} />);
  await waitFor(() => expect(JSON.parse(localStorage.getItem('watchlist.stockLastVisit.v1')!).stocks['600519'].price).toBe(110));
  rerender(<WatchlistStockChanges items={[{ ...item, quoteValid: false, attributionReportId: 2, attributionReportDate: '2026-11-05' }]} ready onOpen={vi.fn()} />);
  const accepted = JSON.parse(localStorage.getItem('watchlist.stockLastVisit.v1')!).stocks['600519'];
  expect(accepted.price).toBe(110);
  expect(accepted.reportId).toBe(8);
});

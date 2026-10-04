import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { PanoramaCatalysts } from './PanoramaCatalysts';
import type { MarketPulseWorkspace } from './marketPulseTypes';

const sectors = [{ sectorCode: 'A', sectorName: '电子', rotationScore: 60 }, { sectorCode: 'B', sectorName: '银行', rotationScore: 40 }];
const workspace: MarketPulseWorkspace = { businessDate: '2026-09-30', qualityStatus: 'READY', eventConfirmations:
  Array.from({ length: 4 }, (_, i) => ({ radarEventId: i, title: `催化${i}`, sectorCode: 'A', sectorName: '电子', eventScore: 80, marketReactionScore: 60 })) };
const props = { workspace, sectors, selectedCode: '', onSelect: vi.fn(), businessDate: '2026-09-30', hasSnapshot: true };

beforeEach(() => { vi.stubGlobal('fetch', vi.fn()); });

test('reuses current confirmations and keeps additional catalysts behind a disclosure', () => {
  render(<PanoramaCatalysts {...props} />);
  expect(screen.getByText('催化2')).toBeVisible();
  expect(screen.getByText('催化3')).not.toBeVisible();
  fireEvent.click(screen.getByText('展开其余 1 条催化'));
  expect(screen.getByText('催化3')).toBeVisible();
  expect(fetch).not.toHaveBeenCalled();
});

test('keeps an unrelated sector empty and returns to all industries without another request', () => {
  const onSelect = vi.fn();
  render(<PanoramaCatalysts {...props} selectedCode="B" onSelect={onSelect} />);
  expect(screen.getByText(/该行业在所选截面中暂无关联催化/)).toBeInTheDocument();
  expect(screen.queryByText('催化0')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '查看全部行业' }));
  expect(onSelect).toHaveBeenCalledWith('');
  expect(fetch).not.toHaveBeenCalled();
});

test('does not substitute current events for an earlier date without a saved snapshot', () => {
  render(<PanoramaCatalysts {...props} businessDate="2026-09-28" hasSnapshot={false} />);
  expect(screen.getByText('这一天尚未保存行业催化截面。')).toBeInTheDocument();
  expect(screen.queryByText('催化0')).not.toBeInTheDocument();
  expect(fetch).not.toHaveBeenCalled();
});

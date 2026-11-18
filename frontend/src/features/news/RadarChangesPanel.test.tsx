import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { RadarChangesPanel } from './RadarChangesPanel';
import { api } from '../../shared/api/client';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
vi.mock('../../shared/api/useViewRevision', () => ({ useViewRevision: vi.fn() }));

const stamp = '2026-11-18T13:15:00';
const row = { eventId: '90071992547409937', title: '回购公告', kind: 'PROGRESS', changedAt: stamp,
  firstObservedAt: stamp, newFact: '公司已取得贷款承诺函。' };
const first = { items: [row], asOf: stamp, hasMore: true };
beforeEach(() => vi.mocked(api).mockReset());

test('filters and pagination use read-only requests and a fixed window', async () => {
  vi.mocked(api).mockResolvedValueOnce(first).mockResolvedValueOnce({ items: [{...row, eventId: '2', title: '另一则公告'}], asOf: stamp, hasMore: false })
    .mockResolvedValueOnce({ items: [], asOf: stamp, hasMore: false });
  render(<RadarChangesPanel/>);
  await screen.findByText('公司已取得贷款承诺函。');
  fireEvent.click(screen.getByRole('button', {name:'查看更多'}));
  await screen.findByText('另一则公告');
  expect(api).toHaveBeenNthCalledWith(2, '/api/research-radar/changes?minutes=30&page=1&asOf=2026-11-18T13%3A15%3A00');
  fireEvent.click(screen.getByRole('button',{name:'传播加快'}));
  await screen.findByText('最近 30 分钟内没有此类变化。');
  expect(api).toHaveBeenLastCalledWith('/api/research-radar/changes?minutes=30&page=0&kind=SPREAD');
  expect(screen.queryByText('另一则公告')).not.toBeInTheDocument();
});

test('a slow previous window cannot overwrite the new window, and errors allow retry', async () => {
  let finish!: (value: unknown) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockRejectedValueOnce(new Error('读取失败')).mockResolvedValueOnce(first);
  render(<RadarChangesPanel/>);
  fireEvent.change(screen.getByRole('combobox',{name:'观察时段'}),{target:{value:'60'}});
  await screen.findByRole('alert');
  fireEvent.click(screen.getByRole('button',{name:'刷新'}));
  await screen.findByText('公司已取得贷款承诺函。');
  await act(async () => finish({ asOf: stamp, items: [], hasMore: false }));
  await waitFor(() => expect(screen.getByText('公司已取得贷款承诺函。')).toBeInTheDocument());
  expect(api).toHaveBeenLastCalledWith('/api/research-radar/changes?minutes=60&page=0');
});

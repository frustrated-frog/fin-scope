import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { NewsRiverPanel } from './NewsRiverPanel';
import { shanghaiToday } from './newsRiverTypes';
import type { RiverOverview } from './newsRiverTypes';

let notify: () => void;
vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
vi.mock('../../shared/api/useViewRevision', () => ({ useViewRevision: (_: string[], callback: () => void) => { notify = callback; } }));
const day = shanghaiToday();
const first = { id: '10', publishedAt: `${day}T10:00:00`, eventCount: 1 };
const second = { id: '11', publishedAt: `${day}T10:30:00`, eventCount: 1 };
const overview: RiverOverview = { date: day, earliestDate: day, frames: [first, second], buckets: [
  { start: `${day}T10:00:00`, finance: 1, technology: 0, politics: 0, other: 0 },
  { start: `${day}T10:30:00`, finance: 0, technology: 1, politics: 0, other: 0 },
] };
const oldItem = { eventId: '6140854697134002116', eventKey: 'key', title: '当时的标题', summary: '当时的摘要', category: 'FINANCE', rank: 1, hotspotScore: 88, change: 'FIRST_ENTRY', observedAt: first.publishedAt };
beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation(path => {
    if (path.startsWith('/api/news/river?')) {
      return Promise.resolve(overview);
    }
    const params = new URLSearchParams(path.split('?')[1]);
    const frame = params.get('batchId') === '10' ? first : second;
    const category = params.get('category');
    return Promise.resolve({ frame, items: category === 'TECHNOLOGY' ? [] : [{ ...oldItem, title: frame.id === '10' ? '当时的标题' : '后来更新的标题' }], total: category === 'TECHNOLOGY' ? 0 : 1, page: 0 });
  });
});
test('selects immutable earlier frames, expands their saved summary and opens current details explicitly', async () => {
  const open = vi.fn(); render(<NewsRiverPanel onOpenCurrent={open} />);
  expect(await screen.findByRole('button', { name: /后来更新的标题/ })).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: '上一个时刻' }));
  expect(await screen.findByRole('button', { name: /当时的标题/ })).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: /当时的标题/ }));
  expect(screen.getByText('当时的摘要')).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: '查看实时详情 ↗' }));
  expect(open).toHaveBeenCalledWith('6140854697134002116');
  expect(api).toHaveBeenCalledWith(expect.stringContaining('batchId=10'));
});
test('clicking a peak selects its historical time and category filters the same frame', async () => {
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  await screen.findByRole('button', { name: /后来更新的标题/ });
  await userEvent.click(screen.getByRole('button', { name: '10:00 新收录 1 个事件' }));
  expect(await screen.findByRole('heading', { name: '10:00–10:30 新收录的事件' })).toBeVisible();
  await waitFor(() => expect(api).toHaveBeenCalledWith(expect.stringMatching(/batchId=10.*bucket=/)));
  await userEvent.click(screen.getByRole('button', { name: '科技' }));
  expect(await screen.findByText('这个切面暂时没有事件')).toBeVisible();
  expect(api).toHaveBeenCalledWith(expect.stringMatching(/batchId=10&category=TECHNOLOGY/));
});
test('background updates preserve the viewed frame until the user accepts them', async () => {
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  await screen.findByRole('button', { name: /后来更新的标题/ });
  await userEvent.click(screen.getByRole('button', { name: '上一个时刻' }));
  await screen.findByRole('button', { name: /当时的标题/ });
  vi.mocked(api).mockImplementation(path => path.startsWith('/api/news/river?')
    ? Promise.resolve({ ...overview, frames: [...overview.frames, { ...second, id: '12' }], buckets: [...overview.buckets, { ...overview.buckets[0], start: `${day}T11:00:00` }] })
    : Promise.resolve({ frame: { ...second, id: '12' }, items: [{ ...oldItem, title: '新的榜单内容' }], total: 1, page: 0 }));
  act(() => notify());
  expect(await screen.findByText(/又有 1 个新事件汇入/)).toBeVisible();
  expect(screen.getByRole('button', { name: /当时的标题/ })).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: '查看更新 →' }));
  expect(await screen.findByRole('button', { name: /新的榜单内容/ })).toBeVisible();
});
test('empty archives finish loading and do not invent past frames', async () => {
  vi.mocked(api).mockResolvedValue({ ...overview, frames: [], buckets: [] });
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  expect(await screen.findByText('时间，从第一次记录开始')).toBeVisible();
  expect(screen.getByRole('slider', { name: '选择历史时刻' })).toBeDisabled();
  expect(screen.queryByRole('button', { name: /当时的标题/ })).not.toBeInTheDocument();
});
test('an empty earlier date can return to today using the realtime button', async () => {
  const previousDay = new Date(new Date(`${day}T00:00:00Z`).getTime() - 86_400_000).toISOString().slice(0, 10);
  const normal = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation(path => path === `/api/news/river?date=${previousDay}`
    ? Promise.resolve({ ...overview, date: previousDay, earliestDate: previousDay, frames: [], buckets: [] }) : normal(path));
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  await screen.findByRole('button', { name: /后来更新的标题/ });
  fireEvent.change(screen.getByLabelText('浏览日期'), { target: { value: previousDay } });
  await screen.findByText('时间，从第一次记录开始');
  await userEvent.click(screen.getByRole('button', { name: '回到实时' }));
  expect(await screen.findByRole('button', { name: /后来更新的标题/ })).toBeVisible();
  expect(screen.getByLabelText('浏览日期')).toHaveValue(day);
});
test('a late page response cannot overwrite the next category selection', async () => {
  let finish: (value: unknown) => void = () => {};
  vi.mocked(api).mockImplementation(path => path.startsWith('/api/news/river?') ? Promise.resolve(overview)
    : path.includes('category=ALL') ? new Promise(resolve => { finish = resolve; })
    : Promise.resolve({ frame: second, items: [], total: 0, page: 0 }));
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  await waitFor(() => expect(api).toHaveBeenCalledWith(expect.stringContaining('/frame?')));
  await userEvent.click(screen.getByRole('button', { name: '科技' }));
  await screen.findByText('这个切面暂时没有事件');
  await act(async () => finish({ frame: second, items: [oldItem], total: 1, page: 0 }));
  expect(screen.queryByRole('button', { name: /当时的标题/ })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('浏览日期'), { target: { value: day } });
});

test('failed overview can be retried without pretending that recording has not started', async () => {
  const normal = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockRejectedValueOnce(new Error('连接暂不可用')).mockImplementation(normal);
  // mockImplementation retains the one-time rejection for the first overview call.
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('连接暂不可用');
  expect(screen.queryByText('时间，从第一次记录开始')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '重新读取' }));
  expect(await screen.findByRole('button', { name: /后来更新的标题/ })).toBeVisible();
});

test('unrecorded hours are distinguished from recorded hours with zero events', async () => {
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  await screen.findByRole('button', { name: /后来更新的标题/ });
  expect(screen.getByText('记录开始前')).toBeVisible();
  expect(screen.getByText('等待后续记录')).toBeVisible();
  expect(screen.getByRole('button', { name: '12:00 新收录 0 个事件' })).toBeDisabled();
});

test('loads the next stable page without losing the already displayed events', async () => {
  vi.mocked(api).mockImplementation(path => {
    if (path.startsWith('/api/news/river?')) {
      return Promise.resolve(overview);
    }
    const params = new URLSearchParams(path.split('?')[1]);
    const index = Number(params.get('page'));
    const from = index ? 50 : 0; const length = index ? 20 : 50;
    return Promise.resolve({ frame: second, items: Array.from({ length }, (_, offset) => ({ ...oldItem,
      eventKey: `event:${from + offset}`, title: `分页事件 ${from + offset}` })), total: 70, page: index });
  });
  render(<NewsRiverPanel onOpenCurrent={vi.fn()} />);
  await screen.findByRole('button', { name: /分页事件 0/ });
  await userEvent.click(screen.getByRole('button', { name: '10:00 新收录 1 个事件' }));
  await screen.findByRole('heading', { name: '10:00–10:30 新收录的事件' });
  await screen.findByRole('button', { name: /分页事件 0/ });
  await userEvent.click(screen.getByRole('button', { name: '继续查看 · 还有 20 件' }));
  expect(await screen.findByRole('button', { name: /分页事件 69/ })).toBeVisible();
  expect(screen.getByRole('button', { name: /分页事件 0/ })).toBeVisible();
  expect(screen.queryByRole('button', { name: /继续查看/ })).not.toBeInTheDocument();
});

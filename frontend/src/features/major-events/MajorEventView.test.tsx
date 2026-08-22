import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import type { MajorEvent } from '../../shared/types';
import { MajorEventView } from './MajorEventView';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const events: MajorEvent[] = [
  {
    id: 1,
    originType: 'NEWS_ITEM',
    originKey: 'news:1',
    title: '美联储释放降息信号',
    summary: '市场开始重新定价实际利率。',
    sourceName: '财经通讯社',
    sourceUrl: 'https://example.com/fed',
    categoryCode: 'MACRO',
    occurredDate: '2026-07-31',
    createdAt: '2026-07-31T09:00:00',
    updatedAt: '2026-07-31T09:00:00'
  },
  {
    id: 2,
    originType: 'ARTICLE',
    originKey: 'article:2',
    title: 'AI 资本开支进入验证期',
    summary: '云厂商指引成为下一阶段的核心观察点。',
    categoryCode: 'AI',
    occurredDate: '2026-08-18',
    note: '订单改善仍需收入兑现。',
    createdAt: '2026-08-18T09:00:00',
    updatedAt: '2026-08-18T09:00:00'
  },
  {
    id: 3,
    originType: 'RADAR_EVENT',
    originKey: 'radar:3',
    title: '铜价与库存同步上行',
    categoryCode: 'COMMODITY',
    occurredDate: '2026-08-22',
    createdAt: '2026-08-22T09:00:00',
    updatedAt: '2026-08-22T09:00:00'
  }
];

beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/major-events?originType=ARTICLE') {
      return Promise.resolve([events[1]]);
    }
    return Promise.resolve(events);
  });
});

test('presents the archive range and orders events by occurred date', async () => {
  render(<MajorEventView addToast={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '市场记忆' })).toBeInTheDocument();
  expect(screen.getByText('3 条记录')).toBeInTheDocument();
  expect(screen.getByText('2 个覆盖月份')).toBeInTheDocument();
  expect(screen.getByText('最近记录 2026.08.22')).toBeInTheDocument();

  const timeline = screen.getByRole('feed', { name: '大事记时间轴' });
  const entries = within(timeline).getAllByRole('article');
  expect(entries.map(entry => within(entry).getByRole('heading').textContent)).toEqual([
    '铜价与库存同步上行',
    'AI 资本开支进入验证期',
    '美联储释放降息信号'
  ]);
  expect(screen.getByRole('heading', { name: '2026 年 08 月' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '2026 年 07 月' })).toBeInTheDocument();
});

test('filters the archive through visible source segments', async () => {
  render(<MajorEventView addToast={vi.fn()} />);
  const articleFilter = await screen.findByRole('button', { name: '文章研究 1' });

  await userEvent.click(articleFilter);

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/major-events?originType=ARTICLE'));
  expect(articleFilter).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('heading', { name: 'AI 资本开支进入验证期' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '铜价与库存同步上行' })).not.toBeInTheDocument();
});

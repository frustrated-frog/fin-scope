import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { NewsView } from './NewsView';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const snapshot = {
  refreshedAt: '2026-07-30T10:00:00',
  sourceCount: 2,
  warnings: [],
  items: [
    { id: 'flash-1', kind: 'FLASH', title: '机器人产业链订单增长', content: '核心零部件需求提升。', url: 'https://example.com/1', publishedAt: '2026-07-30T09:55:00', providerCode: 'CLS_NEWS_FLASH', sourceName: '财联社', sourceTier: 'T2' },
    { id: 'flash-2', kind: 'FLASH', title: '央行公开市场操作', content: '今日开展逆回购操作。', url: 'https://example.com/2', publishedAt: '2026-07-30T09:42:00', providerCode: 'THS_NEWS_FLASH', sourceName: '同花顺', sourceTier: 'T2' },
    { id: 'article-1', kind: 'ARTICLE', title: '上市公司要闻精华', content: '多家公司披露产业进展与订单情况。', url: 'https://example.com/3', publishedAt: '2026-07-30T09:30:00', providerCode: 'THS_NEWS_DIGEST', sourceName: '同花顺', sourceTier: 'T2' }
  ]
};

beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockResolvedValue(snapshot);
});

test('renders realtime items as a time-first timeline and digest items as reading cards', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} />);

  const timeline = await screen.findByRole('feed', { name: '实时快讯时间线' });
  expect(within(timeline).getByText('09:55')).toBeInTheDocument();
  expect(within(timeline).getByText('机器人产业链订单增长')).toBeInTheDocument();
  expect(within(timeline).getByText('09:42')).toBeInTheDocument();

  const digest = screen.getByRole('region', { name: '深度资讯' });
  expect(within(digest).getByRole('heading', { name: '上市公司要闻精华' })).toBeInTheDocument();
  expect(within(digest).queryByText('机器人产业链订单增长')).not.toBeInTheDocument();
});

test('filters the complete feed without truncating article content', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} />);
  await screen.findByRole('heading', { name: '机器人产业链订单增长' });

  await userEvent.type(screen.getByRole('searchbox', { name: '搜索资讯' }), '产业');

  expect(screen.getByRole('heading', { name: '机器人产业链订单增长' })).toBeInTheDocument();
  expect(screen.queryByText('央行公开市场操作')).not.toBeInTheDocument();
  expect(screen.getByText('多家公司披露产业进展与订单情况。')).toBeInTheDocument();
});

test('supports explicit refresh and reports source degradation without hiding healthy news', async () => {
  vi.mocked(api)
    .mockResolvedValueOnce(snapshot)
    .mockResolvedValueOnce({ ...snapshot, warnings: ['财联社：上游读取超时'] });
  const addToast = vi.fn();
  render(<NewsView setMessage={vi.fn()} addToast={addToast} />);
  await screen.findByRole('heading', { name: '机器人产业链订单增长' });

  await userEvent.click(screen.getByRole('button', { name: '刷新资讯' }));

  await waitFor(() => expect(api).toHaveBeenCalledTimes(2));
  expect(screen.getByText('部分来源暂不可用，已展示可用资讯')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '机器人产业链订单增长' })).toBeInTheDocument();
  expect(addToast).toHaveBeenCalledWith('资讯已更新', 'success');
});

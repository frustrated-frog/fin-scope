// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';

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
  const overview = screen.getByLabelText('档案概览');
  expect(overview).toHaveTextContent('3 条记录');
  expect(overview).toHaveTextContent('2 个覆盖月份');
  expect(overview).toHaveTextContent('最近记录 2026.08.22');

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

test('keeps the latest archive date inside its summary column', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.major-events-stats > div:last-child dd\s*{[^}]*display:\s*grid[^}]*white-space:\s*normal/s);
});

test.each([events[0], events[2]])('opens the saved analysis of $title without a generation request', async (event) => {
  const path = `/api/major-events/${event.id}/interpretation`;
  const view = { available: false, contentVersion: 1, current: { status: 'SUCCESS', result: {
    facts: ['归档时的事实'], whyItMatters: '原有解读中的判断', unknowns: [], nextChecks: [],
  } } };
  vi.mocked(api).mockImplementation((url) => Promise.resolve(url === path ? view : events));
  render(<MajorEventView addToast={vi.fn()} />);
  const title = await screen.findByRole('button', { name: `查看新闻解读：${event.title}` });
  expect(api).not.toHaveBeenCalledWith(path, expect.anything());
  await userEvent.click(title);
  const dialog = screen.getByRole('dialog', { name: event.title });
  expect(await within(dialog).findByText('原有解读中的判断')).toBeVisible();
  await userEvent.click(within(dialog).getByRole('button', { name: '关闭新闻解读' }));
  expect(title).toHaveFocus();
  expect(vi.mocked(api).mock.calls.filter(([url]) => url === path).map(([, init]) => init?.method)).toEqual(['GET']);
});

test('generates a missing archive reading only on click and reuses it after reopening', async () => {
  const path = '/api/major-events/1/interpretation';
  let generated = false;
  vi.mocked(api).mockImplementation((url, init) => {
    if (url !== path) {
      return Promise.resolve(events);
    }
    if (init?.method === 'POST') {
      generated = true;
    }
    return Promise.resolve({ available: true, contentVersion: 1, current: generated ? {
      status: 'SUCCESS', result: { facts: ['归档正文'], whyItMatters: '基于归档正文的解释' },
    } : null });
  });
  render(<MajorEventView addToast={vi.fn()} />);
  const title = await screen.findByRole('button', { name: '查看新闻解读：美联储释放降息信号' });
  await userEvent.click(title);
  const generate = await screen.findByRole('button', { name: '生成 AI 解读' });
  expect(generated).toBe(false);
  await userEvent.click(generate);
  expect(await screen.findByText('基于归档正文的解释')).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: '关闭新闻解读' }));
  await userEvent.click(title);
  expect(await screen.findByText('基于归档正文的解释')).toBeVisible();
  expect(vi.mocked(api).mock.calls.filter(([url, init]) => url === path && init?.method === 'POST')).toHaveLength(1);
});

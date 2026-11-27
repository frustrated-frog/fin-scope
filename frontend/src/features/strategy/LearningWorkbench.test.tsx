import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { LearningWorkbench } from './LearningWorkbench';
import type { PaperVersion } from './PaperLab';

const versions = [
  { id: 1, spec: { rule: { name: '动量研究' } }, lifecycle: 'RUNNING', started: '2026-01-01' },
] as PaperVersion[];
const risk = { at: null, returnDays: 0, sharpe: null, drawdown: null };
const stats = { start: '2026-11-23', end: '2026-11-27', days: 0, net: null, market: null, excess: null };
const week = { ...stats, risk, execution: { fills: 1, rejected: 3, fees: 5 }, observations: 5 };

test('explains real observations on request and locks an editable hypothesis only after confirmation', async () => {
  const posts: { url: string; body?: string }[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === 'POST') {
        posts.push({ url, body: init.body as string });
        if (url.includes('/teach/')) {
          return apiResponse({
            model: '测试助教',
            day: '2026-11-27',
            lesson: '逐笔核对未成交。',
            hypothesis: '降低换手会降低费用',
            criterion: '同股票池观察五日',
          });
        }
        return apiResponse({ id: 1 });
      }
      return apiResponse({
        version: 1,
        day: '2026-11-27',
        name: '动量研究',
        accounts: [],
        entries: [],
        daily: { preopen: risk, returnDays: 0, comparison: stats },
        weekly: { current: week, previous: week },
      });
    }),
  );
  const user = userEvent.setup();
  const compare = vi.fn();
  render(
    <LearningWorkbench
      versions={versions}
      loading={false}
      error=""
      onAccount={vi.fn()}
      onVersion={vi.fn()}
      onCompare={compare}
      onFactors={vi.fn()}
      onLab={vi.fn()}
    />,
  );
  await screen.findByText('尚无更早的模拟收盘记录。');
  await user.click(screen.getByRole('button', { name: /每周实验/ }));
  expect(await screen.findByText('两周表现 · 前向模拟')).toBeInTheDocument();
  expect(posts).toHaveLength(0);
  await user.click(screen.getByRole('button', { name: '用 AI 讲解这组数据' }));
  expect(await screen.findByText('逐笔核对未成交。')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '采用为研究草稿' }));
  expect(screen.getByLabelText('假设或待解决的问题')).toHaveValue('降低换手会降低费用');
  expect(posts).toHaveLength(1);
  await user.click(screen.getByRole('button', { name: '锁定标准并保存证据' }));
  await waitFor(() => expect(posts).toHaveLength(2));
  expect(JSON.parse(posts[1].body!)).toMatchObject({
    period: 'weekly',
    hypothesis: '降低换手会降低费用',
    criterion: '同股票池观察五日',
    token: expect.any(String),
  });
  expect(compare).not.toHaveBeenCalled();
});

test('retains access to the other workflows and retries unavailable evidence', async () => {
  const fetch = vi.fn(async () => apiResponse({}));
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();
  render(
    <LearningWorkbench
      versions={versions}
      loading={false}
      error=""
      onAccount={vi.fn()}
      onVersion={vi.fn()}
      onCompare={vi.fn()}
      onFactors={vi.fn()}
      onLab={vi.fn()}
    />,
  );
  expect(await screen.findByRole('alert')).toHaveTextContent('学习记录格式有误');
  await user.click(screen.getByRole('button', { name: /季度体检/ }));
  expect(screen.getByRole('button', { name: /季度体检/ })).toHaveAttribute('aria-pressed', 'true');
  await user.click(screen.getByRole('button', { name: '重试' }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
});

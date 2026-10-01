import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { InvestmentObservationView } from './InvestmentObservationView';
import { reactionSample } from './reactionFixtures.test-support';
import type { ReactionEventPage, ReactionSample } from './reactionTypes';

const sample = { ...reactionSample, sourceIdentity: 'EVENT:stable' };
function page(items: ReactionSample[] = [sample], total = items.length): ReactionEventPage {
  return {
    items,
    total,
    counts: { TRACKING: total, HISTORY: 14, PENDING: 26 },
    pendingReasons: { TIME_MISSING: 2, NO_SUBJECT: 24 },
    stockCounts: { 'EVENT:stable': 1 },
    stockNames: {},
    anchor: 125,
    revision: 10,
    page: 1,
    size: 20,
  };
}
function setup(handler?: (path: string, init?: RequestInit) => unknown) {
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    const custom = handler?.(path, init);
    if (custom !== undefined) {
      return apiResponse(custom);
    }
    if (path.includes('/events?')) {
      return apiResponse(page());
    }
    if (path.endsWith('/discovery')) {
      return apiResponse({
        running: false,
        message: '已同步',
        captured: 1,
        resolved: 1,
      });
    }
    if (/\/1$|\/event\?/.test(path)) {
      return apiResponse(sample);
    }
    return apiResponse([]);
  });
  vi.stubGlobal('fetch', fetch);
  const props = {
    setMessage: vi.fn(),
    addToast: vi.fn(),
    onOpenMajorEvents: vi.fn(),
    onResearch: vi.fn(),
  };
  render(<InvestmentObservationView {...props} />);
  return { fetch, ...props };
}
afterEach(() => {
  vi.useRealTimers();
  window.history.replaceState({}, '', '/');
});

async function open() {
  await userEvent.click(await screen.findByRole('button', { name: /设备公司签订重大合同/ }));
  await screen.findByRole('button', { name: '关注事件' });
}

test('opens an independent accessible detail and returns focus to the unchanged list', async () => {
  setup();
  const row = await screen.findByRole('button', {
    name: /设备公司签订重大合同/,
  });
  await userEvent.click(row);
  expect(await screen.findByRole('dialog', { name: '事件详情' })).toBeInTheDocument();
  expect(await screen.findByRole('link', { name: '查看原始来源 ↗' })).toHaveAttribute(
    'href',
    'https://example.com/announcement',
  );
  expect(screen.getByRole('link', { name: '独立链接 ↗' })).toHaveAttribute(
    'href',
    '?reactionEvent=EVENT%3Astable&reactionStock=600519.SH',
  );
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { bubbles: true, cancelable: true }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(row).toHaveFocus();
});

test('server search, pagination, view totals and pending reasons do not depend on the latest hundred', async () => {
  const { fetch } = setup((path) => (path.includes('/events?') ? page([sample], 125) : undefined));
  await screen.findByText('共 125 件事件 · 每页 20 件');
  await userEvent.click(screen.getByRole('button', { name: '下一页' }));
  await waitFor(() =>
    expect(
      fetch.mock.calls.some(([path]) => String(path).includes('page=2') && String(path).includes('anchor=125')),
    ).toBe(true),
  );
  await userEvent.type(screen.getByLabelText('搜索事件或股票'), '旧合同');
  await userEvent.click(screen.getByRole('button', { name: '搜索' }));
  await waitFor(() =>
    expect(
      fetch.mock.calls.some(
        ([path]) => decodeURIComponent(String(path)).includes('query=旧合同') && String(path).includes('page=1'),
      ),
    ).toBe(true),
  );
  await userEvent.click(screen.getByRole('button', { name: /待补全\s*26/ }));
  expect(await screen.findByLabelText('待补全原因')).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText('待补全原因'), 'TIME_MISSING');
  await waitFor(() =>
    expect(fetch.mock.calls.some(([path]) => String(path).includes('resolutionStatus=TIME_MISSING'))).toBe(true),
  );
});

test('an old event opens directly even when absent from the current page', async () => {
  window.history.replaceState({}, '', '/?reactionEvent=EVENT%3Aold');
  const { fetch } = setup((path) => (path.includes('/events?') ? page([]) : undefined));
  expect(await screen.findByRole('button', { name: '关注事件' })).toBeInTheDocument();
  expect(fetch.mock.calls.some(([path]) => String(path).includes('/event?key=EVENT%3Aold'))).toBe(true);
});

test('polling offers updates without replacing rows and refreshes an old selected detail independently', async () => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
  let latest = false;
  const { fetch } = setup((path) => {
    if (path.includes('/events?')) {
      return {
        ...page(latest ? [{ ...sample, id: 2, title: '新合同' }] : [sample]),
        revision: latest ? 11 : 10,
      };
    }
    if (path.endsWith('/1')) {
      return { ...sample, summary: latest ? '最新详情材料' : sample.summary };
    }
    return undefined;
  });
  await open();
  latest = true;
  await act(async () => {
    await vi.advanceTimersByTimeAsync(15000);
  });
  expect(screen.getByText('最新详情材料')).toBeInTheDocument();
  expect(screen.queryByText('新合同')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: /有新事件或反应变化/ })).toBeInTheDocument();
  expect(fetch.mock.calls.filter(([path]) => String(path).endsWith('/1')).length).toBeGreaterThan(1);
  vi.useRealTimers();
  await userEvent.click(screen.getByRole('button', { name: '关闭事件详情' }));
  await userEvent.click(screen.getByRole('button', { name: /有新事件或反应变化/ }));
  expect(await screen.findByText('新合同')).toBeInTheDocument();
});

test('late detail response cannot overwrite the next event', async () => {
  let resolveOld: ((value: Response) => void) | undefined;
  setup((path) =>
    path.includes('/events?')
      ? page([sample, { ...sample, id: 2, sourceIdentity: 'EVENT:2', title: '另一事件' }])
      : undefined,
  );
  const original = globalThis.fetch;
  vi.stubGlobal('fetch', (input: RequestInfo | URL, init?: RequestInit) =>
    String(input).endsWith('/1')
      ? new Promise<Response>((resolve) => {
          resolveOld = resolve;
        })
      : String(input).endsWith('/2')
        ? Promise.resolve(apiResponse({ ...sample, id: 2, title: '另一事件' }))
        : original(input, init),
  );
  await userEvent.click(await screen.findByRole('button', { name: /设备公司签订重大合同/ }));
  await userEvent.click(screen.getByRole('button', { name: '关闭事件详情' }));
  await userEvent.click(screen.getByRole('button', { name: /另一事件/ }));
  await screen.findByRole('button', { name: '关注事件' });
  await act(async () => {
    resolveOld?.(apiResponse(sample));
  });
  expect(screen.getByRole('heading', { name: '另一事件' })).toBeInTheDocument();
});

test('missing time shows known stock and optional manual correction without forcing a form', async () => {
  const draft = {
    ...sample,
    state: 'DRAFT' as const,
    resolutionStatus: 'TIME_MISSING',
    calculation: undefined,
    publishedAt: undefined,
    discoveryIssue: '股票已关联，来源公开时间待补全',
  };
  setup((path) => (path.includes('/events?') ? page([draft]) : path.endsWith('/1') ? draft : undefined));
  await userEvent.click(await screen.findByRole('button', { name: /设备公司签订重大合同/ }));
  expect(await screen.findByText(/时间待补全 · 股票已关联/)).toBeInTheDocument();
  expect(screen.getByText('手动补充（可选）').closest('details')).not.toHaveAttribute('open');
  expect(screen.queryByText('股票待确认')).not.toBeInTheDocument();
});

test('refresh failure preserves the path and research context', async () => {
  const props = setup((path) =>
    path.endsWith('/refresh') ? { ...sample, refreshError: '行情暂不可用，保留旧结果' } : undefined,
  );
  await open();
  await userEvent.click(screen.getByRole('button', { name: '更新此样本' }));
  expect(await screen.findByText(/行情暂不可用，保留旧结果/)).toBeInTheDocument();
  expect(screen.getByRole('img', { name: /累计收益/ })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '把这个差异带入研究 →' }));
  expect(props.onResearch).toHaveBeenCalledWith(expect.stringContaining('尚未到期'));
});

test('exclusion is distinct from archive and carries the displayed revision', async () => {
  const { fetch } = setup((path, init) =>
    path.endsWith('/exclude')
      ? {
          ...sample,
          excluded: JSON.parse(String(init?.body)).excluded,
          revision: 3,
        }
      : undefined,
  );
  await open();
  await userEvent.click(screen.getByRole('button', { name: '识别错误，排除统计' }));
  expect(await screen.findByText(/此样本已排除统计/)).toBeInTheDocument();
  expect(
    fetch.mock.calls.some(
      ([path, init]) => String(path).endsWith('/exclude') && JSON.parse(String(init?.body)).revision === 2,
    ),
  ).toBe(true);
});

test('empty state explains automatic tracking without fabricating samples', async () => {
  setup((path) => (path.includes('/events?') ? page([]) : undefined));
  expect(await screen.findByRole('heading', { name: '当前没有符合条件的跟踪事件' })).toBeInTheDocument();
});

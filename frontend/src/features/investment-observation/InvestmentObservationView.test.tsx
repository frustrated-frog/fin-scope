import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { InvestmentObservationView } from './InvestmentObservationView';
import { reactionSample } from './reactionFixtures.test-support';
import type { ReactionSample } from './reactionTypes';

function view() {
  const props = { setMessage: vi.fn(), addToast: vi.fn(), onOpenMajorEvents: vi.fn(), onResearch: vi.fn() };
  render(<InvestmentObservationView {...props} />);
  return props;
}

test('opens the reaction matrix, preserves immature windows and links the real source', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse([reactionSample])));
  const props = view();
  const matrix = await screen.findByRole('table', { name: '事件反应矩阵' });
  expect(within(matrix).getByText('+3.00')).toBeInTheDocument();
  expect(within(matrix).getAllByText('尚未到期')).toHaveLength(2);
  await userEvent.click(screen.getByRole('button', { name: /示例设备 设备公司签订重大合同/ }));
  expect(screen.getByRole('link', { name: '查看原始来源 ↗' })).toHaveAttribute('href', 'https://example.com/announcement');
  expect(screen.getByRole('img', { name: /累计收益/ })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '把这个差异带入研究 →' }));
  expect(props.onResearch).toHaveBeenCalledWith(expect.stringContaining('沪深300'));
  expect(props.onResearch).toHaveBeenCalledWith(expect.stringContaining('尚未到期'));
});

test('empty workspace does not fabricate samples or refresh the legacy scoring pool', async () => {
  const fetch = vi.fn(async () => apiResponse([]));
  vi.stubGlobal('fetch', fetch);
  view();
  expect(await screen.findByRole('heading', { name: '从一件明确的事件开始' })).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0]).toEqual(expect.arrayContaining(['/api/investment-reactions']));
});

test('saves a candidate as draft, requires confirmation, then requests a separate refresh', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = [];
  const draft: ReactionSample = { ...reactionSample, id: 8, state: 'DRAFT', instrumentCode: '', instrumentName: undefined,
    publishedAt: undefined, relationNote: undefined, calculation: undefined, revision: 0 };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    requests.push({ path, init });
    if (path.endsWith('/candidates')) {
      return apiResponse([{ majorEventId: 40, title: reactionSample.title, suggestedType: 'CONTRACT' }]);
    }
    if (path.endsWith('/confirm')) {
      return apiResponse({ ...reactionSample, id: 8, revision: 1 });
    }
    if (path.endsWith('/refresh')) {
      return apiResponse({ ...reactionSample, id: 8 });
    }
    return apiResponse(init?.method === 'POST' ? draft : []);
  }));
  view();
  await userEvent.click(screen.getByRole('button', { name: '登记事件' }));
  await userEvent.click(await screen.findByRole('button', { name: '保存到待确认' }));
  await userEvent.type(await screen.findByLabelText('股票代码'), '600519.SH');
  await userEvent.type(screen.getByLabelText('股票名称'), '示例设备');
  fireEvent.change(screen.getByLabelText('公开时间（北京时间）'), { target: { value: '2026-09-18T10:30' } });
  await userEvent.type(screen.getByLabelText('与股票的关联依据'), '上市公司是签约方');
  await userEvent.click(screen.getByRole('button', { name: '确认并开始观察' }));
  await waitFor(() => expect(requests.some(value => value.path.endsWith('/8/refresh'))).toBe(true));
  const confirm = requests.find(value => value.path.endsWith('/8/confirm'));
  expect(JSON.parse(String(confirm?.init?.body))).toMatchObject({ instrumentCode: '600519.SH', publishedAt: '2026-09-18T10:30:00', revision: 0 });
  expect(screen.getByRole('link', { name: '查看原始来源 ↗' })).toBeInTheDocument();
});

test('failed refresh keeps the previously calculated path visible and flags the failure', async () => {
  vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => apiResponse(init?.method === 'POST'
    ? { ...reactionSample, revision: 3, refreshError: '行情暂不可用，保留旧结果', lastAttemptAt: '2026-09-19T16:30:00' }
    : [reactionSample])));
  view();
  await userEvent.click(await screen.findByRole('button', { name: /示例设备 设备公司签订重大合同/ }));
  await userEvent.click(screen.getByRole('button', { name: '更新此样本' }));
  expect(await screen.findByText(/行情暂不可用，保留旧结果/)).toBeInTheDocument();
  expect(screen.getByRole('img', { name: /累计收益/ })).toBeInTheDocument();
  expect(within(screen.getByRole('table', { name: '事件反应矩阵' })).getByText('+3.00')).toBeInTheDocument();
});

test('comparison counts only completed windows and prevents mixing event types', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse([reactionSample, { ...reactionSample, id: 2, title: '年度业绩披露', eventType: 'EARNINGS' }])));
  const props = view();
  await userEvent.click(await screen.findByRole('button', { name: `对照：${reactionSample.title}` }));
  const comparison = screen.getByRole('region', { name: '同类案例对照' });
  expect(within(comparison).getByText('1 个样本 · 五日窗口完成 0 个')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '对照：年度业绩披露' }));
  expect(props.addToast).toHaveBeenCalledWith(expect.stringContaining('相同事件类型'), 'info');
});

test('archives with the displayed revision and restores via the archived list', async () => {
  const changes: object[] = [];
  vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'PATCH') {
      const body = JSON.parse(String(init.body));
      changes.push(body);
      return apiResponse({ ...reactionSample, state: body.archived ? 'ARCHIVED' : 'OBSERVING', revision: body.revision + 1 });
    }
    return apiResponse([reactionSample]);
  }));
  view();
  await userEvent.click(await screen.findByRole('button', { name: /示例设备 设备公司签订重大合同/ }));
  await userEvent.click(screen.getByRole('button', { name: '归档样本' }));
  await userEvent.click(await screen.findByRole('button', { name: '恢复观察' }));
  await waitFor(() => expect(changes).toEqual([{ archived: true, revision: 2 }, { archived: false, revision: 3 }]));
});

test('legacy records remain readable without navigating mismatched radar ids', async () => {
  const fetch = vi.fn(async (input: RequestInfo | URL) => apiResponse(String(input) === '/api/investment-observations'
    ? { focus: [{ id: 1, title: '旧观察记录', summary: '保留的原文', nextValidation: '旧验证点', stage: 'FOCUS' }], tracking: [], learning: [], archived: [] }
    : []));
  vi.stubGlobal('fetch', fetch);
  view();
  await userEvent.click(screen.getByRole('button', { name: '历史资料' }));
  expect(await screen.findByText('旧观察记录')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '查看原始证据' })).not.toBeInTheDocument();
  expect(fetch.mock.calls.some(call => String(call[0]).includes('research-radar'))).toBe(false);
});

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { reactionSample } from './reactionFixtures.test-support';
import { ReactionActivity } from './ReactionActivity';
import { ReactionEventContext } from './ReactionEventContext';
import { InvestmentObservationView } from './InvestmentObservationView';

const change = { id: 10, sampleId: 1, title: '合同反应变化', instrumentName: '示例设备', changeType: 'DATA_CORRECTION', tradeDate: '2026-09-18', detectedAt: '2026-09-19T09:00:00', summary: '已记录行情发生修正', followed: false };
const group = { criteria: '同子类、同公开时段；按时间选取案例', relaxed: false, eventCount: 12, sampleCount: 15, completeCount: 10, notDueCount: 3, missingCount: 2, median: -1.5, lowerQuartile: -3, upperQuartile: 2, cases: [] };

test('daily changes retain correction labels and open the original sample', async () => {
  const onOpen = vi.fn();
  const fetch = vi.fn(async (input: RequestInfo | URL) => apiResponse(String(input).includes('/changes') ? [change] : [reactionSample]));
  vi.stubGlobal('fetch', fetch);
  const { rerender } = render(<ReactionActivity revision={0} onOpen={onOpen} />);
  expect(await screen.findByText(/数据修订 · 行情 2026-09-18/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /合同反应变化/ }));
  expect(onOpen).toHaveBeenCalledWith(1);
  rerender(<ReactionActivity revision={1} onOpen={onOpen} />);
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(4));
  expect(screen.getAllByText('已记录行情发生修正')).toHaveLength(1);
  fireEvent.change(screen.getByLabelText('记录日期'), { target: { value: '2026-09-18' } });
  await waitFor(() => expect(fetch.mock.calls.some(([path]) => String(path).includes('date=2026-09-18'))).toBe(true));
});

test('automatic history exposes full denominators and changes the comparison window', async () => {
  const fetch = vi.fn(async (input: RequestInfo | URL) => apiResponse(String(input).includes('/comparables')
    ? { sessions: 5, sameCompany: group, otherCompanies: { ...group, eventCount: 0, sampleCount: 0, completeCount: 0 } } : []));
  vi.stubGlobal('fetch', fetch);
  render(<ReactionEventContext sample={{ ...reactionSample, eventSubtype: 'CONTRACT_SIGNED' }} onOpen={vi.fn()} />);
  expect(await screen.findByText('12 个事件 / 15 个股票样本')).toBeInTheDocument();
  expect(screen.getAllByText('完整 10 · 未到期 3 · 缺失或停牌 2')).toHaveLength(1);
  expect(screen.getByText('-1.50 pp')).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText('比较窗口'), '1');
  await waitFor(() => expect(fetch.mock.calls.some(([path]) => String(path).endsWith('/comparables?sessions=1'))).toBe(true));
});

test('following an event persists through the API and appears in the focus area', async () => {
  let followed = false;
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path.endsWith('/follow')) {
      followed = JSON.parse(String(init?.body)).followed;
      return apiResponse([{ ...reactionSample, followed }]);
    }
    if (path.endsWith('/followed')) {
      return apiResponse(followed ? [{ ...reactionSample, followed }] : []);
    }
    if (path.endsWith('/recent')) {
      return apiResponse([{ ...reactionSample, followed }]);
    }
    return apiResponse([]);
  });
  vi.stubGlobal('fetch', fetch);
  render(<InvestmentObservationView setMessage={vi.fn()} addToast={vi.fn()} />);
  await userEvent.click(await screen.findByRole('button', { name: /示例设备 设备公司签订重大合同/ }));
  await userEvent.click(screen.getByRole('button', { name: '关注事件' }));
  expect(await screen.findByRole('button', { name: '取消关注', pressed: true })).toBeInTheDocument();
  await waitFor(() => expect(screen.getAllByRole('button', { name: /示例设备 设备公司签订重大合同/ })).toHaveLength(2));
  expect(fetch.mock.calls.some(([path, init]) => String(path).endsWith('/follow') && init?.method === 'PATCH')).toBe(true);
});

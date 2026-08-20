import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { apiResponse } from '../../test/apiEnvelope';
import { InvestmentObservationView } from './InvestmentObservationView';

const focus = {
  id: 7,
  sourceType: 'RADAR_EVENT',
  sourceId: 19,
  title: '光模块订单与资本开支出现共振',
  summary: '多个来源显示云厂商资本开支与上游订单同时改善。',
  subjectType: 'INDUSTRY',
  subjectName: '光模块',
  stage: 'FOCUS',
  changeType: 'ORDER',
  score: 78,
  scoreDimensions: [
    { code: 'CHANGE', label: '变化强度', score: 17, maxScore: 20, explanation: '事件正在扩散' },
    { code: 'INDEPENDENCE', label: '独立来源', score: 11, maxScore: 15, explanation: '两个独立来源' }
  ],
  whyItMatters: '订单变化可能通过收入与盈利预期影响产业链判断。',
  uncertainty: '云厂商预算落地节奏仍需确认。',
  nextValidation: '观察下一季度资本开支指引与供应商订单。',
  supportingEvidenceCount: 4,
  opposingEvidenceCount: 1,
  independentSourceCount: 2,
  disposition: 'ACTIVE',
  revision: 3,
  evidenceInsufficient: false,
  sourceAvailable: true,
  lastChangedAt: '2026-08-20T09:00:00',
  updatedAt: '2026-08-20T09:00:00'
};

const workspace = {
  focus: [focus],
  tracking: [{ ...focus, id: 8, sourceId: 20, title: '铜价变化等待需求验证', stage: 'TRACKING', score: 61 }],
  learning: [],
  archived: [],
  transitions: [],
  activeCount: 2,
  changedTodayCount: 1,
  waitingValidationCount: 1,
  archivedCount: 0,
  refreshedAt: '2026-08-20T10:00:00'
};

test('shows a beginner-friendly evidence desk and links back to source radar evidence', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path === '/api/investment-observations') {
      return apiResponse(workspace);
    }
    if (path === '/api/investment-observations/7') {
      return apiResponse({ observation: focus, transitions: [] });
    }
    return apiResponse(focus);
  }));
  const openSource = vi.fn();

  render(<InvestmentObservationView setMessage={vi.fn()} addToast={vi.fn()} onOpenSource={openSource} />);

  expect(await screen.findByRole('heading', { name: '先看变化，再做判断' })).toBeInTheDocument();
  expect(screen.getByText('光模块订单与资本开支出现共振')).toBeInTheDocument();
  expect(screen.getByText('为什么值得看')).toBeInTheDocument();
  expect(screen.getByText('下一验证点')).toBeInTheDocument();
  expect(screen.getByText('78')).toBeInTheDocument();

  await userEvent.click(screen.getAllByRole('button', { name: '查看原始证据' })[0]);
  expect(openSource).toHaveBeenCalledWith(19);
});

test('updates personal disposition with the current revision', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    requests.push({ path, init });
    if (path === '/api/investment-observations') {
      return apiResponse(workspace);
    }
    return apiResponse({ ...focus, disposition: 'LATER', revision: 4 });
  }));

  render(<InvestmentObservationView setMessage={vi.fn()} addToast={vi.fn()} onOpenSource={vi.fn()} />);
  await userEvent.click(await screen.findByRole('button', { name: '稍后看：光模块订单与资本开支出现共振' }));

  await waitFor(() => expect(requests.some(request => request.path === '/api/investment-observations/7/state'
    && request.init?.method === 'PATCH'
    && request.init.body === JSON.stringify({ disposition: 'LATER', revision: 3 }))).toBe(true));
});

test('automatically seeds an empty workspace without becoming dependent on holdings', async () => {
  let workspaceReads = 0;
  const requests: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    requests.push(path);
    if (path === '/api/investment-observations') {
      workspaceReads += 1;
      return apiResponse(workspaceReads === 1 ? { ...workspace, focus: [], tracking: [], activeCount: 0 } : workspace);
    }
    return apiResponse({ scannedCount: 12, updatedCount: 2 });
  }));

  render(<InvestmentObservationView setMessage={vi.fn()} addToast={vi.fn()} onOpenSource={vi.fn()} />);

  expect(await screen.findByText('光模块订单与资本开支出现共振')).toBeInTheDocument();
  expect(requests).toEqual([
    '/api/investment-observations',
    '/api/investment-observations/refresh',
    '/api/investment-observations'
  ]);
});

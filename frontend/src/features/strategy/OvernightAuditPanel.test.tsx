import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { OvernightAuditPanel } from './OvernightAuditPanel';

const capture = { plan: { enabled: false, instrumentCodes: [], costBps: 20 }, slots: ['14:30', '14:45'],
  serverTime: '2026-09-23T14:00:00', calendarAvailable: true, runs: [{ signalDate: '2026-09-22', cutoff: '14:30',
    status: 'MISSED', instrumentCodes: ['605058.SH'], costBps: 20, results: [], reason: '服务未运行，未补跑' }] };

test('saves explicit watchlist and shows missing windows without fabricating returns', async () => {
  const saved: unknown[] = [];
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'POST') {
      const plan = JSON.parse(String(init.body)); saved.push(plan); return apiResponse(plan);
    }
    return apiResponse(String(url).endsWith('/capture') ? capture : { recordCount: 0, groups: [], limitations: [] });
  }));
  render(<OvernightAuditPanel mode="TAIL_ENTRY" revision={0} />);
  const user = userEvent.setup();
  expect(await screen.findByText('错过窗口')).toBeInTheDocument();
  await user.type(screen.getByLabelText('自动观察名单'), '605058, 000001');
  await user.click(screen.getByLabelText('启用自动留档'));
  await user.click(screen.getByRole('button', { name: '保存留档计划' }));
  await waitFor(() => expect(saved).toEqual([{ enabled: true, instrumentCodes: ['605058', '000001'], costBps: 20 }]));
  expect(screen.getByText(/概率、收益和命中率保持空缺/)).toBeInTheDocument();
});

test('separates modes and retrospective evidence and exposes sample days and missing baseline', async () => {
  const group = { mode: 'TAIL_ENTRY', modelVersion: 'overnight-local-v2', cutoff: '14:30', costBps: 20,
    evidenceKind: 'FORWARD', recordCount: 60, statuses: { SETTLED: 60 }, missingReasons: {},
    targets: [{ target: 'OPEN', count: 60, days: 4, accuracy: .5, brier: .3, baselineCount: 0,
      pairedBrier: null, baselineBrier: null, meanNetReturn: -.01, selectedCount: 0, selectedNetReturn: 0, bins: [] }] };
  vi.stubGlobal('fetch', vi.fn(async (url: RequestInfo | URL) => apiResponse(String(url).endsWith('/capture') ? capture : {
    recordCount: 120, groups: [group, { ...group, evidenceKind: 'RETROSPECTIVE', modelVersion: 'backfilled-version' },
      { ...group, mode: 'AFTER_CLOSE_HOLDING', modelVersion: 'holding-version' }], limitations: [],
  })));
  render(<OvernightAuditPanel mode="TAIL_ENTRY" revision={0} />);
  expect(await screen.findByText('60 / 4')).toBeInTheDocument();
  expect(screen.getByText('— / —')).toBeInTheDocument();
  expect(screen.queryByText('holding-version')).not.toBeInTheDocument();
  expect(screen.queryByText('backfilled-version')).not.toBeInTheDocument();
  await userEvent.setup().selectOptions(screen.getByLabelText('验收记录范围'), 'RETROSPECTIVE');
  expect(screen.getByText('backfilled-version')).toBeInTheDocument();
});

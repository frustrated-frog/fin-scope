import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { OvernightStrategyPanel, ResearchResult } from './OvernightStrategyPanel';
import type { OvernightReport } from './overnightTypes';

const blocked = { mode: 'TAIL_ENTRY', instrumentCode: '605058.SH', signalDate: '2026-09-16', cutoff: '14:30',
  status: 'DATA_UNAVAILABLE', dataThrough: '2026-09-16T14:30:00', generatedAt: '2026-09-16T14:31:00',
  costBps: 20, evidenceKind: 'FORWARD', targets: [], warnings: ['分钟历史不足'] };

test('keeps entry cutoff separate from ledger holding research and never fabricates missing probabilities', async () => {
  const requests: Array<Record<string, unknown>> = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/capture')) {
      return apiResponse({ plan: { enabled: false, instrumentCodes: [], costBps: 20 }, runs: [], slots: [], serverTime: '2026-09-23T12:00:00', calendarAvailable: true });
    }
    if (String(input).endsWith('/validation')) {
      return apiResponse({ groups: [], recordCount: 0, limitations: [] });
    }
    if (String(input).endsWith('/history')) {
      return apiResponse([]);
    }
    if (String(input).endsWith('/stock-account')) {
      return apiResponse({ positions: [{ instrumentCode: '605058.SH', instrumentName: '澳弘电子', averageCost: 20, quantity: 100, openedOn: '2026-09-15' }] });
    }
    const body = JSON.parse(String(init?.body));
    requests.push(body);
    return apiResponse({ ...blocked, ...body });
  }));
  const user = userEvent.setup();
  render(<OvernightStrategyPanel />);
  await user.type(screen.getByLabelText('研究股票'), '605058');
  await user.selectOptions(screen.getByLabelText('数据截止时刻'), '14:50');
  await user.click(screen.getByRole('button', { name: '生成尾盘入场研究' }));
  expect(await screen.findByText('分钟数据未就绪')).toBeInTheDocument();
  expect(screen.queryByText('50.0%')).not.toBeInTheDocument();
  expect(requests[0]).toMatchObject({ mode: 'TAIL_ENTRY', cutoff: '14:50', costBps: 20 });
  await user.click(screen.getByRole('button', { name: /盘后持仓 已经持有/ }));
  expect(await screen.findByText('账本成本 ¥20.00')).toBeInTheDocument();
  expect(screen.getByLabelText('数据截止时刻')).toBeDisabled();
  await user.click(screen.getByRole('button', { name: '生成盘后持仓研究' }));
  await waitFor(() => expect(requests).toHaveLength(2));
  expect(requests[1]).toMatchObject({ mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00', costBps: 10 });
  expect(requests[1]).not.toHaveProperty('costBasis');
});

test('shows both frozen modes and settles observations without generating another prediction', async () => {
  const calls: string[] = [];
  const records = [{ ...blocked, id: 'tail' }, { ...blocked, id: 'holding', mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00' }];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    calls.push(String(input));
    if (String(input).endsWith('/stock-account')) {
      return apiResponse({ positions: [] });
    }
    return apiResponse(records);
  }));
  const user = userEvent.setup();
  render(<OvernightStrategyPanel />);
  await waitFor(() => expect(screen.getByLabelText('两类预测独立档案').querySelectorAll('details')).toHaveLength(2));
  await user.click(screen.getByRole('button', { name: '更新到期结果' }));
  await waitFor(() => expect(calls).toContain('/api/quant/overnight/settle'));
  expect(calls).not.toContain('/api/quant/overnight/generate');
});

const calibrated: OvernightReport = {
  ...blocked, mode: 'TAIL_ENTRY', evidenceKind: 'FORWARD', status: 'WATCH', modelVersion: 'overnight-local-v3-calibrated',
  inputFingerprint: 'a'.repeat(64), targets: [{ target: 'OPEN', status: 'WATCH', sampleCount: 130,
    upProbability: .57, rawUpProbability: .72, baselineProbability: .53, expectedNetReturn: .004,
    lowerNetReturn: -.02, upperNetReturn: .028, validationCount: 60, calibrationCount: 20, trainingCount: 109,
    calibrationStatus: 'FITTED', brierScore: .26, baselineBrier: .25, directionAccuracy: .52,
    reliability: { status: 'BASELINE_NOT_BEATEN', count: 60, brierSkill: -.04, recentBrierSkill: -.08,
      recentCount: 10, intervalCoverage: .78, nominalCoverage: .8, rawBrier: .29, baselineAccuracy: .53,
      from: '2026-06-01', through: '2026-09-15', expectedReturnMae: .015 },
  }],
};

test('shows calibrated probability alongside baseline failure and keeps detailed diagnostics folded', async () => {
  render(<ResearchResult report={calibrated} />);
  expect(screen.getByText('57.0%')).toBeVisible();
  expect(screen.getByText('校准后盈利概率 · 已扣假设成本')).toBeVisible();
  expect(screen.getByText('尚未超过历史基线')).toBeVisible();
  expect(screen.getByText('历史基线盈利比例 53.0%')).toBeVisible();
  expect(screen.getByText('概率误差改善')).not.toBeVisible();
  await userEvent.click(screen.getByText('查看独立验证依据'));
  expect(screen.getByText('-4.0%')).toBeVisible();
  expect(screen.getByText('-8.0%')).toBeVisible();
  expect(screen.getByText('78.0%')).toBeVisible();
  expect(screen.getByText(/这些是历史顺序验证/)).toBeVisible();
});

test('uses the new required sample count and preserves old records without fabricating calibration', () => {
  const { rerender } = render(<ResearchResult report={{ ...calibrated, targets: [{ target: 'CLOSE',
    status: 'INSUFFICIENT_DATA', sampleCount: 60, minimumSamples: 82, missingSamples: 22 }] }} />);
  expect(screen.getByText('可用历史 60 例，至少需要 82 例')).toBeVisible();
  expect(screen.getByText('还需积累 22 个有效收益样本。')).toBeVisible();
  expect(screen.queryByText('57.0%')).not.toBeInTheDocument();
  rerender(<ResearchResult report={{ ...calibrated, modelVersion: 'overnight-local-v2', targets: [{
    target: 'OPEN', status: 'WATCH', sampleCount: 60, upProbability: .57, validationCount: 20 }] }} />);
  expect(screen.queryByText('查看独立验证依据')).not.toBeInTheDocument();
  expect(screen.queryByText('校准后盈利概率 · 已扣假设成本')).not.toBeInTheDocument();
  expect(screen.getByText('57.0%')).toBeVisible();
});

test('explains missing calibration and recent degradation without claiming a trading signal', async () => {
  render(<ResearchResult report={{ ...calibrated, targets: [{ ...calibrated.targets[0],
    calibrationStatus: 'NOT_FITTED', calibrationReason: '校准区正负标签均需至少 5 个',
    reliability: { ...calibrated.targets[0].reliability!, status: 'RECENT_DEGRADATION' } }] }} />);
  expect(screen.getByText('近期效果退化')).toBeVisible();
  expect(screen.queryByText('校准后盈利概率 · 已扣假设成本')).not.toBeInTheDocument();
  await userEvent.click(screen.getByText('查看独立验证依据'));
  expect(screen.getByText('校准区正负标签均需至少 5 个')).toBeVisible();
});

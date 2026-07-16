import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { CapitalResearchBridge } from './CapitalResearchBridge';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const overview = {
  instrument: { id: 7, code: '600519', type: 'STOCK' as const, name: '贵州茅台', market: 'SH' },
  snapshot: { id: 12, instrumentId: 7, asOf: '2026-07-14T15:00:00', fingerprint: 'snapshot-12', qualityStatus: 'COMPLETE' as const, warnings: [] },
  intradayTimeline: [], dailyTrend: [],
  metrics: { latest: null, intradayStreak: { direction: 'FLAT' as const, periods: 0, granularity: 'MINUTE_5' }, dailyStreak: { direction: 'FLAT' as const, periods: 0, granularity: 'DAY_1' }, objectiveTags: [{ code: 'PRICE_FLOW_DIVERGENCE', label: '价资背离', explanation: '方向不一致', window: '2d', version: 'v1', metricRefs: ['flow:12:mainNetInflow'] }] },
  ruleExplanation: null, historicalEvaluation: null, factorObservations: [], watchConditions: [],
  health: { status: 'FRESH_PRIMARY' as const, asOf: '2026-07-14T15:00:00', providerCode: 'EASTMONEY', warnings: [] }
};

test('creates a traceable draft and navigates without starting analysis or experiments', async () => {
  const user = userEvent.setup();
  const onOpen = vi.fn();
  vi.mocked(api).mockResolvedValue({
    id: 9, status: 'DRAFT', factor: { namespace: 'capital', code: 'MAIN_FLOW_SHARE', version: '1.0.0' }
  } as never);

  render(<CapitalResearchBridge overview={overview} addToast={vi.fn()} onOpenQuantResearch={onOpen} />);
  await user.click(screen.getByRole('button', { name: '创建量化研究草稿' }));

  expect(api).toHaveBeenCalledWith('/api/factor-research/research-drafts/from-capital-signal', expect.objectContaining({ method: 'POST' }));
  const body = JSON.parse(String(vi.mocked(api).mock.calls[0][1]?.body));
  expect(body).toMatchObject({
    instrumentCode: '600519.SH', snapshotId: 12,
    snapshotFingerprint: 'snapshot-12', signalCode: 'PRICE_FLOW_DIVERGENCE'
  });
  expect(body.evidenceRefs).toContain('flow:12:mainNetInflow');
  expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ draftId: 9, factorCode: 'MAIN_FLOW_SHARE' }));
  expect(vi.mocked(api).mock.calls.some(([path]) => String(path).includes('/analysis'))).toBe(false);
  expect(vi.mocked(api).mock.calls.some(([path]) => String(path).includes('/experiments'))).toBe(false);
});

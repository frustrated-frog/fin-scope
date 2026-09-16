import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { StrengthDiscoveryPanel } from './StrengthDiscoveryPanel';
import type { StockDiscoveryReport } from './quantTypes';

test('shows rejected events, uncertainty and losses without promoting them to buy signals', async () => {
  const onOpenResearch = vi.fn();
  const report = {
    as_of_date: '2026-09-14',
    strength_watchlist: [{ code: '605058', name: '澳弘电子', sources: ['LIMIT_UP'], admitted: false,
      rejection_reasons: ['OVER_BUDGET'], assessment: { status: 'INSUFFICIENT_DATA', sample_count: 3,
        up_probability: 2 / 3, up_interval: [.2, .9], loss_rate: 1 / 3, execution_status: 'UNVERIFIED' } }],
    discovery_audit: { event_count: 1, event_deep_count: 0, sector_seats: {},
      misses: [{ code: '605058', name: '澳弘电子', reasons: ['OVER_BUDGET'] }],
      scan: { status: 'PARTIAL', warnings: ['历史补跑缺少全市场快照'] } },
    recall_evaluations: [{ status: 'SETTLED', signal_date: '2026-09-11', target_date: '2026-09-14',
      evidence_kind: 'RETROSPECTIVE', covered_count: 50, winner_count: 2,
      event_loss_rate: .6, event_recall: .5, deep_recall: 0, missed_winners: [
        { code: '605058', actual_return: .1, reason: 'OUTSIDE_CANDIDATE_POOL' }] }],
  } as StockDiscoveryReport;
  render(<StrengthDiscoveryPanel report={report} onOpenResearch={onOpenResearch} />);
  expect(screen.getByText(/同类样本不足/)).toBeInTheDocument();
  expect(screen.getByText('可成交性未验证')).toBeInTheDocument();
  expect(screen.getByText(/来源覆盖不完整/)).toBeInTheDocument();
  expect(screen.getByText(/强势事件亏损率 60.0%/)).toBeInTheDocument();
  expect(screen.getByText(/未进入候选池/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '澳弘电子 605058' }));
  expect(onOpenResearch).toHaveBeenCalledWith('605058');
});

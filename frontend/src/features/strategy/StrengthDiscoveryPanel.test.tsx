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

test('filters the whole pool across pages and recovers from empty results', async () => {
  const items: NonNullable<StockDiscoveryReport['strength_watchlist']> = Array.from({ length: 10 }, (_, index) => ({
    code: String(600000 + index), name: `观察股票${index}`, sources: [index === 9 ? 'BROKEN_LIMIT' : 'LIMIT_UP'],
    admitted: false, rejection_reasons: [], assessment: { status: 'INSUFFICIENT_DATA', sample_count: 0, execution_status: 'UNVERIFIED' },
  }));
  const user = userEvent.setup();
  render(<StrengthDiscoveryPanel report={{ as_of_date: '2026-09-16', strength_watchlist: items, source_family: 'FIXTURE', quality_status: 'PARTIAL',
    retrieved_at: '2026-09-16T15:00:00', budget: 10, duration_ms: 0, warnings: [],
    funnel: { constituent_count: 10, admitted_count: 0, quantified_count: 0, deep_review_count: 0, final_count: 0 },
    sectors: [], candidates: [], deep_evidence: [], final_candidates: [] }} onOpenResearch={vi.fn()} />);
  expect(screen.queryByRole('button', { name: '观察股票9 600009' })).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '下一页' }));
  expect(screen.getByRole('button', { name: '观察股票9 600009' })).toBeInTheDocument();
  await user.selectOptions(screen.getByLabelText('事件来源'), 'BROKEN_LIMIT');
  expect(screen.getByText('找到 1 只')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
  await user.type(screen.getByRole('searchbox', { name: '查找股票' }), '不存在');
  expect(screen.getByText('没有匹配的股票')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '清除筛选' }));
  expect(screen.getByText('找到 10 只')).toBeInTheDocument();
  await user.type(screen.getByRole('searchbox', { name: '查找股票' }), '600009');
  expect(screen.getByRole('button', { name: '观察股票9 600009' })).toBeInTheDocument();
});

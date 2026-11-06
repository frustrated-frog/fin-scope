import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { OvernightSelectionProgress } from './OvernightSelectionProgress';
import { selectionProtocol, type SameDaySelection, type SelectionForwardGroup } from './overnightSelectionTypes';

const historical: SameDaySelection = { protocol: selectionProtocol, dayCount: 20, sampleCount: 2110, noCandidateDays: 2,
  forcedTopK: { selectedDays: 20, selectionCountPerArm: 60, dayCoverage: 1,
    precision: { INDEX: .65, STOCK_ONLY: .55, INDEX_ONLY: .497 } },
  matchedCandidates: { selectedDays: 18, selectionCountPerArm: 51, dayCoverage: .9,
    precision: { INDEX: .685, STOCK_ONLY: .556, INDEX_ONLY: .488 } },
};
const forward: SelectionForwardGroup = { mode: 'TAIL_ENTRY', cutoff: '14:45', dayCount: 0,
  scheduledDays: 3, pairedCount: 5, scheduledCount: 9, sameDaySelection: { ...historical, dayCount: 0 } };

test('starts with actual results, retaining missing records and separating retrospective scores', async () => {
  render(<OvernightSelectionProgress cutoff="14:45" historical={historical} forward={forward} />);
  expect(screen.getByRole('button', { name: '真实次日结果' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByText(/已配对 5 \/ 9 条，完整日期 0 \/ 3 日/)).toBeVisible();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(screen.queryByText('65.0%')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '历史研究' }));
  expect(screen.getByText(/历史研究用于诊断，不代表真实预测准确率/)).toBeVisible();
  const table = screen.getByRole('table');
  expect(within(table).getByRole('cell', { name: '65.0%' })).toBeVisible();
  expect(screen.getByText(/20 个完整日期 · 候选覆盖 18 日 · 无看涨候选 2 日/)).toBeVisible();
});

test('no candidates remain missing, never become zero or successful direction guesses', () => {
  const result = { ...historical, dayCount: 1, noCandidateDays: 1,
    matchedCandidates: { selectedDays: 0, selectionCountPerArm: 0, dayCoverage: 0,
      precision: { INDEX: null, STOCK_ONLY: null, INDEX_ONLY: null } } };
  render(<OvernightSelectionProgress cutoff="14:45" forward={{ ...forward, dayCount: 1, sameDaySelection: result }} />);
  expect(screen.getAllByRole('cell', { name: '—' })).toHaveLength(3);
  expect(screen.getByText(/候选覆盖 0 日 · 无看涨候选 1 日/)).toBeVisible();
  expect(screen.queryByRole('cell', { name: '0.0%' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '历史研究' })).toBeDisabled();
});

test('rejects incompatible historical protocols', () => {
  render(<OvernightSelectionProgress cutoff="14:30" historical={{ ...historical, protocol: 'old' }} />);
  expect(screen.getByRole('button', { name: '历史研究' })).toBeDisabled();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

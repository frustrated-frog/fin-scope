import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { OvernightCandidateTraining } from './OvernightCandidateTraining';
import type { CandidateTraining, CandidateTrainingComparison } from './overnightCandidateTypes';

const training: CandidateTraining = { status: 'READY', reason: 'CANDIDATE_TRAINED', trainingDays: 11, trainingRows: 78,
  outsidePoolRows: 77, trainingThrough: '2026-11-12T15:00:00', excludedRows: 0, unverifiedActionRows: 77, minimumDays: 5, minimumRows: 30 };

test('distinguishes small paired historical results from forward accuracy and exposes counts', () => {
  const comparison: CandidateTrainingComparison = { dayCount: 5, sampleCount: 42, plannedDays: 12, warmupDays: 6,
    firstDate: '2026-11-05', lastDate: '2026-11-11', evidenceKind: 'RETROSPECTIVE', eligible: false, momentum: { precision: .4 },
    lastFiveDates: { dayCount: 5, sampleCount: 42, arms: {}, momentum: null }, arms: {
      CANDIDATE: { accuracy: .52, brierScore: .27, topK: { precision: .6 },
        calls: { precision: 1, dayCoverage: .2, selectedDays: 1, selectionCount: 2 } },
      POOL_ROLLING: { accuracy: .51, brierScore: .25, topK: { precision: .49 },
        calls: { precision: null, dayCoverage: 0, selectedDays: 0, selectionCount: 0 } },
    } };
  render(<OvernightCandidateTraining training={training} comparison={comparison} />);
  expect(screen.getByText(/专训在 1 \/ 5 日报看涨，共 2 条/)).toBeVisible();
  expect(screen.getByText(/不等于上线后的准确率/)).toBeVisible();
  expect(screen.getByText(/78 条训练样本/)).toBeVisible();
  expect(screen.getByText('52.0%')).toBeVisible();
});

test('missing historical comparison stays unknown even when the latest model is trained', () => {
  render(<OvernightCandidateTraining training={training} />);
  expect(screen.getByText(/尚无训练完成后的完整回放日/)).toBeVisible();
  expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
});

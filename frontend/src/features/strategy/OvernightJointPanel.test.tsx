import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { OvernightJointPanel, OvernightJointRanking, OvernightJointEvidence } from './OvernightJointPanel';
import type { OvernightJointState } from './overnightJointTypes';
import type { OvernightAutomationJob } from './overnightTypes';

const state: OvernightJointState = { protocol: 'overnight-joint-v1', poolSize: 120, targetSize: 120,
  readySymbols: 19, minimumSymbols: 20, models: [], jobs: [] };

test('separates market coverage from validated forecasting ability and folds details', async () => {
  render(<OvernightJointPanel state={state} mode="TAIL_ENTRY" />);
  expect(screen.getByText('公共样本正在积累')).toBeVisible();
  expect(screen.getByText(/至少 20 只/)).toBeVisible();
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  expect(screen.getByText(/首批 60 个/)).not.toBeVisible();
  await userEvent.click(screen.getByText('样本范围与新旧模型对照'));
  expect(screen.getByText(/同一天的多只股票合计为一天/)).toBeVisible();
});

test('shows paired forward evidence only for the selected mode', async () => {
  const value: OvernightJointState = { ...state, readySymbols: 120,
    models: [{ id: 'one', createdAt: '2026-10-05T10:00:00', labelsThrough: '2026-09-30T15:00:00',
      profile: { key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', costBps: 20 },
      data: { symbolCount: 120, dayCount: 200, rows: 20000 }, targets: [] }],
    forward: { requiredDays: 60, computedAt: '2026-10-05T10:00:00', groups: [
      { key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', costBps: 20, target: '10:00', status: 'ACCUMULATING',
        eligible: false, dayCount: 8, requiredDays: 60, pairedCount: 40, coverage: 1,
        metrics: { accuracy: .6, brierScore: .21, comparisons: { INCUMBENT: { brierScore: .24, accuracy: .55 } } } },
      { key: 'holding', mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00', costBps: 10, target: 'CLOSE', status: 'QUALIFIED',
        eligible: true, dayCount: 60, requiredDays: 60, pairedCount: 200, coverage: 1 },
    ] } };
  render(<OvernightJointPanel state={value} mode="TAIL_ENTRY" />);
  expect(screen.getByText('联合模型正在并行验证')).toBeVisible();
  expect(screen.queryByText(/个退出时点通过/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByText('样本范围与新旧模型对照'));
  expect(screen.getByText('8 / 60')).toBeVisible();
  expect(screen.getByText('0.210 / 0.240')).toBeVisible();
  expect(screen.getByText('60.0% / 55.0%')).toBeVisible();
  expect(screen.queryByText(/15:00 →/)).not.toBeInTheDocument();
});

test('does not present shadow rankings as qualified candidates and explains abstention', () => {
  const job: OvernightAutomationJob = { key: 'one', signalDate: '2026-09-21', cutoff: '14:30', mode: 'TAIL_ENTRY',
    phase: 'PREDICT', status: 'COMPLETED', startedAt: '2026-09-21T14:30:00', attempts: 1,
    ranking: { status: 'SHADOW', target: '10:00', opportunityStatus: 'RESEARCH_ONLY', evaluatedCount: 6, candidates: [] } };
  const { rerender } = render(<OvernightJointRanking job={job} />);
  expect(screen.getByText(/净收益排序仍在验证/)).toBeVisible();
  expect(screen.queryByRole('list')).not.toBeInTheDocument();
  rerender(<OvernightJointRanking job={{ ...job, ranking: { ...job.ranking!, status: 'ACTIVE', opportunityStatus: 'NO_QUALIFIED' } }} />);
  expect(screen.getByText(/本轮没有通过净收益与风险条件的候选/)).toBeVisible();
});

test('labels the individual challenger and downside estimates without replacing the primary value', async () => {
  render(<OvernightJointEvidence target={{ target: '10:00', status: 'WATCH', sampleCount: 140,
    upProbability: .4, joint: { status: 'AVAILABLE', adopted: false, qualified: true, artifactId: 'test',
      upProbability: .6, expectedNetReturn: .005, downsideProbability: .12, lowerNetReturn: -.02,
      upperNetReturn: .03, rankScore: .0038, forwardStatus: 'ACCUMULATING', forwardDays: 4 } }} />);
  expect(screen.getByText('联合模型 · 并行观察')).toBeVisible();
  expect(screen.getByText('60.0%')).not.toBeVisible();
  await userEvent.click(screen.getByText('联合模型 · 并行观察'));
  expect(screen.getByText('12.0%')).toBeVisible();
  expect(screen.getByText(/主参考值仍沿用原有判断/)).toBeVisible();
});

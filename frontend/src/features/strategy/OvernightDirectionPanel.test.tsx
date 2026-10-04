import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { OvernightDirectionPanel, OvernightDirectionSummary } from './OvernightDirectionPanel';
import { ResearchResult } from './OvernightStrategyPanel';
import type { OvernightReport } from './overnightTypes';
import type { OvernightJointState } from './overnightJointTypes';

const report: OvernightReport = { mode: 'TAIL_ENTRY', instrumentCode: '605058.SH', signalDate: '2026-09-21',
  cutoff: '14:30', status: 'WATCH', generatedAt: '2026-09-21T14:31:00', dataThrough: '2026-09-21T14:30:00',
  targetDate: '2026-09-22', costBps: 20, evidenceKind: 'FORWARD', inputFingerprint: 'test', modelVersion: 'test',
  targets: [], warnings: [], closeDirection: { protocol: 'overnight-close-direction-v1',
    target: 'NEXT_SESSION_CLOSE_VS_SIGNAL_CLOSE', status: 'SHADOW', reason: 'test',
    upProbability: .58, notUpProbability: .42, selectedModel: 'SHAPE_LOGISTIC', probabilitySource: 'RAW',
    forwardDays: 4, forwardStatus: 'ACCUMULATING', validated: false,
    historical: { accuracy: .54, balancedAccuracy: .52, brierScore: .25, sampleCount: 460, dayCount: 20,
      predictedUpRate: .3, comparisons: { HISTORICAL_PRIOR: { accuracy: .56, brierScore: .24 } } } } };

test('separates price direction from trading profit and discloses weak evidence', async () => {
  render(<OvernightDirectionPanel report={report} />);
  expect(screen.getByText('模型偏向上涨')).toBeVisible();
  expect(screen.getByText('尚未验证有效')).toBeVisible();
  expect(screen.getByText('58.0%')).toBeVisible();
  expect(screen.getByText(/未上涨概率/)).toBeVisible();
  expect(screen.getByText(/不扣交易成本/)).toBeVisible();
  expect(screen.getByText('54.0% / 56.0%')).not.toBeVisible();
  await userEvent.click(screen.getByText('查看涨跌验证依据'));
  expect(screen.getByText('54.0% / 56.0%')).toBeVisible();
  expect(screen.getByText(/始终猜同一类只能得到 50%/)).toBeVisible();
  expect(screen.getByText(/当前为模型原始概率/)).toBeVisible();
});

test('does not present a historical proportion as model skill or missing data as zero', () => {
  const { rerender } = render(<OvernightDirectionPanel report={{ ...report, closeDirection: {
    ...report.closeDirection!, selectedModel: 'PRIOR' } }} />);
  expect(screen.getByText('历史参考，尚无模型优势')).toBeVisible();
  expect(screen.queryByText('模型偏向上涨')).not.toBeInTheDocument();
  expect(screen.getByText('历史上涨比例')).toBeVisible();
  rerender(<OvernightDirectionPanel report={{ ...report, closeDirection: {
    protocol: 'test', target: 'test', status: 'WAITING_MODEL', reason: '正在自动补齐样本' } }} />);
  expect(screen.getByText('次日涨跌正在准备')).toBeVisible();
  expect(screen.getByText('正在自动补齐样本')).toBeVisible();
  expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
});

test('shows actual price outcome and keeps old reports compatible', () => {
  const { rerender } = render(<OvernightDirectionPanel report={{ ...report,
    outcome: { status: 'ENTRY_UNVERIFIED', targets: [], closeDirection: {
      status: 'SETTLED', actualReturn: -.02, actualUp: false, correct: false } } }} />);
  expect(screen.getByText('次日实际下跌 -2.0%')).toBeVisible();
  expect(screen.getByText('本次方向判断未命中')).toBeVisible();
  rerender(<OvernightDirectionPanel report={{ ...report, closeDirection: undefined }} />);
  expect(screen.queryByRole('region', { name: '次日收盘涨跌预测' })).not.toBeInTheDocument();
});

test('filters direction acceptance by tail and holding modes', async () => {
  const state: OvernightJointState = { protocol: 'test', poolSize: 120, targetSize: 120, readySymbols: 20,
    minimumSymbols: 20, models: [], jobs: [], closeDirectionForward: { groups: [
      { key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', status: 'ACCUMULATING', eligible: false,
        dayCount: 4, requiredDays: 60, coverage: 1, metrics: report.closeDirection!.historical },
      { key: 'holding', mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00', status: 'QUALIFIED', eligible: true,
        dayCount: 60, requiredDays: 60, coverage: 1 },
    ] } };
  render(<OvernightDirectionSummary state={state} mode="TAIL_ENTRY" />);
  await userEvent.click(screen.getByText('次日涨跌独立验证'));
  expect(screen.getByText('14:30 → 次日收盘')).toBeVisible();
  expect(screen.queryByText('15:00 → 次日收盘')).not.toBeInTheDocument();
  expect(screen.getByText(/积累真实预测 · 4 \/ 60 日/)).toBeVisible();
});

test('a ready direction forecast is visible while trading return samples are insufficient', () => {
  render(<ResearchResult report={{ ...report, status: 'INSUFFICIENT_DATA' }} />);
  expect(screen.getByRole('heading', { name: '涨跌预测已生成 · 收益样本不足' })).toBeVisible();
  expect(screen.getByRole('region', { name: '次日收盘涨跌预测' })).toBeVisible();
});

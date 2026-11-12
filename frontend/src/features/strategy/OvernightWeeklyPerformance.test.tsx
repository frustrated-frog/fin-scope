import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { OvernightWeeklyPerformance } from './OvernightWeeklyPerformance';
import type { WeeklyDirectionAudit } from './weeklyDirectionTypes';

const score = { sampleCount: 18, dayCount: 1, predictedUpCount: 0, actualUpCount: 2, trueUpCount: 0,
  missedUpCount: 2, falseUpCount: 0, upPrecision: null, upRecall: 0, predictedUpRate: 0, accuracy: 16/18, alwaysNotUpAccuracy: 16/18 };
const audit: WeeklyDirectionAudit = { protocol: 'overnight-weekly-diagnostics-v1', target: 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE',
  periodStart: '2026-11-09', periodEnd: '2026-11-12', computedAt: '2026-11-12T18:42:00',
  archives: [{ mode: 'TAIL_ENTRY', recordCount: 90, pendingCount: 36 }, { mode: 'AFTER_CLOSE_HOLDING', recordCount: 12, pendingCount: 4 }],
  windows: [{ signalDate: '2026-11-11', cutoff: '14:45', status: 'MISSED', reason: '服务未在有效窗口完成', diagnostic: { gapSeconds: 1140 } }],
  groups: [{ key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', completeDays: 1, scheduledCount: 21, settledCount: 20,
    missing: { MISSING_PREDICTION: 1 }, upDetection: { metrics: score, noUpDays: 1, pairedDays: 0,
      daily: [{ ...score, signalDate: '2026-11-11' }], comparisons: { PRIMARY: null }, missedExampleTotal: 2,
      missedExamples: [{ signalDate: '2026-11-11', instrumentCode: '600001.SH', probability: .4, rawProbability: .4,
        calibratedProbability: .3, trainedAt: '2026-11-10T08:00:00', modelFitThrough: null, indexReturn: null }] } }],
};

test('always-down high accuracy is shown beside missed winners and no fabricated precision', async () => {
  render(<OvernightWeeklyPerformance audit={audit} mode="TAIL_ENTRY" />);
  expect(screen.getByText('—', { selector: 'dd' })).toBeVisible();
  expect(screen.getByText(/看涨 0 条，命中 0 条；实际上涨 2 条，漏判 2 条/)).toBeVisible();
  expect(screen.getByText('整体猜对 88.9%')).toBeVisible();
  expect(screen.getByText('全判不涨 88.9%')).toBeVisible();
  expect(screen.getByText(/已核验 20 \/ 21 条/)).toBeVisible();
  await userEvent.click(screen.getByText('逐日结果与漏判分析'));
  expect(screen.getByText(/最终拟合截止 旧档案未明确记录/)).toBeVisible();
  await userEvent.click(screen.getByText('1 / 1 个已结束尾盘窗口未完整完成'));
  expect(screen.getByText(/调度间隔 1140 秒/)).toBeVisible();
});

test('separates holdings and hides incompatible evidence', () => {
  const { rerender } = render(<OvernightWeeklyPerformance audit={audit} mode="AFTER_CLOSE_HOLDING" />);
  expect(screen.getByText('12')).toBeVisible();
  expect(screen.queryByText(/14:30 →/)).not.toBeInTheDocument();
  expect(screen.queryByText(/未完整完成/)).not.toBeInTheDocument();
  rerender(<OvernightWeeklyPerformance audit={{ ...audit, target: 'old' }} mode="TAIL_ENTRY" />);
  expect(screen.queryByRole('region', { name: '本周看涨识别' })).not.toBeInTheDocument();
});

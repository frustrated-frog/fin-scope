import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { OvernightMarketProgress } from './OvernightMarketProgress';
import type { OvernightJointState } from './overnightJointTypes';

const state: OvernightJointState = { protocol: 'test', poolSize: 120, targetSize: 120, readySymbols: 20,
  minimumSymbols: 20, models: [], jobs: [] };

test('missing training and validation stay explicit, never become zero accuracy', () => {
  render(<OvernightMarketProgress state={state} mode="TAIL_ENTRY" />);
  expect(screen.getAllByText('积累训练数据')).toHaveLength(8);
  expect(screen.getByText('14:30 · 市场整体强弱')).toBeVisible();
  expect(screen.queryByText(/15:00/)).not.toBeInTheDocument();
  expect(screen.queryByText(/命中率/)).not.toBeInTheDocument();
});

test('relative strength shows actual aligned coverage and its separate training requirement', () => {
  render(<OvernightMarketProgress state={{ ...state, contextSnapshots: [{ signalDate: '2026-11-06', cutoff: '14:30',
    observedAt: '2026-11-06T14:20:00', receivedAt: '2026-11-06T14:21:00', capturedSymbols: 100, expectedSymbols: 120,
    relativeStrength: { protocol: 'overnight-relative-strength-v1', capturedIndices: 2, expectedIndices: 3, pairedSymbols: 0 },
  }] }} mode="TAIL_ENTRY" />);
  expect(screen.getByText(/最近采集 2026-11-06 14:20 · 指数 2 \/ 3 个 · 已对齐 0 只股票/)).toBeVisible();
  expect(screen.getAllByText(/有效历史 0 \/ 164 日/)).toHaveLength(2);
});

test('holds timing and separates trained history from actual complete-day results', () => {
  render(<OvernightMarketProgress state={{ ...state,
    marketAblation: [{ cutoff: '15:00', groups: [{ group: 'MARKET', status: 'RESEARCH_ONLY', dayCount: 150, requiredDays: 143 }] }],
    marketForward: { groups: [{ mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00', group: 'MARKET', dayCount: 2,
      requiredDays: 60, pairedCount: 7, scheduledCount: 9,
      metrics: { accuracy: .6, comparisons: { MATCHED_BASELINE: { accuracy: .5 } } } }] },
  }} mode="AFTER_CLOSE_HOLDING" />);
  expect(screen.getByText('模型已训练，积累真实对照')).toBeVisible();
  expect(screen.getByText(/有效历史 150 \/ 143 日 · 完整次日对照 2 \/ 60 日/)).toBeVisible();
  expect(screen.getByText(/已配对 7 \/ 9 条/)).toBeVisible();
  expect(screen.getByText(/60.0% \/ 50.0% · 仅供研究/)).toBeVisible();
  expect(screen.queryByText(/14:30/)).not.toBeInTheDocument();
});

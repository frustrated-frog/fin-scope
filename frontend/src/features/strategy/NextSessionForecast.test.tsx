import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { NextSessionForecast } from './NextSessionForecast';

const prediction = {
  status: 'READY' as const, asOfDate: '2026-09-04', targetDate: '2026-09-07', generatedAt: '2026-09-04T16:00:00',
  label: 'NEXT_CLOSE_RETURN', lastClose: 10, upProbability: .65, expectedReturn: .01,
  lowerReturn: -.02, upperReturn: .03, decision: 'UP' as const, modelVersion: 'next-session-rolling-v1',
  dataFingerprint: 'a'.repeat(64), trainingThrough: '2026-06-01', calibrationThrough: '2026-09-04',
  trainingSampleCount: 504, calibrationSampleCount: 60, validationSampleCount: 60,
  brierScore: .22, baselineBrierScore: .25, accuracy: .6, intervalCoverage: .8, warnings: [],
};

test('shows explicit target and close-to-close evidence without claiming trading profit', () => {
  render(<NextSessionForecast prediction={prediction} />);
  expect(screen.getByText('2026-09-07')).toBeInTheDocument();
  expect(screen.getByText('65.0%')).toBeInTheDocument();
  expect(screen.getByText(/收盘相对/)).toBeInTheDocument();
  expect(screen.getByText(/不等于可成交收益/)).toBeInTheDocument();
});

test('does not manufacture probabilities for missing or stale evidence', () => {
  render(<NextSessionForecast prediction={{ ...prediction, status: 'STALE_DATA', upProbability: undefined,
    expectedReturn: undefined, lowerReturn: undefined, upperReturn: undefined }} />);
  expect(screen.getByText('行情已过期')).toBeInTheDocument();
  expect(screen.queryByText('65.0%')).not.toBeInTheDocument();
});

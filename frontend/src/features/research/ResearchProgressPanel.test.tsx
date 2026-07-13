import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { ResearchRunDetail } from '../../shared/types';
import { ResearchProgressPanel } from './ResearchProgressPanel';

test('shows meaningful live progress instead of exposing internal plan records', () => {
  const detail: ResearchRunDetail = {
    run: {
      id: 16,
      thesisId: 1,
      runDate: '2026-07-13',
      themeCodes: [],
      sourceCount: 9,
      fetchedSourceCount: 8,
      articleCount: 56,
      evidenceCount: 39,
      status: 'RUNNING'
    },
    plannedSources: [],
    agentRuns: [],
    planSteps: [
      { stepId: 'plan_sources', title: 'plan_sources', status: 'COMPLETED' },
      { stepId: 'fetch_sources', title: 'fetch_sources', status: 'COMPLETED' },
      { stepId: 'classify_events', title: 'classify_events', status: 'COMPLETED' },
      { stepId: 'extract_evidence', title: 'extract_evidence', status: 'COMPLETED' },
      { stepId: 'compose_report', title: 'compose_report', status: 'RUNNING', startedAt: new Date().toISOString() },
      { stepId: 'summarize_run', title: 'summarize_run', status: 'PENDING' }
    ],
    reportAvailable: false,
    canRegenerateReport: false
  };

  render(<ResearchProgressPanel detail={detail} />);

  expect(screen.getByText('正在生成研究报告')).toBeInTheDocument();
  expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '67');
  expect(screen.getByText('4 / 6 步已完成')).toBeInTheDocument();
  expect(screen.getByText(/8\/9 个来源/)).toBeInTheDocument();
  expect(screen.getByText('生成研究报告').closest('li')).toHaveAttribute('data-status', 'RUNNING');
});

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { ResearchReport, ResearchRunDetail } from '../../shared/types';
import { ResearchView } from './ResearchView';

test('offers report recovery for a terminal legacy run without promising a missing report', async () => {
  const onRegenerateReport = vi.fn().mockResolvedValue(undefined);
  renderView(legacyDetail(), { onRegenerateReport });

  expect(screen.queryByRole('button', { name: '阅读研究报告' })).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '补建研究报告' }));
  expect(onRegenerateReport).toHaveBeenCalledWith(15);
});

test('uses a dedicated reader when a report is open', () => {
  const report = sampleReport();
  renderView({ ...legacyDetail(), reportAvailable: true }, { report });

  expect(screen.getByRole('article', { name: '研究报告' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: report.title })).toBeInTheDocument();
  expect(screen.queryByText('历次研究运行')).not.toBeInTheDocument();
});

function renderView(
  detail: ResearchRunDetail,
  overrides: Partial<React.ComponentProps<typeof ResearchView>> = {}
) {
  return render(
    <ResearchView
      runs={[detail.run]}
      theses={[]}
      detail={detail}
      report={null}
      busy={false}
      reportBusy={false}
      onRun={vi.fn()}
      onCreateThesis={vi.fn()}
      onOpenRun={vi.fn()}
      onOpenReport={vi.fn()}
      onRegenerateReport={vi.fn()}
      onCloseReport={vi.fn()}
      {...overrides}
    />
  );
}

function legacyDetail(): ResearchRunDetail {
  return {
    run: {
      id: 15,
      thesisId: 1,
      runDate: '2026-07-13',
      themeCodes: [],
      sourceCount: 9,
      fetchedSourceCount: 8,
      articleCount: 28,
      eventCount: 28,
      evidenceCount: 39,
      status: 'PARTIAL_SUCCESS'
    },
    plannedSources: [],
    planSteps: [],
    agentRuns: [],
    reportAvailable: false,
    canRegenerateReport: true
  };
}

function sampleReport(): ResearchReport {
  return {
    id: 4,
    researchRunId: 15,
    thesisId: 1,
    reportType: 'THESIS',
    status: 'COMPLETED',
    title: '科技板块研究报告',
    conclusion: '结论已形成',
    conclusionDirection: 'MIXED',
    confidence: 'MEDIUM',
    executiveSummary: '摘要',
    contentMarkdown: '## 核心判断\n\n正文',
    markdownPath: '/tmp/run-15.md',
    generationMode: 'DETERMINISTIC',
    evidenceCount: 12,
    sourceCount: 5,
    characterCount: 800
  };
}

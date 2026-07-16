import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import {
  ResearchReport,
  ResearchRunDetail,
  ResearchThesis,
  ResearchThesisDetail,
  ThesisFinding
} from '../../shared/types';
import { ResearchView } from './ResearchView';
import { apiResponse } from '../../test/apiEnvelope';

test('renders research runs as a telemetry list', () => {
  renderView(legacyDetail());

  expect(screen.getByText('历次研究运行')).toBeInTheDocument();
  expect(screen.getByText('共 1 次')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /打开研究运行/ })).toHaveClass('research-run-row');
  expect(screen.getAllByText('部分完成').length).toBeGreaterThan(0);
});

test('groups the report action inside a responsive research detail header', () => {
  renderView({ ...legacyDetail(), reportAvailable: true });

  expect(screen.getByRole('button', { name: '阅读研究报告' }).closest('.research-detail-head')).not.toBeNull();
});

test('prioritizes one thesis decision summary over equal-weight output cards', async () => {
  stubThesisDetail(thesisDetail());
  renderView(legacyDetail(), { theses: [thesis()] });

  await userEvent.click(screen.getAllByRole('button', { name: /半导体设备/ }).find((button) => button.classList.contains('research-thesis-card'))!);

  expect(await screen.findByRole('region', { name: '命题决策摘要' })).toBeInTheDocument();
  expect(screen.getByText('中等置信')).toBeInTheDocument();
  expect(screen.getByText('下一验证点')).toBeInTheDocument();
  expect(screen.queryByText('本次研究产物')).not.toBeInTheDocument();
  expect(screen.getByText('2 次研究')).toBeInTheDocument();
  expect(screen.getByText('5 项关联产物')).toBeInTheDocument();
});

test('limits each evidence lane to two findings and reports the remainder', async () => {
  stubThesisDetail(thesisDetail());
  renderView(legacyDetail(), { theses: [thesis()] });

  await userEvent.click(screen.getAllByRole('button', { name: /半导体设备/ }).find((button) => button.classList.contains('research-thesis-card'))!);
  await screen.findByRole('region', { name: '命题决策摘要' });

  expect(screen.getByText('支持一')).toBeInTheDocument();
  expect(screen.getByText('支持二')).toBeInTheDocument();
  expect(screen.queryByText('支持三')).not.toBeInTheDocument();
  expect(screen.getByText('另有 1 条')).toBeInTheDocument();
});

test('keeps sources and traces collapsed and limits the expanded source preview', async () => {
  renderView(detailWithSources());

  const diagnostics = screen.getByText('研究过程与来源').closest('details');
  expect(diagnostics).not.toHaveAttribute('open');
  expect(screen.getByText('已获取 6/6 个来源 · 执行详情默认收起')).toBeInTheDocument();

  await userEvent.click(screen.getByText('研究过程与来源'));

  expect(screen.getAllByTestId('planned-source')).toHaveLength(4);
  expect(screen.getByText('另有 2 个来源未展开')).toBeInTheDocument();
});

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

function detailWithSources(): ResearchRunDetail {
  return {
    ...legacyDetail(),
    run: { ...legacyDetail().run, sourceCount: 6, fetchedSourceCount: 6 },
    plannedSources: Array.from({ length: 6 }, (_, index) => ({
      sourceId: index + 1,
      sourceName: `来源 ${index + 1}`,
      sourceTier: index === 0 ? 'REGULATOR' : 'MEDIA',
      credibility: 5 - Math.min(index, 2),
      enabled: true
    })),
    agentRuns: [{
      id: 1,
      nodeName: 'article-interpret',
      status: 'COMPLETED',
      durationMs: 20,
      output: '已完成文章理解'
    }]
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

function thesis(): ResearchThesis {
  return {
    id: 1,
    question: '科技板块冲高后近期大跌回落，周期是否还能持续',
    subjectType: 'INDUSTRY',
    subjectName: '半导体设备',
    status: 'OPEN',
    conclusion: '支持与转弱信号并存，周期仍需后续经营数据确认。',
    confidence: 'MEDIUM',
    nextValidation: '下一披露期复核订单、资本开支和产能利用率'
  };
}

function thesisDetail(): ResearchThesisDetail {
  return {
    thesis: thesis(),
    findings: [
      finding(1, 'SUPPORT', '支持一'),
      finding(2, 'SUPPORT', '支持二'),
      finding(3, 'SUPPORT', '支持三'),
      finding(4, 'COUNTER', '反证一'),
      finding(5, 'UNKNOWN', '未知一')
    ],
    runs: [legacyDetail().run, { ...legacyDetail().run, id: 14, runDate: '2026-07-12' }],
    outputs: Array.from({ length: 5 }, (_, index) => ({
      id: index + 1,
      researchRunId: 15,
      outputType: 'EVIDENCE',
      outputId: index + 1
    }))
  };
}

function finding(id: number, stance: ThesisFinding['stance'], summary: string): ThesisFinding {
  return { id, thesisId: 1, stance, summary };
}

function stubThesisDetail(detail: ResearchThesisDetail) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(apiResponse(detail, {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })));
}

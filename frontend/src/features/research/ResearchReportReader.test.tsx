import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { ResearchReport } from '../../shared/types';
import { ResearchReportReader } from './ResearchReportReader';

test('opens a complete report in a dedicated, understandable reader', async () => {
  const onBack = vi.fn();
  const report: ResearchReport = {
    id: 3,
    researchRunId: 16,
    thesisId: 1,
    reportType: 'THESIS',
    status: 'COMPLETED',
    title: '科技板块冲高后回落：周期是否还能持续',
    conclusion: '周期仍有延续条件，但需要盈利和资金面进一步确认。',
    conclusionDirection: 'MIXED',
    confidence: 'LOW',
    executiveSummary: '关键结论摘要',
    contentMarkdown: '## 核心判断\n\n这是有组织的研究结论。\n\n## 风险与反证\n\n- 盈利不及预期',
    markdownPath: '/tmp/run-16.md',
    generationMode: 'DETERMINISTIC',
    warningMessage: '目前有效证据数量有限',
    evidenceCount: 3,
    sourceCount: 3,
    characterCount: 1200
  };

  render(<ResearchReportReader report={report} onBack={onBack} />);

  expect(screen.getByRole('article', { name: '研究报告' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: report.title })).toHaveFocus();
  expect(screen.getByText('核心判断')).toBeInTheDocument();
  expect(screen.getByRole('note', { name: '证据边界' })).toHaveTextContent(report.warningMessage!);
  expect(screen.getByText('规则引擎保底生成')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: /返回研究运行/ }));
  expect(onBack).toHaveBeenCalledOnce();
});

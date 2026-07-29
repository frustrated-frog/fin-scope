import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
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
  expect(screen.getByRole('heading', { name: '核心判断' })).toBeInTheDocument();
  expect(screen.getByRole('note', { name: '证据边界' })).toHaveTextContent(report.warningMessage!);
  expect(screen.getByText('规则引擎保底生成')).toBeInTheDocument();
  expect(screen.getByRole('navigation', { name: '报告目录' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '核心判断' })).toHaveAttribute('href', '#section-核心判断');

  await userEvent.click(screen.getByRole('button', { name: /返回研究运行/ }));
  expect(onBack).toHaveBeenCalledOnce();
});

test('presents the structured model generation mode', () => {
  const report: ResearchReport = {
    id: 4, researchRunId: 17, reportType: 'THESIS', status: 'COMPLETED', title: '长鑫科技深度研究报告',
    conclusion: '高市值首先反映集中价格发现。', conclusionDirection: 'MIXED', confidence: 'MEDIUM',
    executiveSummary: '摘要', contentMarkdown: '## 核心结论\n\n结论\n\n## 证据附录\n\n来源',
    markdownPath: '/tmp/run-17.md', generationMode: 'MODEL_STRUCTURED', evidenceCount: 8, sourceCount: 5,
    characterCount: 9000
  };

  render(<ResearchReportReader report={report} onBack={() => undefined} />);

  expect(screen.getByText('结构化模型生成')).toBeInTheDocument();
});

test('keeps the desktop report body on the page centerline', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(
    /\.research-report-reader\.standalone\s*{[^}]*max-width:\s*1480px;[^}]*margin:\s*0\s+auto;/s
  );
  expect(styles).toMatch(
    /\.research-report-reader\.standalone\s*{[^}]*container:\s*report-reader\s*\/\s*inline-size;/s
  );
  expect(styles).toMatch(
    /\.research-report-layout\s*{[^}]*grid-template-columns:\s*minmax\(144px,\s*176px\)\s+minmax\(0,\s*1040px\)\s+minmax\(144px,\s*176px\);[^}]*justify-content:\s*center;/s
  );
  expect(styles).toMatch(
    /\.research-report-layout\s*>\s*\.research-report-document\s*{[^}]*grid-column:\s*2;[^}]*width:\s*100%;/s
  );
  expect(styles).toMatch(/@container\s+report-reader\s*\(max-width:\s*1120px\)/);
  expect(styles).toMatch(
    /@container\s+report-reader\s*\(max-width:\s*1120px\)[\s\S]*\.research-report-toc-container\s+\.research-report-toc\s*>\s*strong\s*{[^}]*display:\s*none;[\s\S]*\.research-report-toc-container\s+\.research-report-toc\s+ol\s*{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/
  );
});

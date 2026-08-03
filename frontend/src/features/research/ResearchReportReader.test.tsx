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

test('presents a report whose factual claims were repaired without calling it a fallback', () => {
  const report: ResearchReport = {
    id: 7, researchRunId: 20, reportType: 'THESIS', status: 'COMPLETED', title: '事实修复报告',
    conclusion: '阶段性结论成立。', conclusionDirection: 'MIXED', confidence: 'MEDIUM',
    executiveSummary: '摘要', contentMarkdown: '## 核心结论\n\n结论\n\n## 证据附录\n\n来源',
    markdownPath: '/tmp/run-20.md', generationMode: 'MODEL_CLAIM_REPAIRED', evidenceCount: 8, sourceCount: 5,
    characterCount: 8000
  };

  render(<ResearchReportReader report={report} onBack={() => undefined} />);

  expect(screen.getByText('模型事实审计修复后生成')).toBeInTheDocument();
});

test('hides legacy evidence anchor markup while preserving evidence navigation', () => {
  const report: ResearchReport = {
    id: 5, researchRunId: 18, reportType: 'THESIS', status: 'COMPLETED', title: '证据锚点兼容报告',
    conclusion: '结论', conclusionDirection: 'MIXED', confidence: 'MEDIUM', executiveSummary: '摘要',
    contentMarkdown: '## 核心结论\n\n参见 [E1](#evidence-e1)。\n\n## 证据附录\n\n'
      + '<a id="evidence-e1"></a>\n### E1 · 示例证据\n\n- 事实摘录：示例事实',
    markdownPath: '/tmp/run-18.md', generationMode: 'EVIDENCE_STRUCTURED_FALLBACK', evidenceCount: 1,
    sourceCount: 1, characterCount: 1800
  };

  render(<ResearchReportReader report={report} onBack={() => undefined} />);

  expect(screen.queryByText('<a id="evidence-e1"></a>')).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'E1' })).toHaveAttribute('href', '#evidence-e1');
  expect(screen.getByRole('heading', { name: 'E1 · 示例证据' })).toHaveAttribute('id', 'evidence-e1');
});

test('renders complete fact and ai interpretation pairs in the report body', () => {
  const completeFact = '长鑫科技上市首日成交额和换手率均处于高位，市场在有限流通盘上完成了集中价格发现。';
  const report: ResearchReport = {
    id: 6, researchRunId: 19, reportType: 'THESIS', status: 'COMPLETED', title: '命题优先研究报告',
    conclusion: '当前交易表现反映集中价格发现。', conclusionDirection: 'MIXED', confidence: 'MEDIUM',
    executiveSummary: '摘要', contentMarkdown: `## 核心结论\n\n结论 [E1](#evidence-e1)\n\n`
      + `## 关键事实与 AI 解读\n\n### 事实 1\n\n**事实：** ${completeFact} [E1](#evidence-e1)\n\n`
      + '**AI 解读：** 该事实说明短期定价高度集中，但不能单独证明长期价值。 [E1](#evidence-e1)\n\n'
      + '## 资料来源\n\n### E1 · 示例来源', markdownPath: '/tmp/run-19.md',
    generationMode: 'MODEL_STRUCTURED', evidenceCount: 1, sourceCount: 1, characterCount: 2400
  };

  render(<ResearchReportReader report={report} onBack={() => undefined} />);

  expect(screen.getByText(completeFact, { exact: false })).toBeInTheDocument();
  expect(screen.getByText(/该事实说明短期定价高度集中/)).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '关键事实与 AI 解读' }))
    .toHaveAttribute('href', '#section-关键事实与-ai-解读');
  expect(screen.queryByText(/已截断/)).not.toBeInTheDocument();
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

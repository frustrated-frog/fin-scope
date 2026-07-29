import { expect, test } from 'vitest';

import { extractReportSections, slugReportHeading } from './researchReportPresentation';

test('extracts stable report sections without treating third-level headings as navigation', () => {
  const markdown = '# 标题\n\n## 核心结论\n正文\n\n### 子问题\n正文\n\n## 核心证据链\n正文';

  expect(extractReportSections(markdown)).toEqual([
    { title: '核心结论', id: 'section-核心结论' },
    { title: '核心证据链', id: 'section-核心证据链' }
  ]);
  expect(slugReportHeading('最终认识与未知项')).toBe('最终认识与未知项');
});

test('extracts thesis-first report sections', () => {
  const markdown = '## 核心结论\n正文\n\n## 关键事实与 AI 解读\n正文\n\n### 事实 1\n正文\n\n## 资料来源\n正文';

  expect(extractReportSections(markdown)).toEqual([
    { title: '核心结论', id: 'section-核心结论' },
    { title: '关键事实与 AI 解读', id: 'section-关键事实与-ai-解读' },
    { title: '资料来源', id: 'section-资料来源' }
  ]);
});

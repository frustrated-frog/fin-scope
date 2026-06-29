import { ReactNode } from 'react';

import { Brief } from '../types';

export function splitLines(value?: string) {
  if (!value) {
    return [];
  }
  return value.split('\n').map((item) => item.trim()).filter(Boolean);
}

export function themeLabel(themeCode?: string) {
  switch (themeCode) {
    case 'china_macro':
      return '中国宏观';
    case 'company_ipo':
      return '公司 / IPO';
    case 'ai_startup':
      return 'AI 创业';
    case 'market':
      return '市场';
    default:
      return themeCode || '综合';
  }
}

export function parseBriefMarkdown(markdown: string, brief: Brief | null) {
  const lines = markdown.split(/\r?\n/);
  const titleLine = lines.find((line) => line.trim().startsWith('# '));
  const rawTitle = titleLine?.replace(/^#\s+/, '').trim() || brief?.title || '每日简报';
  const displayTitle = rawTitle.replace(/\s*-\s*\d{4}-\d{2}-\d{2}\s*$/, '');
  const generatedAt = lines.find((line) => line.trim().startsWith('生成时间：'))?.trim();
  const positioning = lines.find((line) => line.trim().startsWith('定位：'))?.trim();
  const bodyMarkdown = lines
    .filter((line, index) => !(index === lines.indexOf(titleLine ?? '') && line.trim().startsWith('# ')))
    .join('\n')
    .trim();
  const toc = lines
    .filter((line) => /^##\s+/.test(line.trim()))
    .map((line) => {
      const text = line.replace(/^##\s+/, '').trim();
      return { text, slug: slugify(text) };
    });

  return { displayTitle, generatedAt, positioning, bodyMarkdown, toc };
}

export function markdownNodeText(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map(markdownNodeText).join('');
  }
  return '';
}

export function slugify(value: string) {
  const slug = value
    .trim()
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug || 'section';
}

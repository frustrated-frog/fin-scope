// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';

import { render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';

import { AppShell } from './AppShell';

test('groups the workspace and exposes one knowledge entry', () => {
  render(
    <AppShell
      view="knowledge"
      currentTitle="投资认识工作台"
      theme="light"
      articlesCount={2}
      topicsCount={3}
      message="准备就绪"
      toasts={[]}
      onChangeView={vi.fn()}
      onToggleTheme={vi.fn()}
      onRefresh={vi.fn()}
    >
      <div />
    </AppShell>
  );

  expect(screen.getByText('研究流')).toBeInTheDocument();
  expect(screen.getByText('知识与判断')).toBeInTheDocument();
  expect(screen.getByText('系统')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Facts & Knowledge' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Events' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Evidence' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Market Intel' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Financials' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'News Wire' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '研究雷达' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Topics' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Learning' })).not.toBeInTheDocument();
  expect(screen.getByLabelText('认识档案 3')).toHaveTextContent('Files');
  expect(screen.queryByLabelText('主题数量 3')).not.toBeInTheDocument();
  expect(document.querySelector('main.workspace')).not.toHaveClass('workspace--flush-content');
});

test('removes the topbar gap only for the industry chain workspace', () => {
  const { container } = render(
    <AppShell
      view="industryChain"
      currentTitle="Industry Graph · 产业链图谱"
      theme="dark"
      articlesCount={31}
      topicsCount={4}
      message="已打开产业链图谱"
      toasts={[]}
      onChangeView={vi.fn()}
      onToggleTheme={vi.fn()}
      onRefresh={vi.fn()}
    >
      <div />
    </AppShell>
  );

  expect(container.querySelector('main.workspace')).toHaveClass('workspace--flush-content');

  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');
  const flushRule = styles.match(/\.workspace--flush-content\s*{([^}]*)}/s)?.[1] ?? '';
  expect(flushRule).toContain('display: flex');
  expect(flushRule).toContain('flex-direction: column');
  expect(flushRule).toContain('height: 100dvh');
  expect(flushRule).toContain('overflow: hidden');
  expect(styles).toMatch(/\.workspace--flush-content > \.topbar\s*{[^}]*margin-bottom:\s*0/s);
  expect(styles).toMatch(/@media\s*\(max-width:\s*980px\)[\s\S]*\.workspace--flush-content\s*{[^}]*height:\s*auto[^}]*overflow:\s*visible/s);
});

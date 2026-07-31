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
});

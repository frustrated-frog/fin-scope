import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { TopicLibrary } from './TopicLibrary';

test('searches on the server after debounce and keeps creation in a dialog', async () => {
  vi.useFakeTimers();
  const onSearch = vi.fn(async () => undefined);
  render(
    <TopicLibrary
      topics={[{
        id: 1,
        name: 'Agent 工程化',
        description: '从模型能力到可靠工作流',
        lifecycleStatus: 'ACTIVE',
        masteryStatus: 'BUILDING',
        revision: 2,
        articleCount: 4,
        briefCount: 1
      }]}
      totalCount={1}
      loading={false}
      onSearch={onSearch}
      onOpenTopic={vi.fn()}
    />
  );

  fireEvent.change(screen.getByRole('searchbox', { name: '搜索主题' }), {
    target: { value: 'agent' }
  });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(320);
  });
  expect(onSearch).toHaveBeenCalledWith('agent');
  vi.useRealTimers();

  expect(screen.queryByText(/vault/i)).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '新建投资问题' }));
  expect(screen.getByRole('dialog', { name: '新建投资问题' })).toBeInTheDocument();
});

test('separates investment recognition from single-article material', async () => {
  render(
    <TopicLibrary
      topics={[
        { id: 1, name: '需求周期判断', lifecycleStatus: 'ACTIVE', masteryStatus: 'BUILDING', revision: 2, articleCount: 3 },
        { id: 2, name: '某篇文章的自动摘要', lifecycleStatus: 'ACTIVE', masteryStatus: 'EXPLORING', revision: 1, articleCount: 1 }
      ]}
      totalCount={2}
      loading={false}
      onSearch={vi.fn(async () => undefined)}
      onOpenTopic={vi.fn()}
    />
  );

  expect(screen.getByRole('heading', { name: '投资认识' })).toBeInTheDocument();
  expect(screen.getByText('需求周期判断')).toBeInTheDocument();
  expect(screen.queryByText('某篇文章的自动摘要')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '待提炼材料 1' }));
  expect(screen.getByText('某篇文章的自动摘要')).toBeInTheDocument();
  expect(screen.queryByText('需求周期判断')).not.toBeInTheDocument();
});

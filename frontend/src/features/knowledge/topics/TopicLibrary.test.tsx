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
  await userEvent.click(screen.getByRole('button', { name: '新建主题' }));
  expect(screen.getByRole('dialog', { name: '新建研究主题' })).toBeInTheDocument();
});

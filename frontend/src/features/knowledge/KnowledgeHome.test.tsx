import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { KnowledgeHome } from './KnowledgeHome';
import { KnowledgeOverview } from './knowledgeTypes';

test('shows no more than three explainable actions and separates suggestions', async () => {
  const onNavigate = vi.fn();
  const overview: KnowledgeOverview = {
    acceptedTaskCount: 4,
    suggestedTaskCount: 12,
    dueReviewCount: 2,
    activeTopicCount: 3,
    actions: [1, 2, 3, 4].map((id) => ({
      type: 'START_TASK',
      title: `问题 ${id}`,
      reason: `这是行动 ${id} 的依据`,
      sourceLabel: '学习任务',
      routeTarget: `?section=learning&task=${id}`,
      taskId: id
    })),
    activeTopics: [],
    recentEntries: []
  };

  render(<KnowledgeHome overview={overview} onNavigate={onNavigate} />);

  const actions = screen.getByRole('list', { name: '下一步行动' });
  expect(within(actions).getAllByRole('listitem')).toHaveLength(3);
  expect(within(actions).getByText('这是行动 1 的依据')).toBeInTheDocument();
  expect(screen.getByText('12 条待确认建议')).toBeInTheDocument();
  expect(screen.getByText('12 条待确认建议').closest('.knowledge-suggestion-inbox')).toBeTruthy();

  await userEvent.click(within(actions).getByRole('button', { name: '继续处理：问题 1' }));
  expect(onNavigate).toHaveBeenCalledWith('?section=learning&task=1');
});

import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { LearningWorkspace } from './LearningWorkspace';
import { KnowledgeTask, KnowledgeTopic } from '../knowledgeTypes';

const topic: KnowledgeTopic = {
  id: 2,
  name: 'Agent 工程化',
  lifecycleStatus: 'ACTIVE',
  masteryStatus: 'BUILDING',
  revision: 1
};

function task(status: KnowledgeTask['status'], revision = 3): KnowledgeTask {
  return {
    id: 7,
    eventId: 11,
    topicId: status === 'SUGGESTED' ? undefined : 2,
    question: 'Agent 的可靠性应该如何验证？',
    whyNeeded: '把产品演示和可重复运行区分开。',
    status,
    revision
  };
}

test('requires a topic before accepting an agent suggestion', async () => {
  const onAccept = vi.fn(async () => undefined);
  render(<LearningWorkspace
    tasks={[task('SUGGESTED')]}
    topics={[topic]}
    selectedTaskId={7}
    evidence={[]}
    onSelectTask={vi.fn()}
    onAccept={onAccept}
    onStart={vi.fn()}
    onSaveDraft={vi.fn()}
    onComplete={vi.fn()}
    onDismiss={vi.fn()}
    onOpenEvent={vi.fn()}
  />);

  expect(screen.getByRole('button', { name: '接受到学习队列' })).toBeDisabled();
  await userEvent.selectOptions(screen.getByLabelText('归入主题'), '2');
  await userEvent.click(screen.getByRole('button', { name: '接受到学习队列' }));
  expect(onAccept).toHaveBeenCalledWith(7, 2, 3);
});

test('starts a task and writes an evidence-linked answer', async () => {
  const onStart = vi.fn(async () => undefined);
  const onSaveDraft = vi.fn(async () => undefined);
  const onComplete = vi.fn(async () => undefined);
  const { rerender } = render(<LearningWorkspace
    tasks={[task('TODO', 4)]}
    topics={[topic]}
    selectedTaskId={7}
    evidence={[]}
    onSelectTask={vi.fn()}
    onAccept={vi.fn()}
    onStart={onStart}
    onSaveDraft={onSaveDraft}
    onComplete={onComplete}
    onDismiss={vi.fn()}
    onOpenEvent={vi.fn()}
  />);

  await userEvent.click(screen.getByRole('button', { name: '开始回答' }));
  expect(onStart).toHaveBeenCalledWith(7, 4);

  rerender(<LearningWorkspace
    tasks={[task('IN_PROGRESS', 5)]}
    topics={[topic]}
    selectedTaskId={7}
    evidence={[{ id: 21, eventId: 11, claim: '回放测试连续 30 天无状态偏差', sourceTier: 'PRIMARY', confidence: 88 }]}
    onSelectTask={vi.fn()}
    onAccept={vi.fn()}
    onStart={onStart}
    onSaveDraft={onSaveDraft}
    onComplete={onComplete}
    onDismiss={vi.fn()}
    onOpenEvent={vi.fn()}
  />);

  const editor = screen.getByRole('region', { name: '学习答案编辑器' });
  expect(within(editor).getByRole('button', { name: '完成并沉淀' })).toBeDisabled();
  await userEvent.type(within(editor).getByLabelText('我的回答'), '可靠性要用可重复运行与故障恢复来验证。');
  await userEvent.click(within(editor).getByLabelText(/回放测试连续 30 天/));
  await userEvent.click(within(editor).getByRole('button', { name: '保存草稿' }));
  expect(onSaveDraft).toHaveBeenCalledWith(7, expect.objectContaining({
    topicId: 2,
    evidenceIds: [21],
    expectedRevision: 5
  }));
  await userEvent.click(within(editor).getByRole('button', { name: '完成并沉淀' }));
  expect(onComplete).toHaveBeenCalled();
});

test('keeps editor content when optimistic locking reports a conflict', async () => {
  const conflict = Object.assign(new Error('内容已在其他窗口更新'), { status: 409 });
  render(<LearningWorkspace
    tasks={[task('IN_PROGRESS', 5)]}
    topics={[topic]}
    selectedTaskId={7}
    evidence={[]}
    onSelectTask={vi.fn()}
    onAccept={vi.fn()}
    onStart={vi.fn()}
    onSaveDraft={vi.fn()}
    onComplete={vi.fn(async () => { throw conflict; })}
    onDismiss={vi.fn()}
    onOpenEvent={vi.fn()}
  />);

  await userEvent.type(screen.getByLabelText('我的回答'), '不要丢失这一段内容');
  await userEvent.click(screen.getByRole('button', { name: '完成并沉淀' }));

  expect(screen.getByDisplayValue('不要丢失这一段内容')).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('已在其他窗口更新');
});

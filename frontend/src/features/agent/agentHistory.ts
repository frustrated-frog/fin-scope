import type { AgentHistory, AgentMessageResult, AgentToolTrace } from './agentClient';

export type ConversationMessage = {
  id: string;
  role: 'user' | 'agent';
  content: string;
  tone?: 'error' | 'warning';
  tools?: AgentToolTrace[];
  clarificationId?: string | null;
};
export function agentAnswer(inputId: string, result: AgentMessageResult): ConversationMessage {
  return {
    id: `${inputId}:agent`,
    role: 'agent',
    content: result.answer?.trim() || result.error || result.completion_reason || '本次没有返回回答。',
    tone: result.error ? 'error' : result.task_outcome === 'unverified' ? 'warning' : undefined,
    tools: result.tools,
    clarificationId: result.clarification_id
  };
}
export function historyMessages(history: AgentHistory): ConversationMessage[] {
  const jobs = new Map(history.requests.map((job) => [job.input_id, job]));
  const turns = new Map(history.turns.map((turn) => [turn.input_id, turn]));
  // 旧同步轮次在前; 新协议按照宿主接受顺序合并, 保留尚未进入 Runtime 的失败/停止输入。
  const ids = [
    ...history.turns.filter((turn) => !jobs.has(turn.input_id)).map((turn) => turn.input_id),
    ...history.requests.map((job) => job.input_id)
  ];
  const messages: ConversationMessage[] = [];
  for (const id of ids) {
    const job = jobs.get(id),
      turn = turns.get(id);
    messages.push({ id: `${id}:user`, role: 'user', content: turn?.user ?? job?.text ?? '' });
    if (job?.result) {
      messages.push(agentAnswer(id, job.result));
    } else if (job?.status === 'running' || job?.status === 'cancelling') {
      continue;
    } else if (turn) {
      messages.push({
        id: `${id}:agent`,
        role: 'agent',
        content:
          turn.assistant ||
          (turn.status === 'cancelled' || turn.status === 'interrupted'
            ? '运行已停止。'
            : '本次运行未完成。'),
        tools: turn.calls.map((call) => ({ ...call, status: call.artifact ? 'succeeded' : 'failed' }))

      });
    } else {
      messages.push({
        id: `${id}:agent`,
        role: 'agent',
        content: job?.status === 'failed' ? '本次运行失败，请重试。' : '运行已停止。'
      });
    }
  }
  return messages;
}

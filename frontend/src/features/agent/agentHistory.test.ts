import { expect, test } from 'vitest';
import { historyMessages } from './agentHistory';

test('restores stopped inputs in their original order without duplicating runtime turns', () => {
  const messages = historyMessages({ before: null, turns: [{
    input_id: 'first', run_id: 'r1', ordinal: 1, user: '第一问', assistant: '第一答', status: 'completed', task_outcome: 'unverified', calls: []
  }, { input_id: 'third', run_id: 'r3', ordinal: 2, user: '第三问', assistant: '第三答', status: 'completed', task_outcome: 'unverified', calls: [] }],
  requests: [
    { input_id: 'first', text: '第一问', status: 'finished', result: null },
    { input_id: 'second', text: '第二问', status: 'cancelled', result: null },
    { input_id: 'third', text: '第三问', status: 'finished', result: null }
  ] });
  expect(messages.map((message) => message.content)).toEqual(['第一问', '第一答', '第二问', '运行已停止。', '第三问', '第三答']);
});

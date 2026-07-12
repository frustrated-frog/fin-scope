import { afterEach, expect, test, vi } from 'vitest';

import { AsyncTask, TaskProgressEvent } from '../../shared/types';
import { createIngestTaskChannel } from './ingestTaskChannel';

class FakeEventSource {
  onerror: (() => void) | null = null;
  close = vi.fn();
  private listener?: (event: MessageEvent) => void;
  addEventListener(_name: string, listener: (event: MessageEvent) => void) { this.listener = listener; }
  emit(event: TaskProgressEvent) {
    this.listener?.(new MessageEvent('progress', { data: JSON.stringify(event) }));
  }
}

afterEach(() => vi.useRealTimers());

test('uses SSE for immediate phase progress and confirms DONE from persisted task', async () => {
  vi.useFakeTimers();
  const source = new FakeEventSource();
  const onProgress = vi.fn();
  const completed: AsyncTask = { taskId: 'task-1', status: 'COMPLETED', phase: 'COMPLETED', articleId: 9 };
  const fetchTask = vi.fn().mockResolvedValue(completed);
  const channel = createIngestTaskChannel(
    { taskId: 'task-1', status: 'QUEUED', phase: 'QUEUED' },
    { fetchTask, onProgress, eventSourceFactory: () => source as unknown as EventSource }
  );

  source.emit({ eventId: '1', taskId: 'task-1', type: 'PHASE', status: 'RUNNING', phase: 'PARSING' });
  expect(onProgress).toHaveBeenCalledWith(expect.objectContaining({ phase: 'PARSING' }));
  source.emit({ eventId: '2', taskId: 'task-1', type: 'DONE', status: 'COMPLETED', phase: 'COMPLETED' });

  await expect(channel.completion).resolves.toEqual(completed);
  expect(fetchTask).toHaveBeenCalledTimes(1);
  expect(onProgress).not.toHaveBeenCalledWith(expect.objectContaining({ status: 'COMPLETED' }));
  expect(source.close).toHaveBeenCalled();
});

test('falls back to fast persisted polling when SSE disconnects', async () => {
  vi.useFakeTimers();
  const source = new FakeEventSource();
  const fetchTask = vi.fn()
    .mockResolvedValueOnce({ taskId: 'task-2', status: 'RUNNING', phase: 'LLM' })
    .mockResolvedValueOnce({ taskId: 'task-2', status: 'COMPLETED', phase: 'COMPLETED', articleId: 10 });
  const channel = createIngestTaskChannel(
    { taskId: 'task-2', status: 'QUEUED', phase: 'QUEUED' },
    { fetchTask, onProgress: vi.fn(), eventSourceFactory: () => source as unknown as EventSource }
  );

  source.onerror?.();
  await vi.advanceTimersByTimeAsync(1200);
  await vi.advanceTimersByTimeAsync(1200);

  await expect(channel.completion).resolves.toMatchObject({ status: 'COMPLETED', articleId: 10 });
  expect(fetchTask).toHaveBeenCalledTimes(2);
});

test('continues with persisted polling when EventSource cannot be created', async () => {
  vi.useFakeTimers();
  const fetchTask = vi.fn().mockResolvedValue({
    taskId: 'task-3', status: 'COMPLETED', phase: 'COMPLETED', articleId: 11
  });
  const channel = createIngestTaskChannel(
    { taskId: 'task-3', status: 'QUEUED', phase: 'QUEUED' },
    { fetchTask, onProgress: vi.fn(), eventSourceFactory: () => { throw new Error('unsupported'); } }
  );

  await vi.advanceTimersByTimeAsync(1200);
  await expect(channel.completion).resolves.toMatchObject({ articleId: 11 });
});

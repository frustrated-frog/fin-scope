import { AsyncTask, TaskProgressEvent } from '../../shared/types';

type ChannelOptions = {
  fetchTask: () => Promise<AsyncTask>;
  onProgress: (task: AsyncTask) => void;
  eventSourceFactory?: (url: string) => EventSource;
  normalPollMs?: number;
  recoveryPollMs?: number;
  timeoutMs?: number;
};

export type IngestTaskChannel = {
  completion: Promise<AsyncTask>;
  dispose: () => void;
};

/**
 * 双通道任务协调器：SSE 负责低延迟提示，GET task 负责事实确认与恢复。
 * 任何 SSE 终态都必须再次读取持久化任务，绝不直接判定成功。
 */
export function createIngestTaskChannel(initialTask: AsyncTask, options: ChannelOptions): IngestTaskChannel {
  const normalPollMs = options.normalPollMs ?? 5000;
  const recoveryPollMs = options.recoveryPollMs ?? 800;
  const timeoutMs = options.timeoutMs ?? 48_000;
  const createSource = options.eventSourceFactory ?? ((url: string) => new EventSource(url));
  let disposed = false;
  let settled = false;
  let sseHealthy = true;
  let pollTimer: number | undefined;
  let deadlineTimer: number | undefined;
  let pollInFlight = false;
  const seenEventIds = new Set<string>();
  let resolveCompletion!: (task: AsyncTask) => void;
  let rejectCompletion!: (error: Error) => void;
  const completion = new Promise<AsyncTask>((resolve, reject) => {
    resolveCompletion = resolve;
    rejectCompletion = reject;
  });
  let source: EventSource | null = null;
  try {
    source = createSource(`/api/tasks/${initialTask.taskId}/stream`);
  } catch {
    sseHealthy = false;
  }

  const cleanup = () => {
    if (pollTimer !== undefined) window.clearTimeout(pollTimer);
    if (deadlineTimer !== undefined) window.clearTimeout(deadlineTimer);
    source?.close();
  };

  const settle = (task: AsyncTask) => {
    if (settled || disposed) return;
    settled = true;
    cleanup();
    resolveCompletion(task);
  };

  const schedulePoll = (delay: number) => {
    if (disposed || settled) return;
    if (pollTimer !== undefined) window.clearTimeout(pollTimer);
    pollTimer = window.setTimeout(pollPersistedTask, delay);
  };

  const pollPersistedTask = async () => {
    if (disposed || settled || pollInFlight) return;
    pollInFlight = true;
    try {
      const task = await options.fetchTask();
      if (disposed || settled) return;
      if (task.status === 'COMPLETED' || task.status === 'FAILED') {
        settle(task);
        return;
      }
      options.onProgress(task);
    } catch {
      // 查询的短暂失败不覆盖 SSE 已展示的进度，下一轮继续从事实源恢复。
    } finally {
      pollInFlight = false;
    }
    schedulePoll(sseHealthy ? normalPollMs : recoveryPollMs);
  };

  source?.addEventListener('progress', (raw) => {
    if (disposed || settled) return;
    try {
      const event = JSON.parse((raw as MessageEvent).data) as TaskProgressEvent;
      if (event.taskId !== initialTask.taskId || seenEventIds.has(event.eventId)) return;
      seenEventIds.add(event.eventId);
      if (event.type === 'DONE' || event.type === 'ERROR'
          || event.status === 'COMPLETED' || event.status === 'FAILED') {
        if (pollTimer !== undefined) window.clearTimeout(pollTimer);
        void pollPersistedTask();
        return;
      }
      options.onProgress({
        taskId: event.taskId,
        status: event.status || 'RUNNING',
        phase: event.phase || 'QUEUED',
        message: event.message,
        errorMessage: event.errorMessage,
        articleId: event.articleId
      });
    } catch {
      // 单条坏事件不影响后续 SSE 和轮询恢复。
    }
  });

  if (source) {
    source.onerror = () => {
      if (disposed || settled) return;
      sseHealthy = false;
      source?.close();
      schedulePoll(recoveryPollMs);
    };
  }

  deadlineTimer = window.setTimeout(() => {
    if (disposed || settled) return;
    settled = true;
    cleanup();
    rejectCompletion(new Error('生成任务超时，请稍后重试'));
  }, timeoutMs);
  // 建连后立即读取一次数据库快照，之后才进入低频/恢复轮询节奏。
  void pollPersistedTask();

  return {
    completion,
    dispose: () => {
      if (disposed || settled) return;
      disposed = true;
      cleanup();
    }
  };
}

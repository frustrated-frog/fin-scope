package com.finscope.web.sse;

import com.finscope.service.task.TaskProgressEvent;
import com.finscope.service.task.TaskProgressPublisher;
import com.finscope.service.task.TaskView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 文章任务 SSE 适配器：多订阅者、有界早期事件缓冲、数据库快照恢复。 */
@Component
@Slf4j
public class TaskSseRegistry implements TaskProgressPublisher {
    static final int MAX_BUFFERED_EVENTS = 32;
    static final long BUFFER_TTL_MS = 5 * 60 * 1000L;
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    interface EmitterFactory { SseEmitter create(long timeoutMs); }

    private final EmitterFactory emitterFactory;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Deque<TaskProgressEvent>> buffered = new ConcurrentHashMap<>();
    private final Map<String, Long> bufferedAt = new ConcurrentHashMap<>();

    public TaskSseRegistry() { this(SseEmitter::new); }
    TaskSseRegistry(EmitterFactory emitterFactory) { this.emitterFactory = emitterFactory; }

    public SseEmitter subscribe(String taskId, TaskView persistedSnapshot) {
        SseEmitter emitter = emitterFactory.create(TIMEOUT_MS);
        subscribers.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> {
            remove(taskId, emitter);
            if (!subscribers.containsKey(taskId)) clearBuffer(taskId);
        });
        emitter.onError(error -> remove(taskId, emitter));

        // 先补过程事件，再以数据库快照收敛到最新事实，避免旧事件回退 UI。
        Deque<TaskProgressEvent> pending = buffered.get(taskId);
        if (pending != null) {
            List<TaskProgressEvent> snapshot;
            synchronized (pending) { snapshot = new ArrayList<>(pending); }
            for (TaskProgressEvent event : snapshot) send(taskId, emitter, event);
        }
        TaskProgressEvent snapshot = TaskProgressEvent.snapshot(persistedSnapshot);
        send(taskId, emitter, snapshot);
        if (isTerminal(persistedSnapshot.getStatus())) {
            completeEmitter(taskId, emitter);
        }
        return emitter;
    }

    @Override
    public void publish(String taskId, TaskProgressEvent event) {
        buffer(taskId, event);
        List<SseEmitter> current = subscribers.get(taskId);
        if (current == null) return;
        for (SseEmitter emitter : current) send(taskId, emitter, event);
    }

    @Override
    public void complete(String taskId) {
        List<SseEmitter> current = subscribers.remove(taskId);
        if (current != null) for (SseEmitter emitter : current) completeEmitter(taskId, emitter);
        clearBuffer(taskId);
    }

    private void buffer(String taskId, TaskProgressEvent event) {
        Deque<TaskProgressEvent> queue = buffered.computeIfAbsent(taskId, key -> new ArrayDeque<>());
        synchronized (queue) {
            while (queue.size() >= MAX_BUFFERED_EVENTS) queue.removeFirst();
            queue.addLast(event);
        }
        bufferedAt.put(taskId, System.currentTimeMillis());
    }

    private void send(String taskId, SseEmitter emitter, TaskProgressEvent event) {
        try {
            emitter.send(SseEmitter.event().id(event.getEventId()).name("progress").data(event));
        } catch (IOException | IllegalStateException ex) {
            log.debug("文章任务 SSE 推送失败 taskId={} message={}", taskId, ex.getMessage());
            remove(taskId, emitter);
        }
    }

    private void completeEmitter(String taskId, SseEmitter emitter) {
        try { emitter.complete(); } catch (Exception ex) {
            log.debug("文章任务 SSE 关闭失败 taskId={} message={}", taskId, ex.getMessage());
        } finally { remove(taskId, emitter); }
    }

    private void remove(String taskId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(taskId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) subscribers.remove(taskId, (CopyOnWriteArrayList<SseEmitter>) list);
    }

    private void clearBuffer(String taskId) {
        buffered.remove(taskId);
        bufferedAt.remove(taskId);
    }

    /** 清理从未建立订阅且任务未正常收尾的孤儿缓冲。数据库快照仍可恢复状态。 */
    @Scheduled(fixedDelay = 60_000L)
    public void cleanupExpiredBuffers() {
        long cutoff = System.currentTimeMillis() - BUFFER_TTL_MS;
        for (Map.Entry<String, Long> entry : bufferedAt.entrySet()) {
            if (entry.getValue() < cutoff) {
                if (bufferedAt.remove(entry.getKey(), entry.getValue())) buffered.remove(entry.getKey());
            }
        }
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }
}

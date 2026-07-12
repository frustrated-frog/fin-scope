package com.finscope.web.sse;

import com.finscope.service.task.TaskProgressEvent;
import com.finscope.service.task.TaskView;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class TaskSseRegistryTest {
    @Test
    void sendsPersistedSnapshotAndSupportsMultipleSubscribers() throws Exception {
        TaskView view = task("task-1", "RUNNING", "PARSING");
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        AtomicInteger index = new AtomicInteger();
        TaskSseRegistry registry = new TaskSseRegistry(
                timeout -> index.getAndIncrement() == 0 ? first : second);

        registry.subscribe("task-1", view);
        registry.subscribe("task-1", view);
        registry.publish("task-1", TaskProgressEvent.phase(view));

        verify(first, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void boundsEventsBufferedBeforeSubscription() throws Exception {
        TaskView view = task("task-2", "RUNNING", "PARSING");
        SseEmitter emitter = mock(SseEmitter.class);
        TaskSseRegistry registry = new TaskSseRegistry(timeout -> emitter);
        for (int i = 0; i < 40; i++) registry.publish("task-2", TaskProgressEvent.phase(view));

        registry.subscribe("task-2", view);

        verify(emitter, times(TaskSseRegistry.MAX_BUFFERED_EVENTS + 1))
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiresOrphanedBuffersWithoutLosingPersistedSnapshot() throws Exception {
        TaskView view = task("task-3", "RUNNING", "LLM");
        SseEmitter emitter = mock(SseEmitter.class);
        TaskSseRegistry registry = new TaskSseRegistry(timeout -> emitter);
        registry.publish("task-3", TaskProgressEvent.phase(view));
        Map<String, Long> timestamps = (Map<String, Long>) ReflectionTestUtils.getField(registry, "bufferedAt");
        timestamps.put("task-3", 0L);

        registry.cleanupExpiredBuffers();
        registry.subscribe("task-3", view);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    private TaskView task(String id, String status, String phase) {
        TaskView task = new TaskView();
        task.setTaskId(id);
        task.setStatus(status);
        task.setPhase(phase);
        task.setMessage("处理中");
        return task;
    }
}

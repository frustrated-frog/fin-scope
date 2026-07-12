package com.finscope.service.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskProgressEventTest {
    @Test
    void createsSnapshotAndTerminalEventsFromPersistedTaskView() {
        TaskView view = new TaskView();
        view.setTaskId("task-1");
        view.setStatus("COMPLETED");
        view.setPhase("COMPLETED");
        view.setMessage("文章已生成");
        view.setArticleId(42L);

        TaskProgressEvent snapshot = TaskProgressEvent.snapshot(view);
        TaskProgressEvent done = TaskProgressEvent.done(view);

        assertEquals("SNAPSHOT", snapshot.getType());
        assertEquals("DONE", done.getType());
        assertEquals("task-1", done.getTaskId());
        assertEquals(42L, done.getArticleId());
        assertNotNull(done.getEventId());
        assertNotNull(done.getOccurredAt());
    }
}

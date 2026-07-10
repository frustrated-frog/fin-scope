package com.finscope.web.sse;

import com.finscope.service.attribution.AttributionProgressEvent;
import com.finscope.service.attribution.AttributionProgressPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 归因 SSE 注册表：实现进度发布器，管理 taskId -> SseEmitter 的订阅关系。
 * 为解决"任务先跑、前端后订阅"的时序问题，对每个任务缓冲早期事件，订阅时补发。
 */
@Component
@Slf4j
public class AttributionSseRegistry implements AttributionProgressPublisher {
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, List<AttributionProgressEvent>> buffered = new ConcurrentHashMap<>();
    private final Map<String, Boolean> completed = new ConcurrentHashMap<>();

    /** 前端订阅某任务进度。 */
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> cleanup(taskId));
        emitter.onTimeout(() -> cleanup(taskId));
        emitter.onError(e -> cleanup(taskId));
        emitters.put(taskId, emitter);

        // 补发缓冲的早期事件
        List<AttributionProgressEvent> pending = buffered.remove(taskId);
        if (pending != null) {
            for (AttributionProgressEvent event : pending) {
                send(taskId, emitter, event);
            }
        }
        // 若任务已完成，直接结束
        if (Boolean.TRUE.equals(completed.remove(taskId))) {
            emitter.complete();
            emitters.remove(taskId);
        }
        return emitter;
    }

    @Override
    public void publish(String taskId, AttributionProgressEvent event) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            // 前端尚未订阅，先缓冲
            buffered.computeIfAbsent(taskId, k -> new ArrayList<>()).add(event);
            return;
        }
        send(taskId, emitter, event);
    }

    @Override
    public void complete(String taskId) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            // 订阅还没建立，标记完成，subscribe 时收尾
            completed.put(taskId, Boolean.TRUE);
            return;
        }
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.warn("SSE 关闭异常 taskId={} message={}", taskId, ex.getMessage());
        } finally {
            emitters.remove(taskId);
        }
    }

    private void send(String taskId, SseEmitter emitter, AttributionProgressEvent event) {
        try {
            emitter.send(SseEmitter.event().name("progress").data(event));
        } catch (IOException ex) {
            log.warn("SSE 推送失败 taskId={} message={}", taskId, ex.getMessage());
            cleanup(taskId);
        }
    }

    private void cleanup(String taskId) {
        emitters.remove(taskId);
        buffered.remove(taskId);
        completed.remove(taskId);
    }
}
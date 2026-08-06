package com.finscope.web.sse;

import com.finscope.service.cache.ViewRevision;
import com.finscope.service.cache.ViewRevisionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/** 页面只订阅“有新版本”，随后按自身筛选条件读取对应快照。 */
@Component
public class ViewRevisionSseRegistry implements ViewRevisionPublisher {
    private static final Logger log = LoggerFactory.getLogger(ViewRevisionSseRegistry.class);
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;
    private final CopyOnWriteArrayList<SseEmitter> subscribers = new CopyOnWriteArrayList<SseEmitter>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        return emitter;
    }

    @Override
    public void publish(ViewRevision revision) {
        for (SseEmitter emitter : subscribers) {
            sendRevision(emitter, revision);
        }
    }

    @Scheduled(fixedDelay = 20_000L)
    public void heartbeat() {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException error) {
                subscribers.remove(emitter);
            }
        }
    }

    private void sendRevision(SseEmitter emitter, ViewRevision revision) {
        try {
            emitter.send(SseEmitter.event().name("snapshot-ready").data(revision));
        } catch (IOException | IllegalStateException error) {
            log.debug("页面版本 SSE 推送失败: {}", error.getMessage());
            subscribers.remove(emitter);
        }
    }
}

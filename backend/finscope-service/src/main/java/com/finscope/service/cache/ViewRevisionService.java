package com.finscope.service.cache;

import com.finscope.dao.cache.VersionedViewCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 统一管理页面快照 revision，生产完成后才向订阅端发布。 */
@Service
public class ViewRevisionService {
    private final VersionedViewCacheRepository cache;
    private final ViewRevisionPublisher publisher;
    private final Clock clock;

    @Autowired
    public ViewRevisionService(VersionedViewCacheRepository cache, ViewRevisionPublisher publisher) {
        this(cache, publisher, Clock.systemDefaultZone());
    }

    ViewRevisionService(VersionedViewCacheRepository cache, ViewRevisionPublisher publisher, Clock clock) {
        this.cache = cache;
        this.publisher = publisher;
        this.clock = clock;
    }

    public ViewRevision invalidate(String scope) {
        return invalidate(scope, LocalDateTime.now(clock));
    }

    public ViewRevision invalidate(String scope, LocalDateTime completedAt) {
        String normalized = normalize(scope);
        ViewRevision revision = new ViewRevision(normalized, cache.invalidateAndGetRevision(normalized), completedAt);
        publisher.publish(revision);
        return revision;
    }

    public List<ViewRevision> current(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) return Collections.emptyList();
        List<ViewRevision> values = new ArrayList<ViewRevision>();
        for (String scope : scopes) {
            String normalized = normalize(scope);
            values.add(new ViewRevision(normalized, cache.currentRevision(normalized), LocalDateTime.now(clock)));
        }
        return Collections.unmodifiableList(values);
    }

    private String normalize(String scope) {
        if (scope == null || scope.trim().isEmpty()) throw new IllegalArgumentException("页面版本范围不能为空");
        return scope.trim().toLowerCase(Locale.ROOT);
    }
}

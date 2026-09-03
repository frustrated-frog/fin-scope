package com.finscope.dao.radar;

import com.finscope.domain.agent.AgentRun;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class RadarAgentTraceRepository {
    @Resource
    private RedisRadarCacheStore store;

    public void record(AgentRun run) {
        if (run == null || run.getSubjectType() == null || run.getSubjectId() == null) {
            return;
        }
        store.update(state -> {
            run.setId(state.nextSequence());
            if (run.getCreatedAt() == null) {
                run.setCreatedAt(LocalDateTime.now().minus(Duration.ofMillis(Math.max(0L, run.getDurationMs()))));
            }
            state.getAgentRunsBySubject()
                    .computeIfAbsent(key(run.getSubjectType(), run.getSubjectId()),
                            ignored -> new ArrayList<AgentRun>())
                    .add(run);
            return null;
        });
    }

    public List<AgentRun> findBySubject(String subjectType, Long subjectId) {
        if (subjectType == null || subjectId == null) {
            return Collections.emptyList();
        }
        return new ArrayList<AgentRun>(store.read().getAgentRunsBySubject()
                .getOrDefault(key(subjectType, subjectId), Collections.emptyList()));
    }

    private String key(String subjectType, Long subjectId) {
        return subjectType + ':' + subjectId;
    }
}

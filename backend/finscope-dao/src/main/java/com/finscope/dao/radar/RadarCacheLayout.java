package com.finscope.dao.radar;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarRefreshStep;
import com.finscope.domain.radar.RadarSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 仅用于 DAO 的 Redis 物理布局；每个 Map 条目独立存储，不序列化整个状态。 */
final class RadarCacheLayout {
    static final String ACTIVE_KEY = "finscope:radar:v2:active";
    static final String LEGACY_KEY = "finscope:radar:state";
    static final int BATCH_SIZE = 100;
    static final int SNAPSHOT_KEEP_DAYS = 7;
    static final String KEEP_TTL = "__RADAR_KEEP_TTL__";
    static final Set<String> EVENT_OWNED = Set.of("eventSignals", "snapshots", "evidence", "interpretations",
            "userStates", "timelines", "timelineFingerprints", "researchLinks");

    private RadarCacheLayout() {
    }

    static String prefix(String generation) {
        return "finscope:radar:v2:" + generation + ':';
    }

    static List<Section> sections(ObjectMapper mapper) {
        List<Section> result = new ArrayList<>();
        result.add(section(mapper, "signals", Long.class, RadarSignal.class, RadarCacheState::getSignals));
        result.add(section(mapper, "signalIdsByItemId", String.class, Long.class, RadarCacheState::getSignalIdsByItemId));
        result.add(section(mapper, "events", Long.class, RadarEvent.class, RadarCacheState::getEvents));
        result.add(section(mapper, "eventIdsByKey", String.class, Long.class, RadarCacheState::getEventIdsByKey));
        result.add(list(mapper, "eventSignals", RadarEventSignal.class, RadarCacheState::getEventSignals));
        result.add(list(mapper, "snapshots", RadarEventSnapshot.class, RadarCacheState::getSnapshots));
        result.add(list(mapper, "evidence", RadarEvidence.class, RadarCacheState::getEvidence));
        result.add(list(mapper, "interpretations", RadarEventInterpretation.class, RadarCacheState::getInterpretations));
        result.add(section(mapper, "pairDecisions", String.class, RadarPairDecision.class, RadarCacheState::getPairDecisions));
        result.add(section(mapper, "runs", Long.class, RadarRefreshRun.class, RadarCacheState::getRuns));
        result.add(list(mapper, "runSteps", RadarRefreshStep.class, RadarCacheState::getRunSteps));
        result.add(section(mapper, "userStates", Long.class, RadarEventWorkspace.State.class, RadarCacheState::getUserStates));
        result.add(list(mapper, "timelines", RadarEventWorkspace.TimelineEntry.class, RadarCacheState::getTimelines));
        result.add(new Section("timelineFingerprints", Long.class,
                mapper.getTypeFactory().constructCollectionType(Set.class, String.class), RadarCacheState::getTimelineFingerprints));
        result.add(list(mapper, "researchLinks", RadarEventWorkspace.ResearchLink.class, RadarCacheState::getResearchLinks));
        result.add(new Section("agentRunsBySubject", String.class,
                mapper.getTypeFactory().constructCollectionType(List.class, AgentRun.class), RadarCacheState::getAgentRunsBySubject));
        result.add(new Section("industryChainImpacts", Long.class,
                mapper.getTypeFactory().constructMapType(Map.class, Long.class, IndustryChainEventImpact.class),
                RadarCacheState::getIndustryChainImpacts));
        return result;
    }

    static Section notifications(ObjectMapper mapper) {
        return section(mapper, "notifications", Long.class, RadarEventWorkspace.Notification.class, state -> Map.of());
    }

    static Section notificationFingerprints(ObjectMapper mapper) {
        return section(mapper, "notificationFingerprints", String.class, String.class, state -> Map.of());
    }

    private static Section section(ObjectMapper mapper, String name, Class<?> keyType, Class<?> valueType,
                                   Function<RadarCacheState, ? extends Map<?, ?>> source) {
        return new Section(name, keyType, mapper.constructType(valueType), source);
    }

    private static Section list(ObjectMapper mapper, String name, Class<?> valueType,
                                Function<RadarCacheState, ? extends Map<?, ?>> source) {
        return new Section(name, Long.class, mapper.getTypeFactory().constructCollectionType(List.class, valueType), source);
    }

    record Section(String name, Class<?> keyType, JavaType valueType,
                   Function<RadarCacheState, ? extends Map<?, ?>> source) {
        Object parseKey(String value) {
            return keyType == Long.class ? Long.valueOf(value) : value;
        }

        String member(Object key) {
            // URL-safe 编码同时避免业务键中的分隔符与 Redis 物理布局发生冲突。
            return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(String.valueOf(key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String index(String prefix) {
            return prefix + name + ":index";
        }

        String key(String prefix, Object id) {
            return prefix + name + ':' + member(id);
        }
    }
}

package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RadarEventWorkspaceService {
    private final RadarEventWorkspaceRepository workspace;
    private final RadarRepository radar;

    public RadarEventWorkspaceService(RadarEventWorkspaceRepository workspace, RadarRepository radar) {
        this.workspace = workspace; this.radar = radar;
    }

    public Map<Long, RadarEventWorkspace.Summary> summaries(List<Long> eventIds) {
        return eventIds == null || eventIds.isEmpty()
                ? Collections.<Long, RadarEventWorkspace.Summary>emptyMap() : workspace.findSummaries(eventIds);
    }

    public OpenedEvent open(RadarEvent event) {
        RadarEventWorkspace.State state = workspace.updateState(event.getId(), true, null, null, fingerprint(event));
        String observation = text(event.getNextObservation(), "关注事件是否出现新的独立来源、数据或正式公告");
        return new OpenedEvent(state, workspace.ensureDefaultObservation(event.getId(), observation));
    }

    public RadarEventWorkspace.State updateState(Long eventId, Boolean read, Boolean followed, String disposition) {
        RadarEvent event = requireEvent(eventId);
        try {
            return workspace.updateState(eventId, Boolean.TRUE.equals(read), normalize(disposition), followed,
                    Boolean.TRUE.equals(read) ? fingerprint(event) : null);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, ex.getMessage());
        }
    }

    public List<RadarEventWorkspace.Observation> observations(Long eventId) {
        requireEvent(eventId); return workspace.findObservations(eventId);
    }

    public RadarEventWorkspace.Observation addObservation(Long eventId, String content) {
        requireEvent(eventId);
        try { return workspace.addObservation(eventId, content); }
        catch (IllegalArgumentException ex) { throw invalid(ex); }
    }

    public RadarEventWorkspace.Observation updateObservation(Long eventId, Long observationId, String status) {
        requireEvent(eventId);
        try { return workspace.setObservationStatus(eventId, observationId, normalize(status)); }
        catch (IllegalArgumentException ex) { throw invalid(ex); }
    }

    public void deleteObservation(Long eventId, Long observationId) {
        requireEvent(eventId);
        try { workspace.deleteObservation(eventId, observationId); }
        catch (IllegalArgumentException ex) { throw invalid(ex); }
    }

    private RadarEvent requireEvent(Long eventId) {
        return radar.findEvent(eventId).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "雷达事件不存在"));
    }

    private BusinessException invalid(IllegalArgumentException ex) {
        return new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, ex.getMessage());
    }

    private String fingerprint(RadarEvent event) {
        String value = String.valueOf(event.getEventKey()) + '|' + event.getLastSeenAt() + '|'
                + event.getSignalCount() + '|' + event.getEvidenceCount();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (Exception ex) { throw new IllegalStateException("无法生成雷达事件状态指纹", ex); }
    }

    private String normalize(String value) { return value == null || value.trim().isEmpty() ? null : value.trim().toUpperCase(); }
    private String text(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }

    public static final class OpenedEvent {
        private final RadarEventWorkspace.State state;
        private final List<RadarEventWorkspace.Observation> observations;
        OpenedEvent(RadarEventWorkspace.State state, List<RadarEventWorkspace.Observation> observations) {
            this.state = state; this.observations = observations;
        }
        public RadarEventWorkspace.State getState() { return state; }
        public List<RadarEventWorkspace.Observation> getObservations() { return observations; }
    }
}

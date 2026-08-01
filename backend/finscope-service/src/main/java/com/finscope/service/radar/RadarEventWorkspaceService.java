package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RadarEventWorkspaceService {
    private final RadarEventWorkspaceRepository workspace;
    private final RadarRepository radar;
    private final RadarEventTimelineService timeline;

    @Autowired
    public RadarEventWorkspaceService(RadarEventWorkspaceRepository workspace, RadarRepository radar,
                                      RadarEventTimelineService timeline) {
        this.workspace = workspace; this.radar = radar; this.timeline = timeline;
    }

    RadarEventWorkspaceService(RadarEventWorkspaceRepository workspace, RadarRepository radar) {
        this(workspace, radar, null);
    }

    public Map<Long, RadarEventWorkspace.Summary> summaries(List<Long> eventIds) {
        return eventIds == null || eventIds.isEmpty()
                ? Collections.<Long, RadarEventWorkspace.Summary>emptyMap() : workspace.findSummaries(eventIds);
    }

    public OpenedEvent open(RadarEvent event) {
        RadarEventWorkspace.State state = workspace.updateState(event.getId(), true, null, null, fingerprint(event));
        String observation = text(event.getNextObservation(), "关注事件是否出现新的独立来源、数据或正式公告");
        List<RadarEventWorkspace.Observation> observations = workspace.ensureDefaultObservation(event.getId(), observation);
        action(event.getId(), fingerprint(event), "READ", "已查看事件", "事件详情已读", "STATE", event.getId());
        return new OpenedEvent(state, observations, workspace.findResearchLinks(event.getId()));
    }

    public RadarEventWorkspace.State updateState(Long eventId, Boolean read, Boolean followed, String disposition) {
        RadarEvent event = requireEvent(eventId);
        try {
            RadarEventWorkspace.State state = workspace.updateState(eventId, Boolean.TRUE.equals(read), normalize(disposition), followed,
                    Boolean.TRUE.equals(read) ? fingerprint(event) : null);
            if (followed != null) action(eventId, "followed:" + followed, "FOLLOW", followed ? "已关注事件" : "已取消关注", null, "STATE", eventId);
            if (normalize(disposition) != null) action(eventId, "disposition:" + normalize(disposition), "DISPOSITION", "处理状态已更新", normalize(disposition), "STATE", eventId);
            if (Boolean.TRUE.equals(read)) action(eventId, fingerprint(event), "READ", "已查看事件", "事件详情已读", "STATE", eventId);
            return state;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, ex.getMessage());
        }
    }

    public List<RadarEventWorkspace.Observation> observations(Long eventId) {
        requireEvent(eventId); return workspace.findObservations(eventId);
    }

    public RadarEventWorkspace.Observation addObservation(Long eventId, String content) {
        requireEvent(eventId);
        try { RadarEventWorkspace.Observation value=workspace.addObservation(eventId, content);
            action(eventId,"observation:"+value.getId(),"OBSERVATION","新增观察项",value.getContent(),"OBSERVATION",value.getId()); return value; }
        catch (IllegalArgumentException ex) { throw invalid(ex); }
    }

    public RadarEventWorkspace.Observation updateObservation(Long eventId, Long observationId, String status) {
        requireEvent(eventId);
        try { RadarEventWorkspace.Observation value=workspace.setObservationStatus(eventId, observationId, normalize(status));
            action(eventId,"observation:"+observationId+":"+normalize(status),"OBSERVATION_STATUS","观察项状态已更新",normalize(status),"OBSERVATION",observationId); return value; }
        catch (IllegalArgumentException ex) { throw invalid(ex); }
    }

    public void deleteObservation(Long eventId, Long observationId) {
        requireEvent(eventId);
        try { workspace.deleteObservation(eventId, observationId);
            action(eventId,"observation:"+observationId+":deleted","OBSERVATION_DELETE","已删除自定义观察项",null,"OBSERVATION",observationId); }
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
    private void action(Long eventId,String fingerprint,String type,String title,String summary,String referenceType,Long referenceId) {
        if(timeline!=null)timeline.action(eventId,fingerprint,type,title,summary,referenceType,referenceId);
    }

    public static final class OpenedEvent {
        private final RadarEventWorkspace.State state;
        private final List<RadarEventWorkspace.Observation> observations;
        private final List<RadarEventWorkspace.ResearchLink> researchLinks;
        OpenedEvent(RadarEventWorkspace.State state, List<RadarEventWorkspace.Observation> observations,
                    List<RadarEventWorkspace.ResearchLink> researchLinks) {
            this.state = state; this.observations = observations; this.researchLinks=researchLinks;
        }
        public RadarEventWorkspace.State getState() { return state; }
        public List<RadarEventWorkspace.Observation> getObservations() { return observations; }
        public List<RadarEventWorkspace.ResearchLink> getResearchLinks(){return researchLinks;}
    }
}

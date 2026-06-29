package com.finscope.web.response;

import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.SourceProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRunResponse {
    private Long id;
    private LocalDate runDate;
    private List<String> themeCodes = Collections.emptyList();
    private Integer sourceCount;
    private String status;
    private String summary;
    private String errorMessage;
    private List<SourceProfile> plannedSources = Collections.emptyList();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ResearchRunResponse of(ResearchRunPlan plan) {
        return of(plan.getRun(), plan.getPlannedSources());
    }

    public static ResearchRunResponse of(ResearchRun run, List<SourceProfile> plannedSources) {
        ResearchRunResponse response = new ResearchRunResponse();
        response.setId(run.getId());
        response.setRunDate(run.getRunDate());
        response.setThemeCodes(run.getThemeCodes());
        response.setSourceCount(run.getSourceCount());
        response.setStatus(run.getStatus());
        response.setSummary(run.getSummary());
        response.setErrorMessage(run.getErrorMessage());
        response.setPlannedSources(plannedSources);
        response.setCreatedAt(run.getCreatedAt());
        response.setUpdatedAt(run.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public void setRunDate(LocalDate runDate) {
        this.runDate = runDate;
    }

    public List<String> getThemeCodes() {
        return themeCodes;
    }

    public void setThemeCodes(List<String> themeCodes) {
        this.themeCodes = themeCodes == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(themeCodes));
    }

    public Integer getSourceCount() {
        return sourceCount;
    }

    public void setSourceCount(Integer sourceCount) {
        this.sourceCount = sourceCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<SourceProfile> getPlannedSources() {
        return plannedSources;
    }

    public void setPlannedSources(List<SourceProfile> plannedSources) {
        this.plannedSources = plannedSources == null ? Collections.<SourceProfile>emptyList()
                : Collections.unmodifiableList(new ArrayList<SourceProfile>(plannedSources));
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

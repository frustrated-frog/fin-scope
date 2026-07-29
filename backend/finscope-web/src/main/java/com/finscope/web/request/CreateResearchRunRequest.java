package com.finscope.web.request;

import com.finscope.domain.research.ResearchMode;
import java.time.LocalDate;
import java.util.List;

public class CreateResearchRunRequest {
    private Long thesisId;
    private LocalDate runDate;
    private List<String> themeCodes;
    private Integer maxSourcesPerTheme;
    private Boolean includeDisabled;
    private ResearchMode mode = ResearchMode.DEEP;

    public Long getThesisId() {
        return thesisId;
    }

    public void setThesisId(Long thesisId) {
        this.thesisId = thesisId;
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
        this.themeCodes = themeCodes;
    }

    public Integer getMaxSourcesPerTheme() {
        return maxSourcesPerTheme;
    }

    public void setMaxSourcesPerTheme(Integer maxSourcesPerTheme) {
        this.maxSourcesPerTheme = maxSourcesPerTheme;
    }

    public Boolean getIncludeDisabled() {
        return includeDisabled;
    }

    public void setIncludeDisabled(Boolean includeDisabled) {
        this.includeDisabled = includeDisabled;
    }

    public ResearchMode getMode() {
        return ResearchMode.defaultIfNull(mode);
    }

    public void setMode(ResearchMode mode) {
        this.mode = ResearchMode.defaultIfNull(mode);
    }
}

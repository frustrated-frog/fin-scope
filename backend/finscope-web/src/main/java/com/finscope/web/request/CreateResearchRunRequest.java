package com.finscope.web.request;

import com.finscope.domain.research.ResearchMode;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateResearchRunRequest {
    private Long thesisId;
    private LocalDate runDate;
    private List<String> themeCodes;
    private ResearchMode mode = ResearchMode.DEEP;

    public ResearchMode getMode() {
        return ResearchMode.defaultIfNull(mode);
    }

    public void setMode(ResearchMode mode) {
        this.mode = ResearchMode.defaultIfNull(mode);
    }
}

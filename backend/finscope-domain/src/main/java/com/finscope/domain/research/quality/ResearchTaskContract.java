package com.finscope.domain.research.quality;

import com.finscope.common.enums.research.ResearchFactKind;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Frozen at run creation; question numbers are hypotheses, never evidence. Dates are ISO local dates. */
@Data
public class ResearchTaskContract {
    private Long researchRunId;
    private String version = "research-quality-v1";
    private String question;
    private String subject;
    private String subjectType;
    private String subjectCode;
    private List<String> aliases = new ArrayList<>();
    private String event;
    private String asOfDate;
    private String dateFrom;
    private String dateTo;
    private boolean dateRequired;
    private String dateResolution;
    private String dateAnchorUrl;
    private List<ResearchFactKind> requiredFacts = new ArrayList<>();
    private List<String> completionCriteria = new ArrayList<>();
}

package com.finscope.web.response;

import com.finscope.domain.fundamentals.*;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Data
public class FundamentalResearchResponse {
    private String code;
    private String name;
    private CompanyBusinessProfile profile;
    private CompanyBusinessNotes notes;
    private Long reportId;
    private LocalDate periodEnd;
    private String reportType;
    private String scope;
    private String currency;
    private String sourceCode;
    private LocalDateTime publishedAt;
    private String summary;
    private List<FundamentalInsight> insights = new ArrayList<>();
    private List<FundamentalEvent> events = new ArrayList<>();
    private List<FundamentalWatch> suggestions = new ArrayList<>();
    private List<FundamentalWatch> watches = new ArrayList<>();
    private List<String> notices = new ArrayList<>();
    private LocalDateTime assembledAt;

    public static FundamentalResearchResponse of(FundamentalResearchWorkspace value) {
        var result = new FundamentalResearchResponse();
        result.setCode(value.getCode());
        result.setName(value.getName());
        result.setProfile(value.getProfile());
        result.setNotes(value.getNotes());
        result.setReportId(value.getReportId());
        result.setPeriodEnd(value.getPeriodEnd());
        result.setReportType(value.getReportType());
        result.setScope(value.getScope());
        result.setCurrency(value.getCurrency());
        result.setSourceCode(value.getSourceCode());
        result.setPublishedAt(value.getPublishedAt());
        result.setSummary(value.getSummary());
        result.setAssembledAt(value.getAssembledAt());
        result.setInsights(value.getInsights());
        result.setEvents(value.getEvents());
        result.setSuggestions(value.getSuggestions());
        result.setWatches(value.getWatches());
        result.setNotices(value.getNotices());
        return result;
    }
}

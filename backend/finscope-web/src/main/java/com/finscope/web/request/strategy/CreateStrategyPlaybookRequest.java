package com.finscope.web.request.strategy;

import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyPlaybookRule;
import lombok.Data;

import java.util.List;

@Data
public class CreateStrategyPlaybookRequest {
    private String code;
    private String title;
    private String scope;
    private String summary;
    private String cadence;
    private String riskBoundary;
    private String author;
    private String sourceTitle;
    private String sourceType;
    private String sourceRef;
    private String sourcePublishedAt;
    private String validationStatus;
    private String status;
    private String note;
    private List<StrategyPlaybookRule> rules;

    public StrategyPlaybook toPlaybook() {
        StrategyPlaybook value = new StrategyPlaybook();
        value.setCode(code);
        value.setTitle(title);
        value.setScope(scope);
        value.setSummary(summary);
        value.setCadence(cadence);
        value.setRiskBoundary(riskBoundary);
        value.setAuthor(author);
        value.setSourceTitle(sourceTitle);
        value.setSourceType(sourceType);
        value.setSourceRef(sourceRef);
        value.setSourcePublishedAt(sourcePublishedAt);
        value.setValidationStatus(validationStatus);
        value.setStatus(status);
        value.setNote(note);
        return value;
    }
}

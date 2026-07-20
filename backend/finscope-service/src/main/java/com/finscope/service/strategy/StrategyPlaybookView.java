package com.finscope.service.strategy;

import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyPlaybookRule;

import java.util.Collections;
import java.util.List;

public class StrategyPlaybookView {
    private final Long id;
    private final String code;
    private final String title;
    private final String scope;
    private final String summary;
    private final String cadence;
    private final String riskBoundary;
    private final String author;
    private final String sourceTitle;
    private final String sourceType;
    private final String sourceRef;
    private final String sourcePublishedAt;
    private final String validationStatus;
    private final String status;
    private final String note;
    private final long revision;
    private final List<StrategyPlaybookRule> rules;

    private StrategyPlaybookView(StrategyPlaybook value, List<StrategyPlaybookRule> rules) {
        this.id = value.getId();
        this.code = value.getCode();
        this.title = value.getTitle();
        this.scope = value.getScope();
        this.summary = value.getSummary();
        this.cadence = value.getCadence();
        this.riskBoundary = value.getRiskBoundary();
        this.author = value.getAuthor();
        this.sourceTitle = value.getSourceTitle();
        this.sourceType = value.getSourceType();
        this.sourceRef = value.getSourceRef();
        this.sourcePublishedAt = value.getSourcePublishedAt();
        this.validationStatus = value.getValidationStatus();
        this.status = value.getStatus();
        this.note = value.getNote();
        this.revision = value.getRevision();
        this.rules = rules == null ? Collections.emptyList() : Collections.unmodifiableList(rules);
    }

    public static StrategyPlaybookView of(StrategyPlaybook value) {
        return new StrategyPlaybookView(value, Collections.emptyList());
    }

    public static StrategyPlaybookView of(StrategyPlaybook value, List<StrategyPlaybookRule> rules) {
        return new StrategyPlaybookView(value, rules);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getScope() { return scope; }
    public String getSummary() { return summary; }
    public String getCadence() { return cadence; }
    public String getRiskBoundary() { return riskBoundary; }
    public String getAuthor() { return author; }
    public String getSourceTitle() { return sourceTitle; }
    public String getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public String getSourcePublishedAt() { return sourcePublishedAt; }
    public String getValidationStatus() { return validationStatus; }
    public String getStatus() { return status; }
    public String getNote() { return note; }
    public long getRevision() { return revision; }
    public List<StrategyPlaybookRule> getRules() { return rules; }
}

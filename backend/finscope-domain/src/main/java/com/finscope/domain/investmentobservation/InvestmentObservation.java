package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationChangeType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSourceType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSubjectType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InvestmentObservation {
    private Long id;
    private InvestmentObservationSourceType sourceType;
    private Long sourceId;
    private String title;
    private String summary;
    private InvestmentObservationSubjectType subjectType = InvestmentObservationSubjectType.EVENT;
    private String subjectName;
    private InvestmentObservationStage stage;
    private InvestmentObservationChangeType changeType = InvestmentObservationChangeType.OTHER;
    private int score;
    private List<InvestmentObservationScoreDimension> scoreDimensions = new ArrayList<InvestmentObservationScoreDimension>();
    private String whyItMatters;
    private String uncertainty;
    private String nextValidation;
    private int supportingEvidenceCount;
    private int opposingEvidenceCount;
    private int independentSourceCount;
    private LocalDateTime firstObservedAt;
    private LocalDateTime lastChangedAt;
    private String lastSourceFingerprint;
    private InvestmentObservationDisposition disposition = InvestmentObservationDisposition.ACTIVE;
    private int revision;
    private boolean evidenceInsufficient;
    private boolean sourceAvailable = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public InvestmentObservationSourceType getSourceType() { return sourceType; }
    public void setSourceType(InvestmentObservationSourceType sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public InvestmentObservationSubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(InvestmentObservationSubjectType subjectType) { this.subjectType = subjectType; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public InvestmentObservationStage getStage() { return stage; }
    public void setStage(InvestmentObservationStage stage) { this.stage = stage; }
    public InvestmentObservationChangeType getChangeType() { return changeType; }
    public void setChangeType(InvestmentObservationChangeType changeType) { this.changeType = changeType; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public List<InvestmentObservationScoreDimension> getScoreDimensions() { return scoreDimensions; }
    public void setScoreDimensions(List<InvestmentObservationScoreDimension> scoreDimensions) { this.scoreDimensions = scoreDimensions; }
    public String getWhyItMatters() { return whyItMatters; }
    public void setWhyItMatters(String whyItMatters) { this.whyItMatters = whyItMatters; }
    public String getUncertainty() { return uncertainty; }
    public void setUncertainty(String uncertainty) { this.uncertainty = uncertainty; }
    public String getNextValidation() { return nextValidation; }
    public void setNextValidation(String nextValidation) { this.nextValidation = nextValidation; }
    public int getSupportingEvidenceCount() { return supportingEvidenceCount; }
    public void setSupportingEvidenceCount(int supportingEvidenceCount) { this.supportingEvidenceCount = supportingEvidenceCount; }
    public int getOpposingEvidenceCount() { return opposingEvidenceCount; }
    public void setOpposingEvidenceCount(int opposingEvidenceCount) { this.opposingEvidenceCount = opposingEvidenceCount; }
    public int getIndependentSourceCount() { return independentSourceCount; }
    public void setIndependentSourceCount(int independentSourceCount) { this.independentSourceCount = independentSourceCount; }
    public LocalDateTime getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(LocalDateTime firstObservedAt) { this.firstObservedAt = firstObservedAt; }
    public LocalDateTime getLastChangedAt() { return lastChangedAt; }
    public void setLastChangedAt(LocalDateTime lastChangedAt) { this.lastChangedAt = lastChangedAt; }
    public String getLastSourceFingerprint() { return lastSourceFingerprint; }
    public void setLastSourceFingerprint(String lastSourceFingerprint) { this.lastSourceFingerprint = lastSourceFingerprint; }
    public InvestmentObservationDisposition getDisposition() { return disposition; }
    public void setDisposition(InvestmentObservationDisposition disposition) { this.disposition = disposition; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public boolean isEvidenceInsufficient() { return evidenceInsufficient; }
    public void setEvidenceInsufficient(boolean evidenceInsufficient) { this.evidenceInsufficient = evidenceInsufficient; }
    public boolean isSourceAvailable() { return sourceAvailable; }
    public void setSourceAvailable(boolean sourceAvailable) { this.sourceAvailable = sourceAvailable; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.finscope.domain.attribution;

import java.time.LocalDateTime;

/** 一份归因报告对应的受控研究运行状态。 */
public class AttributionResearchRun {
    private Long id;
    private Long reportId;
    private String status;
    private String planJson;
    private String budgetJson;
    private String currentStep;
    private String terminationReason;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public String getBudgetJson() { return budgetJson; }
    public void setBudgetJson(String budgetJson) { this.budgetJson = budgetJson; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

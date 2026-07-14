package com.finscope.domain.attribution;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * 归因报告：某标的某日"为什么涨跌"的结构化研究结果。
 */
@Data
public class AttributionReport {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 标的名称。
     */
    private String instrumentName;
    /**
     * 标的类型。
     */
    private String instrumentType;
    /**
     * 报告日期。
     */
    private LocalDate reportDate;
    /**
     * 涨跌幅百分比。
     */
    private Double changePct;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 归因驱动因素列表。
     */
    private List<AttributionDriver> drivers;
    /**
     * 首要归因驱动因素。
     */
    private AttributionDriver primaryDriver;
    /**
     * 不确定因素列表。
     */
    private List<String> uncertainties = new ArrayList<String>();
    /**
     * 后续观察窗口列表。
     */
    private List<String> observationWindows = new ArrayList<String>();
    /**
     * 免责声明或诚实说明。
     */
    private String disclaimer;
    /**
     * 证据列表。
     */
    private List<AttributionEvidence> evidences;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 警告信息。
     */
    private String warningMessage;
    /**
     * 耗时毫秒数。
     */
    private Long durationMs;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInstrumentCode() {
        return instrumentCode;
    }

    public void setInstrumentCode(String instrumentCode) {
        this.instrumentCode = instrumentCode;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public void setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public Double getChangePct() {
        return changePct;
    }

    public void setChangePct(Double changePct) {
        this.changePct = changePct;
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

    public List<AttributionDriver> getDrivers() {
        return drivers;
    }

    public void setDrivers(List<AttributionDriver> drivers) {
        this.drivers = drivers;
    }

    public AttributionDriver getPrimaryDriver() { return primaryDriver; }
    public void setPrimaryDriver(AttributionDriver primaryDriver) { this.primaryDriver = primaryDriver; }
    public List<String> getUncertainties() { return uncertainties; }
    public void setUncertainties(List<String> uncertainties) {
        this.uncertainties = uncertainties == null ? new ArrayList<String>() : uncertainties;
    }
    public List<String> getObservationWindows() { return observationWindows; }
    public void setObservationWindows(List<String> observationWindows) {
        this.observationWindows = observationWindows == null ? new ArrayList<String>() : observationWindows;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public List<AttributionEvidence> getEvidences() {
        return evidences;
    }

    public void setEvidences(List<AttributionEvidence> evidences) {
        this.evidences = evidences;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
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

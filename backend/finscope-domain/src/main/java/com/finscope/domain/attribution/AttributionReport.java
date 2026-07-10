package com.finscope.domain.attribution;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 归因报告：某标的某日"为什么涨跌"的结构化研究结果。
 */
public class AttributionReport {
    private Long id;
    private String instrumentCode;
    private String instrumentName;
    private String instrumentType;
    private LocalDate reportDate;
    /** 归因时的涨跌幅快照 */
    private Double changePct;
    /** 状态：GENERATING | COMPLETED | FAILED */
    private String status;
    /** 一句话归因（同时用于卡片摘要徽标） */
    private String summary;
    /** 驱动因素（持久化为 JSON） */
    private List<AttributionDriver> drivers;
    /** 诚实说明/免责 */
    private String disclaimer;
    /** 关联证据（非持久化列，查询时组装） */
    private List<AttributionEvidence> evidences;
    private String errorMessage;
    /** 研究耗时毫秒 */
    private Long durationMs;
    private LocalDateTime createdAt;
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
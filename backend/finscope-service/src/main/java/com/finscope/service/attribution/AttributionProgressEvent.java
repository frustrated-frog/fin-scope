package com.finscope.service.attribution;

/**
 * 归因研究进度事件，通过 SSE 推送给前端，驱动"研究过程实时可见"。
 */
public class AttributionProgressEvent {
    /** 事件类型：STAGE(阶段推进) | CLUE(线索) | DONE(完成) | ERROR(失败) */
    private final String type;
    /** 阶段节点名，如 question-plan / web-search */
    private final String stage;
    /** 展示文案 */
    private final String message;
    /** 完成时携带的报告 id（DONE 时有值） */
    private final Long reportId;

    private AttributionProgressEvent(String type, String stage, String message, Long reportId) {
        this.type = type;
        this.stage = stage;
        this.message = message;
        this.reportId = reportId;
    }

    public static AttributionProgressEvent stage(String stage, String message) {
        return new AttributionProgressEvent("STAGE", stage, message, null);
    }

    public static AttributionProgressEvent clue(String message) {
        return new AttributionProgressEvent("CLUE", "web-search", message, null);
    }

    public static AttributionProgressEvent done(Long reportId, String message) {
        return new AttributionProgressEvent("DONE", "attribution-synth", message, reportId);
    }

    public static AttributionProgressEvent error(String message) {
        return new AttributionProgressEvent("ERROR", null, message, null);
    }

    public String getType() {
        return type;
    }

    public String getStage() {
        return stage;
    }

    public String getMessage() {
        return message;
    }

    public Long getReportId() {
        return reportId;
    }
}
package com.finscope.domain.attribution;

import lombok.Data;

/** 归因增量研究的独立快照，不覆盖原报告内容。 */
@Data
public class AttributionComparisonRow {
    private com.finscope.common.enums.attribution.AttributionComparisonKind kind;
    private String code;
    private String name;
    private String reason;
    private String source;
    private String asOfDate;
    private Double changePct;
    /** 截至目标日（含当日）的五个交易日累计涨跌。 */
    private Double fiveSessionChangePct;
    /** 目标股涨跌减去本行标的涨跌，单位为百分点。 */
    private Double stockRelativePct;
    private String note;
}

package com.finscope.domain.quant.data;

import lombok.Data;

import java.time.LocalDate;

@Data
public class QuantDatasetIssue {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 严重程度。
     */
    private String severity;
    /**
     * 问题编码。
     */
    private String issueCode;
    /**
     * 交易日期。
     */
    private LocalDate tradeDate;
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 提示消息。
     */
    private String message;
    /**
     * 问题数量。
     */
    private int issueCount;
}

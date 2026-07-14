package com.finscope.domain.quant.data;

import lombok.Data;

import java.time.LocalDate;

@Data
public class QuantUniverseMember {
    /**
     * 数据集 ID。
     */
    private Long datasetId;
    /**
     * 交易日期。
     */
    private LocalDate tradeDate;
    /**
     * 标的代码。
     */
    private String instrumentCode;
    /**
     * 成员名称。
     */
    private boolean member = true;
    /**
     * 来源类型。
     */
    private String sourceKind;
}

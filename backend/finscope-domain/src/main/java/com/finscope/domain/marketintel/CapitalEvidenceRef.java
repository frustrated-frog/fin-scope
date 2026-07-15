package com.finscope.domain.marketintel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent 可引用的一条原始数值证据。引用、展示名称与单位在进入模型前即固定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapitalEvidenceRef {
    private String ref;
    private String label;
    private String category;
    private BigDecimal value;
    private String unit;
    private LocalDateTime observedAt;
}

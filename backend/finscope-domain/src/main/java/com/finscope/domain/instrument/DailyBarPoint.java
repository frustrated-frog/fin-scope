package com.finscope.domain.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * 单根日 K 线数据，用于自选页标的的 K 线展示。
 *
 * <p>由 Python market-data-service 的 daily-bars 接口返回，经过 rpc 层
 * {@code PythonDailyBarClient} 解析后透传给前端。字段为前端渲染所需的最小集。</p>
 */
@Data
public class DailyBarPoint {
    private String code;
    private String market;
    private LocalDate tradeDate;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private BigDecimal amount;
    private BigDecimal amplitude;
    private BigDecimal changePct;
    private BigDecimal turnoverRate;
    private String adjustment;
    private Boolean isComplete;
}

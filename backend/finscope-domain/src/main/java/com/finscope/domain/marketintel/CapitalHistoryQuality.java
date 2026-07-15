package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 资金行为历史样本在进入事件研究前的确定性质量报告。
 */
@Data
public class CapitalHistoryQuality {
    private String status;
    private int dailySampleCount;
    private BigDecimal priceCoverageRate;
    private BigDecimal amountCoverageRate;
    private LocalDate latestDataDate;
    private List<String> dataGaps = Collections.emptyList();

    public boolean isReliable() {
        return "RELIABLE".equals(status);
    }

    public void setDataGaps(List<String> values) {
        dataGaps = Collections.unmodifiableList(new ArrayList<String>(values == null
                ? Collections.<String>emptyList() : values));
    }
}

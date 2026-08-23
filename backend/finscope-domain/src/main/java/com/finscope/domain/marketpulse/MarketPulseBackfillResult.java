package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 有界历史市场判断回填结果，单日失败不阻断其他交易日。 */
@Data
public class MarketPulseBackfillResult {
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private List<MarketPulseRefreshResult> results = new ArrayList<>();
    private Map<String, String> failures = new LinkedHashMap<>();
}

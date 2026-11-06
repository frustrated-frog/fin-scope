package com.finscope.domain.instrument;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 自选股票行情展示的历史输入，基准失败不会删除股票行情。 */
@Data
public class StockMarketHistory {
    private DailyBarHistory stock;
    private DailyBarHistory benchmark;
    private String benchmarkCode;
    private String benchmarkName;
    private List<String> warnings = new ArrayList<String>();
}

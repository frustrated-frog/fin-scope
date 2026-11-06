package com.finscope.domain.instrument;

import com.finscope.domain.valuation.StockValuationView;
import com.finscope.domain.valuation.StockValuationPeer;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 股票详情的确定性财务与真实估值快照，局部刷新失败仍保留可用本地数据。 */
@Data
public class StockFundamentals {
    private Instrument instrument;
    private List<StockFinancialQuarter> quarters = new ArrayList<StockFinancialQuarter>();
    private StockValuationView valuation;
    private List<StockValuationPeer> peers = new ArrayList<StockValuationPeer>();
    private String peerComparisonNote;
    private List<String> warnings = new ArrayList<String>();
    private Instant refreshedAt;
}

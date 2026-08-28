package com.finscope.rpc.valuation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExternalValuationSnapshot {
    private String name;
    private BigDecimal peTtm;
    private BigDecimal peMrq;
    private BigDecimal pbMrq;
    private BigDecimal psTtm;
    private BigDecimal pcfTtm;
    private Instant observedAt;
    private String sourceCode;
    private String qualityStatus;
    private List<String> warnings = new ArrayList<String>();
}

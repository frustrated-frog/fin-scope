package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;

public record ReactionStockSummary(String code, String name, long trackingCount, long historyCount,
                                   String latestTitle, String latestPublishedAt, ReactionEventSubtype latestSubtype) { }

package com.finscope.domain.investmentobservation;

import lombok.Data;

@Data
public class ReactionRefreshResult {
    private int refreshed;
    private int failed;
    private boolean busy;
}

package com.finscope.domain.fundamentals;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import com.finscope.common.enums.fundamentals.FundamentalDimension;

@Data
public class FundamentalInsight {
    private FundamentalDimension dimension;
    private String title;
    private String conclusion;
    private boolean attention;
    private String nextCheck;
    private List<FundamentalMetric> metrics = new ArrayList<>();
}

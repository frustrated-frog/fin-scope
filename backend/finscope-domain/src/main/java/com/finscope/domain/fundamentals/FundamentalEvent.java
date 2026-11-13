package com.finscope.domain.fundamentals;

import lombok.Data;
import java.time.LocalDateTime;
import com.finscope.common.enums.fundamentals.FundamentalDimension;

@Data
public class FundamentalEvent {
    private Long sampleId;
    private String title;
    private String subtype;
    private FundamentalDimension dimension;
    private String impact;
    private String nextCheck;
    private String sourceUrl;
    private LocalDateTime publishedAt;
}

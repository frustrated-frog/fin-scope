package com.finscope.domain.marketpulse;

import lombok.Data;
import com.finscope.common.enums.marketpulse.ResearchMemberStatus;
import com.finscope.common.enums.marketpulse.ResearchMemberReason;
import java.time.LocalDate;

/** 逐股补齐结果；READY代表指定日期的连续前复权行情满足要求。 */
@Data
public class ResearchMemberResult {
    private String instrumentCode;
    private LocalDate businessDate;
    private ResearchMemberStatus status;
    private ResearchMemberReason reason;
    private String message;
    private Integer validBars;
    private Integer requiredBars;
    private String sourceCode;
}

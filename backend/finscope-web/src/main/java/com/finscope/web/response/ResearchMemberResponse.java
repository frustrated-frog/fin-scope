package com.finscope.web.response;

import lombok.Data;
import com.finscope.common.enums.marketpulse.ResearchMemberStatus;
import com.finscope.common.enums.marketpulse.ResearchMemberReason;
import com.finscope.domain.marketpulse.ResearchMemberResult;

/** 面向浏览器的成员行情状态，日期使用ISO文本。 */
@Data
public class ResearchMemberResponse {
    private String instrumentCode;
    private String businessDate;
    private ResearchMemberStatus status;
    private ResearchMemberReason reason;
    private String message;
    private Integer validBars;
    private Integer requiredBars;
    private String sourceCode;

    public static ResearchMemberResponse of(ResearchMemberResult source) {
        ResearchMemberResponse value = new ResearchMemberResponse();
        value.setInstrumentCode(source.getInstrumentCode());
        value.setBusinessDate(source.getBusinessDate().toString());
        value.setStatus(source.getStatus());
        value.setReason(source.getReason());
        value.setMessage(source.getMessage());
        value.setValidBars(source.getValidBars());
        value.setRequiredBars(source.getRequiredBars());
        value.setSourceCode(source.getSourceCode());
        return value;
    }
}

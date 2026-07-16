package com.finscope.web.request.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class FreezeCapitalFlowRequest {
    private LocalDate from;
    private LocalDate to;
    private LocalDateTime asOfTime;

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public LocalDateTime getAsOfTime() { return asOfTime; }
    public void setAsOfTime(LocalDateTime asOfTime) { this.asOfTime = asOfTime; }

    public void validate() {
        if (from == null || to == null || asOfTime == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "冻结请求必须提供 from、to 和 asOfTime");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "冻结开始日期不能晚于结束日期");
        }
    }
}

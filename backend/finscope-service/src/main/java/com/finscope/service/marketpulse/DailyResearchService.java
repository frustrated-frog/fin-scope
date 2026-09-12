package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.DailyResearchSnapshot;
import com.finscope.rpc.marketpulse.PythonDailyResearchSource;
import org.springframework.stereotype.Service;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.marketpulse.ResearchMemberResult;
import com.finscope.rpc.marketpulse.PythonResearchMemberSource;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import java.time.LocalDate;

/** 独立于市场快照的本地样本查询，不触发全市场抓取或改变已有收盘判断。 */
@Service
public class DailyResearchService {
    private static final Pattern MEMBER_CODE = Pattern.compile(
            "^(?:(?:600|601|603|605|688)\\d{3}\\.SH|(?:000|001|002|003|300|301)\\d{3}\\.SZ|(?:43|83|87|88|92)\\d{4}\\.BJ)$");
    @Resource
    private PythonDailyResearchSource source;
    @Resource
    private PythonResearchMemberSource memberSource;

    public ResearchMemberResult ensureMember(LocalDate businessDate, String instrumentCode) {
        if (businessDate == null || instrumentCode == null || !MEMBER_CODE.matcher(instrumentCode).matches()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        return memberSource.ensure(businessDate, instrumentCode);
    }

    public DailyResearchSnapshot query(LocalDate businessDate) {
        return source.fetch(businessDate);
    }
}

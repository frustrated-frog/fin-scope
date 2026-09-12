package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.DailyResearchSnapshot;
import com.finscope.rpc.marketpulse.PythonDailyResearchSource;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;

/** 独立于市场快照的本地样本查询，不触发全市场抓取或改变已有收盘判断。 */
@Service
public class DailyResearchService {
    @Resource
    private PythonDailyResearchSource source;

    public DailyResearchSnapshot query(LocalDate businessDate) {
        return source.fetch(businessDate);
    }
}

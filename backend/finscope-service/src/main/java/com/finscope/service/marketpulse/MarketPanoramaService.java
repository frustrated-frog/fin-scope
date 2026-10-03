package com.finscope.service.marketpulse;

import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 读取有界的历史截面；浏览全景不会触发采集或重算。 */
@Service
public class MarketPanoramaService {
    @Resource
    private MarketPulseRepository repository;

    public List<MarketPulseWorkspace> history(LocalDate businessDate, int limit) {
        if (businessDate == null || limit < 1 || limit > 60) {
            throw new IllegalArgumentException("全景日期必填，观察范围为1至60个交易日");
        }
        return repository.findRecentWorkspaces(limit, businessDate).stream()
                .filter(value -> value.getBusinessDate() != null && !value.getBusinessDate().isAfter(businessDate))
                .sorted(Comparator.comparing(MarketPulseWorkspace::getBusinessDate))
                .toList();
    }
}

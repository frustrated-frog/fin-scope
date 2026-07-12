package com.finscope.service.attribution;

import com.finscope.dao.attribution.AttributionResearchRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;

/** 启动时关闭遗留 RUNNING 状态，避免前端永久显示研究中。 */
@Component
@DependsOn("databaseInitializer")
@Slf4j
public class AttributionStartupRecoveryService {
    private final AttributionResearchRunRepository repository;

    public AttributionStartupRecoveryService(AttributionResearchRunRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void recover() {
        int recovered = repository.failStaleRunningRuns(LocalDateTime.now().minusMinutes(3));
        if (recovered > 0) log.warn("已收敛遗留归因研究运行 recovered={}", recovered);
    }
}

package com.finscope.service.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.strategy.StrategyPlaybookRepository;
import com.finscope.domain.strategy.StrategyPlaybook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StrategyPlaybookService {
    private static final Set<String> STATUSES = new LinkedHashSet<>(
            Arrays.asList("RESEARCHING", "ACTIVE", "PAUSED"));
    private static final String[][] CATALOG = {
            {"FUND_DCA", "长期定投", "基金", "用固定节奏建立长期仓位", "每月", "不因短期涨跌改变投入纪律"},
            {"FUND_REBALANCE", "目标权重再平衡", "基金", "按角色目标权重纠正组合偏离", "每月检查", "单次调整前重新确认投资逻辑"},
            {"FUND_SATELLITE", "研究驱动卫星仓", "基金", "用事件与产业证据管理主题暴露", "每周观察", "卫星仓不得挤占核心仓"},
            {"STOCK_QUALITY", "质量成长观察", "股票", "追踪盈利质量、成长与估值匹配", "财报期", "逻辑失效优先于价格波动"},
            {"STOCK_EVENT", "事件研究观察", "股票", "把事件假设转成可验证观察窗口", "事件后", "必须写清失效条件"}
    };

    @Resource
    private StrategyPlaybookRepository repository;

    public List<StrategyPlaybookView> list() {
        Map<String, StrategyPlaybook> storedByCode = new HashMap<>();
        for (StrategyPlaybook stored : repository.findAll()) {
            storedByCode.put(stored.getCode(), stored);
        }

        List<StrategyPlaybookView> result = new ArrayList<>();
        for (String[] item : CATALOG) {
            StrategyPlaybook state = storedByCode.get(item[0]);
            String status = state == null ? "RESEARCHING" : state.getStatus();
            String note = state == null ? null : state.getNote();
            long revision = state == null ? 0 : state.getRevision();
            result.add(new StrategyPlaybookView(item[0], item[1], item[2], item[3], item[4],
                    item[5], status, note, revision));
        }
        return result;
    }

    @Transactional
    public StrategyPlaybook update(String code, String status, String note, long revision) {
        if (!known(code)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "策略模板不存在");
        }
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "策略状态不合法");
        }
        repository.upsert(code, "RESEARCHING", null);
        if (!repository.updateStatus(code, status, note, revision)) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT, "记录已被更新，请刷新后再试");
        }
        return repository.findByCode(code).orElseThrow(IllegalStateException::new);
    }

    private boolean known(String code) {
        for (String[] item : CATALOG) {
            if (item[0].equals(code)) {
                return true;
            }
        }
        return false;
    }
}

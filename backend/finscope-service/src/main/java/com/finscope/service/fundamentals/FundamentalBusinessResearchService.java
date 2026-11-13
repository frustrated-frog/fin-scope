package com.finscope.service.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalBusinessAspect;
import com.finscope.common.enums.fundamentals.FundamentalBusinessResearchStatus;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.fundamentals.FundamentalBusinessFinding;
import com.finscope.domain.fundamentals.FundamentalBusinessProjector;
import com.finscope.domain.fundamentals.FundamentalBusinessResearch;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.service.learningcard.StockLearningCardService;
import com.finscope.service.learningcard.StockLearningFramework;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FundamentalBusinessResearchService {
    @Resource
    private FundamentalStockResolver stocks;
    @Resource
    private StockLearningCardRepository cards;
    @Resource
    private StockLearningCardService learning;
    @Resource
    private LlmChatClient llm;
    // 与既有学习卡运行租约使用同一系统时区。
    private Clock clock = Clock.systemDefaultZone();

    public FundamentalBusinessResearch view(String code) {
        var stock = stocks.require(code);
        var result = new FundamentalBusinessResearch();
        var card = cards.findByInstrumentId(stock.getId()).orElse(null);
        if (card == null) {
            return result;
        }
        var lastStored = cards.latest(card.getId()).orElse(null);
        var active = cards.active(card.getId()).filter(value -> !isStale(value)).orElse(null);
        var latest = active == null ? lastStored : active;
        if (latest == null) {
            return result;
        }
        result.setLatestRunId(latest.getId());
        Map<FundamentalBusinessAspect, FundamentalBusinessFinding> findings = new LinkedHashMap<>();
        collect(latest, findings);
        if (findings.size() < FundamentalBusinessAspect.values().length) {
            for (var previous : cards.recentCompletedBusinessRuns(card.getId())) {
                collect(previous, findings);
                if (findings.size() == FundamentalBusinessAspect.values().length) {
                    break;
                }
            }
        }
        result.setFindings(findings.values().stream().sorted(java.util.Comparator.comparingInt(value -> value.getAspect().ordinal())).toList());
        result.setShowingPrevious(findings.values().stream().anyMatch(value -> !latest.getId().equals(value.getRunId())));
        applyStatus(result, latest);
        return result;
    }

    public FundamentalBusinessResearch start(String code) {
        var current = view(code);
        if (current.getStatus() == FundamentalBusinessResearchStatus.RUNNING) {
            return current;
        }
        if (!llm.isConfigured()) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR, "请先配置可用的模型服务，再自动整理经营资料");
        }
        try {
            learning.start(code);
        } catch (BusinessConflictException error) {
            var concurrent = view(code);
            if (concurrent.getStatus() == FundamentalBusinessResearchStatus.RUNNING) {
                return concurrent;
            }
            throw error;
        }
        return view(code);
    }

    private void collect(StockLearningCardRun run, Map<FundamentalBusinessAspect, FundamentalBusinessFinding> findings) {
        if (!StockLearningFramework.CODE.equals(run.getFrameworkCode())) {
            return;
        }
        for (var finding : new FundamentalBusinessProjector().project(run)) {
            findings.putIfAbsent(finding.getAspect(), finding);
        }
    }

    private void applyStatus(FundamentalBusinessResearch result, StockLearningCardRun run) {
        boolean stale = isStale(run);
        if ("RUNNING".equals(run.getStatus()) && !stale) {
            result.setStatus(FundamentalBusinessResearchStatus.RUNNING);
            result.setMessage("COLLECTING_EVIDENCE".equals(run.getStage()) ? "正在检索公开资料，完成后会自动更新经营摘要。"
                    : "SYNTHESIZING_CARDS".equals(run.getStage()) ? "正在整理经营认识与参考来源。" : "经营研究已进入队列。");
        } else if ("FAILED".equals(run.getStatus()) || stale) {
            result.setStatus(FundamentalBusinessResearchStatus.FAILED);
            result.setMessage(stale ? "上次研究未完成，可重新整理；已有经营认识继续保留。"
                    : "本次研究未完成，可重试；已有经营认识与手工笔记继续保留。");
        } else {
            boolean complete = result.getFindings().size() == FundamentalBusinessAspect.values().length;
            result.setStatus(complete && !result.isShowingPrevious() ? FundamentalBusinessResearchStatus.READY : FundamentalBusinessResearchStatus.PARTIAL);
            result.setMessage(result.getFindings().isEmpty() ? "公开资料暂不足以形成有来源的经营摘要，可重新整理。"
                    : result.isShowingPrevious() ? "已补充可用经营认识，部分栏目保留上次研究并标注整理时间。"
                    : "已整理 " + result.getFindings().size() + " 项经营认识，可核对来源后补入自己的经营笔记。");
        }
    }

    private boolean isStale(StockLearningCardRun run) {
        return "RUNNING".equals(run.getStatus()) && run.getCreatedAt() != null
                && run.getCreatedAt().isBefore(LocalDateTime.now(clock).minusMinutes(30));
    }
}

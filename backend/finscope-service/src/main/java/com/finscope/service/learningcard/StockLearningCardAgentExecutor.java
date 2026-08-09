package com.finscope.service.learningcard;

import com.finscope.dao.learningcard.StockLearningCardRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.domain.learningcard.StockLearningCardWatchItem;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class StockLearningCardAgentExecutor {
    private final StockLearningCardRepository cards;
    private final SearchEvidenceGateway search;
    private final SearchEvidenceContentService content;
    private final StockLearningCardSynthesisAgent synthesis;
    private final Executor executor;

    @Autowired
    public StockLearningCardAgentExecutor(StockLearningCardRepository cards, SearchEvidenceGateway search,
                                          SearchEvidenceContentService content,
                                          StockLearningCardSynthesisAgent synthesis,
                                          @Qualifier("stockLearningCardExecutor") Executor executor) {
        this.cards = cards; this.search = search; this.content = content;
        this.synthesis = synthesis; this.executor = executor;
    }

    public void schedule(Instrument instrument, StockLearningCardRun run) {
        executor.execute(() -> execute(instrument, run));
    }

    public void execute(Instrument instrument, StockLearningCardRun run) {
        List<StockLearningCardClaim> claims = new ArrayList<StockLearningCardClaim>();
        try {
            progress(run, "COLLECTING_EVIDENCE", "正在按六个学习维度收集公开资料");
            Map<String, List<StockLearningCardEvidence>> evidenceByDimension =
                    new LinkedHashMap<String, List<StockLearningCardEvidence>>();
            for (String dimension : StockLearningFramework.dimensions()) {
                try {
                    String query = StockLearningFramework.queryFor(dimension, instrument.getName(), instrument.getCode());
                    evidenceByDimension.put(dimension, collect(query, instrument.getName()));
                } catch (Exception error) {
                    evidenceByDimension.put(dimension, null);
                }
            }
            progress(run, "SYNTHESIZING_CARDS", "公开资料收集完成，正在生成六维学习卡");
            int order = 1;
            for (String dimension : StockLearningFramework.dimensions()) {
                StockLearningCardClaim claim;
                try {
                    List<StockLearningCardEvidence> evidence = evidenceByDimension.get(dimension);
                    if (evidence == null) throw new IllegalStateException("该维度资料收集失败");
                    claim = synthesis.synthesize(instrument.getName(), instrument.getCode(), dimension, evidence);
                } catch (Exception error) {
                    claim = failed(dimension);
                }
                claim.setSortOrder(order++); claims.add(claim);
            }
            finish(run, claims);
        } catch (Exception error) {
            String failedStage = value(run.getStage(), "COLLECTING_EVIDENCE");
            run.setStatus("FAILED"); run.setStage("COMPLETED"); run.setFailedStage(failedStage);
            run.setErrorCode("AGENT_EXECUTION_FAILED"); run.setUserMessage("学习卡生成中断，已保留运行记录，可以重新生成");
            run.setRetryable(true); run.setSummary("股票学习卡未能完成"); run.setEvidenceCompleteness("MISSING");
            run.setGenerationMode("CONTROLLED");
            cards.updateRun(run, claims, Collections.<StockLearningCardWatchItem>emptyList());
        }
    }

    private List<StockLearningCardEvidence> collect(String query, String subject) {
        SearchEvidenceBatch batch = search.search(new SearchEvidenceRequest(query, SearchDepth.DEEP,
                5, 5, "cn", "zh", 25_000));
        if (batch.isAllProvidersFailed()) throw new IllegalStateException("公开资料搜索暂不可用");
        List<StockLearningCardEvidence> result = new ArrayList<StockLearningCardEvidence>();
        int index = 1;
        for (SearchEvidence item : batch.getEvidence()) {
            ResearchEvidenceAcquisitionResult acquired = content.acquire(item, query, subject, index <= 2);
            result.add(new StockLearningCardEvidence("E" + index, text(item.getTitle()), text(item.getUrl()),
                    text(item.getSourceDomain()), text(item.getPublishedAt()), text(acquired.getContent())));
            index++;
        }
        return result;
    }

    private void progress(StockLearningCardRun run, String stage, String message) {
        run.setStatus("RUNNING"); run.setStage(stage); run.setSummary(message); run.setEvidenceCompleteness("PENDING");
        run.setGenerationMode("CONTROLLED");
        cards.updateRun(run, Collections.<StockLearningCardClaim>emptyList(), Collections.<StockLearningCardWatchItem>emptyList());
    }

    private void finish(StockLearningCardRun run, List<StockLearningCardClaim> claims) {
        int ready = 0, failed = 0, insufficient = 0;
        for (StockLearningCardClaim claim : claims) {
            if ("READY".equals(claim.getStatus())) ready++;
            else if ("FAILED".equals(claim.getStatus())) failed++;
            else if ("INSUFFICIENT_EVIDENCE".equals(claim.getStatus())) insufficient++;
        }
        run.setStage("COMPLETED"); run.setGenerationMode(ready > 0 ? "MODEL_ASSISTED" : "CONTROLLED");
        run.setEvidenceCompleteness(ready == claims.size() ? "COMPLETE" : ready > 0 ? "PARTIAL" : "MISSING");
        run.setConclusionStatus(ready > 0 ? "CONTINUE_LEARNING" : "INSUFFICIENT_EVIDENCE");
        if (ready == claims.size()) {
            run.setStatus("READY"); run.setSummary("六个学习维度均已生成"); run.setRetryable(false);
        } else if (ready > 0) {
            run.setStatus("DEGRADED"); run.setFailedStage("SYNTHESIZING_CARDS"); run.setErrorCode("DIMENSION_PARTIAL_FAILURE");
            run.setUserMessage("部分学习维度未能生成，其他结果已保留，可以重新生成补全"); run.setRetryable(true);
            run.setSummary("已生成" + ready + "个维度，" + (claims.size() - ready) + "个维度需要重试");
        } else if (failed > 0) {
            run.setStatus("FAILED"); run.setFailedStage(failed > 0 ? "SYNTHESIZING_CARDS" : "COLLECTING_EVIDENCE");
            run.setErrorCode("NO_DIMENSION_COMPLETED"); run.setUserMessage("暂未生成可用学习卡，请稍后重新生成"); run.setRetryable(true);
            run.setSummary("六个学习维度均未形成可用判断");
        } else {
            run.setStatus("DEGRADED"); run.setFailedStage(null); run.setErrorCode("INSUFFICIENT_EVIDENCE");
            run.setUserMessage("当前公开资料不足，六个维度暂不形成判断，可以稍后重新生成"); run.setRetryable(true);
            run.setSummary("已完成资料检索，但公开证据仍不足");
        }
        cards.updateRun(run, claims, Collections.singletonList(watch()));
    }

    private StockLearningCardClaim failed(String dimension) {
        StockLearningCardClaim claim = new StockLearningCardClaim();
        claim.setDimensionCode(dimension); claim.setStatus("FAILED"); claim.setFailureMessage("该维度生成失败，可以重新生成学习卡");
        claim.setJudgment("暂未形成判断"); claim.setRationale("该维度处理过程中发生异常，已与其他维度隔离");
        claim.setCounterargument("仍需补充与现有认识相反的公开材料"); claim.setUnknowns("该维度当前保持未知"); claim.setConfidence("LOW");
        return claim;
    }

    private StockLearningCardWatchItem watch() {
        StockLearningCardWatchItem item = new StockLearningCardWatchItem();
        item.setMetric("下一次公开披露中的经营与财务变化"); item.setBaseline("当前学习卡公开资料");
        item.setFrequency("下一次财报或重大公告"); item.setUpgradeCondition("关键经营指标与当前认识同向改善");
        item.setDowngradeCondition("关键经营指标与当前认识持续背离"); item.setNextReviewAt(LocalDateTime.now().plusMonths(3)); item.setSortOrder(1);
        return item;
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
    private String value(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
}

package com.finscope.service.supplychain;

import com.finscope.dao.supplychain.StockSupplyChainRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.supplychain.StockSupplyChainEvidence;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** 异步收集公开资料并生成股票产业链快照。 */
@Service
public class StockSupplyChainRefreshExecutor {
    private static final Logger log = LoggerFactory.getLogger(StockSupplyChainRefreshExecutor.class);
    private static final int MAX_EVIDENCE_CONTENT = 6000;

    private final StockSupplyChainRepository repository;
    private final SearchEvidenceGateway search;
    private final SearchEvidenceContentService content;
    private final StockSupplyChainSynthesisAgent synthesis;
    private final Executor executor;

    public StockSupplyChainRefreshExecutor(
            StockSupplyChainRepository repository,
            SearchEvidenceGateway search,
            SearchEvidenceContentService content,
            StockSupplyChainSynthesisAgent synthesis,
            @Qualifier("stockSupplyChainExecutor") Executor executor) {
        this.repository = repository;
        this.search = search;
        this.content = content;
        this.synthesis = synthesis;
        this.executor = executor;
    }

    public void schedule(Instrument instrument, StockSupplyChainRefreshRun run) {
        executor.execute(() -> execute(instrument, run));
    }

    public void execute(Instrument instrument, StockSupplyChainRefreshRun run) {
        String stage = "COLLECTING_EVIDENCE";
        try {
            progress(run, stage, "正在核对公司公告、定期报告与产业链公开资料");
            String query = query(instrument);
            SearchEvidenceBatch batch = search.search(new SearchEvidenceRequest(
                    query, SearchDepth.DEEP, 8, 8, "cn", "zh", 25_000));
            if (batch.isAllProvidersFailed() || batch.getEvidence().isEmpty()) {
                throw new IllegalStateException("公开资料搜索暂不可用");
            }
            List<StockSupplyChainEvidence> evidence = evidence(batch.getEvidence(), query, instrument.getName());
            stage = "SYNTHESIZING";
            progress(run, stage, "公开资料已冻结，正在归纳上下游关系并核对证据引用");
            StockSupplyChainSnapshot snapshot = synthesis.synthesize(
                    instrument.getName(), instrument.getCode(), evidence);
            snapshot.setInstrumentId(instrument.getId());
            repository.replaceSnapshotAndComplete(snapshot, run);
        } catch (Exception error) {
            log.warn("Stock supply-chain refresh failed: instrumentId={}, stage={}, errorType={}",
                    instrument.getId(), stage, error.getClass().getSimpleName());
            run.setStatus("FAILED");
            run.setStage("COMPLETED");
            run.setErrorCode("COLLECTING_EVIDENCE".equals(stage)
                    ? "EVIDENCE_COLLECTION_FAILED" : "SYNTHESIS_FAILED");
            run.setErrorMessage(error.getClass().getSimpleName());
            run.setMessage("产业链证据刷新失败，已保留原产业链快照，可以稍后重试");
            run.setRetryable(true);
            repository.updateRun(run);
        }
    }

    private List<StockSupplyChainEvidence> evidence(List<SearchEvidence> hits,
                                                     String query, String companyName) {
        List<StockSupplyChainEvidence> result = new ArrayList<StockSupplyChainEvidence>();
        int index = 1;
        for (SearchEvidence hit : hits) {
            ResearchEvidenceAcquisitionResult acquired = content.acquire(
                    hit, query, companyName, index <= 3);
            StockSupplyChainEvidence item = new StockSupplyChainEvidence();
            item.setEvidenceCode("E" + index);
            item.setTitle(text(hit.getTitle()));
            item.setUrl(text(hit.getUrl()));
            item.setSource(text(hit.getSourceDomain()));
            item.setSourceTier(text(hit.getSourceTier()));
            item.setPublishedAt(text(hit.getPublishedAt()));
            item.setExcerpt(limit(text(acquired.getContent()), MAX_EVIDENCE_CONTENT));
            result.add(item);
            index++;
        }
        return result;
    }

    private void progress(StockSupplyChainRefreshRun run, String stage, String message) {
        run.setStatus("RUNNING");
        run.setStage(stage);
        run.setMessage(message);
        repository.updateRun(run);
    }

    private String query(Instrument instrument) {
        return instrument.getName() + " " + instrument.getCode()
                + " 年报 主要供应商 主要客户 上游 下游 产业链 主营业务 公司公告";
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}

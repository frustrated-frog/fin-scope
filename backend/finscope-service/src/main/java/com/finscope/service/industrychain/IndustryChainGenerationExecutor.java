package com.finscope.service.industrychain;

import com.finscope.dao.industrychain.IndustryChainRepository;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

/** 异步生成一个产业链图谱修订并原子发布。 */
@Service
public class IndustryChainGenerationExecutor {
    private static final Logger log = LoggerFactory.getLogger(IndustryChainGenerationExecutor.class);

    private final IndustryChainRepository repository;
    private final IndustryChainEvidenceCollector collector;
    private final IndustryChainSynthesisAgent synthesis;
    private final Executor executor;

    public IndustryChainGenerationExecutor(IndustryChainRepository repository,
                                           IndustryChainEvidenceCollector collector,
                                           IndustryChainSynthesisAgent synthesis,
                                           @Qualifier("industryChainExecutor") Executor executor) {
        this.repository = repository;
        this.collector = collector;
        this.synthesis = synthesis;
        this.executor = executor;
    }

    public void schedule(IndustryChain chain, IndustryChainRevision revision) {
        executor.execute(() -> execute(chain, revision));
    }

    public void execute(IndustryChain chain, IndustryChainRevision revision) {
        String stage = "COLLECTING_EVIDENCE";
        try {
            progress(revision, stage, "正在通过三路搜索核对产业全景、上下游与代表公司");
            List<IndustryChainEvidence> evidence = collector.collect(chain.getName());
            stage = "SYNTHESIZING";
            progress(revision, stage, "公开资料已冻结，正在生成节点、关系与证据引用");
            IndustryChainGraph graph = synthesis.synthesize(chain.getName(), evidence);
            graph.setChainId(chain.getId());
            graph.setRevisionId(revision.getId());
            repository.publish(revision, graph);
        } catch (Exception error) {
            log.warn("Industry-chain generation failed: chainId={}, stage={}, errorType={}",
                    chain.getId(), stage, error.getClass().getSimpleName());
            String code = "COLLECTING_EVIDENCE".equals(stage)
                    ? "EVIDENCE_COLLECTION_FAILED" : "SYNTHESIS_FAILED";
            repository.fail(revision, code, "本次图谱生成失败，已保留上一版图谱，可以稍后重试");
        }
    }

    private void progress(IndustryChainRevision revision, String stage, String message) {
        revision.setStatus("RUNNING");
        revision.setStage(stage);
        revision.setMessage(message);
        repository.updateRevision(revision);
    }
}

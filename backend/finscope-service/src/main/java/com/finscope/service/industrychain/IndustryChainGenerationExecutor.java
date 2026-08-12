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
        schedule(chain.getId(), revision.getId());
    }

    public void schedule(Long chainId, Long revisionId) {
        executor.execute(() -> executeRequested(chainId, revisionId, "local-recovery"));
    }

    public void executeRequested(Long chainId, Long revisionId, String eventId) {
        IndustryChainRevision revision = repository.claimGeneration(chainId, revisionId).orElse(null);
        if (revision == null) {
            log.info("Skipping duplicate industry-chain generation: eventId={}, chainId={}, revisionId={}",
                    eventId, chainId, revisionId);
            return;
        }
        IndustryChain chain = repository.findChain(chainId).orElse(null);
        if (chain == null) {
            repository.fail(revision, "CHAIN_NOT_FOUND", "产业链主题不存在，无法继续补全");
            return;
        }
        log.info("Industry-chain generation claimed: eventId={}, chainId={}, revisionId={}",
                eventId, chainId, revisionId);
        executeClaimed(chain, revision);
    }

    /** 仅供同步单元测试与兼容调用；生产异步入口必须先通过 executeRequested 领取。 */
    public void execute(IndustryChain chain, IndustryChainRevision revision) {
        executeClaimed(chain, revision);
    }

    private void executeClaimed(IndustryChain chain, IndustryChainRevision revision) {
        String stage = "COLLECTING_EVIDENCE";
        try {
            progress(revision, stage, "正在通过三路搜索核对产业全景、上下游与代表公司");
            List<IndustryChainEvidence> evidence = collector.collect(chain.getName());
            IndustryChainGraph previous = repository.findPublishedGraph(chain.getId()).orElse(null);
            stage = previous == null ? "SYNTHESIZING" : "COMPLETING_STRUCTURE";
            progress(revision, stage, previous == null
                    ? "公开资料已冻结，正在生成节点、关系与证据引用"
                    : "正在沿用有效产业骨架，补齐材料、设备、部件、技术与应用节点");
            IndustryChainGraph graph = previous == null
                    ? synthesis.synthesize(chain.getName(), evidence)
                    : synthesis.synthesize(chain.getName(), evidence, previous);
            graph.setChainId(chain.getId());
            graph.setRevisionId(revision.getId());
            stage = "VALIDATING_STRUCTURE";
            progress(revision, stage, "结构补全已完成，正在校验环节覆盖、关系语义与研究画像");
            repository.publish(revision, graph);
        } catch (IllegalArgumentException error) {
            log.warn("Industry-chain generation failed: chainId={}, stage={}, errorType={}, reason={}",
                    chain.getId(), stage, error.getClass().getSimpleName(), compactReason(error));
            repository.fail(revision, "SYNTHESIS_FAILED",
                    "本次图谱结构未通过校验，已保留上一版图谱，可以重新生成");
        } catch (Exception error) {
            log.warn("Industry-chain generation will retry: chainId={}, stage={}, errorType={}, reason={}",
                    chain.getId(), stage, error.getClass().getSimpleName(), compactReason(error));
            repository.releaseGeneration(revision, "生成服务暂时不可用，任务等待 Kafka 重试或恢复扫描");
            throw error instanceof RuntimeException ? (RuntimeException) error
                    : new IllegalStateException("产业链图谱生成暂时失败", error);
        }
    }

    private void progress(IndustryChainRevision revision, String stage, String message) {
        revision.setStatus("RUNNING");
        revision.setStage(stage);
        revision.setMessage(message);
        repository.updateRevision(revision);
    }

    private String compactReason(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "no-message";
        }
        String compact = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "...";
    }
}

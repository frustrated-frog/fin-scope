package com.finscope.service.industrychain;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.industrychain.IndustryChainRepository;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainGenerationMessage;
import com.finscope.domain.industrychain.IndustryChainGenerationPublisher;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainRevision;
import com.finscope.domain.industrychain.IndustryChainStructureAssessment;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** 产业链图谱工作台的查询、创建与异步刷新编排。 */
@Service
public class IndustryChainService {
    private static final long REVISION_LEASE_MINUTES = 30L;

    private final IndustryChainRepository repository;
    private final IndustryChainGenerationExecutor executor;
    private final IndustryChainStructureAssessor structureAssessor;
    private final IndustryChainGenerationPublisher generationPublisher;

    public IndustryChainService(IndustryChainRepository repository,
                                IndustryChainGenerationExecutor executor,
                                IndustryChainStructureAssessor structureAssessor,
                                IndustryChainGenerationPublisher generationPublisher) {
        this.repository = repository;
        this.executor = executor;
        this.structureAssessor = structureAssessor;
        this.generationPublisher = generationPublisher;
    }

    public List<IndustryChain> list() {
        return repository.listChains();
    }

    public Workspace create(String rawName) {
        String name = displayName(rawName);
        String normalized = name.toLowerCase(Locale.ROOT);
        IndustryChain chain = repository.findByNormalizedName(normalized).orElse(null);
        if (chain == null) {
            try {
                chain = repository.createChain(name, normalized);
            } catch (DataAccessException error) {
                chain = repository.findByNormalizedName(normalized).orElseThrow(() -> error);
            }
        }
        IndustryChainRevision revision = start(chain);
        IndustryChainGraph graph = repository.findPublishedGraph(chain.getId()).orElse(null);
        return new Workspace(chain, revision, graph, structureAssessor.assess(graph));
    }

    public Workspace get(Long chainId) {
        IndustryChain chain = requiredChain(chainId);
        IndustryChainGraph graph = repository.findPublishedGraph(chainId).orElse(null);
        return new Workspace(chain, repository.latestRevision(chainId).orElse(null), graph,
                structureAssessor.assess(graph));
    }

    public IndustryChainRevision refresh(Long chainId) {
        return start(requiredChain(chainId));
    }

    public List<IndustryChainRevision> revisions(Long chainId) {
        requiredChain(chainId);
        return repository.findRevisions(chainId);
    }

    public FocusResult focus(Long chainId, String stockCode) {
        IndustryChainGraph graph = repository.findPublishedGraph(chainId)
                .orElseThrow(() -> new ResourceNotFoundException("产业链图谱尚未生成"));
        String code = stockCode == null ? "" : stockCode.trim();
        Set<String> selected = new LinkedHashSet<String>();
        for (IndustryChainNode node : graph.getNodes()) {
            if (code.equalsIgnoreCase(node.getStockCode())) {
                selected.add(node.getNodeKey());
            }
        }
        ArrayDeque<String> queue = new ArrayDeque<String>(selected);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (IndustryChainEdge edge : graph.getEdges()) {
                String neighbor = current.equals(edge.getSourceKey()) ? edge.getTargetKey()
                        : current.equals(edge.getTargetKey()) ? edge.getSourceKey() : null;
                if (neighbor != null && selected.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return new FocusResult(code, new ArrayList<String>(selected));
    }

    private IndustryChainRevision start(IndustryChain chain) {
        IndustryChainRevision active = repository.activeRevision(chain.getId()).orElse(null);
        if (active != null) {
            if (!isStale(active)) {
                throw new BusinessConflictException("该产业链图谱仍在生成中");
            }
            repository.fail(active, "STALE_REVISION_EXPIRED", "上一次生成因服务中断未完成，已允许重新生成");
        }
        IndustryChainRevision revision;
        try {
            revision = repository.createRevision(chain.getId());
        } catch (DataAccessException error) {
            if (repository.activeRevision(chain.getId()).isPresent()) {
                throw new BusinessConflictException("该产业链图谱仍在生成中");
            }
            throw error;
        }
        try {
            IndustryChainGenerationMessage message = IndustryChainGenerationMessage.requested(
                    chain.getId(), revision.getId());
            if (!generationPublisher.publish(message)) {
                executor.schedule(chain, revision);
            }
        } catch (RuntimeException error) {
            return repository.fail(revision, "QUEUE_REJECTED", "图谱生成队列暂时繁忙，可以稍后重试");
        }
        return revision;
    }

    private boolean isStale(IndustryChainRevision revision) {
        LocalDateTime heartbeat = revision.getLeaseUpdatedAt() == null
                ? revision.getCreatedAt() : revision.getLeaseUpdatedAt();
        return heartbeat != null && heartbeat
                .isBefore(LocalDateTime.now().minusMinutes(REVISION_LEASE_MINUTES));
    }

    private IndustryChain requiredChain(Long id) {
        return repository.findChain(id)
                .orElseThrow(() -> new ResourceNotFoundException("产业链不存在：" + id));
    }

    private String displayName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 60) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "产业链名称需为 1 至 60 个字符");
        }
        return name;
    }

    public static final class Workspace {
        private final IndustryChain chain;
        private final IndustryChainRevision revision;
        private final IndustryChainGraph graph;
        private final IndustryChainStructureAssessment structure;

        public Workspace(IndustryChain chain, IndustryChainRevision revision, IndustryChainGraph graph,
                         IndustryChainStructureAssessment structure) {
            this.chain = chain;
            this.revision = revision;
            this.graph = graph;
            this.structure = structure;
        }

        public Workspace(IndustryChain chain, IndustryChainRevision revision, IndustryChainGraph graph) {
            this(chain, revision, graph, null);
        }

        public IndustryChain getChain() { return chain; }
        public IndustryChainRevision getRevision() { return revision; }
        public IndustryChainGraph getGraph() { return graph; }
        public IndustryChainStructureAssessment getStructure() { return structure; }
    }

    public static final class FocusResult {
        private final String stockCode;
        private final List<String> nodeKeys;

        public FocusResult(String stockCode, List<String> nodeKeys) {
            this.stockCode = stockCode;
            this.nodeKeys = nodeKeys;
        }

        public String getStockCode() { return stockCode; }
        public List<String> getNodeKeys() { return nodeKeys; }
    }
}

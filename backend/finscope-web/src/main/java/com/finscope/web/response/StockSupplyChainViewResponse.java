package com.finscope.web.response;

import com.finscope.domain.supplychain.StockSupplyChainEvidence;
import com.finscope.domain.supplychain.StockSupplyChainNode;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.service.supplychain.StockSupplyChainService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 股票产业链当前快照与异步刷新状态响应。 */
public final class StockSupplyChainViewResponse {
    private String code;
    private String name;
    private SnapshotResponse snapshot;
    private RefreshRunResponse refreshRun;

    public static StockSupplyChainViewResponse of(StockSupplyChainService.StockSupplyChainView view) {
        StockSupplyChainViewResponse response = new StockSupplyChainViewResponse();
        response.code = view.getCode();
        response.name = view.getName();
        response.snapshot = view.getSnapshot() == null ? null : SnapshotResponse.of(view.getSnapshot());
        response.refreshRun = view.getRefreshRun() == null
                ? null : RefreshRunResponse.of(view.getRefreshRun());
        return response;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public SnapshotResponse getSnapshot() { return snapshot; }
    public RefreshRunResponse getRefreshRun() { return refreshRun; }

    public static final class SnapshotResponse {
        private String companyCode;
        private String companyName;
        private String summary;
        private String position;
        private String limitations;
        private String schemaVersion;
        private String model;
        private LocalDate evidenceAsOf;
        private LocalDateTime generatedAt;
        private LocalDateTime updatedAt;
        private List<NodeResponse> nodes;
        private List<EvidenceResponse> evidence;

        private static SnapshotResponse of(StockSupplyChainSnapshot snapshot) {
            SnapshotResponse response = new SnapshotResponse();
            response.companyCode = snapshot.getCompanyCode();
            response.companyName = snapshot.getCompanyName();
            response.summary = snapshot.getSummary();
            response.position = snapshot.getPosition();
            response.limitations = snapshot.getLimitations();
            response.schemaVersion = snapshot.getSchemaVersion();
            response.model = snapshot.getModel();
            response.evidenceAsOf = snapshot.getEvidenceAsOf();
            response.generatedAt = snapshot.getGeneratedAt();
            response.updatedAt = snapshot.getUpdatedAt();
            response.nodes = new ArrayList<NodeResponse>();
            for (StockSupplyChainNode node : snapshot.getNodes()) {
                response.nodes.add(NodeResponse.of(node));
            }
            response.evidence = new ArrayList<EvidenceResponse>();
            for (StockSupplyChainEvidence item : snapshot.getEvidence()) {
                response.evidence.add(EvidenceResponse.of(item));
            }
            return response;
        }

        public String getCompanyCode() { return companyCode; }
        public String getCompanyName() { return companyName; }
        public String getSummary() { return summary; }
        public String getPosition() { return position; }
        public String getLimitations() { return limitations; }
        public String getSchemaVersion() { return schemaVersion; }
        public String getModel() { return model; }
        public LocalDate getEvidenceAsOf() { return evidenceAsOf; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public List<NodeResponse> getNodes() { return nodes; }
        public List<EvidenceResponse> getEvidence() { return evidence; }
    }

    public static final class NodeResponse {
        private String layer;
        private String name;
        private String relationType;
        private String description;
        private String confidence;
        private List<String> evidenceRefs;

        private static NodeResponse of(StockSupplyChainNode node) {
            NodeResponse response = new NodeResponse();
            response.layer = node.getLayer();
            response.name = node.getName();
            response.relationType = node.getRelationType();
            response.description = node.getDescription();
            response.confidence = node.getConfidence();
            response.evidenceRefs = new ArrayList<String>(node.getEvidenceRefs());
            return response;
        }

        public String getLayer() { return layer; }
        public String getName() { return name; }
        public String getRelationType() { return relationType; }
        public String getDescription() { return description; }
        public String getConfidence() { return confidence; }
        public List<String> getEvidenceRefs() { return evidenceRefs; }
    }

    public static final class EvidenceResponse {
        private String evidenceCode;
        private String title;
        private String url;
        private String source;
        private String sourceTier;
        private String publishedAt;
        private String excerpt;

        private static EvidenceResponse of(StockSupplyChainEvidence item) {
            EvidenceResponse response = new EvidenceResponse();
            response.evidenceCode = item.getEvidenceCode();
            response.title = item.getTitle();
            response.url = item.getUrl();
            response.source = item.getSource();
            response.sourceTier = item.getSourceTier();
            response.publishedAt = item.getPublishedAt();
            response.excerpt = limit(item.getExcerpt(), 320);
            return response;
        }

        public String getEvidenceCode() { return evidenceCode; }
        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getSource() { return source; }
        public String getSourceTier() { return sourceTier; }
        public String getPublishedAt() { return publishedAt; }
        public String getExcerpt() { return excerpt; }
    }

    public static final class RefreshRunResponse {
        private Long id;
        private String status;
        private String stage;
        private String message;
        private String errorCode;
        private boolean retryable;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;

        public static RefreshRunResponse of(StockSupplyChainRefreshRun run) {
            RefreshRunResponse response = new RefreshRunResponse();
            response.id = run.getId();
            response.status = run.getStatus();
            response.stage = run.getStage();
            response.message = run.getMessage();
            response.errorCode = run.getErrorCode();
            response.retryable = run.isRetryable();
            response.createdAt = run.getCreatedAt();
            response.completedAt = run.getCompletedAt();
            return response;
        }

        public Long getId() { return id; }
        public String getStatus() { return status; }
        public String getStage() { return stage; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public boolean isRetryable() { return retryable; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
    }

    private static String limit(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}

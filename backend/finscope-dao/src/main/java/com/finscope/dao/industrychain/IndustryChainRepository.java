package com.finscope.dao.industrychain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainRevision;
import com.finscope.domain.industrychain.IndustryChainResearchContent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 产业链主题、修订以及版本内节点和关系的 SQLite 存储。 */
@Repository
public class IndustryChainRepository {
    private static final int GENERATION_LEASE_MINUTES = 25;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ObjectMapper objectMapper;

    private final RowMapper<IndustryChain> chainMapper = (rs, row) -> {
        IndustryChain value = new IndustryChain();
        value.setId(rs.getLong("id"));
        value.setName(rs.getString("name"));
        value.setNormalizedName(rs.getString("normalized_name"));
        value.setSummary(rs.getString("summary"));
        long revision = rs.getLong("current_revision_id");
        value.setCurrentRevisionId(rs.wasNull() ? null : revision);
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    private final RowMapper<IndustryChainRevision> revisionMapper = (rs, row) -> {
        IndustryChainRevision value = new IndustryChainRevision();
        value.setId(rs.getLong("id"));
        value.setChainId(rs.getLong("chain_id"));
        value.setStatus(rs.getString("status"));
        value.setStage(rs.getString("stage"));
        value.setMessage(rs.getString("message"));
        value.setErrorCode(rs.getString("error_code"));
        value.setRetryable(rs.getInt("retryable") == 1);
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setLeaseUpdatedAt(TimeUtil.localDateTime(rs, "lease_updated_at"));
        value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at"));
        return value;
    };

    public IndustryChain createChain(String name, String normalizedName) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO industry_chain(name,normalized_name,created_at,updated_at) VALUES(?,?,?,?)",
                name, normalizedName, TimeUtil.text(now), TimeUtil.text(now));
        return findByNormalizedName(normalizedName)
                .orElseThrow(() -> new IllegalStateException("产业链主题保存失败"));
    }

    public Optional<IndustryChain> findChain(Long id) {
        List<IndustryChain> values = jdbcTemplate.query("SELECT * FROM industry_chain WHERE id=?", chainMapper, id);
        return first(values);
    }

    public Optional<IndustryChain> findByNormalizedName(String normalizedName) {
        List<IndustryChain> values = jdbcTemplate.query(
                "SELECT * FROM industry_chain WHERE normalized_name=?", chainMapper, normalizedName);
        return first(values);
    }

    public List<IndustryChain> listChains() {
        return jdbcTemplate.query("SELECT * FROM industry_chain ORDER BY updated_at DESC,id DESC", chainMapper);
    }

    public IndustryChainRevision createRevision(Long chainId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO industry_chain_revision(chain_id,status,stage,message,retryable,created_at) "
                        + "VALUES(?,'RUNNING','QUEUED','产业链图谱生成已进入队列',0,?)",
                chainId, TimeUtil.text(now));
        return latestRevision(chainId).orElseThrow(() -> new IllegalStateException("产业链修订保存失败"));
    }

    public Optional<IndustryChainRevision> latestRevision(Long chainId) {
        return first(jdbcTemplate.query("SELECT * FROM industry_chain_revision WHERE chain_id=? ORDER BY id DESC LIMIT 1",
                revisionMapper, chainId));
    }

    public Optional<IndustryChainRevision> activeRevision(Long chainId) {
        return first(jdbcTemplate.query("SELECT * FROM industry_chain_revision WHERE chain_id=? AND status='RUNNING' "
                + "ORDER BY id DESC LIMIT 1", revisionMapper, chainId));
    }

    /** 通过条件更新原子领取一次尚未消费的生成任务。 */
    public Optional<IndustryChainRevision> claimGeneration(Long chainId, Long revisionId) {
        String now = TimeUtil.text(LocalDateTime.now());
        String expired = TimeUtil.text(LocalDateTime.now().minusMinutes(GENERATION_LEASE_MINUTES));
        int updated = jdbcTemplate.update("UPDATE industry_chain_revision SET stage='DISPATCHED',message=?,"
                        + "lease_updated_at=? WHERE id=? AND chain_id=? AND status='RUNNING' AND (stage='QUEUED' "
                        + "OR (stage NOT IN ('PUBLISHING') AND lease_updated_at IS NOT NULL AND lease_updated_at<?))",
                "图谱补全任务已进入异步执行", now, revisionId, chainId, expired);
        return updated == 1 ? findRevision(revisionId) : Optional.empty();
    }

    public List<IndustryChainRevision> findRecoverableGenerations() {
        String queuedBefore = TimeUtil.text(LocalDateTime.now().minusMinutes(5));
        String leaseBefore = TimeUtil.text(LocalDateTime.now().minusMinutes(GENERATION_LEASE_MINUTES));
        return jdbcTemplate.query("SELECT * FROM industry_chain_revision WHERE status='RUNNING' AND "
                        + "((stage='QUEUED' AND created_at<?) OR (stage NOT IN ('QUEUED','PUBLISHING') "
                        + "AND lease_updated_at IS NOT NULL AND lease_updated_at<?)) ORDER BY id",
                revisionMapper, queuedBefore, leaseBefore);
    }

    public List<IndustryChainRevision> findRevisions(Long chainId) {
        return jdbcTemplate.query("SELECT * FROM industry_chain_revision WHERE chain_id=? ORDER BY id DESC",
                revisionMapper, chainId);
    }

    public IndustryChainRevision updateRevision(IndustryChainRevision revision) {
        LocalDateTime completedAt = "RUNNING".equals(revision.getStatus()) ? null : LocalDateTime.now();
        int updated = jdbcTemplate.update("UPDATE industry_chain_revision SET status=?,stage=?,message=?,error_code=?,"
                        + "retryable=?,completed_at=?,lease_updated_at=? WHERE id=? AND status='RUNNING'",
                revision.getStatus(), revision.getStage(), revision.getMessage(), revision.getErrorCode(),
                revision.isRetryable() ? 1 : 0, TimeUtil.text(completedAt), TimeUtil.text(LocalDateTime.now()),
                revision.getId());
        if (updated != 1) {
            throw new IllegalStateException("产业链修订已失效");
        }
        return findRevision(revision.getId()).orElseThrow(() -> new IllegalStateException("产业链修订不存在"));
    }

    public IndustryChainRevision fail(IndustryChainRevision revision, String errorCode, String message) {
        int updated = jdbcTemplate.update("UPDATE industry_chain_revision SET status='FAILED',stage='COMPLETED',"
                        + "error_code=?,message=?,retryable=1,completed_at=? WHERE id=? AND status='RUNNING'",
                errorCode, message, TimeUtil.text(LocalDateTime.now()), revision.getId());
        if (updated != 1) {
            return findRevision(revision.getId())
                    .orElseThrow(() -> new IllegalStateException("产业链修订不存在"));
        }
        return findRevision(revision.getId()).orElseThrow(() -> new IllegalStateException("产业链修订不存在"));
    }

    public void releaseGeneration(IndustryChainRevision revision, String message) {
        jdbcTemplate.update("UPDATE industry_chain_revision SET stage='QUEUED',message=?,retryable=1,"
                        + "lease_updated_at=NULL WHERE id=? AND chain_id=? AND status='RUNNING'",
                message, revision.getId(), revision.getChainId());
    }

    @Transactional
    public IndustryChainGraph publish(IndustryChainRevision revision, IndustryChainGraph graph) {
        int claimed = jdbcTemplate.update("UPDATE industry_chain_revision SET stage='PUBLISHING',message=? "
                        + "WHERE id=? AND chain_id=? AND status='RUNNING' "
                        + "AND stage IN ('QUEUED','DISPATCHED','COLLECTING_EVIDENCE','SYNTHESIZING',"
                        + "'COMPLETING_STRUCTURE','VALIDATING_STRUCTURE')",
                "结构校验完成，正在原子发布新版本", revision.getId(), revision.getChainId());
        if (claimed != 1) {
            throw new IllegalStateException("产业链修订已失效，拒绝发布过期结果");
        }
        graph.setChainId(revision.getChainId());
        graph.setRevisionId(revision.getId());
        int order = 0;
        for (IndustryChainEvidence evidence : graph.getEvidence()) {
            jdbcTemplate.update("INSERT INTO industry_chain_evidence(revision_id,evidence_code,title,url,source,"
                            + "source_tier,published_at,excerpt,sort_order) VALUES(?,?,?,?,?,?,?,?,?)",
                    revision.getId(), evidence.getEvidenceCode(), evidence.getTitle(), evidence.getUrl(),
                    evidence.getSource(), evidence.getSourceTier(), evidence.getPublishedAt(), evidence.getExcerpt(), order++);
        }
        order = 0;
        for (IndustryChainNode node : graph.getNodes()) {
            jdbcTemplate.update("INSERT INTO industry_chain_node(revision_id,node_key,type,name,description,stage_order,"
                            + "stock_code,confidence,evidence_refs_json,sort_order) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    revision.getId(), node.getNodeKey(), node.getType(), node.getName(), node.getDescription(),
                    node.getStageOrder(), node.getStockCode(), node.getConfidence(), json(node.getEvidenceRefs()), order++);
        }
        order = 0;
        for (IndustryChainEdge edge : graph.getEdges()) {
            jdbcTemplate.update("INSERT INTO industry_chain_edge(revision_id,edge_key,source_key,target_key,type,nature,"
                            + "description,confidence,strength,direction_note,evidence_refs_json,sort_order) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    revision.getId(), edge.getEdgeKey(), edge.getSourceKey(), edge.getTargetKey(), edge.getType(),
                    edge.getNature(), edge.getDescription(), edge.getConfidence(), edge.getStrength(),
                    edge.getDirectionNote(), json(edge.getEvidenceRefs()), order++);
        }
        LocalDateTime completed = LocalDateTime.now();
        int published = jdbcTemplate.update("UPDATE industry_chain_revision SET status='READY',stage='COMPLETED',message=?,error_code=NULL,"
                        + "retryable=0,graph_summary=?,limitations=?,research_content_json=?,schema_version=?,model=?,"
                        + "generated_at=?,completed_at=? WHERE id=? AND chain_id=? AND status='RUNNING' AND stage='PUBLISHING'",
                "产业链图谱已更新", graph.getSummary(), graph.getLimitations(), researchJson(graph.getResearchContent()),
                graph.getSchemaVersion(), graph.getModel(),
                TimeUtil.text(graph.getGeneratedAt()), TimeUtil.text(completed), revision.getId(), revision.getChainId());
        if (published != 1) {
            throw new IllegalStateException("产业链修订发布期间已失效");
        }
        int switched = jdbcTemplate.update("UPDATE industry_chain SET summary=?,current_revision_id=?,updated_at=? "
                        + "WHERE id=? AND EXISTS(SELECT 1 FROM industry_chain_revision "
                        + "WHERE id=? AND chain_id=? AND status='READY')",
                graph.getSummary(), revision.getId(), TimeUtil.text(completed), revision.getChainId(),
                revision.getId(), revision.getChainId());
        if (switched != 1) {
            throw new IllegalStateException("产业链当前版本切换失败");
        }
        return findPublishedGraph(revision.getChainId())
                .orElseThrow(() -> new IllegalStateException("产业链图谱发布失败"));
    }

    public Optional<IndustryChainGraph> findPublishedGraph(Long chainId) {
        Optional<IndustryChain> chain = findChain(chainId);
        if (!chain.isPresent() || chain.get().getCurrentRevisionId() == null) {
            return Optional.empty();
        }
        Long revisionId = chain.get().getCurrentRevisionId();
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM industry_chain_revision WHERE id=?", revisionId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        java.util.Map<String, Object> row = rows.get(0);
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setChainId(chainId);
        graph.setRevisionId(revisionId);
        graph.setName(chain.get().getName());
        graph.setSummary(text(row.get("graph_summary")));
        graph.setLimitations(text(row.get("limitations")));
        graph.setResearchContent(researchContent(row.get("research_content_json")));
        graph.setSchemaVersion(text(row.get("schema_version")));
        graph.setModel(text(row.get("model")));
        graph.setGeneratedAt(parseTime(row.get("generated_at")));
        graph.setEvidence(readEvidence(revisionId));
        graph.setNodes(readNodes(revisionId));
        graph.setEdges(readEdges(revisionId));
        return Optional.of(graph);
    }

    public Optional<IndustryChainRevision> findRevision(Long id) {
        return first(jdbcTemplate.query("SELECT * FROM industry_chain_revision WHERE id=?", revisionMapper, id));
    }

    private List<IndustryChainEvidence> readEvidence(Long revisionId) {
        return jdbcTemplate.query("SELECT * FROM industry_chain_evidence WHERE revision_id=? ORDER BY sort_order,id",
                (rs, row) -> {
                    IndustryChainEvidence value = new IndustryChainEvidence();
                    value.setEvidenceCode(rs.getString("evidence_code"));
                    value.setTitle(rs.getString("title"));
                    value.setUrl(rs.getString("url"));
                    value.setSource(rs.getString("source"));
                    value.setSourceTier(rs.getString("source_tier"));
                    value.setPublishedAt(rs.getString("published_at"));
                    value.setExcerpt(rs.getString("excerpt"));
                    return value;
                }, revisionId);
    }

    private List<IndustryChainNode> readNodes(Long revisionId) {
        return jdbcTemplate.query("SELECT * FROM industry_chain_node WHERE revision_id=? ORDER BY sort_order,id",
                (rs, row) -> {
                    IndustryChainNode value = new IndustryChainNode();
                    value.setNodeKey(rs.getString("node_key"));
                    value.setType(rs.getString("type"));
                    value.setName(rs.getString("name"));
                    value.setDescription(rs.getString("description"));
                    int stageOrder = rs.getInt("stage_order");
                    value.setStageOrder(rs.wasNull() ? null : stageOrder);
                    value.setStockCode(rs.getString("stock_code"));
                    value.setConfidence(rs.getString("confidence"));
                    value.setEvidenceRefs(refs(rs.getString("evidence_refs_json")));
                    return value;
                }, revisionId);
    }

    private List<IndustryChainEdge> readEdges(Long revisionId) {
        return jdbcTemplate.query("SELECT * FROM industry_chain_edge WHERE revision_id=? ORDER BY sort_order,id",
                (rs, row) -> {
                    IndustryChainEdge value = new IndustryChainEdge();
                    value.setEdgeKey(rs.getString("edge_key"));
                    value.setSourceKey(rs.getString("source_key"));
                    value.setTargetKey(rs.getString("target_key"));
                    value.setType(rs.getString("type"));
                    value.setNature(rs.getString("nature"));
                    value.setDescription(rs.getString("description"));
                    value.setConfidence(rs.getString("confidence"));
                    value.setStrength(rs.getString("strength"));
                    value.setDirectionNote(rs.getString("direction_note"));
                    value.setEvidenceRefs(refs(rs.getString("evidence_refs_json")));
                    return value;
                }, revisionId);
    }

    private String json(List<String> refs) {
        try {
            return objectMapper.writeValueAsString(refs == null ? new ArrayList<String>() : refs);
        } catch (Exception error) {
            throw new IllegalStateException("产业链证据引用序列化失败", error);
        }
    }

    private String researchJson(IndustryChainResearchContent content) {
        try {
            return objectMapper.writeValueAsString(
                    content == null ? new IndustryChainResearchContent() : content);
        } catch (Exception error) {
            throw new IllegalStateException("产业链研究内容序列化失败", error);
        }
    }

    private IndustryChainResearchContent researchContent(Object value) {
        String json = text(value);
        if (json.isEmpty()) {
            return new IndustryChainResearchContent();
        }
        try {
            return objectMapper.readValue(json, IndustryChainResearchContent.class);
        } catch (Exception error) {
            throw new IllegalStateException("产业链研究内容解析失败", error);
        }
    }

    private List<String> refs(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception error) {
            throw new IllegalStateException("产业链证据引用解析失败", error);
        }
    }

    private LocalDateTime parseTime(Object value) {
        String text = text(value);
        return text.isEmpty() ? null : LocalDateTime.parse(text);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private <T> Optional<T> first(List<T> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }
}

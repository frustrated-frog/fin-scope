package com.finscope.service.investmentrecognition;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.investmentrecognition.InvestmentRecognitionCandidateRepository;
import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionCandidate;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.topic.Topic;
import com.finscope.service.knowledge.KnowledgeTopicService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import com.finscope.common.exception.BizErrorCode;

@Service
public class InvestmentRecognitionService {
    private static final List<String> MUTABLE_STATUSES = Arrays.asList(
            "CANDIDATE", "NEEDS_EVIDENCE", "DISMISSED", "INVALIDATED");
    private final InvestmentRecognitionCandidateRepository candidates;
    private final KnowledgeTopicService topics;
    private final TopicRepository topicRepository;
    private final KnowledgeEntryRepository entries;

    public InvestmentRecognitionService(InvestmentRecognitionCandidateRepository candidates,
                                        KnowledgeTopicService topics,
                                        TopicRepository topicRepository,
                                        KnowledgeEntryRepository entries) {
        this.candidates = candidates;
        this.topics = topics;
        this.topicRepository = topicRepository;
        this.entries = entries;
    }

    public List<InvestmentRecognitionCandidate> list(String status) {
        return blank(status) ? candidates.findAll() : candidates.findByStatus(status.trim().toUpperCase());
    }

    @Transactional
    public InvestmentRecognitionCandidate accept(long id, long expectedRevision) {
        InvestmentRecognitionCandidate candidate = require(id);
        if (!"CANDIDATE".equals(candidate.getStatus())) {
            throw new BusinessException(BizErrorCode.KNOWLEDGE_ONLY_AGENT_CANDIDATE);
        }
        requireCompleteEvidence(candidate);
        Topic topic = topics.create(topicName(candidate), topicDescription(candidate));
        if (!topicRepository.updateKnowledgeState(topic.getId(), "ACTIVE", "REVIEWING", topic.getRevision())) {
            throw new BusinessException(BizErrorCode.KNOWLEDGE_PROFILE_STATE_CHANGED);
        }
        KnowledgeEntry entry = entries.saveDraft(entry(candidate, topic.getId()));
        if (!entries.finalizeDraft(entry.getId(), entry.getRevision())) {
            throw new BusinessException(BizErrorCode.KNOWLEDGE_CONCLUSION_CHANGED);
        }
        if (!candidates.updateStatus(id, "ACCEPTED", expectedRevision, topic.getId())) {
            throw new BusinessException(BizErrorCode.AGENT_CANDIDATE_UPDATED);
        }
        return require(id);
    }

    public InvestmentRecognitionCandidate updateStatus(long id, String status, long expectedRevision) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!MUTABLE_STATUSES.contains(normalized) || "CANDIDATE".equals(normalized)) {
            throw new BusinessException(BizErrorCode.CANDIDATE_STATE_UNSUPPORTED);
        }
        InvestmentRecognitionCandidate candidate = require(id);
        if ("ACCEPTED".equals(candidate.getStatus())) {
            throw new BusinessException(BizErrorCode.FORMAL_KNOWLEDGE_REVIEW_REQUIRED);
        }
        if (!candidates.updateStatus(id, normalized, expectedRevision, candidate.getTopicId())) {
            throw new BusinessException(BizErrorCode.AGENT_CANDIDATE_UPDATED);
        }
        return require(id);
    }

    private KnowledgeEntry entry(InvestmentRecognitionCandidate candidate, Long topicId) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setTopicId(topicId);
        entry.setEntryType("CONCLUSION");
        entry.setQuestionSnapshot(candidate.getThesis());
        entry.setConfidence(candidate.getConfidence());
        entry.setContentMarkdown(markdown(candidate));
        return entry;
    }

    private String markdown(InvestmentRecognitionCandidate value) {
        StringBuilder text = new StringBuilder();
        text.append("## 投资命题\n\n").append(value.getThesis()).append("\n\n")
                .append("### 已观察变化\n\n").append(value.getObservedChange()).append("\n\n")
                .append("### 作用机制\n\n").append(value.getMechanism()).append("\n\n")
                .append("### 支持数据\n\n");
        appendList(text, value.getSupportingData());
        text.append("\n### 反向证据\n\n");
        appendList(text, value.getCounterData());
        text.append("\n### 后续验证\n\n");
        appendList(text, value.getValidationMetrics());
        text.append("\n### 失效条件\n\n").append(value.getInvalidationConditions())
                .append("\n\n### 时间窗口\n\n").append(value.getHorizon());
        return text.toString();
    }

    private void appendList(StringBuilder text, List<String> values) {
        if (values == null || values.isEmpty()) {
            text.append("- 暂无\n");
            return;
        }
        for (String value : values) text.append("- ").append(value).append("\n");
    }

    private String topicName(InvestmentRecognitionCandidate value) {
        String name = value.getSubjectName() + "｜" + value.getThesis();
        return name.length() <= 80 ? name : name.substring(0, 80);
    }
    private String topicDescription(InvestmentRecognitionCandidate value) {
        return value.getSubjectType() + " " + value.getSubjectCode() + "；观察窗口：" + value.getHorizon();
    }
    private void requireCompleteEvidence(InvestmentRecognitionCandidate value) {
        if (!"SUFFICIENT".equals(value.getEvidenceCompleteness())
                || blank(value.getThesis()) || blank(value.getObservedChange()) || blank(value.getMechanism())
                || blank(value.getInvalidationConditions()) || blank(value.getHorizon())
                || empty(value.getSupportingData()) || empty(value.getCounterData())
                || empty(value.getValidationMetrics())) {
            throw new BusinessException(BizErrorCode.EVIDENCE_CANDIDATE_INCOMPLETE);
        }
    }
    private boolean empty(List<String> values) {
        if (values == null || values.isEmpty()) return true;
        for (String value : values) if (!blank(value)) return false;
        return true;
    }
    private InvestmentRecognitionCandidate require(long id) {
        return candidates.findById(id).orElseThrow(() ->
                new BusinessException(BizErrorCode.AGENT_CANDIDATE_NOT_FOUND));
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

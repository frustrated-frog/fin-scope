package com.finscope.service.knowledge;

import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.KnowledgeProjectionJobRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import com.finscope.domain.topic.Topic;
import com.finscope.service.vault.VaultWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KnowledgeVaultProjector {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeVaultProjector.class);

    private final KnowledgeProjectionJobRepository jobs;
    private final KnowledgeEntryRepository entries;
    private final TopicRepository topics;
    private final VaultWriter writer;

    public KnowledgeVaultProjector(KnowledgeProjectionJobRepository jobs,
                                   KnowledgeEntryRepository entries,
                                   TopicRepository topics,
                                   VaultWriter writer) {
        this.jobs = jobs;
        this.entries = entries;
        this.topics = topics;
        this.writer = writer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(KnowledgeProjectionRequested event) {
        project(event.getJobId());
    }

    public void project(long jobId) {
        KnowledgeProjectionJob job = jobs.findById(jobId).orElse(null);
        if (job == null || !jobs.claim(jobId)) {
            return;
        }
        try {
            if (job.getEntryId() == null) {
                throw new IllegalStateException("Projection job has no entry");
            }
            KnowledgeEntry entry = entries.findById(job.getEntryId())
                    .orElseThrow(() -> new IllegalStateException("Knowledge entry not found"));
            Topic topic = topics.findById(job.getTopicId())
                    .orElseThrow(() -> new IllegalStateException("知识主题不存在"));
            writer.appendKnowledgeEntry(topic.getSlug(), entry.getId(), render(entry));
            jobs.markCompleted(jobId);
        } catch (Exception error) {
            jobs.markFailed(jobId, errorSummary(error));
            log.warn("知识投影失败: jobId={}, errorType={}",
                    jobId, error.getClass().getSimpleName());
        }
    }

    private String render(KnowledgeEntry entry) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("<!-- knowledge-entry:").append(entry.getId()).append(" -->\n");
        markdown.append("## ").append(label(entry.getEntryType())).append("\n\n");
        if (entry.getQuestionSnapshot() != null && !entry.getQuestionSnapshot().trim().isEmpty()) {
            markdown.append("**问题：** ").append(entry.getQuestionSnapshot().trim()).append("\n\n");
        }
        markdown.append(entry.getContentMarkdown() == null ? "" : entry.getContentMarkdown().trim());
        markdown.append("\n\n> 置信度：").append(entry.getConfidence()).append("\n");
        return markdown.toString();
    }

    private String label(String entryType) {
        if ("REVIEW".equals(entryType)) {
            return "主题复习";
        }
        if ("CONCLUSION".equals(entryType)) {
            return "研究结论";
        }
        if ("INSIGHT".equals(entryType)) {
            return "知识洞察";
        }
        return "学习成果";
    }

    private String errorSummary(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        message = message.replace('\n', ' ').replace('\r', ' ');
        String summary = error.getClass().getSimpleName() + ": " + message;
        return summary.length() <= 500 ? summary : summary.substring(0, 500);
    }
}

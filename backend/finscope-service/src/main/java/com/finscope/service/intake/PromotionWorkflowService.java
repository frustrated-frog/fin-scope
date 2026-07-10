package com.finscope.service.intake;

import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.intake.PromoteIntakeCandidateResponse;
import com.finscope.domain.research.EventCluster;
import com.finscope.service.research.EventClusterService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class PromotionWorkflowService {
    @Resource
    private EventClusterService eventClusterService;
    @Resource
    private EvidenceItemRepository evidenceItemRepository;
    @Resource
    private LearningTaskRepository learningTaskRepository;
    @Resource
    private ContentIdeaRepository contentIdeaRepository;

    public PromoteIntakeCandidateResponse attach(Long candidateId, String humanStatus, Article article) {
        PromoteIntakeCandidateResponse response = baseResponse(candidateId, humanStatus, article);
        if (article == null || article.getId() == null) {
            markFailed(response, "文章不存在，无法生成研究工作包");
            return response;
        }
        try {
            EventCluster event = eventClusterService.attachArticle(article);
            int evidenceCount = evidenceItemRepository.countByEventId(event.getId());
            int learningTaskCount = learningTaskRepository.countByEventId(event.getId());
            int contentIdeaCount = contentIdeaRepository.countByEventId(event.getId());

            response.setEventId(event.getId());
            response.setEventTitle(event.getCanonicalTitle());
            response.setEvidenceCount(evidenceCount);
            response.setLearningTaskCount(learningTaskCount);
            response.setContentIdeaCount(contentIdeaCount);
            response.setWorkflowStatus("SUCCESS");
            response.setWorkflowSummary("研究工作包已生成：事件 #" + event.getId() + "，"
                    + firstNonBlank(event.getCanonicalTitle(), "未命名事件") + "，证据 " + evidenceCount
                    + " 条，学习任务 " + learningTaskCount + " 个，选题 " + contentIdeaCount + " 个");
            return response;
        } catch (Exception ex) {
            markFailed(response, ex.getMessage());
            return response;
        }
    }

    private PromoteIntakeCandidateResponse baseResponse(Long candidateId, String humanStatus, Article article) {
        PromoteIntakeCandidateResponse response = new PromoteIntakeCandidateResponse();
        response.setCandidateId(candidateId);
        response.setStatus(humanStatus);
        response.setArticleId(article == null ? null : article.getId());
        response.setEvidenceCount(0);
        response.setLearningTaskCount(0);
        response.setContentIdeaCount(0);
        return response;
    }

    private void markFailed(PromoteIntakeCandidateResponse response, String message) {
        String reason = firstNonBlank(message, "未知错误");
        response.setWorkflowStatus("FAILED");
        response.setWorkflowErrorMessage(reason);
        response.setWorkflowSummary("研究工作包生成失败：" + reason);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}

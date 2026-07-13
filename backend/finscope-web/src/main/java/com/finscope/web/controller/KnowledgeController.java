package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.knowledge.KnowledgeEntry;
import com.finscope.domain.knowledge.KnowledgeOverview;
import com.finscope.domain.knowledge.KnowledgeReviewResult;
import com.finscope.domain.knowledge.KnowledgeTopicWorkspace;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.topic.Topic;
import com.finscope.service.knowledge.KnowledgeLearningService;
import com.finscope.service.knowledge.KnowledgeContextService;
import com.finscope.service.knowledge.KnowledgeOverviewService;
import com.finscope.service.knowledge.KnowledgeReviewService;
import com.finscope.service.knowledge.KnowledgeTopicService;
import com.finscope.web.request.knowledge.AcceptKnowledgeTaskRequest;
import com.finscope.web.request.knowledge.DismissKnowledgeTaskRequest;
import com.finscope.web.request.knowledge.KnowledgeEntryRequest;
import com.finscope.web.request.knowledge.KnowledgeRevisionRequest;
import com.finscope.web.request.knowledge.KnowledgeReviewRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP boundary for the knowledge workbench.
 *
 * <p>The controller validates only transport-level requirements. Lifecycle,
 * revision and evidence rules remain in semantic command services.</p>
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeOverviewService overviewService;
    private final KnowledgeLearningService learningService;
    private final KnowledgeContextService contextService;
    private final KnowledgeTopicService topicService;
    private final KnowledgeReviewService reviewService;

    public KnowledgeController(KnowledgeOverviewService overviewService,
                               KnowledgeLearningService learningService,
                               KnowledgeContextService contextService,
                               KnowledgeTopicService topicService,
                               KnowledgeReviewService reviewService) {
        this.overviewService = overviewService;
        this.learningService = learningService;
        this.contextService = contextService;
        this.topicService = topicService;
        this.reviewService = reviewService;
    }

    @GetMapping("/overview")
    public KnowledgeOverview overview() {
        return overviewService.load();
    }

    @GetMapping("/topics")
    public PageResponse<Topic> topics(
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String mastery,
            @RequestParam(defaultValue = "false") boolean dueOnly,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return overviewService.topicsPage(
                lifecycle, mastery, dueOnly, query, page, size);
    }

    @GetMapping("/tasks")
    public PageResponse<LearningTask> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return overviewService.tasksPage(status, topicId, query, page, size);
    }

    @GetMapping("/reviews/due")
    public PageResponse<Topic> dueReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return overviewService.dueReviews(page, size);
    }

    @GetMapping("/tasks/{taskId}/evidence")
    public List<EvidenceItem> taskEvidence(@PathVariable long taskId) {
        return contextService.evidenceForTask(taskId);
    }

    @GetMapping("/topics/{topicId}")
    public KnowledgeTopicWorkspace topicWorkspace(@PathVariable long topicId) {
        return topicService.load(topicId);
    }

    @PostMapping("/topics/{topicId}/reviews")
    public KnowledgeReviewResult review(@PathVariable long topicId,
                                        @RequestBody KnowledgeReviewRequest request) {
        require(request.getConclusion(), "conclusion");
        require(request.getConfidence(), "confidence");
        require(request.getIntervalDays(), "intervalDays");
        require(request.getExpectedRevision(), "expectedRevision");
        return reviewService.review(topicId, request.getConclusion(), request.getConfidence(),
                request.getEvidenceIds(), request.getIntervalDays(), request.getExpectedRevision());
    }

    @PostMapping("/tasks/{taskId}/accept")
    public LearningTask accept(@PathVariable long taskId,
                               @RequestBody AcceptKnowledgeTaskRequest request) {
        require(request.getTopicId(), "topicId");
        require(request.getExpectedRevision(), "expectedRevision");
        return learningService.acceptSuggestion(
                taskId, request.getTopicId(), request.getExpectedRevision());
    }

    @PostMapping("/tasks/{taskId}/start")
    public LearningTask start(@PathVariable long taskId,
                              @RequestBody KnowledgeRevisionRequest request) {
        require(request.getExpectedRevision(), "expectedRevision");
        return learningService.startTask(taskId, request.getExpectedRevision());
    }

    @PutMapping("/tasks/{taskId}/draft")
    public KnowledgeEntry saveDraft(@PathVariable long taskId,
                                    @RequestBody KnowledgeEntryRequest request) {
        validateEntryRequest(request);
        return learningService.saveDraft(taskId, request.getTopicId(),
                request.getMarkdown(), request.getConfidence(),
                request.getEvidenceIds(), request.getExpectedRevision());
    }

    @PostMapping("/tasks/{taskId}/complete")
    public KnowledgeEntry complete(@PathVariable long taskId,
                                   @RequestBody KnowledgeEntryRequest request) {
        validateEntryRequest(request);
        return learningService.completeTask(taskId, request.getTopicId(),
                request.getMarkdown(), request.getConfidence(),
                request.getEvidenceIds(), request.getExpectedRevision());
    }

    @PostMapping("/tasks/{taskId}/dismiss")
    public LearningTask dismiss(@PathVariable long taskId,
                                @RequestBody DismissKnowledgeTaskRequest request) {
        require(request.getExpectedRevision(), "expectedRevision");
        return learningService.dismissTask(
                taskId, request.getReason(), request.getExpectedRevision());
    }

    private void validateEntryRequest(KnowledgeEntryRequest request) {
        require(request.getTopicId(), "topicId");
        require(request.getMarkdown(), "markdown");
        require(request.getConfidence(), "confidence");
        require(request.getExpectedRevision(), "expectedRevision");
    }

    private void require(Object value, String field) {
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "缺少必填字段: " + field);
        }
    }
}

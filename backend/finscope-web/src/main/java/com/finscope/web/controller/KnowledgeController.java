package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
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
import org.springframework.http.ResponseEntity;

import java.util.List;
import com.finscope.common.exception.BizErrorCode;

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

    /**
     * 查询知识工作台总览。
     *
     * @return 知识工作台总览，包含主题、任务和复盘等汇总信息。
     */
    @GetMapping("/overview")
    public ApiResponse<KnowledgeOverview> overview() {
        return ApiResponses.success(overviewService.load());
    }

    /**
     * 分页查询主题列表。
     *
     * @param lifecycle 生命周期状态过滤条件，可为空。
     * @param mastery 掌握度过滤条件，可为空。
     * @param dueOnly 是否仅返回到期复习的主题。
     * @param query 关键词过滤条件，可为空。
     * @param page 页码，从 0 开始。
     * @param size 每页条数。
     * @return 分页后的主题结果。
     */
    @GetMapping("/topics")
    public ApiResponse<PageResponse<Topic>> topics(
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String mastery,
            @RequestParam(defaultValue = "false") boolean dueOnly,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.success(overviewService.topicsPage(
                lifecycle, mastery, dueOnly, query, page, size));
    }

    /**
     * 分页查询学习任务列表。
     *
     * @param status 任务状态过滤条件，可为空。
     * @param topicId 主题 ID 过滤条件，可为空。
     * @param query 关键词过滤条件，可为空。
     * @param page 页码，从 0 开始。
     * @param size 每页条数。
     * @return 分页后的学习任务结果。
     */
    @GetMapping("/tasks")
    public ApiResponse<PageResponse<LearningTask>> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.success(overviewService.tasksPage(status, topicId, query, page, size));
    }

    /**
     * 分页查询到期待复习的主题。
     *
     * @param page 页码，从 0 开始。
     * @param size 每页条数。
     * @return 分页后的到期复习主题结果。
     */
    @GetMapping("/reviews/due")
    public ApiResponse<PageResponse<Topic>> dueReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponses.success(overviewService.dueReviews(page, size));
    }

    /**
     * 查询学习任务的证据列表。
     *
     * @param taskId 学习任务 ID。
     * @return 该任务关联的证据条目列表。
     */
    @GetMapping("/tasks/{taskId}/evidence")
    public ApiResponse<List<EvidenceItem>> taskEvidence(@PathVariable long taskId) {
        return ApiResponses.success(contextService.evidenceForTask(taskId));
    }

    /**
     * 查询主题工作台详情。
     *
     * @param topicId 主题 ID。
     * @return 主题工作台详情，包含知识条目、任务和证据。
     */
    @GetMapping("/topics/{topicId}")
    public ApiResponse<KnowledgeTopicWorkspace> topicWorkspace(@PathVariable long topicId) {
        return ApiResponses.success(topicService.load(topicId));
    }

    /**
     * 创建主题。
     *
     * @param request 主题实体，包含名称和描述。
     * @return 新创建的主题。
     */
    @PostMapping("/topics")
    public ApiResponse<Topic> createTopic(@RequestBody Topic request) {
        require(request, "request");
        return ApiResponses.success(topicService.create(request.getName(), request.getDescription()));
    }

    /**
     * 提交主题复盘。
     *
     * @param topicId 主题 ID。
     * @param request 复盘请求，包含结论、置信度、证据 ID、复习间隔天数和期望版本号。
     * @return 主题复盘结果。
     */
    @PostMapping("/topics/{topicId}/reviews")
    public ApiResponse<KnowledgeReviewResult> review(@PathVariable long topicId,
                                        @RequestBody KnowledgeReviewRequest request) {
        require(request, "request");
        require(request.getConclusion(), "conclusion");
        require(request.getConfidence(), "confidence");
        require(request.getIntervalDays(), "intervalDays");
        require(request.getExpectedRevision(), "expectedRevision");
        return ApiResponses.success(reviewService.review(topicId, request.getConclusion(), request.getConfidence(),
                request.getEvidenceIds(), request.getIntervalDays(), request.getExpectedRevision()));
    }

    /**
     * 采纳学习建议任务。
     *
     * @param taskId 学习任务 ID。
     * @param request 采纳请求，包含目标主题 ID 和期望版本号。
     * @return 采纳后的学习任务。
     */
    @PostMapping("/tasks/{taskId}/accept")
    public ApiResponse<LearningTask> accept(@PathVariable long taskId,
                               @RequestBody AcceptKnowledgeTaskRequest request) {
        require(request, "request");
        require(request.getTopicId(), "topicId");
        require(request.getExpectedRevision(), "expectedRevision");
        return ApiResponses.success(learningService.acceptSuggestion(
                taskId, request.getTopicId(), request.getExpectedRevision()));
    }

    /**
     * 开始学习任务。
     *
     * @param taskId 学习任务 ID。
     * @param request 版本请求，包含期望版本号。
     * @return 已开始的学习任务。
     */
    @PostMapping("/tasks/{taskId}/start")
    public ApiResponse<LearningTask> start(@PathVariable long taskId,
                              @RequestBody KnowledgeRevisionRequest request) {
        require(request, "request");
        require(request.getExpectedRevision(), "expectedRevision");
        return ApiResponses.success(learningService.startTask(taskId, request.getExpectedRevision()));
    }

    /**
     * 保存学习任务草稿。
     *
     * @param taskId 学习任务 ID。
     * @param request 知识条目请求，包含主题 ID、正文、置信度、证据 ID 及任务与条目的期望版本号。
     * @return 已保存的知识条目草稿。
     */
    @PutMapping("/tasks/{taskId}/draft")
    public ApiResponse<KnowledgeEntry> saveDraft(@PathVariable long taskId,
                                    @RequestBody KnowledgeEntryRequest request) {
        validateEntryRequest(request);
        return ApiResponses.success(learningService.saveDraft(taskId, request.getTopicId(),
                request.getMarkdown(), request.getConfidence(),
                request.getEvidenceIds(), request.getExpectedTaskRevision(),
                request.getExpectedEntryRevision()));
    }

    /**
     * 查询学习任务草稿。
     *
     * @param taskId 学习任务 ID。
     * @return 存在草稿时返回草稿内容；否则返回 204 No Content。
     */
    @GetMapping("/tasks/{taskId}/draft")
    public ResponseEntity<ApiResponse<KnowledgeEntry>> draft(@PathVariable long taskId) {
        return learningService.findDraft(taskId)
                .map(value -> ResponseEntity.ok(ApiResponses.success(value)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 完成学习任务并生成知识条目。
     *
     * @param taskId 学习任务 ID。
     * @param request 知识条目请求，包含主题 ID、正文、置信度、证据 ID 及任务与条目的期望版本号。
     * @return 完成任务后生成的知识条目。
     */
    @PostMapping("/tasks/{taskId}/complete")
    public ApiResponse<KnowledgeEntry> complete(@PathVariable long taskId,
                                   @RequestBody KnowledgeEntryRequest request) {
        validateEntryRequest(request);
        return ApiResponses.success(learningService.completeTask(taskId, request.getTopicId(),
                request.getMarkdown(), request.getConfidence(),
                request.getEvidenceIds(), request.getExpectedTaskRevision(),
                request.getExpectedEntryRevision()));
    }

    /**
     * 忽略学习任务。
     *
     * @param taskId 学习任务 ID。
     * @param request 忽略请求，包含忽略原因和期望版本号。
     * @return 已忽略的学习任务。
     */
    @PostMapping("/tasks/{taskId}/dismiss")
    public ApiResponse<LearningTask> dismiss(@PathVariable long taskId,
                                @RequestBody DismissKnowledgeTaskRequest request) {
        require(request, "request");
        require(request.getExpectedRevision(), "expectedRevision");
        return ApiResponses.success(learningService.dismissTask(
                taskId, request.getReason(), request.getExpectedRevision()));
    }

    private void validateEntryRequest(KnowledgeEntryRequest request) {
        require(request, "request");
        require(request.getTopicId(), "topicId");
        require(request.getMarkdown(), "markdown");
        require(request.getConfidence(), "confidence");
        require(request.getExpectedTaskRevision(), "expectedTaskRevision");
    }

    private void require(Object value, String field) {
        if (value == null) {
            throw new BusinessException(BizErrorCode.REQUIRED_FIELD_MISSING, field);
        }
    }
}

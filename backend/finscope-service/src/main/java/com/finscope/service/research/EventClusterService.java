package com.finscope.service.research;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.InfrastructureException;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.response.PageResponse;
import com.finscope.service.article.ArticleCategoryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;
import com.finscope.common.exception.BizErrorCode;

@Service
public class EventClusterService {

    private static final Set<String> VALID_EVENT_STATUSES = new LinkedHashSet<String>(Arrays.asList(
            ResearchEnums.EVENT_ACTIVE,
            ResearchEnums.EVENT_COOLING,
            ResearchEnums.EVENT_ARCHIVED));

    @Resource
    private EventClusterRepository eventClusterRepository;
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private EvidenceItemRepository evidenceItemRepository;
    @Resource
    private LearningTaskRepository learningTaskRepository;
    @Resource
    private ContentIdeaRepository contentIdeaRepository;
    @Resource
    private EventClassifier eventClassifier;
    @Resource
    private EvidenceService evidenceService;
    @Resource
    private LearningTaskService learningTaskService;
    @Resource
    private ContentIdeaService contentIdeaService;
    @Resource
    private ArticleCategoryPolicy articleCategoryPolicy;
    @Resource
    private ResearchRunOutputService researchRunOutputService;

    @Transactional
    public synchronized EventCluster attachArticle(Article article) {
        if (article == null || article.getId() == null) {
            throw new BusinessException(BizErrorCode.ARTICLE_MUST_BE_SAVED);
        }
        java.util.Optional<EventArticleLink> existingLink = eventClusterRepository.findByArticleId(article.getId());
        if (existingLink.isPresent()) {
            Long eventId = existingLink.get().getEventId();
            return eventClusterRepository.findById(eventId)
                    .orElseThrow(() -> new InfrastructureException(
                            ErrorCode.DATA_INTEGRITY_ERROR, "文章关联的事件不存在：" + eventId));
        }

        EventClassifier.EventSignature signature = eventClassifier.signature(article);
        List<EventCluster> candidates = eventClusterRepository.findRecentMergeableByTheme(signature.getThemeCode(), 80);
        EventClassifier.MatchDecision decision = eventClassifier.decide(article, signature, candidates);
        EventCluster event = decision.getEvent() == null
                ? createEvent(article, signature)
                : updateEvent(article, decision);

        EventArticleLink link = new EventArticleLink();
        link.setEventId(event.getId());
        link.setArticleId(article.getId());
        link.setRelationType(ResearchEnums.NOVELTY_NEW.equals(decision.getNoveltyType())
                ? ResearchEnums.RELATION_PRIMARY : ResearchEnums.RELATION_SUPPORTING);
        link.setMatchScore(decision.getEvent() == null ? 1.0 : decision.getMatchScore());
        link.setNoveltyType(decision.getNoveltyType());
        link.setNoveltyReason(decision.getNoveltyReason());
        eventClusterRepository.linkArticle(link);
        java.util.Optional<EventArticleLink> persistedLink = eventClusterRepository.findByArticleId(article.getId());
        if (persistedLink.isPresent() && !event.getId().equals(persistedLink.get().getEventId())) {
            return detail(persistedLink.get().getEventId());
        }

        event.setArticleCount(eventClusterRepository.countLinks(event.getId()));
        if (articleCategoryPolicy.isEvidenceEligible(article.getCategory())) {
            event.setEvidenceCount(evidenceService.capture(event, article));
        }
        boolean meaningfulUpdate = ResearchEnums.NOVELTY_NEW.equals(decision.getNoveltyType())
                || ResearchEnums.NOVELTY_FOLLOW_UP.equals(decision.getNoveltyType());
        learningTaskService.generateIfAbsent(event, article, meaningfulUpdate);
        contentIdeaService.generateIfAbsent(event, article, meaningfulUpdate);
        eventClusterRepository.update(event);
        researchRunOutputService.recordCurrentRun(ResearchRunOutputService.EVENT, event.getId());
        return eventClusterRepository.findById(event.getId()).orElse(event);
    }

    public List<EventCluster> list() {
        return eventClusterRepository.findAll();
    }

    public List<EventCluster> list(String themeCode, String status, String noveltyState, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(BizErrorCode.DATE_RANGE_INVALID);
        }
        return eventClusterRepository.findAllFiltered(themeCode, status, noveltyState, dateFrom, dateTo);
    }

    public PageResponse<EventCluster> listPaged(String themeCode, String status, String noveltyState,
                                                 LocalDate dateFrom, LocalDate dateTo, int page, int pageSize) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(BizErrorCode.DATE_RANGE_INVALID);
        }
        return PageResponse.of(eventClusterRepository.findFilteredPage(themeCode, status, noveltyState, dateFrom, dateTo, page, pageSize),
                eventClusterRepository.countFiltered(themeCode, status, noveltyState, dateFrom, dateTo), page, pageSize);
    }

    public EventCluster detail(Long id) {
        return eventClusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("事件不存在：" + id));
    }

    public List<EventArticleLink> articles(Long eventId) {
        detail(eventId);
        return eventClusterRepository.findLinksByEventId(eventId);
    }

    public EventCluster updateStatus(Long eventId, String status) {
        detail(eventId);
        String normalizedStatus = normalizeStatus(status);
        if (!VALID_EVENT_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(BizErrorCode.EVENT_STATE_UNSUPPORTED, status);
        }
        return eventClusterRepository.updateStatus(eventId, normalizedStatus);
    }

    @Transactional
    public EventCluster merge(Long sourceEventId, Long targetEventId) {
        if (sourceEventId == null || targetEventId == null) {
            throw new BusinessException(BizErrorCode.EVENT_MERGE_IDS_REQUIRED);
        }
        if (sourceEventId.equals(targetEventId)) {
            throw new BusinessException(BizErrorCode.EVENT_MERGE_TO_SELF);
        }
        EventCluster source = detail(sourceEventId);
        EventCluster target = detail(targetEventId);
        ensureGovernable(source, "源事件");
        ensureGovernable(target, "目标事件");

        eventClusterRepository.moveLinks(source.getId(), target.getId());
        evidenceItemRepository.moveByEventId(source.getId(), target.getId());
        learningTaskRepository.moveByEventId(source.getId(), target.getId());
        contentIdeaRepository.moveByEventId(source.getId(), target.getId());
        eventClusterRepository.refreshCounts(Arrays.asList(source.getId(), target.getId()));

        EventCluster archivedSource = detail(source.getId());
        archivedSource.setStatus(ResearchEnums.EVENT_ARCHIVED);
        eventClusterRepository.update(archivedSource);
        return detail(target.getId());
    }

    @Transactional
    public EventCluster moveArticle(Long sourceEventId,
                                    Long articleId,
                                    Long targetEventId,
                                    Boolean createNewEvent) {
        EventCluster source = detail(sourceEventId);
        ensureGovernable(source, "源事件");
        EventArticleLink link = eventClusterRepository.findLink(source.getId(), articleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "事件 " + sourceEventId + " 中不存在文章关联：" + articleId));
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在：" + articleId));

        boolean shouldCreateNewEvent = Boolean.TRUE.equals(createNewEvent);
        if (shouldCreateNewEvent == (targetEventId != null)) {
            throw new BusinessException(BizErrorCode.EVENT_MERGE_TARGET_OR_NEW_REQUIRED);
        }

        EventCluster target = shouldCreateNewEvent ? createEventFromGovernance(article) : detail(targetEventId);
        ensureGovernable(target, "目标事件");
        if (source.getId().equals(target.getId())) {
            throw new BusinessException(BizErrorCode.ARTICLE_MOVE_TO_SOURCE_EVENT);
        }

        int moved = eventClusterRepository.moveArticleLink(source.getId(), articleId, target.getId(),
                governanceReason(link.getNoveltyReason()));
        if (moved == 0) {
            throw new BusinessException(BizErrorCode.ARTICLE_LINK_UPDATED, articleId);
        }
        evidenceItemRepository.moveByEventIdAndArticleId(source.getId(), articleId, target.getId());
        eventClusterRepository.refreshCounts(Arrays.asList(source.getId(), target.getId()));
        eventClusterRepository.archiveIfEmpty(Arrays.asList(source.getId()));
        return detail(target.getId());
    }

    private EventCluster createEvent(Article article, EventClassifier.EventSignature signature) {
        LocalDateTime seenAt = article.getFetchedAt() == null ? LocalDateTime.now() : article.getFetchedAt();
        EventCluster event = new EventCluster();
        event.setCanonicalTitle(StringUtils.firstNonBlank(article.getTitle(), "未命名事件"));
        event.setCanonicalEventKey(signature.getCanonicalEventKey());
        event.setThemeCode(signature.getThemeCode());
        event.setSummary(StringUtils.firstNonBlank(article.getSummary(), article.getBody(), article.getTitle()));
        event.setStatus(ResearchEnums.EVENT_ACTIVE);
        event.setFirstSeenAt(seenAt);
        event.setLastSeenAt(seenAt);
        event.setLastMeaningfulUpdateAt(seenAt);
        event.setImportanceScore(signature.getImportanceScore());
        event.setNoveltyState(ResearchEnums.NOVELTY_NEW);
        event.setEvidenceCount(0);
        event.setArticleCount(0);
        return eventClusterRepository.save(event);
    }

    private EventCluster createEventFromGovernance(Article article) {
        EventClassifier.EventSignature signature = eventClassifier.signature(article);
        return createEvent(article, signature);
    }

    private EventCluster updateEvent(Article article, EventClassifier.MatchDecision decision) {
        EventCluster event = decision.getEvent();
        LocalDateTime seenAt = article.getFetchedAt() == null ? LocalDateTime.now() : article.getFetchedAt();
        event.setLastSeenAt(seenAt);
        if (ResearchEnums.NOVELTY_FOLLOW_UP.equals(decision.getNoveltyType())
                || ResearchEnums.NOVELTY_NEW.equals(decision.getNoveltyType())) {
            event.setLastMeaningfulUpdateAt(seenAt);
            event.setNoveltyState(decision.getNoveltyType());
            if (article.getSummary() != null && !article.getSummary().isEmpty()) {
                event.setSummary(article.getSummary());
            }
        } else if (!ResearchEnums.NOVELTY_FOLLOW_UP.equals(event.getNoveltyState())) {
            event.setNoveltyState(decision.getNoveltyType());
        }
        return eventClusterRepository.update(event);
    }

    private String normalizeStatus(String status) {
        return StringUtils.firstNonBlank(status, "").trim().toUpperCase(Locale.ROOT);
    }

    private void ensureGovernable(EventCluster event, String role) {
        if (ResearchEnums.EVENT_ARCHIVED.equals(event.getStatus())) {
            throw new BusinessException(BizErrorCode.ARCHIVED_EVENT_ACTION_FORBIDDEN, event.getId() + "（" + role + "）");
        }
    }

    private String governanceReason(String originalReason) {
        String marker = "人工治理调整";
        if (StringUtils.isBlank(originalReason)) {
            return marker;
        }
        if (originalReason.contains(marker)) {
            return originalReason;
        }
        return originalReason + "；" + marker;
    }
}

package com.finscope.service.research;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
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
import java.util.stream.Collectors;

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

    public EventCluster attachArticle(Article article) {
        if (article == null || article.getId() == null) {
            throw new IllegalArgumentException("Article must be saved before attaching to event memory");
        }
        if (eventClusterRepository.findByArticleId(article.getId()).isPresent()) {
            Long eventId = eventClusterRepository.findByArticleId(article.getId()).get().getEventId();
            return eventClusterRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Event link points to missing event: " + eventId));
        }

        EventClassifier.EventSignature signature = eventClassifier.signature(article);
        List<EventCluster> candidates = eventClusterRepository.findRecentByTheme(signature.getThemeCode(), 80);
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

        event.setArticleCount(eventClusterRepository.countLinks(event.getId()));
        if (articleCategoryPolicy.isEvidenceEligible(article.getCategory())) {
            event.setEvidenceCount(evidenceService.capture(event, article));
        }
        boolean meaningfulUpdate = ResearchEnums.NOVELTY_NEW.equals(decision.getNoveltyType())
                || ResearchEnums.NOVELTY_FOLLOW_UP.equals(decision.getNoveltyType());
        learningTaskService.generateIfAbsent(event, article, meaningfulUpdate);
        contentIdeaService.generateIfAbsent(event, article, meaningfulUpdate);
        eventClusterRepository.update(event);
        return eventClusterRepository.findById(event.getId()).orElse(event);
    }

    public List<EventCluster> list() {
        return eventClusterRepository.findAll();
    }

    public List<EventCluster> list(String themeCode,
                                   String status,
                                   String noveltyState,
                                   LocalDate dateFrom,
                                   LocalDate dateTo) {
        return eventClusterRepository.findAll().stream()
                .filter(event -> matches(event.getThemeCode(), themeCode))
                .filter(event -> matches(event.getStatus(), status))
                .filter(event -> matches(event.getNoveltyState(), noveltyState))
                .filter(event -> withinDateRange(event, dateFrom, dateTo))
                .collect(Collectors.toList());
    }

    public EventCluster detail(Long id) {
        return eventClusterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
    }

    public List<EventArticleLink> articles(Long eventId) {
        detail(eventId);
        return eventClusterRepository.findLinksByEventId(eventId);
    }

    public EventCluster updateStatus(Long eventId, String status) {
        detail(eventId);
        String normalizedStatus = normalizeStatus(status);
        if (!VALID_EVENT_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported event status: " + status);
        }
        return eventClusterRepository.updateStatus(eventId, normalizedStatus);
    }

    @Transactional
    public EventCluster merge(Long sourceEventId, Long targetEventId) {
        if (sourceEventId == null || targetEventId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "sourceEventId and targetEventId are required");
        }
        if (sourceEventId.equals(targetEventId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Cannot merge an event into itself");
        }
        EventCluster source = detail(sourceEventId);
        EventCluster target = detail(targetEventId);

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
        EventArticleLink link = eventClusterRepository.findLink(source.getId(), articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Article link not found for event " + sourceEventId + " and article " + articleId));
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Article not found: " + articleId));

        boolean shouldCreateNewEvent = Boolean.TRUE.equals(createNewEvent);
        if (shouldCreateNewEvent == (targetEventId != null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Specify either targetEventId or createNewEvent=true");
        }

        EventCluster target = shouldCreateNewEvent ? createEventFromGovernance(article) : detail(targetEventId);
        if (source.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Cannot move article to the same event");
        }

        eventClusterRepository.moveArticleLink(source.getId(), articleId, target.getId(),
                governanceReason(link.getNoveltyReason()));
        evidenceItemRepository.moveByEventIdAndArticleId(source.getId(), articleId, target.getId());
        eventClusterRepository.refreshCounts(Arrays.asList(source.getId(), target.getId()));
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

    private boolean matches(String actual, String expected) {
        if (StringUtils.isBlank(expected)) {
            return true;
        }
        return StringUtils.firstNonBlank(actual, "").equalsIgnoreCase(expected.trim());
    }

    private boolean withinDateRange(EventCluster event, LocalDate dateFrom, LocalDate dateTo) {
        LocalDate seenDate = event.getLastSeenAt() == null ? null : event.getLastSeenAt().toLocalDate();
        if (seenDate == null) {
            return dateFrom == null && dateTo == null;
        }
        if (dateFrom != null && seenDate.isBefore(dateFrom)) {
            return false;
        }
        return dateTo == null || !seenDate.isAfter(dateTo);
    }

    private String normalizeStatus(String status) {
        return StringUtils.firstNonBlank(status, "").trim().toUpperCase(Locale.ROOT);
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

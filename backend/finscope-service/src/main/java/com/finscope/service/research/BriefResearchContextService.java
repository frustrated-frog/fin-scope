package com.finscope.service.research;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.BriefResearchContext;
import com.finscope.domain.research.ContentIdea;
import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.LearningTask;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BriefResearchContextService {
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private EventClusterRepository eventClusterRepository;
    @Resource
    private EvidenceService evidenceService;
    @Resource
    private LearningTaskService learningTaskService;
    @Resource
    private ContentIdeaService contentIdeaService;

    public BriefResearchContext build(LocalDate date) {
        BriefResearchContext context = new BriefResearchContext();
        context.setBriefDate(date);

        List<Article> articles = articleRepository.findByDate(date);
        Set<Long> eventIds = new LinkedHashSet<Long>();
        for (Article article : articles) {
            EventArticleLink link = eventClusterRepository.findByArticleId(article.getId()).orElse(null);
            if (link != null && link.getEventId() != null) {
                eventIds.add(link.getEventId());
            }
        }

        List<EventCluster> events = new ArrayList<EventCluster>();
        List<com.finscope.domain.research.EvidenceItem> evidenceItems = new ArrayList<com.finscope.domain.research.EvidenceItem>();
        List<LearningTask> learningTasks = new ArrayList<LearningTask>();
        List<ContentIdea> contentIdeas = new ArrayList<ContentIdea>();
        for (Long eventId : eventIds) {
            EventCluster event = eventClusterRepository.findById(eventId).orElse(null);
            if (event == null) {
                continue;
            }
            events.add(event);
            evidenceItems.addAll(evidenceService.listByEventId(eventId));
            learningTasks.addAll(learningTaskService.listByEventId(eventId));
            contentIdeas.addAll(contentIdeaService.listByEventId(eventId));
        }

        events.sort(Comparator.comparing(BriefResearchContextService::eventImportance).reversed()
                .thenComparing(EventCluster::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
        evidenceItems.sort(Comparator.comparing(BriefResearchContextService::evidenceConfidence).reversed()
                .thenComparing(item -> nullSafe(item.getClaim())));
        learningTasks.sort(Comparator.comparing((LearningTask task) -> nullSafe(task.getThemeCode()))
                .thenComparing(task -> nullSafe(task.getQuestion())));
        contentIdeas.sort(Comparator.comparing(BriefResearchContextService::ideaScore).reversed()
                .thenComparing(idea -> nullSafe(idea.getTitle())));

        context.setEvents(events);
        context.setEvidenceItems(evidenceItems);
        context.setLearningTasks(learningTasks);
        context.setContentIdeas(contentIdeas);
        return context;
    }

    private static int eventImportance(EventCluster event) {
        return event == null || event.getImportanceScore() == null ? 0 : event.getImportanceScore();
    }

    private static int evidenceConfidence(com.finscope.domain.research.EvidenceItem evidenceItem) {
        return evidenceItem == null || evidenceItem.getConfidence() == null ? 0 : evidenceItem.getConfidence();
    }

    private static int ideaScore(ContentIdea contentIdea) {
        return contentIdea == null || contentIdea.getScore() == null ? 0 : contentIdea.getScore();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

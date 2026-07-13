package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.SourceProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RunScopedResearchContext {
    private final ResearchRun run;
    private final ResearchThesis thesis;
    private final List<SourceProfile> sources;
    private final List<Article> articles;
    private final List<EventCluster> events;
    private final List<EvidenceItem> evidenceItems;

    RunScopedResearchContext(ResearchRun run, ResearchThesis thesis, List<SourceProfile> sources,
                             List<Article> articles, List<EventCluster> events, List<EvidenceItem> evidenceItems) {
        this.run = run;
        this.thesis = thesis;
        this.sources = immutable(sources);
        this.articles = immutable(articles);
        this.events = immutable(events);
        this.evidenceItems = immutable(evidenceItems);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public ResearchRun getRun() { return run; }
    public ResearchThesis getThesis() { return thesis; }
    public List<SourceProfile> getSources() { return sources; }
    public List<Article> getArticles() { return articles; }
    public List<EventCluster> getEvents() { return events; }
    public List<EvidenceItem> getEvidenceItems() { return evidenceItems; }
    public List<Long> getArticleIds() { return articles.stream().map(Article::getId).collect(Collectors.toList()); }
    public List<Long> getEventIds() { return events.stream().map(EventCluster::getId).collect(Collectors.toList()); }
    public List<Long> getEvidenceIds() { return evidenceItems.stream().map(EvidenceItem::getId).collect(Collectors.toList()); }
}

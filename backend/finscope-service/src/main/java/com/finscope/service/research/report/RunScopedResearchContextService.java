package com.finscope.service.research.report;

import com.finscope.common.exception.InfrastructureException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.ResearchRunOutputRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunOutput;
import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RunScopedResearchContextService {
    private static final int MAX_ARTICLES = 30;
    private static final int MAX_EVENTS = 30;
    private static final int MAX_EVIDENCE = 30;

    private final ResearchRunRepository runRepository;
    private final ResearchThesisRepository thesisRepository;
    private final ResearchRunOutputRepository outputRepository;
    private final ArticleRepository articleRepository;
    private final EventClusterRepository eventRepository;
    private final EvidenceItemRepository evidenceRepository;

    public RunScopedResearchContextService(ResearchRunRepository runRepository,
                                           ResearchThesisRepository thesisRepository,
                                           ResearchRunOutputRepository outputRepository,
                                           ArticleRepository articleRepository,
                                           EventClusterRepository eventRepository,
                                           EvidenceItemRepository evidenceRepository) {
        this.runRepository = runRepository;
        this.thesisRepository = thesisRepository;
        this.outputRepository = outputRepository;
        this.articleRepository = articleRepository;
        this.eventRepository = eventRepository;
        this.evidenceRepository = evidenceRepository;
    }

    public RunScopedResearchContext load(Long runId) {
        ResearchRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("研究运行不存在：" + runId));
        ResearchThesis thesis = run.getThesisId() == null ? null : thesisRepository.findById(run.getThesisId())
                .orElseThrow(() -> new InfrastructureException(
                        ErrorCode.DATA_INTEGRITY_ERROR, "研究运行关联的研究命题不存在：" + run.getThesisId()));
        List<Article> articles = new ArrayList<Article>();
        List<EventCluster> events = new ArrayList<EventCluster>();
        List<EvidenceItem> evidenceItems = new ArrayList<EvidenceItem>();
        Set<Long> articleIds = new LinkedHashSet<Long>();
        Set<Long> eventIds = new LinkedHashSet<Long>();
        Set<Long> evidenceIds = new LinkedHashSet<Long>();
        for (ResearchRunOutput output : outputRepository.findByRunId(runId)) {
            if ("ARTICLE".equals(output.getOutputType())) articleIds.add(output.getOutputId());
            else if ("EVENT".equals(output.getOutputType())) eventIds.add(output.getOutputId());
            else if ("EVIDENCE".equals(output.getOutputType())) evidenceIds.add(output.getOutputId());
        }
        for (Long id : tail(articleIds, MAX_ARTICLES)) {
            articleRepository.findById(id).ifPresent(articles::add);
        }
        for (Long id : tail(eventIds, MAX_EVENTS)) {
            eventRepository.findById(id).ifPresent(events::add);
        }
        for (Long id : tail(evidenceIds, MAX_EVIDENCE)) {
            evidenceRepository.findById(id).ifPresent(evidenceItems::add);
        }
        return new RunScopedResearchContext(run, thesis, runRepository.findSourcesByRunId(runId),
                articles, events, evidenceItems);
    }

    private List<Long> tail(Set<Long> values, int maximum) {
        List<Long> list = new ArrayList<Long>(values);
        return list.size() <= maximum ? list : new ArrayList<Long>(list.subList(list.size() - maximum, list.size()));
    }
}

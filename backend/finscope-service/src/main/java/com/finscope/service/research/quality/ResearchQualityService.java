package com.finscope.service.research.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.research.quality.ResearchQualityRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.*;
import com.finscope.domain.research.quality.*;
import com.finscope.service.research.report.ResearchEvidenceCard;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ResearchQualityService {
    @Resource
    private ResearchQualityRepository repository;
    @Resource
    private ResearchTaskContractBuilder contractBuilder;
    @Resource
    private ResearchQualityEngine engine;
    @Resource
    private ResearchFactCandidateAgent candidateAgent;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResearchTaskContract initialize(ResearchRun run, ResearchThesis thesis) {
        return repository.findContract(run.getId()).orElseGet(
                () -> repository.initializeContract(contractBuilder.build(run, thesis)));
    }

    public ResearchQualityAssessment assess(ResearchRun run, ResearchThesis thesis, List<Article> articles,
                                              List<ResearchSearchEvidence> search, List<ResearchEvidenceCard> selected,
                                              boolean modelCandidates) {
        ResearchTaskContract contract = initialize(run, thesis);
        List<ResearchEvidenceDocument> docs = documents(articles, search, selected);
        String fingerprint;
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (mapper.writeValueAsString(contract) + mapper.writeValueAsString(docs)
                            + "|model=" + modelCandidates).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("研究证据指纹计算失败", error);
        }
        Optional<ResearchQualityAssessment> existing = repository.find(run.getId(), fingerprint);
        if (existing.isPresent()) {
            return existing.get();
        }
        List<StructuredResearchFact> candidates = Collections.emptyList();
        if (modelCandidates && candidateAgent.isConfigured() && repository.reserveModelAttempt(run.getId())) {
            candidates = candidateAgent.propose(contract, docs);
        }
        ResearchQualityAssessment assessment = engine.assess(contract, docs, candidates);
        assessment.setFingerprint(fingerprint);
        return repository.save(assessment);
    }

    public Optional<ResearchQualityAssessment> findLatest(Long runId) {
        return repository.findLatest(runId);
    }

    private List<ResearchEvidenceDocument> documents(List<Article> articles, List<ResearchSearchEvidence> search,
                                                     List<ResearchEvidenceCard> selected) {
        Set<String> accepted = new HashSet<>();
        for (ResearchEvidenceCard card : selected) {
            accepted.add(text(card.getArticle().getUrl()));
        }
        Map<String, ResearchEvidenceDocument> docs = new LinkedHashMap<>();
        for (Article article : articles) {
            ResearchEvidenceDocument doc = new ResearchEvidenceDocument();
            doc.setReference("article:" + (article.getId() == null ? docs.size() : article.getId()));
            doc.setTitle(article.getTitle());
            doc.setUrl(article.getUrl());
            doc.setSource(article.getSourceName());
            doc.setText(body(article.getBody(), article.getSummary()));
            doc.setPublishedDate(article.getPublishedAt() == null ? null : article.getPublishedAt().toLocalDate().toString());
            doc.setSelected(accepted.contains(text(article.getUrl())));
            docs.put(text(article.getUrl()), doc);
        }
        for (ResearchSearchEvidence item : search) {
            ResearchEvidenceDocument doc = new ResearchEvidenceDocument();
            doc.setReference("search:" + item.getId());
            doc.setTitle(item.getTitle());
            doc.setUrl(item.getUrl());
            doc.setSource(item.getSourceDomain());
            doc.setText(body(item.getContent(), item.getSearchSnippet()));
            doc.setPublishedDate(item.getPublishedAt());
            doc.setSnippetOnly("SEARCH_SNIPPET".equals(item.getContentOrigin()));
            doc.setSelected(accepted.contains(text(item.getUrl())));
            docs.putIfAbsent(text(item.getUrl()), doc);
        }
        return new ArrayList<>(docs.values());
    }

    private String body(String body, String fallback) {
        return body == null || body.isBlank() ? text(fallback) : body;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}

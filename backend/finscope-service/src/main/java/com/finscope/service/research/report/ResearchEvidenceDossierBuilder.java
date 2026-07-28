package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchSourceIdentity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ResearchEvidenceDossierBuilder {
    static final int MAX_PROMPT_CHARACTERS = 14000;
    private static final int MAX_ITEMS = 12;
    private static final int MAX_EXCERPT_CHARACTERS = 900;

    public List<ResearchEvidenceDossier> build(List<ResearchEvidenceCard> cards) {
        List<ResearchEvidenceCard> sorted = new ArrayList<ResearchEvidenceCard>(cards);
        sorted.sort(Comparator.comparingInt(ResearchEvidenceCard::getRelevanceScore).reversed());
        List<ResearchEvidenceCard> selected = new ArrayList<ResearchEvidenceCard>();
        ResearchEvidenceCard counter = firstCounter(sorted);
        for (ResearchEvidenceCard card : sorted) {
            if (selected.size() >= MAX_ITEMS) break;
            if (card == counter) continue;
            selected.add(card);
        }
        if (counter != null) {
            if (selected.size() >= MAX_ITEMS) selected.remove(selected.size() - 1);
            selected.add(counter);
        }
        List<ResearchEvidenceDossier> result = new ArrayList<ResearchEvidenceDossier>();
        for (int index = 0; index < selected.size(); index++) {
            result.add(toDossier("E" + (index + 1), selected.get(index)));
        }
        return result;
    }

    int promptCharacters(List<ResearchEvidenceDossier> dossier) {
        int characters = 0;
        for (ResearchEvidenceDossier item : dossier) {
            characters += value(item.getTitle()).length() + value(item.getFactExcerpt()).length()
                    + value(item.getSourceName()).length() + value(item.getUrl()).length() + 120;
        }
        return characters;
    }

    private ResearchEvidenceDossier toDossier(String reference, ResearchEvidenceCard card) {
        Article article = card.getArticle();
        String excerpt = combine(card.getClaim(), article.getSummary(), article.getBody());
        return new ResearchEvidenceDossier(reference, article.getId(),
                card.getEvidenceItem() == null ? null : card.getEvidenceItem().getId(),
                ResearchSourceIdentity.resolve(article), value(article.getSourceName()), sourceTier(article),
                value(article.getTitle()), article.getPublishedAt(), value(article.getUrl()), excerpt,
                card.getStance(), card.getRelevanceScore());
    }

    private ResearchEvidenceCard firstCounter(List<ResearchEvidenceCard> cards) {
        for (ResearchEvidenceCard card : cards) if ("COUNTER".equals(card.getStance())) return card;
        return null;
    }

    private String combine(String claim, String summary, String body) {
        StringBuilder out = new StringBuilder();
        appendUnique(out, claim);
        appendUnique(out, summary);
        appendUnique(out, body);
        String compact = out.toString().replaceAll("\\s+", " ").trim();
        return compact.length() <= MAX_EXCERPT_CHARACTERS ? compact
                : compact.substring(0, MAX_EXCERPT_CHARACTERS - 1).trim() + "…";
    }

    private void appendUnique(StringBuilder out, String value) {
        String clean = value(value).replaceAll("\\s+", " ").trim();
        if (clean.isEmpty() || out.indexOf(clean) >= 0) return;
        if (out.length() > 0) out.append("；");
        out.append(clean);
    }

    private String sourceTier(Article article) {
        String source = value(article.getSourceName()).toLowerCase();
        if (source.contains("公告") || source.contains("交易所") || source.contains("政府")) return "PRIMARY";
        if (source.contains("协会") || source.contains("统计")) return "AUTHORITATIVE";
        if (source.contains("google news")) return "AGGREGATOR";
        return "MEDIA";
    }

    private String value(String value) { return value == null ? "" : value.trim(); }
}

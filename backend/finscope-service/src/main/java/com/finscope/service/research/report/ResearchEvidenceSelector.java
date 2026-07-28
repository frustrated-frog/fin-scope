package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ResearchSourceIdentity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ResearchEvidenceSelector {
    private static final int MAX_EVIDENCE = 15;
    private static final int MAX_PER_SOURCE = 5;
    private static final List<String> SUPPORT_TERMS = Arrays.asList(
            "增长", "上调", "上修", "扩张", "景气", "复苏", "持续", "增加", "突破", "创高", "投资计划",
            "资本开支周期", "迎来新一轮", "growth", "raise", "expand", "recovery");
    private static final List<String> COUNTER_TERMS = Arrays.asList(
            "下滑", "削减", "下调", "放缓", "回落", "风险", "衰退", "库存", "限制", "制裁", "decline", "cut", "slowdown", "risk", "sanction");

    public List<ResearchEvidenceCard> select(ResearchThesis thesis, List<Article> articles,
                                             List<EvidenceItem> evidenceItems) {
        if (thesis == null || articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, EvidenceItem> evidenceByArticle = evidenceByArticle(evidenceItems);
        List<String> subjectKeywords = subjectKeywords(thesis);
        List<ResearchEvidenceCard> candidates = new ArrayList<ResearchEvidenceCard>();
        Set<String> fingerprints = new HashSet<String>();
        for (Article article : articles) {
            if (article == null) {
                continue;
            }
            String fingerprint = normalize(firstNonBlank(article.getTitle(), article.getUrl()));
            if (!fingerprints.add(fingerprint)) {
                continue;
            }
            EvidenceItem evidence = evidenceByArticle.get(article.getId());
            int score = relevanceScore(article, evidence, subjectKeywords);
            if (score < 25) {
                continue;
            }
            String text = combinedText(article, evidence);
            String stance = stance(article, text);
            String claim = evidence != null && !isBlank(evidence.getClaim())
                    ? evidence.getClaim().trim() : firstNonBlank(article.getSummary(), article.getTitle());
            candidates.add(new ResearchEvidenceCard(article, evidence, stance, Math.min(score, 100), cleanClaim(claim)));
        }
        candidates.sort(Comparator.comparingInt(ResearchEvidenceCard::getRelevanceScore).reversed()
                .thenComparing(card -> card.getArticle().getId() == null ? Long.MAX_VALUE : card.getArticle().getId()));
        List<ResearchEvidenceCard> selected = new ArrayList<ResearchEvidenceCard>();
        Map<String, Integer> sourceCounts = new HashMap<String, Integer>();
        for (ResearchEvidenceCard candidate : candidates) {
            String source = ResearchSourceIdentity.resolve(candidate.getArticle());
            int count = sourceCounts.getOrDefault(source, 0);
            if (count >= MAX_PER_SOURCE) {
                continue;
            }
            selected.add(candidate);
            sourceCounts.put(source, count + 1);
            if (selected.size() == MAX_EVIDENCE) {
                break;
            }
        }
        return selected;
    }

    private Map<Long, EvidenceItem> evidenceByArticle(List<EvidenceItem> items) {
        Map<Long, EvidenceItem> result = new HashMap<Long, EvidenceItem>();
        if (items == null) {
            return result;
        }
        for (EvidenceItem item : items) {
            if (item.getArticleId() != null) {
                EvidenceItem current = result.get(item.getArticleId());
                if (current == null || value(item.getConfidence()) > value(current.getConfidence())) {
                    result.put(item.getArticleId(), item);
                }
            }
        }
        return result;
    }

    private List<String> subjectKeywords(ResearchThesis thesis) {
        Set<String> keywords = new LinkedHashSet<String>();
        add(keywords, thesis.getSubjectName());
        String subject = normalize(thesis.getSubjectName());
        if (subject.contains("半导体") || subject.contains("芯片")) {
            keywords.addAll(Arrays.asList("半导体", "芯片", "晶圆", "光刻", "存储", "设备订单", "资本开支",
                    "semiconductor", "chip", "wafer", "foundry", "asml", "tsmc", "smic"));
        }
        if (subject.contains("人工智能") || subject.equals("ai")) {
            keywords.addAll(Arrays.asList("人工智能", "大模型", "算力", "ai", "llm"));
        }
        return new ArrayList<String>(keywords);
    }

    private void add(Set<String> values, String value) {
        if (!isBlank(value)) {
            values.add(normalize(value));
        }
    }

    private int relevanceScore(Article article, EvidenceItem evidence, List<String> keywords) {
        String title = normalize(article.getTitle());
        String summary = normalize(article.getSummary());
        String body = normalize(compact(article.getBody(), 1500));
        String headline = title + " " + summary + " " + (evidence == null ? "" : normalize(evidence.getClaim()));
        if (title.contains("etf") || title.contains("基金") || title.contains("净值") || title.contains("卡位")
                || title.contains("最强赛道") || title.contains("涨停")) {
            return 0;
        }
        String subject = keywords.isEmpty() ? "" : keywords.get(0);
        if (subject.contains("设备") && !(headline.contains("设备") || headline.contains("光刻")
                || headline.contains("晶圆厂") || headline.contains("资本开支") || headline.contains("订单")
                || headline.contains("asml") || headline.contains("applied materials") || headline.contains("lam research"))) {
            return 0;
        }
        int score = 0;
        for (String keyword : keywords) {
            if (keyword.length() < 2) {
                continue;
            }
            if (title.contains(keyword)) score += 28;
            if (summary.contains(keyword)) score += 14;
            if (body.contains(keyword)) score += 5;
        }
        if (evidence != null) {
            score += 15;
            if (normalize(evidence.getClaim()).contains(normalize(firstNonBlank(keywords.isEmpty() ? null : keywords.get(0), "-")))) {
                score += 10;
            }
        }
        return score;
    }

    private String cleanClaim(String value) {
        String plain = value == null ? "" : value;
        int labelEnd = plain.indexOf("](");
        if (plain.startsWith("[") && labelEnd > 1) {
            plain = plain.substring(1, labelEnd);
        } else {
            plain = plain.replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1");
        }
        return compact(plain, 260);
    }

    private String combinedText(Article article, EvidenceItem evidence) {
        return normalize(firstNonBlank(article.getTitle(), "") + " " + firstNonBlank(article.getSummary(), "")
                + " " + (evidence == null ? "" : firstNonBlank(evidence.getClaim(), "")));
    }

    private String stance(Article article, String text) {
        int support = hits(text, SUPPORT_TERMS);
        int counter = hits(text, COUNTER_TERMS);
        if (counter > support) return "COUNTER";
        if (support > 0) return "SUPPORT";
        String source = normalize(article == null ? null : article.getSourceName());
        if (source.contains("反方证据搜索")) return "COUNTER";
        if (source.contains("支持证据搜索")) return "SUPPORT";
        return "NEUTRAL";
    }

    private int hits(String text, List<String> terms) {
        int count = 0;
        for (String term : terms) {
            if (text.contains(term)) count++;
        }
        return count;
    }

    private String compact(String value, int max) {
        if (value == null) return "";
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() <= max ? compacted : compacted.substring(0, max) + "…";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}

package com.finscope.service.research.report;

import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.majorevent.MajorEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reuses only an explicitly quoted, uniquely archived event predating the run. */
@Service
public class ResearchArchivedEventSource {
    private static final Pattern EVENT_QUESTION = Pattern.compile("^围绕[“\"](.+)[”\"]，哪些事实已经确认[，,]后续应重点观察什么[？?]$");
    @Resource
    private MajorEventRepository repository;

    public List<Article> articlesFor(RunScopedResearchContext context) {
        List<Article> result = new ArrayList<>(context.getArticles());
        if (context.getThesis() == null || context.getRun().getCreatedAt() == null) {
            return result;
        }
        String question = context.getThesis().getQuestion();
        Matcher match = EVENT_QUESTION.matcher(question == null ? "" : question);
        if (!match.matches()) {
            return result;
        }
        MajorEvent event = repository.findUniqueTitleBefore(match.group(1), context.getRun().getCreatedAt()).orElse(null);
        if (event == null || event.getSummary() == null || event.getSummary().isBlank()) {
            return result;
        }
        Article article = new Article();
        article.setTitle(event.getTitle());
        article.setSourceName(event.getSourceName());
        article.setUrl(event.getSourceUrl());
        article.setPublishedAt(event.getOccurredDate() == null ? null : event.getOccurredDate().atStartOfDay());
        String summary = (event.getOccurredDate() == null ? "" : "发生日期：" + event.getOccurredDate() + "。")
                + "已归档快讯记载：" + event.getSummary();
        article.setSummary(summary);
        article.setBody(summary);
        result.add(article);
        return result;
    }
}

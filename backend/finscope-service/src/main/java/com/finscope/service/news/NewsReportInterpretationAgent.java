package com.finscope.service.news;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.news.NewsReadingAnalysis;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.news.NewsReportInterpretation;
import com.finscope.domain.news.NewsReportVersion;
import com.finscope.rpc.llm.NewsAiLunaClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class NewsReportInterpretationAgent {
    private static final int MAX_BODY_CHARS = 18_000;
    @Resource
    private NewsAiLunaClient llm;
    @Resource
    private ObjectMapper json;

    public NewsReportInterpretation.Result interpret(NewsReport report, NewsReportVersion version) throws Exception {
        String body = text(version.getContent());
        String excerpt = body.substring(0, Math.min(body.length(), MAX_BODY_CHARS));
        String input = json.writeValueAsString(Map.of(
                "title", text(version.getTitle()), "body", excerpt,
                "bodyTruncated", body.length() > MAX_BODY_CHARS, "source", text(report.getSourceName()),
                "publishedAt", text(report.getPublishedAt()), "contentVersion", version.getVersion()));
        var request = llm.prepare(NewsReadingGuidance.PROMPT, input,
                NewsReadingGuidance.MAX_OUTPUT_TOKENS, NewsReadingGuidance.SCHEMA);
        var reply = llm.complete(request);
        if (!reply.isCompleted() || reply.isRefused()) {
            throw new IllegalStateException("模型未返回完整的新闻解读");
        }
        NewsReadingAnalysis analysis = json.readerFor(NewsReadingAnalysis.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(reply.getText());
        var reading = NewsReadingGuidance.reading(analysis);
        var result = new NewsReportInterpretation.Result();
        result.setFormatVersion(NewsReportInterpretation.CURRENT_FORMAT);
        result.setReading(reading);
        result.setFacts(List.of(reading.lead()));
        result.setWhyItMatters(NewsReadingGuidance.whyItMatters(analysis, version.getTitle(), excerpt));
        result.setUnknowns(List.of());
        result.setNextChecks(List.of());
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}

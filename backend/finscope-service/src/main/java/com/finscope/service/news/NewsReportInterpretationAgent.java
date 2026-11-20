package com.finscope.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.news.NewsReportInterpretation;
import com.finscope.domain.news.NewsReportVersion;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class NewsReportInterpretationAgent {
    private static final int MAX_BODY_CHARS = 18_000;
    private static final int TIMEOUT_MS = 55_000;
    private static final int MAX_OUTPUT_TOKENS = 1_600;
    private static final String PROMPT = NewsReadingGuidance.PROMPT + """
            只返回读者可直接阅读的中文纯文本。第一段用一句话写出实际变化。简单消息到此结束。
            读者需要背景时，再接一至三段短文，每段解释一个难点。用换行分段，不写标题、列表、JSON 或 Markdown。
            截断的材料只能支持片段内的结论。
            """;

    @Resource
    private LlmChatClient llm;
    @Resource
    private ObjectMapper json;

    public NewsReportInterpretation.Result interpret(NewsReport report, NewsReportVersion version) throws Exception {
        String body = text(version.getContent());
        String input = json.writeValueAsString(Map.of(
                "title", text(version.getTitle()), "body", body.substring(0, Math.min(body.length(), MAX_BODY_CHARS)),
                "bodyTruncated", body.length() > MAX_BODY_CHARS, "source", text(report.getSourceName()),
                "publishedAt", text(report.getPublishedAt()), "contentVersion", version.getVersion()));
        String raw = text(llm.complete(PROMPT, input, TIMEOUT_MS, MAX_OUTPUT_TOKENS)).strip();
        // 阅读结果本身是散文，直接按段落保存，不要求模型对引号、换行再做 JSON 转义。
        List<String> paragraphs = Arrays.stream(raw.split("\\R+"))
                .map(String::strip).filter(value -> !value.isEmpty()).toList();
        if (paragraphs.isEmpty() || paragraphs.size() > 4 || paragraphs.getFirst().length() > 500
                || paragraphs.getFirst().codePoints().noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)
                || raw.startsWith("{") || raw.startsWith("[") || raw.startsWith("```")
                || paragraphs.stream().anyMatch(paragraph -> paragraph.length() > 800)) {
            throw new IllegalStateException("新闻解读正文格式无效");
        }
        var result = new NewsReportInterpretation.Result();
        result.setFormatVersion(NewsReportInterpretation.CURRENT_FORMAT);
        result.setFacts(List.of(paragraphs.getFirst()));
        result.setWhyItMatters(String.join("\n\n", paragraphs.subList(1, paragraphs.size())));
        result.setUnknowns(List.of());
        result.setNextChecks(List.of());
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}

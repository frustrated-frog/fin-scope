package com.finscope.service.research.mission;

import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class ResearchSearchSourceFactory {
    private static final Pattern PROTOCOL_PREFIX = Pattern.compile("(?i)^\\s*[a-z][a-z0-9+.-]*://.*");

    public Source create(ResearchMissionTask task) {
        if (task == null || !"public_news_search".equals(task.getToolCode())) {
            throw new IllegalArgumentException("只有已校验的公开新闻搜索任务可以生成临时来源");
        }
        String query = task.getQueryText() == null ? "" : task.getQueryText().trim();
        if (query.isEmpty() || query.length() > 180 || PROTOCOL_PREFIX.matcher(query).matches()
                || hasControlCharacter(query)) {
            throw new IllegalArgumentException("研究搜索词未通过安全校验");
        }
        Source source = new Source();
        source.setName("Google News · " + safeTitle(task.getTitle(), task.getTaskKey()));
        source.setType("RSS");
        source.setUrl("https://news.google.com/rss/search?q=" + encode(query)
                + "&hl=zh-CN&gl=CN&ceid=CN:zh-Hans");
        source.setEnabled(true);
        source.setScheduledEnabled(false);
        source.setMaxItemsPerRun(5);
        source.setCredibility(3);
        source.setTags("研究任务,动态来源," + task.getIntent());
        return source;
    }

    private String safeTitle(String title, String fallback) {
        String value = title == null || title.trim().isEmpty() ? fallback : title.trim();
        return value.length() <= 60 ? value : value.substring(0, 60);
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception impossible) {
            throw new IllegalStateException("UTF-8 query encoding is unavailable", impossible);
        }
    }

    private boolean hasControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) && !Character.isWhitespace(current)) {
                return true;
            }
        }
        return false;
    }
}

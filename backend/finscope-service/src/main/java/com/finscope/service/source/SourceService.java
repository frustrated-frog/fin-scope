package com.finscope.service.source;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SourceService {
    @Resource
    private SourceRepository sourceRepository;

    public List<Source> list() {
        return sourceRepository.findAll();
    }

    public Source create(Source source) {
        return sourceRepository.save(source);
    }

    /**
     * 安装适合一次完整研究运行的免费 RSS 新闻源。按 URL 幂等创建或更新，
     * 由调用方显式触发，避免应用启动时修改用户已有配置。
     */
    public List<Source> installRecommendedNewsSources() {
        Map<String, Source> existingByUrl = new LinkedHashMap<String, Source>();
        for (Source source : sourceRepository.findAll()) {
            existingByUrl.put(normalizeUrl(source.getUrl()), source);
        }

        List<Source> installed = new ArrayList<Source>();
        for (Source preset : recommendedNewsSources()) {
            Source existing = existingByUrl.get(normalizeUrl(preset.getUrl()));
            if (existing == null) {
                installed.add(sourceRepository.save(preset));
            } else {
                installed.add(sourceRepository.update(existing.getId(), preset));
            }
        }
        return installed;
    }

    public Source update(Long id, Source source) {
        return sourceRepository.update(id, source);
    }

    public void delete(Long id) {
        sourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("信息源不存在：" + id));
        sourceRepository.delete(id);
    }

    private List<Source> recommendedNewsSources() {
        return Arrays.asList(
                rss("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", 4, 4,
                        "china_macro,company_ipo,market,财经,市场,公司"),
                rss("Federal Reserve Press Releases", "https://www.federalreserve.gov/feeds/press_all.xml", 3, 5,
                        "official,regulator,china_macro,宏观,政策,市场"),
                rss("TechCrunch", "https://techcrunch.com/feed/", 4, 4,
                        "ai_startup,company_ipo,AI,创业,融资,公司"),
                rss("Google News 中文科技与半导体",
                        "https://news.google.com/rss/search?q=%E7%A7%91%E6%8A%80+%E5%8D%8A%E5%AF%BC%E4%BD%93+AI&hl=zh-CN&gl=CN&ceid=CN:zh-Hans",
                        5, 3, "china_macro,ai_startup,company_ipo,市场,AI,半导体,科技,公司")
        );
    }

    private Source rss(String name, String url, int maxItems, int credibility, String tags) {
        Source source = new Source();
        source.setName(name);
        source.setType("RSS");
        source.setUrl(url);
        source.setEnabled(true);
        source.setFetchFrequencyMinutes(60);
        source.setScheduledEnabled(false);
        source.setMaxItemsPerRun(maxItems);
        source.setCredibility(credibility);
        source.setTags(tags);
        return source;
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

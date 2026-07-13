package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Service
public class ThesisQueryExpansionService {
    public List<Source> queries(ResearchThesis thesis, int round) {
        if (thesis == null || round < 1 || round > 3) {
            throw new IllegalArgumentException("Research query round must be between 1 and 3");
        }
        String subject = value(thesis.getSubjectName(), thesis.getQuestion());
        String primary;
        String counter;
        if (round == 1) {
            primary = subject + " 订单 资本开支 景气 周期 when:30d";
            counter = subject + " 下滑 回落 风险 库存 when:30d";
        } else if (round == 2) {
            primary = subject + " 公司公告 业绩 在手订单 产能利用率 when:90d";
            counter = subject + " 下调 削减资本开支 需求放缓 when:90d";
        } else {
            primary = subject + " 行业协会 销售额 出货量 预测 when:180d";
            counter = subject + " 出口限制 制裁 客户验证 延期 when:180d";
        }
        return Arrays.asList(source(round, 1, primary), source(round, 2, counter));
    }

    private Source source(int round, int queryIndex, String query) {
        Source source = new Source();
        source.setName((queryIndex == 1 ? "Google News" : "Bing News") + " · 命题检索 R" + round + "-Q" + queryIndex);
        source.setType("RSS");
        source.setUrl(queryIndex == 1
                ? "https://news.google.com/rss/search?q=" + encode(query) + "&hl=zh-CN&gl=CN&ceid=CN:zh-Hans"
                : "https://www.bing.com/news/search?q=" + encode(query) + "&format=RSS&setlang=zh-cn");
        source.setEnabled(true);
        source.setScheduledEnabled(false);
        source.setMaxItemsPerRun(5);
        source.setCredibility(3);
        source.setTags("命题检索,动态来源," + (queryIndex == 1 ? "Google News" : "Bing News"));
        return source;
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encode research query", ex);
        }
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

package com.finscope.service.news;

import com.finscope.common.enums.news.NewsClassificationStatus;

import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.NewsItemClassification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 有界词表分类；匹配依据可见，未匹配保持未归类，不推断利好利空。 */
@Service
public class NewsRuleClassifier {
    public static final String VERSION = "RULE_V1";
    private static final Map<String, List<String>> RULES = rules();

    public NewsItemClassification classify(NewsClassificationCandidate candidate, List<NewsCategory> categories) {
        String text = String.valueOf(candidate.getTitle()) + " " + String.valueOf(candidate.getContent());
        NewsItemClassification result = new NewsItemClassification();
        result.setItemId(candidate.getItemId());
        result.setModelName(VERSION);
        result.setStatus(NewsClassificationStatus.UNCLASSIFIED.name());
        for (Map.Entry<String, List<String>> rule : RULES.entrySet()) {
            if (categories.stream().noneMatch(category -> category.isEnabled() && category.getCode().equals(rule.getKey()))) {
                continue;
            }
            for (String keyword : rule.getValue()) {
                int position = text.indexOf(keyword);
                if (position >= 0) {
                    result.setStatus(NewsClassificationStatus.CLASSIFIED.name());
                    result.setCategoryCode(rule.getKey());
                    result.setReason("规则 v1：命中「" + keyword + "」，原文位置 " + position);
                    return result;
                }
            }
        }
        result.setReason("规则 v1：未命中明确分类，保留原文待归类");
        return result;
    }

    private static Map<String, List<String>> rules() {
        Map<String, List<String>> rules = new LinkedHashMap<>();
        // 宏观流动性规则先于公司回购，避免将央行逆回购归入公司事件。
        rules.put("MACRO_POLICY", List.of("逆回购", "存款准备金率", "货币政策", "财政政策", "国务院", "证监会", "CPI", "PMI"));
        rules.put("GLOBAL", List.of("美联储", "欧洲央行", "非农就业", "纳斯达克", "地缘冲突"));
        rules.put("MARKET_MOVE", List.of("涨停", "跌停", "板块走强", "板块拉升", "成交额突破", "沪指", "深成指"));
        rules.put("COMPANY", List.of("股份回购", "回购股份", "回购公司股份", "业绩预告", "净利润", "年度报告", "重大订单", "中标", "董事会", "股东增持", "股东减持"));
        rules.put("INDUSTRY", List.of("行业产能", "行业需求", "产业链", "行业库存", "技术路线", "供需格局"));
        return rules;
    }
}

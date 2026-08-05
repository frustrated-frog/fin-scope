package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Assigns one stable, user-facing Dashboard category to every radar event. */
@Service
public class RadarDashboardCategoryService {
    public static final String FINANCE = "FINANCE";
    public static final String TECHNOLOGY = "TECHNOLOGY";
    public static final String POLITICS = "POLITICS";

    private static final String[] TECHNOLOGY_TERMS = {
            "人工智能", "大模型", "生成式AI", "AI模型", "AGENT", "OPENAI", "ANTHROPIC", "DEEPSEEK",
            "芯片", "半导体", "算力", "GPU", "CPU", "机器人", "云计算", "数据中心", "光通信", "电池",
            "软件", "开源", "算法", "科技", "自动驾驶", "量子计算", "操作系统"
    };
    private static final String[] POLITICS_TERMS = {
            "政治", "政府", "国务院", "白宫", "国会", "议会", "总统", "首相", "选举", "政党",
            "外交", "制裁", "关税", "出口限制", "出口管制", "监管新规", "法案", "地缘", "冲突",
            "战争", "停火", "军方", "国防", "领土", "峰会"
    };
    private static final String[] FINANCE_TERMS = {
            "金融", "股票", "A股", "港股", "美股", "证券", "基金", "银行", "保险", "债券", "利率",
            "汇率", "央行", "降息", "加息", "财报", "业绩", "营收", "利润", "分红", "融资", "并购",
            "上市", "交易", "投资", "资金", "指数", "期货", "商品", "房地产"
    };

    public String classify(RadarEvent event) {
        if (event == null) return FINANCE;
        String category = normalize(event.getCategoryCode());
        String text = normalize(event.getCanonicalTitle()) + " " + normalize(event.getSummary());

        int technology = score(text, TECHNOLOGY_TERMS);
        int politics = score(text, POLITICS_TERMS);
        int finance = score(text, FINANCE_TERMS);

        if (technology > 0) technology += 2;
        if (politics > 0) politics += 2;
        if ("GLOBAL".equals(category) || "MACRO_POLICY".equals(category)) politics += 1;
        if ("COMPANY".equals(category) || "MARKET_MOVE".equals(category)) finance += 1;
        if ("INDUSTRY".equals(category) && technology > 0) technology += 1;

        if (politics > technology && politics > finance) return POLITICS;
        if (technology > politics && technology > finance) return TECHNOLOGY;
        return FINANCE;
    }

    private int score(String text, String[] terms) {
        int score = 0;
        for (String term : terms) if (text.contains(normalize(term))) score++;
        return score;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

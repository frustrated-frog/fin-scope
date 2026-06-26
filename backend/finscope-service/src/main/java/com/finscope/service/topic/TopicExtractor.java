package com.finscope.service.topic;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class TopicExtractor {
    private static final List<String> FINANCIAL_TERMS = Arrays.asList(
            "美联储", "降息", "加息", "黄金", "美债", "利率", "通胀", "CPI", "GDP", "人民币",
            "A股", "港股", "政策", "财报", "流动性", "风险偏好", "估值", "成长股", "价值股",
            "汇率", "原油", "地产", "消费", "半导体", "AI");

    public TopicExtraction extract(String text) {
        String value = text == null ? "" : text;
        TopicExtraction technical = extractTechnicalPractice(value);
        if (technical != null) {
            return technical;
        }
        TopicExtraction osint = extractOsintPractice(value);
        if (osint != null) {
            return osint;
        }
        TopicExtraction quantLoop = extractQuantLoopPractice(value);
        if (quantLoop != null) {
            return quantLoop;
        }
        List<String> terms = new ArrayList<String>();
        for (String term : FINANCIAL_TERMS) {
            if (value.contains(term) && !terms.contains(term)) {
                terms.add(term);
            }
        }
        if (terms.isEmpty()) {
            terms.add(fallbackTopicName(value));
        }
        String primary = terms.get(0);
        List<String> questions = new ArrayList<String>();
        questions.add(primary + " 的核心驱动变量是什么？");
        questions.add(primary + " 如何影响资产定价和风险偏好？");
        questions.add("这次信息是新事件、旧事件后续，还是噪音？");
        return new TopicExtraction(primary, "围绕「" + primary + "」沉淀的投研学习主题", terms, questions);
    }

    private TopicExtraction extractTechnicalPractice(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean cloudflareStack = lower.contains("cloudflare")
                && (lower.contains("workers") || lower.contains("serverless") || lower.contains("d1") || lower.contains("r2"));
        if (!cloudflareStack) {
            return null;
        }
        List<String> terms = new ArrayList<String>();
        addIfPresent(terms, lower, "Cloudflare", "cloudflare");
        addIfPresent(terms, lower, "Workers", "workers");
        addIfPresent(terms, lower, "D1", "d1");
        addIfPresent(terms, lower, "R2", "r2");
        addIfPresent(terms, lower, "Pages", "pages");
        addIfPresent(terms, lower, "KV", "kv");
        addIfPresent(terms, lower, "Serverless", "serverless");
        List<String> questions = new ArrayList<String>();
        questions.add("Cloudflare 免费额度的边界、限制和长期稳定性分别是什么？");
        questions.add("Workers、D1、R2、Pages、KV 这套部署组合适合哪些个人项目？");
        questions.add("如果未来访问量增长，迁移成本和供应商锁定风险如何控制？");
        return new TopicExtraction(
                "Cloudflare 免费基础设施实践",
                "拆解 Cloudflare Workers、D1、R2、Pages、KV 如何组成低成本 Serverless 基础设施，并沉淀成个人项目的技术选型卡片。",
                terms,
                questions);
    }

    private TopicExtraction extractOsintPractice(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean osintPractice = lower.contains("osint")
                || text.contains("开源情报")
                || text.contains("公开信息检索")
                || text.contains("查人")
                || text.contains("查公司")
                || text.contains("查设备")
                || text.contains("网络资产")
                || text.contains("暴露面");
        if (!osintPractice) {
            return null;
        }
        List<String> terms = new ArrayList<String>();
        terms.add("OSINT");
        addIfPresent(terms, text, "公开信息检索", "公开信息");
        addIfPresent(terms, text, "网络资产搜索", "网络资产");
        addIfPresent(terms, text, "暴露面排查", "暴露面");
        terms.add("信息安全");
        List<String> questions = new ArrayList<String>();
        questions.add("这些 OSINT 工具的合法合规边界、数据来源和隐私风险分别是什么？");
        questions.add("哪些场景适合沉淀为防御侧资产排查流程，哪些高风险用法应该避免？");
        questions.add("工具之间如何交叉验证，避免把公开线索误判成确定事实？");
        return new TopicExtraction(
                "OSINT 开源情报工具实践",
                "梳理公开信息检索、网络资产搜索和暴露面排查工具的组合方式，同时记录合法合规边界和防滥用注意事项。",
                terms,
                questions);
    }

    private TopicExtraction extractQuantLoopPractice(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean quantLoop = lower.contains("quant trading")
                && (lower.contains("loop engineering") || lower.contains("backtest") || lower.contains("monitor risk"));
        if (!quantLoop) {
            return null;
        }
        List<String> terms = new ArrayList<String>();
        addIfPresent(terms, lower, "loop engineering", "loop engineering");
        addIfPresent(terms, lower, "quant trading", "quant trading");
        addIfPresent(terms, lower, "backtest", "backtest");
        addIfPresent(terms, lower, "signal verification", "verifies every signal");
        addIfPresent(terms, lower, "risk monitor", "monitor risk");
        if (terms.isEmpty()) {
            terms.add("quant trading");
        }
        List<String> questions = new ArrayList<String>();
        questions.add("每个交易信号进入执行前如何验证、回测和拒绝？");
        questions.add("市场数据、信号生成、风控监控和记忆复盘之间的循环边界如何设计？");
        questions.add("哪些环节适合 agent 自动化，哪些环节必须保留人工审批或风控阈值？");
        return new TopicExtraction(
                "Loop Engineering Quant Trading System",
                "围绕量化交易系统的 loop engineering 方法，沉淀数据、信号、回测、执行、风控和记忆复盘组成的自我改进流程。",
                terms,
                questions);
    }

    private void addIfPresent(List<String> terms, String text, String term, String needle) {
        if (text.contains(needle) && !terms.contains(term)) {
            terms.add(term);
        }
    }

    private String fallbackTopicName(String text) {
        String normalized = text == null ? "" : text.replaceAll("[\\p{Punct}\\s]+", "");
        if (normalized.isEmpty()) {
            return "未命名主题";
        }
        return normalized.length() > 12 ? normalized.substring(0, 12) : normalized;
    }
}

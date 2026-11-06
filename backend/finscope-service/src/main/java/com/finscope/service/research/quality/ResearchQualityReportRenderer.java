package com.finscope.service.research.quality;

import com.finscope.domain.research.quality.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

/** Additional detail for long reports; the report body is never truncated to fit this appendix. */
@Component
public class ResearchQualityReportRenderer {
    public String render(ResearchQualityAssessment quality) {
        ResearchTaskContract contract = quality.getContract();
        StringBuilder out = new StringBuilder("\n\n## 任务契约与事实校验\n\n");
        out.append("研究问题：").append(cell(contract.getQuestion())).append("\n\n")
                .append("研究对象：").append(cell(contract.getSubject())).append("；事件：").append(cell(contract.getEvent()))
                .append("；研究截止日期：").append(contract.getAsOfDate()).append("。\n\n")
                .append("事件时间窗：").append(contract.getDateFrom() == null ? "问题未指定，保留各项事实的独立日期"
                        : contract.getDateFrom() + " 至 " + contract.getDateTo()).append("。网页发布时间不代替事件日期。\n\n")
                .append("时间依据：").append(cell(contract.getDateResolution()))
                .append(contract.getDateAnchorUrl() == null ? "" : "；归档事件来源：" + cell(contract.getDateAnchorUrl())).append("。\n\n")
                .append("任务事实覆盖率：").append(Math.round(quality.getTaskCoverage() * 100)).append("%；合并转载后的来源组：")
                .append(quality.getIndependentSourceCount()).append("。覆盖率表示原文与任务的匹配程度，不等同于投资结论的正确概率。\n\n")
                .append("### 已核验事实与原文位置\n\n")
                .append("| 来源引用 | 事实维度 | 数字与原始单位 | 事件日期 / 口径 | 原文片段与位置 |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        Map<String, List<StructuredResearchFact>> byQuote = new LinkedHashMap<>();
        for (StructuredResearchFact fact : quality.getFacts()) {
            if (fact.isVerified()) {
                byQuote.computeIfAbsent(fact.getEvidenceRef() + ":" + fact.getQuoteStart() + ":" + fact.getQuoteEnd(),
                        key -> new ArrayList<>()).add(fact);
            }
        }
        for (List<StructuredResearchFact> facts : byQuote.values()) {
            StructuredResearchFact fact = facts.get(0);
            String kinds = facts.stream().map(item -> item.getKind().getLabel()).distinct().collect(Collectors.joining("、"));
            String numbers = facts.stream().filter(item -> item.getValue() != null)
                    .map(item -> item.getValueRelation().getLabel() + item.getValue() + item.getUnit()).distinct().collect(Collectors.joining("；"));
            out.append("| ").append(cell(fact.getEvidenceRef())).append(" | ").append(kinds)
                    .append(" | ").append(cell(numbers.isBlank() ? "定性事实" : numbers))
                    .append(" | ").append(cell(fact.getEventDate())).append(" / ")
                    .append(cell(facts.stream().map(item -> scopeLabel(item.getScope())).distinct().collect(Collectors.joining("；"))))
                    .append(" | ").append(cell(fact.getQuote())).append("（字符 ")
                    .append(fact.getQuoteStart()).append("–").append(fact.getQuoteEnd()).append("） |\n");
        }
        out.append("\n原文位置按留存正文的 Java 字符索引计算，区间左闭右开；未核验的搜索摘要不会计入事实覆盖。\n\n")
                .append("### 来源谱系与拒绝原因\n\n")
                .append("| 引用 | 原始网页 | 来源组 | 采用情况 | 谱系依据与校验问题 |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        for (ResearchEvidenceReview review : quality.getEvidence()) {
            out.append("| ").append(cell(review.getEvidenceRef())).append(" | ").append(cell(review.getUrl()))
                    .append(" | ").append(cell(review.getSourceGroup())).append(" | ")
                    .append(review.isAccepted() ? "有核验通过的事实" : "未作为已确认事实采用")
                    .append(" | ").append(cell(review.getLineageReason())).append("；")
                    .append(cell(review.getIssues().stream().map(issue -> issue.getLabel()).collect(Collectors.joining("；"))))
                    .append(" |\n");
        }
        appendList(out, "### 未解决的事实与时间缺口", quality.getMissingConditions());
        appendList(out, "### 数值冲突与处理边界", quality.getConflicts());
        appendList(out, "### 下一轮核验动作", quality.getFeedback());
        out.append("\n建议检索：").append(cell(quality.getRecoveryQuery())).append("。\n\n")
                .append("校验依据为当前保存的公开资料；来源合并采用同站点、显式转载和正文重合规则，不能保证识别所有隐蔽转载。")
                .append("不同日期或统计口径的数字分别保留；口径不明时不强行合并，也不据此消除冲突。\n");
        return out.toString();
    }

    private void appendList(StringBuilder out, String title, List<String> items) {
        out.append('\n').append(title).append("\n\n");
        if (items.isEmpty()) {
            out.append("当前规则未检出此类问题，仍应结合原始材料复核。\n");
        }
        for (String item : items) {
            out.append("- ").append(cell(item)).append('\n');
        }
    }

    private String cell(String value) {
        return value == null ? "未提供" : value.replace("|", "\\|").replaceAll("[\\r\\n]+", " ")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private String scopeLabel(String scope) {
        return scope == null ? "未提供统计口径" : scope.replace("|", " / ")
                .replace("SINGLE_TRADE", "单笔交易").replace("DAILY_TOTAL", "当日合计")
                .replace("REPORTED_EVENT", "原文所述事件").replace("_AVERAGE_PRICE", "的平均价格")
                .replace("_TRADE_PRICE", "的成交价格");
    }
}

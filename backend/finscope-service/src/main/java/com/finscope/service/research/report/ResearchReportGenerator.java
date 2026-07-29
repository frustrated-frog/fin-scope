package com.finscope.service.research.report;

import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ResearchReportGenerator {
    public GeneratedResearchReport generate(ResearchThesis thesis, List<ResearchEvidenceCard> evidence,
                                             EvidenceSufficiency sufficiency) {
        int support = count(evidence, "SUPPORT");
        int counter = count(evidence, "COUNTER");
        int sources = sourceCount(evidence);
        String direction = direction(support, counter, sufficiency.isSufficient());
        String confidence = confidence(evidence.size(), sources, sufficiency.isSufficient());
        String conclusion = conclusion(thesis, direction, confidence);
        String overview = overview(thesis, conclusion, confidence);
        String title = value(thesis.getSubjectName(), "研究命题") + "深度研究报告";
        String markdown = markdown(thesis, title, conclusion, evidence, sufficiency);
        return new GeneratedResearchReport(title, conclusion, direction, confidence,
                ResearchReportPolicy.bound(overview, ResearchReportPolicy.MAX_EXECUTIVE_SUMMARY_CHARACTERS),
                markdown, "DETERMINISTIC");
    }

    private String markdown(ResearchThesis thesis, String title, String conclusion,
                            List<ResearchEvidenceCard> evidence, EvidenceSufficiency sufficiency) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(title).append("\n\n")
                .append("> 研究日期：").append(LocalDate.now()).append("  \n")
                .append("> 研究命题：").append(value(thesis.getQuestion(), "未命名命题")).append("\n\n")
                .append("## 核心结论\n\n")
                .append(conclusion).append(' ').append(references(evidence, null, 4)).append("\n\n")
                .append("**结论边界：** 当前认识限定在本次公开材料覆盖的观察期内。短期价格、交易或单次披露可以改变判断，")
                .append("但不能替代后续经营结果、行业数据和正式披露。\n\n")
                .append("## 关键事实与 AI 解读\n\n");

        for (int index = 0; index < evidence.size(); index++) {
            ResearchEvidenceCard card = evidence.get(index);
            out.append("### 事实 ").append(index + 1).append(" · ")
                    .append(markdownText(value(card.getArticle().getTitle(), "未命名事实"))).append("\n\n")
                    .append("**事实：** ").append(card.getClaim()).append(' ').append(reference(index)).append("\n\n")
                    .append("**AI 解读：** ").append(interpretation(thesis, card)).append(' ')
                    .append(reference(index)).append("\n\n")
                    .append("**解释边界：** 该事实只覆盖当前材料明确描述的时间和口径，不能单独证明长期趋势；")
                    .append("需要与后续披露及其他独立来源共同验证。\n\n");
        }
        if (evidence.isEmpty()) {
            out.append("当前没有选出能够完整引用的对象特定事实，因此不生成事实性判断，等待下一轮资料补充。\n\n");
        }

        out.append("## 命题拆解与综合判断\n\n")
                .append("### 1. 当前材料最明确地说明了什么？\n\n")
                .append("现有材料首先确认研究对象在当前观察期出现了可核验变化。判断的重点不是新闻数量，")
                .append("而是这些变化是否指向同一关键变量，以及能否在后续结果中持续出现。")
                .append(references(evidence, null, 3)).append("\n\n")
                .append("### 2. 这些变化如何影响原命题？\n\n")
                .append("每项事实都需要经过“事实变化—关键变量—可观察结果”的传导链解释。只有当不同来源描述的变化能够")
                .append("共同落到同一结果变量，当前结论才会得到强化；若变化只停留在价格或情绪层面，结论仍需保持条件性。")
                .append(references(evidence, null, 4)).append("\n\n")
                .append("### 3. 当前还不能得出什么结论？\n\n")
                .append("现有事实不能直接推出长期价值、精确目标或买卖时点，也不能把一次异常变化外推为稳定趋势。")
                .append("尚未被正式披露和连续观察覆盖的部分，应保留为未知项。\n\n")
                .append("## 影响机制\n\n")
                .append(mechanism(thesis)).append(' ').append(references(evidence, null, 4)).append("\n\n")
                .append("## 不同解释与不确定性\n\n")
                .append("同一组事实可能存在不止一种解释：变化可能来自基本面、供需和经营预期，也可能来自流通结构、")
                .append("短期事件、统计口径或市场情绪。区分这些解释的关键，不是继续增加相似报道，而是观察后续结果是否按")
                .append("相应机制兑现。").append(references(evidence, "COUNTER", 4)).append("\n\n")
                .append("**尚未确认：** 当前材料未覆盖的连续披露期、可比口径和长期兑现程度，仍可能改变本报告的认识。\n\n")
                .append("## 情景推演\n\n")
                .append("### 基准情景\n\n")
                .append("关键指标保持当前方向但不同来源和时间窗口仍有分化，市场逐步消化已有信息。此时维持当前结论，")
                .append("等待后续结果验证。\n\n")
                .append("### 强化情景\n\n")
                .append("多个独立来源继续指向同一关键变量，且后续正式披露与实际结果同步改善。此时可以提高结论确定性。\n\n")
                .append("### 削弱情景\n\n")
                .append("后续结果没有兑现当前变化，或新的对象特定事实显示关键变量转弱。此时应下调判断，必要时推翻当前结论。\n\n")
                .append("## 结论更新条件\n\n");
        appendUpdateConditions(out, thesis, sufficiency);

        out.append("## 资料来源\n\n");
        if (evidence.isEmpty()) {
            out.append("本次运行没有可列出的资料来源。\n");
        } else {
            for (int index = 0; index < evidence.size(); index++) {
                Article article = evidence.get(index).getArticle();
                out.append("### E").append(index + 1).append(" · ")
                        .append(markdownText(value(article.getTitle(), "未命名来源"))).append("\n\n")
                        .append("- 来源：").append(value(article.getSourceName(), "未知来源")).append("\n")
                        .append("- 发布时间：").append(article.getPublishedAt() == null ? "未提供" : article.getPublishedAt()).append("\n")
                        .append("- 原文：").append(sourceReference(article)).append("\n\n");
            }
        }
        return out.toString();
    }

    private String interpretation(ResearchThesis thesis, ResearchEvidenceCard card) {
        String question = value(thesis.getQuestion(), value(thesis.getSubjectName(), "研究命题"));
        if ("COUNTER".equals(card.getStance())) {
            return "对于“" + question + "”，这条信息揭示了当前解释中的约束条件。它提示价格、交易或经营变化可能存在"
                    + "未被充分消化的风险，判断时需要把短期现象与能够持续兑现的结果分开。";
        }
        if ("SUPPORT".equals(card.getStance())) {
            return "对于“" + question + "”，这条信息说明与命题相关的关键变量已经出现可观察变化。其意义在于为当前方向"
                    + "提供对象特定的事实基础，但能否形成持续结论仍取决于后续披露和实际结果。";
        }
        return "对于“" + question + "”，这条信息提供了必要的事实背景，有助于校准时间、口径和市场状态。它本身不决定"
                + "结论方向，但会影响其他事实应当如何解释。";
    }

    private String mechanism(ResearchThesis thesis) {
        if ("COMPANY".equalsIgnoreCase(value(thesis.getSubjectType(), ""))) {
            return "公司层面的变化通常先影响收入或需求预期，再影响利润率、现金流和估值。交易数据反映市场如何定价这些预期，"
                    + "但只有经营指标和正式披露能够确认预期是否兑现。因此，本报告把市场表现视为线索，把后续经营结果视为验证。";
        }
        if ("PORTFOLIO".equalsIgnoreCase(value(thesis.getSubjectType(), ""))) {
            return "组合层面的变化通过行业暴露、风险因子、资产相关性和流动性传导。单个成分的变化只有在影响组合主要风险来源时，"
                    + "才会实质改变命题；分散度和相关性变化决定冲击能否被组合吸收。";
        }
        return "行业层面的变化通常沿需求、供给、价格、库存、产能利用率和企业结果逐级传导。领先指标用于识别方向，同步指标用于"
                + "确认兑现，价格和估值则反映市场预期。只有这些层级出现相互印证，才能把阶段变化判断为更稳定的趋势。";
    }

    private void appendUpdateConditions(StringBuilder out, ResearchThesis thesis, EvidenceSufficiency sufficiency) {
        if ("COMPANY".equalsIgnoreCase(value(thesis.getSubjectType(), ""))) {
            out.append("- 收入、利润率、现金流与管理层指引连续改善时，强化当前结论。\n")
                    .append("- 订单、用户需求或核心业务量兑现到正式业绩时，提高判断确定性。\n")
                    .append("- 经营结果连续恶化、现金流转负或正式披露否定关键事实时，削弱或推翻当前结论。\n");
        } else {
            out.append("- 需求、供给、价格、库存和产能利用率连续同向改善时，强化当前结论。\n")
                    .append("- 领先指标兑现到多个主体的实际结果时，提高判断确定性。\n")
                    .append("- 关键指标连续转弱、政策或竞争格局改变传导机制时，削弱或推翻当前结论。\n");
        }
        if (!sufficiency.isSufficient()) {
            out.append("- 当前资料尚未覆盖全部必要方向；在缺口补齐前，结论保持低确定性。\n");
        }
        out.append('\n');
    }

    private String overview(ResearchThesis thesis, String conclusion, String confidence) {
        return conclusion + "本报告围绕“" + value(thesis.getQuestion(), "未命名命题")
                + "”组织对象特定事实，逐条解释其对关键变量的影响，并保留不同解释和结论更新条件。"
                + "当前结论置信度为" + confidenceLabel(confidence)
                + "，适合作为后续复核基线，不构成投资买卖建议。";
    }

    private String conclusion(ResearchThesis thesis, String direction, String confidence) {
        String question = value(thesis.getQuestion(), value(thesis.getSubjectName(), "研究命题"));
        if ("SUPPORT".equals(direction) || "PARTIAL_SUPPORT".equals(direction)) {
            return "阶段性结论：现有事实更倾向于确认命题“" + question + "”，但结论仍受观察期和后续兑现约束。"
                    + "当前确定性为" + confidenceLabel(confidence) + "。";
        }
        if ("CHALLENGE".equals(direction) || "PARTIAL_CHALLENGE".equals(direction)) {
            return "阶段性结论：现有事实提示命题“" + question + "”需要更谨慎地理解，部分关键变化尚不足以支持长期外推。"
                    + "当前确定性为" + confidenceLabel(confidence) + "。";
        }
        return "阶段性结论：命题“" + question + "”存在多种可成立的解释，当前事实能够形成方向性认识，"
                + "但仍需后续结果区分。当前确定性为" + confidenceLabel(confidence) + "。";
    }

    private String references(List<ResearchEvidenceCard> evidence, String stance, int limit) {
        StringBuilder value = new StringBuilder();
        int count = 0;
        for (int index = 0; index < evidence.size() && count < limit; index++) {
            if (stance != null && !stance.equals(evidence.get(index).getStance())) continue;
            value.append(reference(index));
            count++;
        }
        return value.toString();
    }

    private String reference(int index) {
        return "[E" + (index + 1) + "](#evidence-e" + (index + 1) + ")";
    }

    private String sourceReference(Article article) {
        String title = markdownText(value(article.getTitle(), "未命名来源"));
        String url = article.getUrl();
        if (url != null && url.length() <= 500) return "[" + title + "](" + url + ")";
        return title;
    }

    private String markdownText(String value) {
        return value.replace("[", "（").replace("]", "）").replace("\n", " ");
    }

    private String direction(int support, int counter, boolean sufficient) {
        if (support > counter) return sufficient ? "SUPPORT" : "PARTIAL_SUPPORT";
        if (counter > support) return sufficient ? "CHALLENGE" : "PARTIAL_CHALLENGE";
        return "MIXED";
    }

    private String confidence(int evidenceCount, int sources, boolean sufficient) {
        if (sufficient && evidenceCount >= 10 && sources >= 3) return "HIGH";
        if (evidenceCount >= 4 && sources >= 2) return "MEDIUM";
        return "LOW";
    }

    private String confidenceLabel(String confidence) {
        if ("HIGH".equals(confidence)) return "高";
        if ("MEDIUM".equals(confidence)) return "中";
        return "低";
    }

    private int count(List<ResearchEvidenceCard> evidence, String stance) {
        int count = 0;
        for (ResearchEvidenceCard card : evidence) if (stance.equals(card.getStance())) count++;
        return count;
    }

    private int sourceCount(List<ResearchEvidenceCard> evidence) {
        Set<String> sources = new HashSet<String>();
        for (ResearchEvidenceCard card : evidence) sources.add(card.getSourceIdentity());
        return sources.size();
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchSourceIdentity;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ResearchReportGenerator {
    public GeneratedResearchReport generate(ResearchThesis thesis, List<ResearchEvidenceCard> evidence,
                                             EvidenceSufficiency sufficiency) {
        int support = count(evidence, "SUPPORT");
        int counter = count(evidence, "COUNTER");
        int neutral = evidence.size() - support - counter;
        int sourceCount = sourceCount(evidence);
        String direction = direction(support, counter, sufficiency.isSufficient());
        String confidence = confidence(evidence.size(), sourceCount, sufficiency.isSufficient());
        String conclusion = conclusion(thesis, direction, confidence, support, counter);
        String summary = executiveSummary(thesis, conclusion, evidence.size(), sourceCount, support, counter,
                neutral, sufficiency);
        String title = value(thesis.getSubjectName(), "研究命题") + "命题研究报告";
        String markdown = markdown(thesis, title, conclusion, confidence, summary, evidence, sufficiency,
                support, counter, neutral);
        return new GeneratedResearchReport(title, conclusion, direction, confidence,
                ResearchReportPolicy.bound(summary, ResearchReportPolicy.MAX_EXECUTIVE_SUMMARY_CHARACTERS),
                ResearchReportPolicy.bound(markdown, ResearchReportPolicy.MAX_REPORT_CHARACTERS), "DETERMINISTIC");
    }

    private String markdown(ResearchThesis thesis, String title, String conclusion, String confidence, String summary,
                            List<ResearchEvidenceCard> evidence, EvidenceSufficiency sufficiency,
                            int support, int counter, int neutral) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(title).append("\n\n");
        out.append("> 研究日期：").append(LocalDate.now()).append("  ").append("命题：")
                .append(value(thesis.getQuestion(), "未命名命题")).append("  ").append("置信度：")
                .append(confidenceLabel(confidence)).append("\n\n");
        out.append("## 核心结论\n\n").append(conclusion).append("\n\n");
        out.append("## 执行摘要\n\n").append(summary).append("\n\n");
        out.append("## 命题拆解\n\n");
        out.append("本报告把命题拆成四个可验证环节：核心经营或行业指标是否改善、变化能否持续并兑现到结果、")
                .append("市场定价是否已经反映预期、哪些反向信号足以推翻当前判断。")
                .append("判断对象限定为“").append(value(thesis.getSubjectName(), "目标对象"))
                .append("”，不把泛科技、泛 AI 或与产业链无直接关系的论文资讯作为结论依据。\n\n");
        out.append("- 支持证据：").append(support).append(" 条\n");
        out.append("- 反向证据：").append(counter).append(" 条\n");
        out.append("- 中性证据：").append(neutral).append(" 条\n\n");
        out.append("## 关键证据\n\n");
        appendEvidence(out, evidence, false);
        out.append("## 反方证据与风险\n\n");
        appendEvidence(out, evidence, true);
        out.append("## 机制推演\n\n");
        out.append("价格变化本身不能单独确认或推翻命题。若关键经营指标、外部需求和管理层指引持续改善，")
                .append("价格回落更可能是预期或估值修正；若领先指标、实际结果和风险暴露同时恶化，则应把它视为基本面转向。")
                .append("因此，本报告优先采信能够连接“外部驱动—经营指标—结果兑现”的证据链，而不是只依据单日涨跌或情绪标题。\n\n");
        out.append("### 情景推演\n\n");
        out.append("- **基准情景**：核心指标温和改善，但不同业务或细分方向继续分化；结果兑现与估值匹配度决定后续表现。\n");
        out.append("- **上行情景**：多个独立领先指标连续改善，并在后续披露中兑现为收入、利润或目标变量的上修；此时可上调结论置信度。\n");
        out.append("- **下行情景**：需求、经营指引和实际结果同步转弱，且反向风险连续两个观察期未缓解；此时应转为挑战命题。\n");
        out.append("- **验证原则**：至少观察两个披露期，并区分企业公告、行业统计与媒体转述；单条预测或市场观点只作为线索，不作为确认信号。\n\n");
        out.append("## 结论边界与后续验证\n\n");
        out.append("### 证据局限\n\n");
        if (sufficiency.getWarnings().isEmpty()) {
            out.append("- 当前证据达到最低覆盖要求，但仍属于公开信息条件下的阶段性判断。\n");
        } else {
            for (String warning : sufficiency.getWarnings()) out.append("- ").append(warning).append("。\n");
        }
        out.append("\n### 下一轮验证信号\n\n");
        appendNextValidationSignals(out, thesis);
        out.append("## 判定框架与监测仪表盘\n\n");
        out.append("### 指标分层\n\n");
        out.append("研究不应把所有指标放在同一层级。**领先指标**包括需求预期、管理层指引、订单或用户行为和政策变化；")
                .append("它们先于最终结果，适合判断方向。**同步指标**包括收入、利润率、现金流、供需和目标变量的实际变化；")
                .append("它们用于确认领先信号是否兑现。**滞后指标**包括板块估值、机构持仓和历史价格表现；它们能解释市场定价，")
                .append("但不能代替产业证据。报告后续更新时，应优先补领先指标，再用同步指标交叉验证，最后才讨论估值。\n\n");
        out.append("### 结论升级条件\n\n");
        out.append("若至少两个独立的一手或高可信来源显示领先指标改善，同时后续实际结果连续兑现，且关键风险没有反向恶化，")
                .append("可把“部分支持”升级为“支持”。如果只有媒体预测或市场价格上涨，而公告、经营指标或行业数据没有确认，")
                .append("应维持原置信度。升级结论还要求证据覆盖不同主体，避免把同一篇报道的多次转载误计为多个来源。\n\n");
        out.append("### 结论降级与失效条件\n\n");
        out.append("若需求主体连续削减投入、领先指标同比和环比同时转弱、结果兑现中断，或政策与竞争变化显著压缩可实现空间，")
                .append("应先降级置信度，再评估是否转为挑战命题。任何单一公司异常都要区分公司份额变化与行业总量变化；")
                .append("只有行业总量与多个企业经营指标同向恶化，才足以判定周期层面的拐点。\n\n");
        out.append("### 下一次更新的最小数据包\n\n");
        out.append("下一轮报告至少需要：一项公司公告或权威原始披露、一项行业或业务统计、一个领先指标和一个明确的反向风险信号。")
                .append("每项数据要记录发布日期、原始来源、对应经营变量和支持方向。若免费新闻聚合仍无法补齐，应保留缺口，")
                .append("而不是用更多低相关标题填满证据数量。这样可以在控制模型成本和报告长度的同时，让每次更新真正改变或强化判断。\n\n");
        out.append("## 来源\n\n");
        if (evidence.isEmpty()) {
            out.append("- 本次运行没有筛选出满足相关性门槛的来源；报告仍给出低置信度基线判断，等待下一轮验证。\n");
        } else {
            int index = 1;
            for (ResearchEvidenceCard card : evidence) {
                Article article = card.getArticle();
                out.append(index++).append(". ").append(sourceReference(article)).append("\n");
            }
        }
        return out.toString();
    }

    private void appendEvidence(StringBuilder out, List<ResearchEvidenceCard> evidence, boolean counterOnly) {
        int shown = 0;
        for (ResearchEvidenceCard card : evidence) {
            boolean isCounter = "COUNTER".equals(card.getStance());
            if (counterOnly != isCounter) continue;
            Article article = card.getArticle();
            out.append("- **").append(stanceLabel(card.getStance())).append("｜")
                    .append(markdownText(value(article.getTitle(), "未命名证据"))).append("**：")
                    .append(ResearchReportPolicy.bound(card.getClaim(), ResearchReportPolicy.MAX_CLAIM_CHARACTERS))
                    .append("（来源：").append(value(article.getSourceName(), "未知来源"))
                    .append("；相关性 ").append(card.getRelevanceScore()).append("/100）\n");
            shown++;
        }
        if (shown == 0) {
            out.append(counterOnly ? "- 本次运行未发现明确反向证据，这是当前结论的重要局限。\n"
                    : "- 本次运行未筛选出达到相关性门槛的关键证据。\n");
        }
        out.append("\n");
    }

    private String executiveSummary(ResearchThesis thesis, String conclusion, int evidenceCount, int sourceCount,
                                    int support, int counter, int neutral, EvidenceSufficiency sufficiency) {
        String subject = value(thesis.getSubjectName(), "研究对象");
        StringBuilder text = new StringBuilder();
        text.append(conclusion).append("本次判断严格限定在运行内数据，共筛选 ").append(evidenceCount)
                .append(" 条高相关证据，来自 ").append(sourceCount).append(" 个来源，其中支持 ").append(support)
                .append(" 条、反向 ").append(counter).append(" 条、中性 ").append(neutral).append(" 条。")
                .append("报告没有把同日其他研究、泛科技新闻或与").append(subject).append("无直接关系的内容混入证据池。")
                .append("分析上把市场价格变化与基本面分开处理：价格反映预期和估值，需求、经营指标、实际结果与风险暴露才决定命题能否兑现。")
                .append("只要领先指标和实际结果没有同步转弱，价格回撤更接近预期重估；一旦需求、经营指引和结果连续恶化，结论就应转向谨慎。")
                .append("当前结论适合用作后续跟踪的基线，而不是一次性买卖判断。其价值在于明确了哪些事实会强化判断、哪些事实会推翻判断。")
                .append("后续应优先核验与研究对象直接相关的一手披露、领先经营指标、结果兑现指标以及政策和竞争风险。")
                .append("如果这些指标连续两个披露期同向改善，可以上调置信度；若只剩股价反弹而经营指标没有跟随，应下调置信度。")
                .append("来源之间若只是转载同一消息，不应被视为多重独立证据；报告已限制单一来源占比，但仍需在下一轮补充公司公告、监管披露或行业统计。")
                .append("在事实、推理和判断之间，本报告采用明确分层：文章与证据卡只承担事实输入，周期机制属于基于这些事实的推理，最终方向则是带置信度的判断。")
                .append("这意味着任何单条利好或利空都不能直接决定结论，只有多个独立来源指向同一关键变量，且该变量能传导到可观测结果，才会显著改变判断。")
                .append("对于尚未覆盖的数据，不使用常识性数字填空，也不把搜索结果排序当作重要性排序；证据缺口会保留在报告中，并转化为下一轮可执行的验证问题。")
                .append("阅读时应先看核心结论的条件，再看反方证据和失效信号，最后核对来源；这样可以避免只摘取支持原有观点的段落，也方便在新数据出现后快速修订。")
                .append("最终结论保留条件和边界，避免用模糊的‘信息不足’结束研究，也避免把低质量数量堆积误当成确定性。");
        if (!sufficiency.isSufficient()) {
            text.append("由于当前证据门槛尚未完全满足，本次仍输出方向性结论，但置信度已主动下调，缺口会作为下一轮检索任务继续验证。");
        }
        return ResearchReportPolicy.bound(text.toString(), ResearchReportPolicy.MAX_EXECUTIVE_SUMMARY_CHARACTERS);
    }

    private String conclusion(ResearchThesis thesis, String direction, String confidence, int support, int counter) {
        String subject = value(thesis.getSubjectName(), "研究对象");
        String question = value(thesis.getQuestion(), subject + "的研究命题");
        if ("SUPPORT".equals(direction) || "PARTIAL_SUPPORT".equals(direction)) {
            return "阶段性结论：现有证据更支持命题“" + question + "”，但" + subject + "内部或不同观察期仍会分化。"
                    + "单一价格变化暂不足以推翻判断，当前置信度为" + confidenceLabel(confidence)
                    + "；支持与反向证据比为 " + support + ":" + counter + "。";
        }
        if ("CHALLENGE".equals(direction) || "PARTIAL_CHALLENGE".equals(direction)) {
            return "阶段性结论：现有证据更倾向于挑战命题“" + question + "”，反向信号可能已包含基本面预期下修。"
                    + "当前置信度为" + confidenceLabel(confidence) + "；支持与反向证据比为 " + support + ":" + counter + "。";
        }
        return "阶段性结论：命题“" + question + "”尚未被证伪，但支持与转弱信号并存，仍需要后续经营或行业数据确认。"
                + "当前置信度为" + confidenceLabel(confidence) + "；支持与反向证据比为 " + support + ":" + counter + "。";
    }

    private void appendNextValidationSignals(StringBuilder out, ResearchThesis thesis) {
        if ("COMPANY".equalsIgnoreCase(value(thesis.getSubjectType(), ""))) {
            out.append("- 收入、利润率、现金流与管理层指引是否连续两个披露期改善。\n");
            out.append("- 订单、用户需求或核心业务量能否兑现到后续业绩。\n");
            out.append("- 竞争、监管、供应链和估值风险是否改变增长的可持续性。\n\n");
            return;
        }
        if ("PORTFOLIO".equalsIgnoreCase(value(thesis.getSubjectType(), ""))) {
            out.append("- 组合主要风险因子、行业暴露与相关性是否发生结构性变化。\n");
            out.append("- 成分资产盈利预期、估值和资金流是否出现同向恶化。\n");
            out.append("- 集中度、流动性和宏观冲击是否超过组合可承受边界。\n\n");
            return;
        }
        out.append("- 需求、供给、价格、库存与产能利用率是否连续两个观察期同向改善。\n");
        out.append("- 核心参与者的投入、订单或经营指引能否兑现到行业结果。\n");
        out.append("- 政策、竞争格局和技术替代是否改变可兑现市场空间。\n\n");
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

    private int count(List<ResearchEvidenceCard> evidence, String stance) {
        int count = 0;
        for (ResearchEvidenceCard card : evidence) if (stance.equals(card.getStance())) count++;
        return count;
    }

    private int sourceCount(List<ResearchEvidenceCard> evidence) {
        Set<String> sources = new HashSet<String>();
        for (ResearchEvidenceCard card : evidence) sources.add(ResearchSourceIdentity.resolve(card.getArticle()));
        return sources.size();
    }

    private String stanceLabel(String stance) {
        if ("SUPPORT".equals(stance)) return "支持";
        if ("COUNTER".equals(stance)) return "反向";
        return "中性";
    }

    private String confidenceLabel(String confidence) {
        if ("HIGH".equals(confidence)) return "高";
        if ("MEDIUM".equals(confidence)) return "中";
        return "低";
    }

    private String markdownText(String value) {
        return value.replace("[", "（").replace("]", "）").replace("\n", " ");
    }

    private String sourceReference(Article article) {
        String title = markdownText(value(article.getTitle(), "未命名来源"));
        String source = value(article.getSourceName(), "未知来源");
        String articleId = article.getId() == null ? "" : " · 文章 #" + article.getId();
        String url = article.getUrl();
        if (url != null && url.length() <= 500) {
            return "[" + title + "](" + url + ") — " + source + articleId;
        }
        return title + " — " + source + articleId;
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

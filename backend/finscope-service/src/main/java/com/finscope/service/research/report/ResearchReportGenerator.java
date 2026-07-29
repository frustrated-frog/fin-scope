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
        String title = value(thesis.getSubjectName(), "研究命题") + "深度研究报告";
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
        out.append("> 研究日期：").append(LocalDate.now()).append("  \n")
                .append("> 原始问题：").append(value(thesis.getQuestion(), "未命名命题")).append("  \n")
                .append("> 判断：").append(direction(support, counter, sufficiency.isSufficient()))
                .append(" · 置信度：").append(confidenceLabel(confidence)).append("\n\n");
        out.append("## 核心结论\n\n").append(conclusion).append(' ')
                .append(references(evidence, null, 3)).append("\n\n")
                .append("**置信度依据：** 本次判断使用 ").append(evidence.size()).append(" 条可追溯证据，覆盖 ")
                .append(sourceCount(evidence)).append(" 个独立来源；支持、反向和中性证据分别为 ")
                .append(support).append("、").append(counter).append("、").append(neutral)
                .append(" 条。置信度同时受到公开资料完整性、观察期长度和反方覆盖程度约束。\n\n");
        out.append("## 关键认识\n\n");
        int insightCount = Math.min(5, evidence.size());
        for (int index = 0; index < insightCount; index++) {
            ResearchEvidenceCard card = evidence.get(index);
            out.append("- **").append(stanceLabel(card.getStance())).append("认识 ").append(index + 1)
                    .append("：** ").append(ResearchReportPolicy.bound(card.getClaim(), 260)).append(' ')
                    .append(reference(index)).append("。这条材料的作用是限定当前判断，而不是单独决定最终方向。\n");
        }
        if (insightCount == 0) out.append("- 当前没有足以形成对象特定认识的证据，以下内容仅保留研究框架和待验证项。\n");
        out.append('\n');
        out.append("## 执行摘要\n\n").append(summary).append("\n\n");
        out.append("## 研究范围与口径\n\n")
                .append("- **研究对象：** ").append(value(thesis.getSubjectName(), "目标对象")).append("。\n")
                .append("- **研究问题：** ").append(value(thesis.getQuestion(), "未命名命题")).append("。\n")
                .append("- **证据边界：** 只使用本次运行筛选出的公开材料，不把搜索排名、重复转载或模型常识当作事实。\n")
                .append("- **判断口径：** 区分事实、机制推理和阶段判断；价格信号不能替代经营、行业或监管事实。\n")
                .append("- **本次不能回答：** 未被当前证据覆盖的长期结果、精确预测和投资买卖时点。\n\n");
        out.append("## 关键事实与数字\n\n")
                .append("| 证据 | 立场 | 可验证事实 | 来源层级 | 相关性 |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        for (int index = 0; index < evidence.size(); index++) {
            ResearchEvidenceCard card = evidence.get(index);
            out.append("| ").append(reference(index)).append(" | ").append(stanceLabel(card.getStance()))
                    .append(" | ").append(tableText(ResearchReportPolicy.bound(card.getClaim(), 260)))
                    .append(" | ").append(value(card.getSourceTier(), "T3"))
                    .append(" | ").append(card.getRelevanceScore()).append("/100 |\n");
        }
        if (evidence.isEmpty()) out.append("| — | 未知 | 当前没有可引用事实 | — | — |\n");
        out.append("\n## 发生了什么\n\n")
                .append("本次运行把与研究对象直接相关的材料聚合为一个有边界的证据集。当前证据分布为支持 ")
                .append(support).append(" 条、反向 ").append(counter).append(" 条、中性 ").append(neutral)
                .append(" 条。材料之间若只是转述同一事件，只能提高线索可见度，不能自动提高结论可信度。")
                .append(references(evidence, null, 4)).append("\n\n")
                .append("这些事实首先说明市场或基本面出现了值得跟踪的变化，但是否足以回答总命题，仍取决于变化能否通过")
                .append("“外部驱动—关键指标—结果兑现”形成连续证据链。\n\n");
        out.append("## 命题拆解与逐题判断\n\n")
                .append("### 1. 当前事实是否直接对应研究对象？\n\n")
                .append("**当前回答：** 已筛选材料均通过对象相关性门槛，但不同来源的直接性和完整性不同。")
                .append(references(evidence, null, 3)).append("\n\n")
                .append("**对总命题的影响：** 高相关来源可以建立事实底座；摘要材料和间接报道只能作为补充线索。\n\n")
                .append("### 2. 支持方向是否有独立来源交叉验证？\n\n")
                .append("**当前回答：** 支持证据为 ").append(support).append(" 条，必须结合来源独立性判断，不能按标题数量机械累加。")
                .append(references(evidence, "SUPPORT", 3)).append("\n\n")
                .append("**对总命题的影响：** 只有不同主体、不同披露渠道指向同一关键变量，才可以提高结论置信度。\n\n")
                .append("### 3. 哪些反向事实可能推翻当前解释？\n\n")
                .append("**当前回答：** 当前反向证据为 ").append(counter).append(" 条。若反向材料覆盖需求、指引或实际结果，")
                .append("其权重应高于单纯价格波动。").append(references(evidence, "COUNTER", 3)).append("\n\n")
                .append("**仍未知：** 缺少的连续披露期、经营口径或反方一手资料仍需在下一轮验证。\n\n");
        out.append("## 核心证据链\n\n");
        int chainCount = Math.min(4, evidence.size());
        for (int index = 0; index < chainCount; index++) {
            ResearchEvidenceCard card = evidence.get(index);
            out.append("### 证据链 ").append(index + 1).append("\n\n")
                    .append("- **事实：** ").append(ResearchReportPolicy.bound(card.getClaim(), 300)).append(' ')
                    .append(reference(index)).append("\n")
                    .append("- **推理：** 该事实影响研究命题的关键变量，但仍需与其他独立来源和后续结果交叉验证。\n")
                    .append("- **判断：** 当前将其计为“").append(stanceLabel(card.getStance()))
                    .append("”材料，不把单条证据扩大为确定性结论。\n")
                    .append("- **替代解释：** 变化也可能来自短期事件、统计口径、流通结构或预期调整。\n\n");
        }
        if (chainCount == 0) out.append("当前没有可建立的证据链，所有对象特定结论均应视为待验证。\n\n");
        out.append("## 反方解释与争议\n\n");
        String counterRefs = references(evidence, "COUNTER", 4);
        if (counter == 0) {
            out.append("**最强反方观点：** 当前材料没有覆盖足够的反向事实，这本身就是结论局限。不能把“没有搜到反证”解释为“反证不存在”。\n\n");
        } else {
            out.append("**最强反方观点：** 现有反向材料提示，当前变化可能只是短期价格发现、预期修正或局部分化，并不必然代表长期趋势成立。")
                    .append(counterRefs).append("\n\n");
        }
        out.append("**当前回应：** 维持带条件的阶段性判断，并要求后续一手披露和同步指标确认。\n\n")
                .append("**反方成为更优解释的条件：** 需求、经营指引和实际结果连续恶化，或关键支持证据被后续一手资料否定。\n\n");
        out.append("## 机制与情景推演\n\n");
        out.append("价格变化本身不能单独确认或推翻命题。若关键经营指标、外部需求和管理层指引持续改善，")
                .append("价格回落更可能是预期或估值修正；若领先指标、实际结果和风险暴露同时恶化，则应把它视为基本面转向。\n\n")
                .append("### 情景 1 · 基准\n\n");
        out.append("- **基准情景**：核心指标温和改善，但不同业务或细分方向继续分化；结果兑现与估值匹配度决定后续表现。\n");
        out.append("\n### 情景 2 · 上行\n\n- 多个独立领先指标连续改善，并在后续披露中兑现为结果上修；此时可上调结论置信度。\n")
                .append("\n### 情景 3 · 下行\n\n- 需求、经营指引和实际结果同步转弱，且反向风险连续两个观察期未缓解；此时应挑战命题。\n\n");
        out.append("## 最终认识与未知项\n\n")
                .append("当前可以形成的认识，是现有公开证据更接近“").append(direction(support, counter, sufficiency.isSufficient()))
                .append("”而非确定性答案。这个认识的价值在于明确了支持方向、反方解释和下一次更新条件。\n\n")
                .append("- **可以形成的认识：** 结论必须建立在对象特定事实和独立来源交叉验证之上。\n")
                .append("- **可以形成的认识：** 单日价格、单篇报道或同源转载不足以证明长期命题。\n")
                .append("- **仍然未知：** 尚未被本次材料覆盖的连续经营结果、精确口径和长期兑现程度。\n")
                .append("- **仍然未知：** 当前反方材料能否在后续披露期得到重复确认。\n\n");
        out.append("## 跟踪清单与失效条件\n\n");
        appendNextValidationSignals(out, thesis);
        out.append("### 证据局限\n\n");
        if (sufficiency.getWarnings().isEmpty()) {
            out.append("- 当前证据达到最低覆盖要求，但仍属于公开信息条件下的阶段性判断。\n");
        } else {
            for (String warning : sufficiency.getWarnings()) out.append("- ").append(warning).append("。\n");
        }
        out.append("\n### 结论升级条件\n\n");
        out.append("若至少两个独立的一手或高可信来源显示领先指标改善，同时后续实际结果连续兑现，且关键风险没有反向恶化，")
                .append("可上调当前判断；如果只有媒体预测或市场价格上涨，而公告、经营指标或行业数据没有确认，应维持原置信度。\n\n");
        out.append("### 结论降级与失效条件\n\n");
        out.append("若需求主体连续削减投入、领先指标同比和环比同时转弱、结果兑现中断，或政策与竞争变化显著压缩可实现空间，")
                .append("应先降级置信度，再评估是否转为挑战命题。\n\n");
        out.append("## 证据附录\n\n");
        if (evidence.isEmpty()) {
            out.append("- 本次运行没有筛选出满足相关性门槛的来源；报告仍给出低置信度基线判断，等待下一轮验证。\n");
        } else {
            for (int index = 0; index < evidence.size(); index++) {
                ResearchEvidenceCard card = evidence.get(index);
                Article article = card.getArticle();
                out.append("### E").append(index + 1).append(" · ")
                        .append(markdownText(value(article.getTitle(), "未命名证据"))).append("\n\n")
                        .append("- 来源：").append(value(article.getSourceName(), "未知来源"))
                        .append("（").append(value(card.getSourceTier(), "T3")).append("）\n")
                        .append("- 立场：").append(stanceLabel(card.getStance()))
                        .append("；相关性：").append(card.getRelevanceScore()).append("/100\n")
                        .append("- 事实摘录：").append(ResearchReportPolicy.bound(card.getClaim(), 420)).append("\n")
                        .append("- 原文：").append(sourceReference(article)).append("\n\n");
            }
        }
        return out.toString();
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

    private String tableText(String value) {
        return markdownText(value(value, "未提供")).replace("|", "｜");
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
        for (ResearchEvidenceCard card : evidence) sources.add(card.getSourceIdentity());
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

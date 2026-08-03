package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class DeterministicReportBlueprintBuilder {
    public ResearchReportBlueprint build(ResearchThesis thesis,
                                         List<ResearchEvidenceDossier> dossier,
                                         boolean sufficient) {
        List<ResearchEvidenceDossier> evidence = dossier == null
                ? Collections.<ResearchEvidenceDossier>emptyList() : dossier;
        ResearchReportBlueprint result = new ResearchReportBlueprint();
        int support = count(evidence, "SUPPORT");
        int counter = count(evidence, "COUNTER");
        result.setDirection(direction(support, counter, sufficient));
        result.setConfidence(confidence(evidence, sufficient));
        result.setDirectAnswer(directAnswer(thesis, evidence, result.getDirection(), sufficient));
        result.setConfidenceBasis(confidenceBasis(evidence, support, counter, sufficient));
        result.setTimeRange(timeRange(evidence));
        result.setDefinitions(definitions(thesis));
        result.setExcludedQuestions(Arrays.asList(
                "当前证据未覆盖的长期结果和精确预测",
                "脱离后续正式披露的买卖时点或目标价格"));
        buildInsights(result, evidence);
        buildSubQuestions(result, thesis, evidence);
        buildArgumentChains(result, thesis, evidence);
        buildCounterargument(result, thesis, evidence);
        buildScenarios(result, thesis, evidence);
        buildKnowledge(result, thesis);
        buildWatchItems(result, thesis);
        return result;
    }

    private void buildInsights(ResearchReportBlueprint result, List<ResearchEvidenceDossier> evidence) {
        int target = Math.min(5, Math.max(3, evidence.size()));
        for (int index = 0; index < target; index++) {
            ResearchEvidenceDossier item = evidence.isEmpty() ? null : evidence.get(index % evidence.size());
            ResearchReportBlueprint.KeyInsight insight = new ResearchReportBlueprint.KeyInsight();
            if (item == null) {
                insight.setFinding("当前缺少可形成对象特定认识的公开证据");
                insight.setMeaning("结论只能保留研究问题和待验证变量，不能扩写为事实判断");
            } else {
                insight.setFinding(shortFact(item.getFactExcerpt(), 150));
                insight.setMeaning(meaning(item.getStance()));
                insight.setEvidenceRefs(Collections.singletonList(item.getEvidenceRef()));
            }
            result.getKeyInsights().add(insight);
        }
    }

    private void buildSubQuestions(ResearchReportBlueprint result,
                                   ResearchThesis thesis,
                                   List<ResearchEvidenceDossier> evidence) {
        List<String> questions = questions(thesis);
        for (int index = 0; index < questions.size(); index++) {
            ResearchEvidenceDossier primary = evidence.isEmpty() ? null : evidence.get(index % evidence.size());
            ResearchEvidenceDossier counter = first(evidence, "COUNTER");
            ResearchReportBlueprint.SubQuestion item = new ResearchReportBlueprint.SubQuestion();
            item.setKey("question_" + (index + 1));
            item.setQuestion(questions.get(index));
            item.setAnswer(primary == null
                    ? "当前公开证据尚不足以回答该子问题。"
                    : "当前最直接的材料显示：" + shortFact(primary.getFactExcerpt(), 220));
            if (primary != null) item.setEvidenceRefs(Collections.singletonList(primary.getEvidenceRef()));
            if (counter != null && (primary == null || !counter.getEvidenceRef().equals(primary.getEvidenceRef()))) {
                item.setCounterEvidenceRefs(Collections.singletonList(counter.getEvidenceRef()));
            }
            item.setImpact("该子问题决定当前现象能否从可验证事实传导到研究问题的阶段性答案，"
                    + "需要把短期信号、持续性和替代解释分开判断。");
            item.setUnknowns(Collections.singletonList("缺少连续观察期或同口径后续披露时，当前判断仍可能变化"));
            result.getSubQuestions().add(item);
        }
    }

    private void buildArgumentChains(ResearchReportBlueprint result,
                                     ResearchThesis thesis,
                                     List<ResearchEvidenceDossier> evidence) {
        int target = Math.min(4, Math.max(2, evidence.size()));
        for (int index = 0; index < target; index++) {
            ResearchEvidenceDossier item = evidence.isEmpty() ? null : evidence.get(index % evidence.size());
            ResearchReportBlueprint.ArgumentChain chain = new ResearchReportBlueprint.ArgumentChain();
            if (item == null) {
                chain.setFact("当前没有可引用事实，证据缺口本身限制了结论强度。");
                chain.setInference("没有对象特定事实就无法建立从驱动因素到结果变量的传导关系。");
                chain.setJudgment("维持低置信度并等待新增证据。");
                chain.setAlternativeExplanation("搜索范围或公开披露不足也可能造成暂时缺证。");
            } else {
                chain.setFact(shortFact(item.getFactExcerpt(), 320));
                chain.setInference(inference(thesis, item));
                chain.setJudgment(judgment(item.getStance()));
                chain.setAlternativeExplanation(alternative(item.getStance()));
                chain.setEvidenceRefs(Collections.singletonList(item.getEvidenceRef()));
            }
            result.getArgumentChains().add(chain);
        }
    }

    private void buildCounterargument(ResearchReportBlueprint result,
                                      ResearchThesis thesis,
                                      List<ResearchEvidenceDossier> evidence) {
        ResearchEvidenceDossier counterEvidence = first(evidence, "COUNTER");
        ResearchEvidenceDossier boundary = counterEvidence == null && !evidence.isEmpty()
                ? evidence.get(evidence.size() - 1) : counterEvidence;
        ResearchReportBlueprint.Counterargument counter = new ResearchReportBlueprint.Counterargument();
        if (counterEvidence != null) {
            counter.setClaim(shortFact(counterEvidence.getFactExcerpt(), 260));
            counter.setResponse("该材料说明主结论存在实质约束，但是否成为主导解释，仍取决于同类信号能否在后续披露中重复出现。");
        } else {
            counter.setClaim("当前证据没有提供独立、对象特定的反向事实；未发现反证不能被解释为反证不存在。");
            counter.setResponse("因此不把证据数量优势直接升级为高置信度结论，并把补充反向一手材料列为下一轮优先任务。");
        }
        if (boundary != null) counter.setEvidenceRefs(Collections.singletonList(boundary.getEvidenceRef()));
        counter.setBecomesDominantWhen(Arrays.asList(
                "后续正式披露连续否定当前支持方向",
                "关键结果变量转弱且反向信号得到两个以上独立来源确认"));
        result.setStrongestCounterargument(counter);
    }

    private void buildScenarios(ResearchReportBlueprint result,
                                ResearchThesis thesis,
                                List<ResearchEvidenceDossier> evidence) {
        List<String> coreRefs = firstRefs(evidence, null, 3);
        List<String> counterRefs = firstRefs(evidence, "COUNTER", 2);
        addScenario(result, "基准情景", "现有关键变量延续当前方向，但不同来源和观察期仍有分化",
                "当前事实继续影响市场预期或经营判断，后续结果逐步验证其持续性",
                "正式披露与当前方向大体一致，但没有出现足以显著上调置信度的新事实",
                "维持当前阶段性结论和置信度", coreRefs);
        addScenario(result, "上行情景", "至少两个独立来源确认关键变量改善，且结果变量同步兑现",
                "支持性驱动从预期或单次事件传导到可持续、可观察的结果",
                "对象特定指标连续改善，原有不确定性下降",
                "强化当前结论并允许提高置信度", coreRefs);
        addScenario(result, "下行情景", "反向事实连续出现，或后续披露否定当前关键假设",
                "支持性传导中断，风险从替代解释转为主导解释",
                "关键结果转弱、指引下调或风险暴露扩大",
                "下调或推翻当前结论", counterRefs.isEmpty() ? coreRefs : counterRefs);
    }

    private void addScenario(ResearchReportBlueprint result, String name, String trigger,
                             String mechanism, String observable, String impact, List<String> refs) {
        ResearchReportBlueprint.Scenario item = new ResearchReportBlueprint.Scenario();
        item.setName(name);
        item.setTrigger(trigger);
        item.setMechanism(mechanism);
        item.setObservableResult(observable);
        item.setImpact(impact);
        item.setEvidenceRefs(new ArrayList<String>(refs));
        result.getScenarios().add(item);
    }

    private void buildKnowledge(ResearchReportBlueprint result, ResearchThesis thesis) {
        result.setKnowledgeTakeaways(Arrays.asList(
                "当前结论只对“" + value(thesis.getQuestion()) + "”及本次证据覆盖期有效",
                "单条报道、单日价格或同源转载不能独立证明长期趋势",
                "只有关键变量在后续正式披露中兑现，才能提高判断确定性"));
        result.setUnknowns(Arrays.asList(
                "当前公开材料没有覆盖的连续观察期和可比口径",
                "最强反方解释能否在后续独立来源中得到重复确认"));
    }

    private void buildWatchItems(ResearchReportBlueprint result, ResearchThesis thesis) {
        List<String> metrics = watchMetrics(thesis);
        for (String metric : metrics) {
            ResearchReportBlueprint.WatchItem item = new ResearchReportBlueprint.WatchItem();
            item.setMetric(metric);
            item.setBaseline("以本次证据中已披露事实为基线；未披露数值不作估算");
            item.setFrequency("正式公告、定期报告或权威行业数据更新时");
            item.setUpgradeCondition("至少两个独立高可信来源同向改善，并由后续结果确认");
            item.setDowngradeCondition("连续两个观察期转弱、正式披露否定关键事实或反向风险成为主导");
            result.getWatchItems().add(item);
        }
    }

    private List<String> questions(ResearchThesis thesis) {
        String text = normalized(value(thesis.getQuestion()) + " " + value(thesis.getSubjectName()));
        if (containsAny(text, "上市", "市值", "成交", "换手", "股价", "交易")) {
            return Arrays.asList("已经确认的价格、成交与流通事实是什么？",
                    "流通结构和估值口径如何影响表面市值与交易表现？",
                    "当前变化更接近持续性重估还是短期价格发现？",
                    "哪些反向事实足以推翻当前市场解释？");
        }
        if (containsAny(text, "行业", "周期", "供需", "库存", "价格", "产能")) {
            return Arrays.asList("需求、供给与价格信号分别发生了什么变化？",
                    "库存、产能利用率和资本开支是否形成连续传导？",
                    "不同企业或细分环节为何出现分化？",
                    "哪些政策、竞争或周期风险可能中断当前趋势？");
        }
        if (containsAny(text, "政策", "意见", "办法", "监管", "实施")) {
            return Arrays.asList("政策原文确认了哪些核心条款和适用范围？",
                    "政策通过什么机制影响研究对象？",
                    "落地执行需要哪些配套条件和资源？",
                    "哪些约束会导致政策效果弱于预期？");
        }
        return Arrays.asList("当前已经确认的对象特定事实是什么？",
                "这些事实通过什么关键变量影响原研究问题？",
                "当前信号是否已经兑现为可持续结果？",
                "哪些反方事实和未知项可能改变结论？");
    }

    private List<String> watchMetrics(ResearchThesis thesis) {
        String text = normalized(value(thesis.getQuestion()));
        if (containsAny(text, "上市", "市值", "成交", "换手", "股价", "交易")) {
            return Arrays.asList("成交额与换手中枢", "自由流通结构与解禁变化", "估值口径与可比对象",
                    "后续经营或行业结果兑现");
        }
        if (containsAny(text, "行业", "周期", "供需", "库存", "价格", "产能")) {
            return Arrays.asList("需求与订单", "价格与库存", "产能利用率与资本开支", "竞争和政策约束");
        }
        return Arrays.asList("收入、利润率、现金流与管理层指引", "核心业务或结果指标",
                "现金流或投入强度", "关键反向风险");
    }

    private List<String> definitions(ResearchThesis thesis) {
        String text = normalized(value(thesis.getQuestion()));
        if (containsAny(text, "市值", "成交", "换手", "股价")) {
            return Arrays.asList("总市值、自由流通市值和实际成交资金规模必须分开理解",
                    "短期价格与换手反映价格发现和预期分歧，不能直接替代长期价值判断");
        }
        return Arrays.asList("事实只取自本次运行内可追溯材料",
                "事实、机制推理和阶段判断分层表达，推理不能被当作新增事实");
    }

    private String directAnswer(ResearchThesis thesis, List<ResearchEvidenceDossier> evidence,
                                String direction, boolean sufficient) {
        String answer = "阶段性结论：关于“" + value(thesis.getQuestion()) + "”，现有证据形成“"
                + directionLabel(direction) + "”判断。";
        if (!evidence.isEmpty()) answer += "最直接的事实基础是" + shortFact(evidence.get(0).getFactExcerpt(), 140);
        ResearchEvidenceDossier counter = first(evidence, "COUNTER");
        if (counter != null) answer += "同时，" + shortFact(counter.getFactExcerpt(), 110) + "，因此结论仍有明确边界。";
        if (!sufficient) answer += "当前资料尚未覆盖全部必要方向，不能据此形成高置信度结论。";
        return answer;
    }

    private String confidenceBasis(List<ResearchEvidenceDossier> evidence, int support, int counter, boolean sufficient) {
        int primary = 0;
        java.util.Set<String> sources = new java.util.HashSet<String>();
        for (ResearchEvidenceDossier item : evidence) {
            sources.add(value(item.getSourceIdentity()).toLowerCase(Locale.ROOT));
            if ("T1".equals(item.getSourceTier()) || "PRIMARY".equals(item.getSourceTier())
                    || "AUTHORITATIVE".equals(item.getSourceTier())) primary++;
        }
        return "本次蓝图使用" + evidence.size() + "条证据、" + sources.size() + "个独立来源，其中高等级来源"
                + primary + "条，支持与反向材料分别为" + support + "条和" + counter + "条。"
                + (sufficient ? "证据达到最低覆盖要求，但仍受公开资料和观察期限制。"
                : "证据尚未达到完整覆盖要求，因此置信度受到主动限制。");
    }

    private String timeRange(List<ResearchEvidenceDossier> evidence) {
        java.time.LocalDateTime earliest = null;
        java.time.LocalDateTime latest = null;
        for (ResearchEvidenceDossier item : evidence) {
            if (item.getPublishedAt() == null) continue;
            if (earliest == null || item.getPublishedAt().isBefore(earliest)) earliest = item.getPublishedAt();
            if (latest == null || item.getPublishedAt().isAfter(latest)) latest = item.getPublishedAt();
        }
        if (earliest == null) return "本次运行公开证据覆盖期；部分材料未提供发布时间";
        return earliest.toLocalDate() + " 至 " + latest.toLocalDate();
    }

    private String inference(ResearchThesis thesis, ResearchEvidenceDossier item) {
        return "该事实先改变与“" + value(thesis.getQuestion()) + "”相关的关键变量和市场预期，"
                + "再通过后续正式披露或可观察结果决定影响是否持续。";
    }

    private String judgment(String stance) {
        if ("SUPPORT".equals(stance)) return "当前将其视为支持性证据，但不能由单条材料外推为确定性长期结论。";
        if ("COUNTER".equals(stance)) return "当前将其视为能削弱主解释的反向证据，需要提高后续验证优先级。";
        return "当前将其视为限定研究口径的中性事实，主要用于约束结论边界。";
    }

    private String alternative(String stance) {
        return "同一变化也可能来自短期事件、统计口径、流通结构或预期调整；只有后续结果按相同方向兑现，"
                + "才能排除这些替代解释。";
    }

    private String meaning(String stance) {
        if ("SUPPORT".equals(stance)) return "这项事实强化当前方向，但仍需后续结果和独立来源确认持续性";
        if ("COUNTER".equals(stance)) return "这项事实构成实质反方约束，决定当前结论不能被表达为单边确定性判断";
        return "这项事实用于界定观察口径和结论边界，避免把表面现象直接外推为长期结论";
    }

    private String direction(int support, int counter, boolean sufficient) {
        if (support == 0 && counter > 0) return sufficient ? "CHALLENGE" : "PARTIAL_CHALLENGE";
        if (counter == 0 && support > 0) return sufficient ? "SUPPORT" : "PARTIAL_SUPPORT";
        if (support >= counter * 2 + 1) return "PARTIAL_SUPPORT";
        if (counter >= support * 2 + 1) return "PARTIAL_CHALLENGE";
        return "MIXED";
    }

    private String confidence(List<ResearchEvidenceDossier> evidence, boolean sufficient) {
        if (!sufficient) return "LOW";
        int highTier = 0;
        for (ResearchEvidenceDossier item : evidence) {
            if ("T1".equals(item.getSourceTier()) || "PRIMARY".equals(item.getSourceTier())) highTier++;
        }
        return evidence.size() >= 8 && highTier >= 3 ? "HIGH" : "MEDIUM";
    }

    private int count(List<ResearchEvidenceDossier> evidence, String stance) {
        int result = 0;
        for (ResearchEvidenceDossier item : evidence) if (stance.equals(item.getStance())) result++;
        return result;
    }

    private ResearchEvidenceDossier first(List<ResearchEvidenceDossier> evidence, String stance) {
        for (ResearchEvidenceDossier item : evidence) if (stance.equals(item.getStance())) return item;
        return null;
    }

    private List<String> firstRefs(List<ResearchEvidenceDossier> evidence, String stance, int limit) {
        List<String> result = new ArrayList<String>();
        for (ResearchEvidenceDossier item : evidence) {
            if (stance != null && !stance.equals(item.getStance())) continue;
            result.add(item.getEvidenceRef());
            if (result.size() >= limit) break;
        }
        return result;
    }

    private String directionLabel(String direction) {
        if ("SUPPORT".equals(direction)) return "支持";
        if ("PARTIAL_SUPPORT".equals(direction)) return "部分支持";
        if ("PARTIAL_CHALLENGE".equals(direction)) return "部分挑战";
        if ("CHALLENGE".equals(direction)) return "挑战";
        return "混合";
    }

    private String shortFact(String value, int limit) {
        String clean = ResearchFactText.completeExcerpt(value, limit);
        return clean.isEmpty() ? "当前公开材料未提供可复述事实" : clean;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String normalized(String value) {
        return value(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}

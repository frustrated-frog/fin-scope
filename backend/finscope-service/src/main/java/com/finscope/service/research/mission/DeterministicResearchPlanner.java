package com.finscope.service.research.mission;

import com.finscope.service.research.method.ResearchMethodRegistry;
import com.finscope.service.research.method.ResearchMethodSelection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class DeterministicResearchPlanner {
    private final ResearchMethodRegistry methodRegistry;

    public DeterministicResearchPlanner() {
        this(ResearchMethodRegistry.defaults());
    }

    @Autowired
    public DeterministicResearchPlanner(ResearchMethodRegistry methodRegistry) {
        this.methodRegistry = methodRegistry;
    }

    public ResearchMissionDraft plan(ResearchPlanningInput input) {
        String subject = compact(input == null ? null : input.getSubjectName(), "研究对象");
        String question = compact(input == null ? null : input.getQuestion(), "研究命题");
        ResearchMissionDraft draft = new ResearchMissionDraft();
        ResearchMethodSelection methods = methodRegistry.recommendedSelection(input);
        draft.setResearchType(methods.getResearchType());
        draft.setMethodCodes(methods.getMethodCodes());
        draft.setRequiredEvidence(methods.getRequiredEvidence());
        draft.setRequiredCalculations(methods.getRequiredCalculations());
        draft.setCounterChecks(methods.getCounterChecks());
        draft.setCompletionCriteria(methods.getCompletionCriteria());
        draft.setScopeSummary("围绕“" + question + "”核对需求、供给、公司兑现与反方风险");
        draft.setSuccessCriteria(Arrays.asList(
                "至少六条有效证据",
                "至少两个独立来源",
                "同时包含支持与反方或风险证据"));

        List<ResearchMissionTaskDraft> tasks = new ArrayList<ResearchMissionTaskDraft>();
        tasks.add(task("baseline_scan", "基线来源扫描", "现有信息源提供了哪些相关事实？",
                "COLLECT", "source_scan", "BASELINE", Collections.<String>emptyList(),
                null, "先建立已有证据基线", "配置来源中的相关文章"));
        String stockCode = input == null ? "" : compactCode(input.getSubjectCode());
        boolean financialQuality = methods.getMethodCodes().contains("FINANCIAL_STATEMENT_QUALITY");
        boolean companyQuality = methods.getMethodCodes().contains("COMPANY_QUALITY");
        if (!stockCode.isEmpty()) {
            tasks.add(task("research_map", "研究地图与关键变量", "哪些系统变化和关键变量决定命题成立？",
                    "SEARCH", "public_news_search", "BREADTH", Arrays.asList("baseline_scan"),
                    subject + (companyQuality ? " 行业结构 商业模式 竞争壁垒 治理 关键变量"
                            : " 行业结构 商业模式 关键变量 最新变化"),
                    "先建立研究地图，避免直接堆叠零散新闻", "行业结构、商业模式与关键变量"));
            tasks.add(task("primary_disclosure", "公司一手披露",
                    financialQuality ? "多期财报中的利润、现金流和资产质量是否相互印证？"
                            : "公司公告和财报披露了哪些可核验事实？",
                    "SEARCH", "research_material_search", "PRIMARY", Arrays.asList("research_map"),
                    stockCode + (financialQuality
                            ? " ANNOUNCEMENT 多期财报 利润 经营现金流 应收 存货 审计意见"
                            : " ANNOUNCEMENT 财报 业绩 经营"),
                    "优先用公司和监管披露建立事实基线",
                    financialQuality ? "利润表、资产负债表、现金流量表、附注与审计意见" : "公告、财报和监管披露"));
            tasks.add(task("professional_context", "专业资料与行业语境",
                    financialQuality ? "同行和专业机构如何解释盈利质量与资本回报变化？"
                            : "专业机构如何解释公司的经营变化？",
                    "SEARCH", "research_material_search", "BREADTH", Arrays.asList("primary_disclosure"),
                    stockCode + (financialQuality
                            ? " BROKER_REPORT 盈利质量 杜邦 同行 资本回报"
                            : " BROKER_REPORT 经营 行业 估值"),
                    "用专业资料补充行业语境并与一手材料交叉核对",
                    financialQuality ? "同行财务指标、杜邦拆解和盈利质量解释" : "券商研报与行业资料"));
        }
        String supportTaskKey = stockCode.isEmpty() ? "search_support" : "crosscheck_chain";
        tasks.add(task(supportTaskKey, stockCode.isEmpty() ? "支持证据搜索" : "产业链交叉核对",
                stockCode.isEmpty() ? "哪些最新事实支持该命题？" : "客户、供应商和竞争对手的事实是否印证公司披露？",
                "SEARCH", "public_news_search", "SUPPORT", dependencies(stockCode, "professional_context", "baseline_scan"),
                stockCode.isEmpty() ? subject + " 资本开支 订单 需求 最新公告"
                        : subject + (companyQuality
                        ? " 商业模式 客户 供应商 竞争对手 壁垒 资本配置 交叉验证"
                        : " 客户 供应商 竞争对手 订单 需求 交叉验证"),
                "验证核心驱动是否继续成立",
                companyQuality ? "业务结构、竞争壁垒、治理与资本配置的交叉验证信息"
                        : "订单、需求或产业链交叉验证信息"));
        tasks.add(task("search_counter", "反方证据搜索", "哪些事实可能推翻或限制该命题？",
                "SEARCH", "public_news_search", "COUNTER", dependencies(stockCode, supportTaskKey, "baseline_scan"),
                subject + (financialQuality
                        ? " 非经常性损益 应收 存货 减值 资本化 审计意见 反方证据"
                        : companyQuality ? " 替代技术 集中度 治理 资本配置 风险 反方证据"
                        : " 资本开支 风险 下调 延迟 反方证据"),
                "主动寻找证伪与边界",
                financialQuality ? "非经常性损益、应收存货背离、减值资本化和审计风险证据"
                        : "下调、延迟、风险或需求转弱证据"));
        if (stockCode.isEmpty()) {
            tasks.add(task("search_primary", "一手证据搜索", "是否存在公司或机构的一手披露？",
                    "SEARCH", "public_news_search", "PRIMARY", Arrays.asList("baseline_scan"),
                    subject + " 公司公告 财报 官方数据 原始来源", "提高来源独立性和可追溯性", "公司公告、财报或官方数据"));
        }
        List<String> evidenceDependencies = stockCode.isEmpty()
                ? Arrays.asList("search_support", "search_counter", "search_primary")
                : Arrays.asList("primary_disclosure", "professional_context", supportTaskKey, "search_counter");
        tasks.add(task("assess_evidence", "证据充分性判断", "当前证据是否达到研究合同标准？",
                "ASSESS", "evidence_assess", "ASSESS",
                evidenceDependencies,
                null, "用确定性门槛决定是否停止搜索", "证据数量、独立来源和正反覆盖"));
        tasks.add(task("synthesize_report", "研究报告合成", "证据支持怎样的阶段性结论？",
                "SYNTHESIS", "report_synthesis", "SYNTHESIS", Arrays.asList("assess_evidence"),
                null, "只用冻结证据形成可溯源结论", "带边界、风险与后续验证的研究报告"));
        draft.setTasks(tasks);
        return draft;
    }

    private ResearchMissionTaskDraft task(String key,
                                          String title,
                                          String question,
                                          String taskType,
                                          String toolCode,
                                          String intent,
                                          List<String> dependencies,
                                          String queryText,
                                          String rationale,
                                          String expectedEvidence) {
        ResearchMissionTaskDraft task = new ResearchMissionTaskDraft();
        task.setTaskKey(key);
        task.setTitle(title);
        task.setQuestion(question);
        task.setTaskType(taskType);
        task.setToolCode(toolCode);
        task.setIntent(intent);
        task.setDependencies(dependencies);
        task.setParallelGroup("public_news_search".equals(toolCode)
                || "research_material_search".equals(toolCode) ? "evidence_search" : null);
        task.setQueryText(queryText);
        task.setRationale(rationale);
        task.setExpectedEvidence(expectedEvidence);
        return task;
    }

    private String compactCode(String value) {
        if (value == null) return "";
        String code = value.trim();
        return code.matches("\\d{6}") ? code : "";
    }

    private List<String> dependencies(String stockCode, String stockTask, String genericTask) {
        return Arrays.asList(stockCode.isEmpty() ? genericTask : stockTask);
    }

    private String compact(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String compacted = value.trim().replaceAll("\\s+", " ");
        return compacted.length() <= 80 ? compacted : compacted.substring(0, 80);
    }
}

package com.finscope.service.research.mission;

import com.finscope.domain.research.mission.ResearchToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResearchToolRegistry {
    private final List<ResearchToolDescriptor> tools;
    private final Map<String, ResearchToolDescriptor> byCode;

    public ResearchToolRegistry() {
        List<ResearchToolDescriptor> configured = Arrays.asList(
                descriptor("source_scan", "配置来源扫描", "扫描用户启用的信息源并沉淀文章证据",
                        schema("themeCodes", "主题编码列表"), schema("articleIds", "新增文章ID列表"),
                        60000, false, false, "LOW", "EXTERNAL_ACTION"),
                descriptor("public_news_search", "Tavily 公开资料搜索",
                        "使用 Tavily 补充本次研究证据，搜索材料不进入文章库",
                        schema("query", "不含协议头的公开搜索词",
                                "intent", "证据意图（SUPPORT/COUNTER/PRIMARY/UPDATE）"),
                        schema("searchEvidenceRefs", "本次研究证据引用列表"),
                        15000, true, false, "MEDIUM", "EXTERNAL_ACTION"),
                descriptor("research_material_search", "结构化研究资料检索",
                        "从公告、互动问答、研报和财经快讯检索运行级证据",
                        schema("stockCode", "六位 A 股代码",
                                "materialType", "ANNOUNCEMENT/INTERACTION/BROKER_REPORT/NEWS_FLASH",
                                "query", "检索关键词，可为空字符串"),
                        schema("searchEvidenceRefs", "本次研究证据引用列表"),
                        45000, true, true, "LOW", "EXTERNAL_ACTION"),
                descriptor("evidence_assess", "证据缺口判断", "评估证据数量、独立来源和正反覆盖",
                        Collections.<String, String>emptyMap(),
                        schema("recommendedIntent", "下一证据意图"),
                        5000, true, true, "LOW", "INTERNAL"),
                descriptor("report_synthesis", "研究报告合成", "仅使用冻结证据生成带边界的研究报告",
                        schema("researchRunId", "研究运行ID"), schema("reportId", "报告ID"),
                        30000, true, false, "LOW", "INTERNAL"));
        Map<String, ResearchToolDescriptor> index = new LinkedHashMap<String, ResearchToolDescriptor>();
        for (ResearchToolDescriptor tool : configured) {
            index.put(tool.getCode(), tool);
        }
        tools = Collections.unmodifiableList(configured);
        byCode = Collections.unmodifiableMap(index);
    }

    public List<ResearchToolDescriptor> list() {
        return tools;
    }

    public boolean contains(String code) {
        return byCode.containsKey(code);
    }

    public ResearchToolDescriptor required(String code) {
        ResearchToolDescriptor tool = byCode.get(code);
        if (tool == null) {
            throw new IllegalArgumentException("未注册的研究工具：" + code);
        }
        return tool;
    }

    private ResearchToolDescriptor descriptor(String code,
                                              String name,
                                              String description,
                                              Map<String, String> input,
                                              Map<String, String> output,
                                              int timeoutMs,
                                              boolean readOnly,
                                              boolean parallelizable,
                                              String riskLevel,
                                              String budgetType) {
        ResearchToolDescriptor value = new ResearchToolDescriptor();
        value.setCode(code);
        value.setName(name);
        value.setDescription(description);
        value.setInputSchema(input);
        value.setOutputSchema(output);
        value.setTimeoutMs(timeoutMs);
        value.setReadOnly(readOnly);
        value.setParallelizable(parallelizable);
        value.setRiskLevel(riskLevel);
        value.setBudgetType(budgetType);
        return value;
    }

    private Map<String, String> schema(String... definitions) {
        Map<String, String> value = new LinkedHashMap<String, String>();
        for (int index = 0; index < definitions.length; index += 2) {
            value.put(definitions[index], definitions[index + 1]);
        }
        return value;
    }
}

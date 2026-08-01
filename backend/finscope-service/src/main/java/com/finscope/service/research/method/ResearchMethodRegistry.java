package com.finscope.service.research.method;

import com.finscope.service.research.mission.ResearchPlanningInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class ResearchMethodRegistry {
    private final List<ResearchMethod> methods;
    private final Map<String, ResearchMethod> byCode;

    @Autowired
    public ResearchMethodRegistry(List<ResearchMethod> methods) {
        this.methods = methods == null ? Collections.<ResearchMethod>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchMethod>(methods));
        this.byCode = new LinkedHashMap<String, ResearchMethod>();
        for (ResearchMethod method : this.methods) {
            String code = method.definition().getCode();
            if (byCode.put(code, method) != null) {
                throw new IllegalArgumentException("投研方法编码重复：" + code);
            }
        }
    }

    public static ResearchMethodRegistry defaults() {
        return new ResearchMethodRegistry(Arrays.<ResearchMethod>asList(
                new FinancialStatementQualityMethod(), new CompanyQualityMethod()));
    }

    public List<ResearchMethodDefinition> list() {
        List<ResearchMethodDefinition> values = new ArrayList<ResearchMethodDefinition>();
        for (ResearchMethod method : methods) values.add(method.definition());
        return Collections.unmodifiableList(values);
    }

    public List<ResearchMethodDefinition> recommend(ResearchPlanningInput input) {
        List<ResearchMethodDefinition> values = new ArrayList<ResearchMethodDefinition>();
        for (ResearchMethod method : methods) if (method.supports(input)) values.add(method.definition());
        return Collections.unmodifiableList(values);
    }

    public ResearchMethodDefinition required(String code) {
        ResearchMethod method = byCode.get(code);
        if (method == null) throw new IllegalArgumentException("未注册的投研方法：" + code);
        return method.definition();
    }

    public boolean supports(String code, ResearchPlanningInput input) {
        ResearchMethod method = byCode.get(code);
        return method != null && method.supports(input);
    }

    public ResearchMethodSelection recommendedSelection(ResearchPlanningInput input) {
        List<String> codes = new ArrayList<String>();
        for (ResearchMethodDefinition definition : recommend(input)) codes.add(definition.getCode());
        return selection(codes, input);
    }

    public ResearchMethodSelection selection(List<String> codes, ResearchPlanningInput input) {
        List<String> selectedCodes = codes == null ? Collections.<String>emptyList() : codes;
        LinkedHashSet<String> uniqueCodes = new LinkedHashSet<String>();
        LinkedHashSet<String> evidence = new LinkedHashSet<String>();
        LinkedHashSet<String> calculations = new LinkedHashSet<String>();
        LinkedHashSet<String> counters = new LinkedHashSet<String>();
        LinkedHashSet<String> completion = new LinkedHashSet<String>();
        for (String code : selectedCodes) {
            if (code == null || !uniqueCodes.add(code)) {
                throw new IllegalArgumentException("投研方法编码为空或重复：" + code);
            }
            ResearchMethod method = byCode.get(code);
            if (method == null) throw new IllegalArgumentException("未注册的投研方法：" + code);
            if (!method.supports(input)) throw new IllegalArgumentException("投研方法不支持当前研究对象：" + code);
            ResearchMethodDefinition definition = method.definition();
            evidence.addAll(definition.getRequiredEvidence());
            calculations.addAll(definition.getRequiredCalculations());
            counters.addAll(definition.getCounterChecks());
            completion.addAll(definition.getCompletionCriteria());
        }
        if (uniqueCodes.isEmpty() && !recommend(input).isEmpty()) {
            throw new IllegalArgumentException("当前研究问题必须选择已注册的投研方法");
        }
        String type = uniqueCodes.contains("FINANCIAL_STATEMENT_QUALITY") ? "COMPANY_FINANCIAL"
                : uniqueCodes.contains("COMPANY_QUALITY") ? "COMPANY_QUALITY" : "GENERAL_RESEARCH";
        return new ResearchMethodSelection(type, new ArrayList<String>(uniqueCodes),
                new ArrayList<String>(evidence), new ArrayList<String>(calculations),
                new ArrayList<String>(counters), new ArrayList<String>(completion));
    }
}

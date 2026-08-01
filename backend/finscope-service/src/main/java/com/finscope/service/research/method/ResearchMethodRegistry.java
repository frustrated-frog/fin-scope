package com.finscope.service.research.method;

import com.finscope.service.research.mission.ResearchPlanningInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
}

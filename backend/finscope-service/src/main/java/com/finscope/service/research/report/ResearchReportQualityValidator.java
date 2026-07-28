package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchReportQualityValidator {
    private static final Pattern EVIDENCE_REF = Pattern.compile("\\[(E\\d+)]");
    private static final String[] REQUIRED = {"核心结论", "关键认识", "执行摘要", "研究范围与口径",
            "关键事实与数字", "发生了什么", "命题拆解与逐题判断", "核心证据链", "反方解释与争议",
            "机制与情景推演", "最终认识与未知项", "跟踪清单与失效条件", "证据附录"};

    public List<String> validate(String markdown, ResearchThesis thesis, List<ResearchEvidenceDossier> dossier) {
        List<String> issues = new ArrayList<String>();
        if (markdown == null || markdown.length() < 6000) issues.add("REPORT_TOO_SHORT");
        if (markdown != null && markdown.length() > ResearchReportPolicy.MAX_REPORT_CHARACTERS) issues.add("REPORT_TOO_LONG");
        String value = markdown == null ? "" : markdown;
        for (String heading : REQUIRED) if (!value.contains("## " + heading)) issues.add("MISSING_SECTION:" + heading);
        Set<String> allowed = new HashSet<String>();
        for (ResearchEvidenceDossier item : dossier) allowed.add(item.getEvidenceRef());
        Matcher matcher = EVIDENCE_REF.matcher(value);
        while (matcher.find()) if (!allowed.contains(matcher.group(1))) issues.add("INVALID_EVIDENCE_REF:" + matcher.group(1));
        if (thesis != null && thesis.getSubjectName() != null && !value.contains(thesis.getSubjectName())) issues.add("SUBJECT_NOT_GROUNDED");
        return new ArrayList<String>(new java.util.LinkedHashSet<String>(issues));
    }
}

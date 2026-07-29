package com.finscope.service.research.report;

import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResearchReportRepairAgent {
    private static final Pattern REF = Pattern.compile("\\[(E\\d+)]");
    private final LlmChatClient client;

    public ResearchReportRepairAgent(LlmChatClient client) {
        this.client = client;
    }

    public String repair(String markdown, ResearchClaimAudit audit, List<ResearchEvidenceDossier> dossier) {
        if (audit == null || !audit.hasBlockingIssues()) return markdown;
        Set<String> allowed = allowed(dossier);
        if (client != null && client.isConfigured()) {
            try {
                String output = client.complete(systemPrompt(allowed), userPrompt(markdown, audit, dossier),
                        60_000, 12_000);
                String candidate = stripFence(output);
                if (isValid(candidate, markdown, allowed)) {
                    ResearchClaimAudit candidateAudit = new ResearchClaimAuditor(new ResearchClaimExtractor())
                            .audit(candidate, dossier);
                    return candidateAudit.hasBlockingIssues()
                            ? deterministicRepair(candidate, candidateAudit) : candidate;
                }
            } catch (Exception ignored) {
                // Deterministic degradation below keeps the report pipeline available.
            }
        }
        return deterministicRepair(markdown, audit);
    }

    private String systemPrompt(Set<String> allowed) {
        return "你是金融研究引用修复器。只修复审计指出的事实句，保留原 Markdown 结构。"
                + "不得新增事实、数字或引用。允许的证据编号仅为：" + String.join(",", allowed)
                + "。无法被证据支持时，删除具体数字并明确写成待验证判断。只输出完整 Markdown。";
    }

    private String userPrompt(String markdown, ResearchClaimAudit audit, List<ResearchEvidenceDossier> dossier) {
        StringBuilder value = new StringBuilder("待修复报告：\n").append(markdown).append("\n\n审计问题：\n");
        for (ResearchClaimAuditItem item : audit.getItems()) {
            if (item.isBlocking()) value.append("- ").append(item.getStatus()).append(" | ")
                    .append(item.getReason()).append(" | ").append(item.getClaim().getRawText()).append('\n');
        }
        value.append("\n证据：\n");
        if (dossier != null) for (ResearchEvidenceDossier item : dossier) value.append('[')
                .append(item.getEvidenceRef()).append("] ").append(item.getFactExcerpt()).append('\n');
        return value.toString();
    }

    private String deterministicRepair(String markdown, ResearchClaimAudit audit) {
        String result = markdown == null ? "" : markdown;
        for (ResearchClaimAuditItem item : audit.getItems()) {
            if (!item.isBlocking()) continue;
            StringBuilder refs = new StringBuilder();
            for (String ref : item.getClaim().getEvidenceRefs()) refs.append('[').append(ref).append(']');
            result = result.replace(item.getClaim().getRawText(),
                    "**审计降级：** 原事实表述缺乏充分引用支持，已移除具体断言并标记为待验证；"
                            + "原关联材料仅供复核。" + refs);
        }
        return result;
    }

    private boolean isValid(String markdown, String original, Set<String> allowed) {
        if (markdown == null || markdown.trim().isEmpty() || !markdown.contains("## ")) return false;
        Matcher matcher = REF.matcher(markdown);
        while (matcher.find()) if (!allowed.contains(matcher.group(1))) return false;
        if (original != null && markdown.length() * 10 < original.length() * 8) return false;
        for (String heading : headings(original)) if (!markdown.contains("## " + heading)) return false;
        Set<String> originalRefs = cited(original, allowed);
        if (!cited(markdown, allowed).containsAll(originalRefs)) return false;
        return true;
    }

    private Set<String> cited(String markdown, Set<String> allowed) {
        Set<String> result = new HashSet<String>();
        Matcher matcher = REF.matcher(markdown == null ? "" : markdown);
        while (matcher.find()) if (allowed.contains(matcher.group(1))) result.add(matcher.group(1));
        return result;
    }

    private Set<String> headings(String markdown) {
        Set<String> result = new HashSet<String>();
        Matcher matcher = Pattern.compile("(?m)^##\\s+(.+?)\\s*$").matcher(markdown == null ? "" : markdown);
        while (matcher.find()) result.add(matcher.group(1).trim());
        return result;
    }

    private Set<String> allowed(List<ResearchEvidenceDossier> dossier) {
        Set<String> result = new HashSet<String>();
        if (dossier != null) for (ResearchEvidenceDossier item : dossier) result.add(item.getEvidenceRef());
        return result;
    }

    private String stripFence(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("```")) {
            int firstBreak = result.indexOf('\n');
            int lastFence = result.lastIndexOf("```");
            if (firstBreak >= 0 && lastFence > firstBreak) result = result.substring(firstBreak + 1, lastFence).trim();
        }
        return result;
    }
}

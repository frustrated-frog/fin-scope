package com.finscope.web;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.llm.OpenAiCompatibleLlmClient;
import com.finscope.service.research.report.ResearchEvidenceDossier;
import com.finscope.service.research.report.ResearchReportBlueprint;
import com.finscope.service.research.report.ResearchReportBlueprintAgent;
import com.finscope.service.research.report.ResearchReportBlueprintValidator;
import com.finscope.service.research.report.ResearchReportNarrative;
import com.finscope.service.research.report.ResearchReportNarrativeAgent;
import com.finscope.service.research.report.ResearchReportQualityValidator;
import com.finscope.service.research.report.StructuredResearchReportAssembler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("real-model")
class ResearchReportModelSmokeTest {

    @Test
    void configuredModelEnhancesACompleteReportWithoutDatabaseAccess() throws Exception {
        assumeTrue(Boolean.getBoolean("finscope.real-model-smoke"),
                "Run explicitly with -Dfinscope.real-model-smoke=true");
        Properties properties = applicationProperties();
        LlmChatClient llm = new OpenAiCompatibleLlmClient(
                Boolean.parseBoolean(properties.getProperty("finscope.llm.enabled")),
                properties.getProperty("finscope.llm.base-url"),
                properties.getProperty("finscope.llm.api-key"),
                properties.getProperty("finscope.llm.model"),
                Integer.parseInt(properties.getProperty("finscope.llm.timeout-ms")),
                Double.parseDouble(properties.getProperty("finscope.llm.temperature")));

        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectType("INDUSTRY");
        thesis.setSubjectName("半导体设备");
        thesis.setQuestion("半导体设备需求回升是否具有持续性？");
        List<ResearchEvidenceDossier> dossier = dossier();

        ResearchReportBlueprint blueprint = new ResearchReportBlueprintAgent(
                llm, new ResearchReportBlueprintValidator()).generate(thesis, dossier);
        assertTrue(blueprint.isModelEnhanced(), "blueprint diagnostics=" + blueprint.getDiagnostics());

        ResearchReportNarrative narrative = new ResearchReportNarrativeAgent(llm)
                .generate(thesis, blueprint, dossier);
        assertTrue(narrative.isModelEnhanced(), "narrative diagnostics=" + narrative.getDiagnostics());

        String markdown = new StructuredResearchReportAssembler().assemble(thesis, blueprint, narrative, dossier);
        assertTrue(markdown.length() >= 6000, "report characters=" + markdown.length());
        assertTrue(new ResearchReportQualityValidator().validate(markdown, thesis, dossier).isEmpty());
    }

    private Properties applicationProperties() throws Exception {
        Properties result = new Properties();
        boolean inLlm = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("application.yml").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int indent = leadingSpaces(line);
                String trimmed = line.trim();
                if (indent == 2 && "llm:".equals(trimmed)) {
                    inLlm = true;
                    continue;
                }
                if (inLlm && indent <= 2 && !trimmed.isEmpty() && !trimmed.startsWith("#")) break;
                if (!inLlm || indent != 4 || !trimmed.contains(":")) continue;
                int separator = trimmed.indexOf(':');
                String key = trimmed.substring(0, separator).trim();
                String value = unquote(trimmed.substring(separator + 1).trim());
                result.setProperty("finscope.llm." + key, value);
            }
        }
        return result;
    }

    private int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') count++;
        return count;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private List<ResearchEvidenceDossier> dossier() {
        return Arrays.asList(
                evidence("E1", "交易所公告", "T1", "设备企业订单公告",
                        "公司公告显示在手订单同比增长，主要来自先进制程扩产项目。", "SUPPORT", 96),
                evidence("E2", "行业协会", "T1", "全球半导体销售数据",
                        "行业协会数据显示全球半导体销售额连续多个季度同比增长。", "SUPPORT", 93),
                evidence("E3", "晶圆厂财报", "T1", "晶圆厂资本开支指引",
                        "晶圆厂维持年度资本开支计划，重点投向先进制程与先进封装。", "SUPPORT", 92),
                evidence("E4", "设备公司财报", "T1", "设备公司收入与交付",
                        "设备公司披露收入增长，同时提示交付节奏受客户验收时间影响。", "SUPPORT", 89),
                evidence("E5", "产业研究机构", "T2", "成熟制程利用率跟踪",
                        "部分成熟制程产线利用率仍处低位，扩产意愿存在地区和产品分化。", "COUNTER", 88),
                evidence("E6", "公司风险公告", "T1", "出口限制风险提示",
                        "公司提示出口限制和客户资本开支调整可能造成订单延迟或取消。", "COUNTER", 91));
    }

    private ResearchEvidenceDossier evidence(String ref, String source, String tier, String title,
                                              String fact, String stance, int relevance) {
        return new ResearchEvidenceDossier(ref, null, null, source, source, tier, title,
                LocalDateTime.of(2026, 7, 30, 9, 0), "https://example.com/" + ref.toLowerCase(),
                fact, stance, relevance);
    }
}

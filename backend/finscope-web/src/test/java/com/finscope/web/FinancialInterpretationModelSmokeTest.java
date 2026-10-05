package com.finscope.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.rpc.llm.OpenAiCompatibleLlmClient;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.financials.FinancialAnalysisEngine;
import com.finscope.service.financials.FinancialAnalysisResult;
import com.finscope.service.financials.FinancialEvidencePacket;
import com.finscope.service.financials.FinancialEvidencePacketAssembler;
import com.finscope.service.financials.FinancialEvidenceSelector;
import com.finscope.service.financials.FinancialInterpretationAgent;
import com.finscope.service.financials.FinancialInterpretationFallbackBuilder;
import com.finscope.service.financials.FinancialInterpretationGate;
import com.finscope.service.financials.FinancialInterpretationResponseParser;
import com.finscope.service.financials.FinancialTrendEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 显式 opt-in 的真实模型验证；输入与输出均位于仓库外，不启动定时任务、不写主库。 */
@Tag("real-model")
class FinancialInterpretationModelSmokeTest {
    @Test
    void generatesDetailedEvidenceBoundReportUsingLocalConfigurationAndSavedSample() throws Exception {
        assumeTrue(Boolean.getBoolean("finscope.financial-real-model-smoke"));
        Path input = Path.of(System.getProperty("finscope.financial-smoke-input"));
        Path output = Path.of(System.getProperty("finscope.financial-smoke-output"));
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        JsonNode source = json.readTree(Files.readString(input));
        FinancialReportView current = json.treeToValue(source.path("current"), FinancialReportView.class);
        List<FinancialReportView> history = new ArrayList<>();
        for (JsonNode item : source.path("history")) {
            history.add(json.treeToValue(item, FinancialReportView.class));
        }
        FinancialReportView prior = history.stream().filter(item -> item.getReport().getPeriodEnd()
                .equals(current.getReport().getPeriodEnd().minusYears(1))).findFirst().orElse(null);
        FinancialAnalysisResult calculated = new FinancialAnalysisEngine().analyze(lines(current), lines(prior));
        current.setMetrics(calculated.getMetrics());
        current.setFindings(calculated.getFindings());
        current.setDataGaps(calculated.getDataGaps());
        FinancialEvidencePacketAssembler assembler = new FinancialEvidencePacketAssembler();
        ReflectionTestUtils.setField(assembler, "json", json);
        ReflectionTestUtils.setField(assembler, "trends", new FinancialTrendEngine());
        ReflectionTestUtils.setField(assembler, "selector", new FinancialEvidenceSelector());
        FinancialEvidencePacket packet = assembler.assemble(current, history);
        FinancialInterpretationGate gate = new FinancialInterpretationGate();
        ReflectionTestUtils.setField(gate, "json", json);
        FinancialInterpretationAgent agent = new FinancialInterpretationAgent();
        ReflectionTestUtils.setField(agent, "json", json);
        LlmChatClient delegate = client();
        LlmChatClient observed = new LlmChatClient() {
            private int calls;

            @Override
            public boolean isConfigured() {
                return delegate.isConfigured();
            }

            @Override
            public String modelName() {
                return delegate.modelName();
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) throws Exception {
                String response = delegate.complete(systemPrompt, userPrompt);
                Files.writeString(Path.of(output + ".model-" + (++calls) + ".txt"), response);
                return response;
            }
        };
        ReflectionTestUtils.setField(agent, "llm", observed);
        ReflectionTestUtils.setField(agent, "parser", new FinancialInterpretationResponseParser(json));
        ReflectionTestUtils.setField(agent, "gate", gate);
        ReflectionTestUtils.setField(agent, "fallback", new FinancialInterpretationFallbackBuilder());
        long started = System.nanoTime();
        FinancialInterpretationAgent.Execution execution = agent.interpretWithMetrics(packet);
        FinancialInterpretation result = execution.getValue();
        result.setDurationMs((System.nanoTime() - started) / 1_000_000L);
        result.setId(1L);
        result.setSnapshotId(1L);
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), result);
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of(output + ".evidence.json").toFile(), packet.getModelEvidence());
        assertEquals("SUCCESS", result.getStatus(), "failure=" + result.getFailureCode() + "; validation=" + result.getValidationErrors());
        assertEquals(10, result.getResult().getSections().size());
        assertTrue(result.getResult().getSections().stream().filter(section -> !section.getAnalysis().isEmpty()).count() >= 6);
        assertTrue(result.getResult().getSections().stream().allMatch(section -> section.getLearningExplanation() != null));
    }

    private OpenAiCompatibleLlmClient client() throws Exception {
        Properties properties = new Properties();
        boolean inLlm = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("application.yml").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (line.equals("  llm:")) {
                    inLlm = true;
                    continue;
                }
                if (inLlm && !line.startsWith("    ") && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    break;
                }
                if (inLlm && line.startsWith("    ") && trimmed.contains(":")) {
                    int separator = trimmed.indexOf(':');
                    String value = trimmed.substring(separator + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    properties.setProperty("finscope.llm." + trimmed.substring(0, separator).trim(), value);
                }
            }
        }
        Path local = Path.of(System.getProperty("user.home"), ".config", "finscope", "llm.local.properties");
        try (InputStream input = Files.newInputStream(local)) {
            Properties credentials = new Properties();
            credentials.load(input);
            // 仅读取 LLM 设置，不打印、不复制其他运行时凭据。
            for (String key : credentials.stringPropertyNames()) {
                if (key.startsWith("finscope.llm.")) {
                    properties.setProperty(key, credentials.getProperty(key));
                }
            }
        }
        return new OpenAiCompatibleLlmClient(Boolean.parseBoolean(properties.getProperty("finscope.llm.enabled")),
                properties.getProperty("finscope.llm.base-url"), properties.getProperty("finscope.llm.api-key"),
                properties.getProperty("finscope.llm.model"), Integer.parseInt(properties.getProperty("finscope.llm.timeout-ms")),
                Double.parseDouble(properties.getProperty("finscope.llm.temperature")));
    }

    private List<FinancialLineItem> lines(FinancialReportView view) {
        List<FinancialLineItem> lines = new ArrayList<>();
        if (view != null) {
            view.getStatements().values().forEach(lines::addAll);
        }
        return lines;
    }
}

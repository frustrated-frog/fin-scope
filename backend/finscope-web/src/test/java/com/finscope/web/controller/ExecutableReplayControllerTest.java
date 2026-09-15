package com.finscope.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.service.factorresearch.FactorProviderRegistry;
import com.finscope.service.factorresearch.LegacyQuantFactorProvider;
import com.finscope.service.quant.backtest.ExecutableReplayService;
import com.finscope.service.quant.backtest.ExecutableReplayValidator;
import com.finscope.service.quant.backtest.QuantBacktestEngine;
import com.finscope.service.quant.factor.FactorRegistry;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutableReplayController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class,
        ExecutableReplayService.class, ExecutableReplayValidator.class, QuantBacktestEngine.class,
        FactorRegistry.class, FactorProviderRegistry.class, LegacyQuantFactorProvider.class})
class ExecutableReplayControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void replaysPublishedFixtureThroughRealServiceAndExportsReport() throws Exception {
        String input = Files.readString(Path.of("../../docs/quant/examples/executable-replay-synthetic.json"));
        String first = mvc.perform(post("/api/quant/executable-replays")
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engineVersion").value("executable-replay-v1"))
                .andExpect(jsonPath("$.data.account.equityCurve.length()").value(12))
                .andExpect(jsonPath("$.data.account.trades[0].tradeDate").value("2026-09-02"))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/quant/executable-replays")
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first).get("data"), mapper.readTree(second).get("data"));
        // Reproducible diagnostic artifact from the actual controller, not a hand-written account result.
        Files.createDirectories(Path.of("target"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/executable-replay-synthetic.report.json").toFile(),
                mapper.readTree(first).get("data"));
    }

    @Test
    void returnsUnifiedBadRequestForMissingCostsAndUnknownExecutionState() throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree(Files.readString(Path.of("../../docs/quant/examples/executable-replay-synthetic.json")));
        ((ObjectNode) input.get("protocol")).remove("minimumCommission");
        mvc.perform(post("/api/quant/executable-replays").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(input)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FS-1002"));
        ((ObjectNode) input.get("protocol")).put("minimumCommission", 5);
        ((ObjectNode) input.get("bars").get(0)).put("openState", "UNKNOWN");
        mvc.perform(post("/api/quant/executable-replays").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(input)))
                .andExpect(status().isBadRequest());
    }
}

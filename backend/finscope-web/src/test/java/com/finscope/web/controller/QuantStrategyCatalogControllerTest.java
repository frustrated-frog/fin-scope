package com.finscope.web.controller;

import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSource;
import com.finscope.domain.quant.catalog.QuantStrategyCatalogSyncResult;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.service.quant.catalog.QuantStrategyCandidateDraftService;
import com.finscope.service.quant.catalog.QuantStrategyCatalogService;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuantStrategyCatalogControllerTest {
    private QuantStrategyCatalogService catalog;
    private QuantStrategyCandidateDraftService drafts;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        catalog = mock(QuantStrategyCatalogService.class);
        drafts = mock(QuantStrategyCandidateDraftService.class);
        QuantController controller = new QuantController();
        ReflectionTestUtils.setField(controller, "quantStrategyCatalogService", catalog);
        ReflectionTestUtils.setField(controller, "quantStrategyCandidateDraftService", drafts);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void synchronizesAndQueriesCatalogCandidates() throws Exception {
        QuantStrategyCatalogSyncResult result = new QuantStrategyCatalogSyncResult();
        result.setCommitSha("abc123"); result.setImportedCount(40);
        when(catalog.sync()).thenReturn(result);
        QuantStrategyCandidate candidate = new QuantStrategyCandidate();
        candidate.setId(7L); candidate.setTitle("价值策略"); candidate.setCompatibilityStatus("ADAPTABLE");
        when(catalog.list("ADAPTABLE", "价值")).thenReturn(Collections.singletonList(candidate));

        mvc.perform(post("/api/quant/catalog/sync"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.commitSha").value("abc123"));
        mvc.perform(get("/api/quant/catalog/candidates").param("compatibility", "ADAPTABLE").param("query", "价值"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").value("价值策略"));

        verify(catalog).list("ADAPTABLE", "价值");
    }

    @Test
    void returnsSourceDetailAndCreatesCandidateDraft() throws Exception {
        QuantStrategyCatalogSource source = new QuantStrategyCatalogSource(); source.setCode("AWESOME_SYSTEMATIC_TRADING");
        when(catalog.source()).thenReturn(Optional.of(source));
        QuantStrategyCandidate candidate = new QuantStrategyCandidate(); candidate.setId(7L); candidate.setTitle("价值策略");
        when(catalog.find(7L)).thenReturn(Optional.of(candidate));
        QuantStrategyDraft draft = new QuantStrategyDraft(); draft.setId(11L); draft.setStatus("VALIDATED");
        when(drafts.generate(7L, 3L)).thenReturn(draft);

        mvc.perform(get("/api/quant/catalog/source"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("AWESOME_SYSTEMATIC_TRADING"));
        mvc.perform(get("/api/quant/catalog/candidates/7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("价值策略"));
        mvc.perform(post("/api/quant/catalog/candidates/7/drafts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    void rejectsCandidateDraftWithoutDataset() throws Exception {
        mvc.perform(post("/api/quant/catalog/candidates/7/drafts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }
}

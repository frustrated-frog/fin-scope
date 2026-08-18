package com.finscope.web.controller;

import com.finscope.common.enums.quant.QuantStrategyEvidenceLevel;
import com.finscope.domain.quant.academy.QuantStrategyAcademyBuildResult;
import com.finscope.domain.quant.academy.QuantStrategyAcademyCard;
import com.finscope.service.quant.academy.QuantStrategyAcademyService;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuantAcademyControllerTest {
    private MockMvc mockMvc;
    private QuantStrategyAcademyService service;

    @BeforeEach
    void setUp() {
        service = mock(QuantStrategyAcademyService.class);
        QuantController controller = new QuantController();
        ReflectionTestUtils.setField(controller, "quantStrategyAcademyService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsAcademyCards() throws Exception {
        QuantStrategyAcademyCard card = new QuantStrategyAcademyCard();
        card.setCandidateId(7L);
        card.setTitle("质量价值策略");
        card.setEvidenceLevel(QuantStrategyEvidenceLevel.HISTORICAL_EVIDENCE);
        when(service.cards(3L)).thenReturn(Collections.singletonList(card));

        mockMvc.perform(get("/api/quant/academy/cards").param("datasetId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].candidateId").value(7))
                .andExpect(jsonPath("$.data[0].evidenceLevel").value("HISTORICAL_EVIDENCE"));
    }

    @Test
    void startsBoundedAcademyBuild() throws Exception {
        QuantStrategyAcademyBuildResult result = new QuantStrategyAcademyBuildResult();
        result.setScannedCount(6);
        result.setExperimentStartedCount(5);
        when(service.build(3L)).thenReturn(result);

        mockMvc.perform(post("/api/quant/academy/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":3}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.scannedCount").value(6))
                .andExpect(jsonPath("$.data.experimentStartedCount").value(5));
    }
}

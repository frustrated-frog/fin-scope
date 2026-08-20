package com.finscope.web.controller;

import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationDetail;
import com.finscope.domain.investmentobservation.InvestmentObservationRefreshResult;
import com.finscope.domain.investmentobservation.InvestmentObservationWorkspace;
import com.finscope.service.investmentobservation.InvestmentObservationService;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvestmentObservationControllerTest {
    private MockMvc mockMvc;
    private InvestmentObservationService service;

    @BeforeEach
    void setUp() {
        service = mock(InvestmentObservationService.class);
        InvestmentObservationController controller = new InvestmentObservationController();
        ReflectionTestUtils.setField(controller, "service", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void exposesTheIndependentObservationWorkspace() throws Exception {
        InvestmentObservationWorkspace workspace = new InvestmentObservationWorkspace();
        workspace.setActiveCount(4);
        workspace.getFocus().add(observation(7L));
        when(service.workspace()).thenReturn(workspace);

        mockMvc.perform(get("/api/investment-observations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCount").value(4))
                .andExpect(jsonPath("$.data.focus[0].title").value("产业链变化"));
    }

    @Test
    void refreshesCandidatesWithoutCallingTheRadarHttpApi() throws Exception {
        InvestmentObservationRefreshResult result = new InvestmentObservationRefreshResult();
        result.setScannedCount(18);
        result.setUpdatedCount(12);
        when(service.refresh()).thenReturn(result);

        mockMvc.perform(post("/api/investment-observations/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scannedCount").value(18))
                .andExpect(jsonPath("$.data.updatedCount").value(12));
    }

    @Test
    void exposesObservationDetailAndTransitionHistory() throws Exception {
        InvestmentObservationDetail detail = new InvestmentObservationDetail();
        detail.setObservation(observation(7L));
        when(service.detail(7L)).thenReturn(detail);

        mockMvc.perform(get("/api/investment-observations/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.observation.id").value(7));
    }

    @Test
    void updatesThePersonalDispositionWithOptimisticRevision() throws Exception {
        when(service.updateDisposition(7L, InvestmentObservationDisposition.LATER, 3))
                .thenReturn(observation(7L));

        mockMvc.perform(patch("/api/investment-observations/7/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"LATER\",\"revision\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));

        verify(service).updateDisposition(7L, InvestmentObservationDisposition.LATER, 3);
    }

    @Test
    void archivesAnObservationWithAReason() throws Exception {
        when(service.archive(7L, 3, "阶段性结论已验证")).thenReturn(observation(7L));

        mockMvc.perform(post("/api/investment-observations/7/archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revision\":3,\"reason\":\"阶段性结论已验证\"}"))
                .andExpect(status().isOk());

        verify(service).archive(7L, 3, "阶段性结论已验证");
    }

    private InvestmentObservation observation(Long id) {
        InvestmentObservation value = new InvestmentObservation();
        value.setId(id);
        value.setTitle("产业链变化");
        value.setRevision(3);
        return value;
    }
}

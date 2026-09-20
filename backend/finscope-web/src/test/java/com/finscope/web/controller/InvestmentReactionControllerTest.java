package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.service.investmentobservation.ReactionRegistrationService;
import com.finscope.service.investmentobservation.ReactionRefreshService;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvestmentReactionControllerTest {
    private final ReactionRegistrationService registration = mock(ReactionRegistrationService.class);
    private final ReactionRefreshService refresh = mock(ReactionRefreshService.class);
    private final com.finscope.service.investmentobservation.ReactionWorkspaceService workspace = mock(com.finscope.service.investmentobservation.ReactionWorkspaceService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        InvestmentReactionController controller = new InvestmentReactionController();
        ReflectionTestUtils.setField(controller, "registration", registration);
        ReflectionTestUtils.setField(controller, "refreshService", refresh);
        ReflectionTestUtils.setField(controller, "workspace", workspace);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void readsPersistedChangesAndValidatesFollowMutation() throws Exception {
        var day = java.time.LocalDate.parse("2026-09-20");
        when(workspace.changes(day, Long.MAX_VALUE)).thenReturn(List.of());
        mvc.perform(get("/api/investment-reactions/changes?date=2026-09-20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(workspace).changes(day, Long.MAX_VALUE);
        mvc.perform(patch("/api/investment-reactions/1/follow").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(workspace, never()).follow(anyLong(), anyBoolean());
        when(workspace.follow(1, true)).thenReturn(List.of());
        mvc.perform(patch("/api/investment-reactions/1/follow").contentType(MediaType.APPLICATION_JSON).content("{\"followed\":true}"))
                .andExpect(status().isOk());
        verify(workspace).follow(1, true);
    }

    @Test
    void listsTypedSnapshotsAndRegistersDrafts() throws Exception {
        ReactionSample sample = new ReactionSample();
        sample.setId(1L);
        sample.setMajorEventId(40L);
        sample.setSourceOriginType("NEWS_ITEM");
        sample.setRegisteredAt(LocalDateTime.parse("2026-09-20T10:00:00"));
        when(registration.list(null, 0, 100)).thenReturn(List.of(sample));
        when(registration.createDraft(40L)).thenReturn(sample);
        mvc.perform(get("/api/investment-reactions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].majorEventId").value(40))
                .andExpect(jsonPath("$.data[0].sourceOriginType").value("NEWS_ITEM"));
        mvc.perform(post("/api/investment-reactions").contentType(MediaType.APPLICATION_JSON).content("{\"majorEventId\":40}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("DRAFT"));
    }

    @Test
    void validatesBeforeMutationAndMapsConflicts() throws Exception {
        mvc.perform(post("/api/investment-reactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/investment-reactions/1/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentCode\":\"000300.SH\",\"revision\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/investment-reactions/1/archive").contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registration);
        when(refresh.refresh(1)).thenThrow(new BusinessException(ErrorCode.DATA_VERSION_CONFLICT));
        mvc.perform(post("/api/investment-reactions/1/refresh"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FS-2004"));
    }
}

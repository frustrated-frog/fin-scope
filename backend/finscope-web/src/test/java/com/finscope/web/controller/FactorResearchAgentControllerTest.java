package com.finscope.web.controller;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorResearchAgentRun;
import com.finscope.service.factorresearch.FactorResearchAgentService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FactorResearchAgentController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class FactorResearchAgentControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private FactorResearchAgentService service;

    @Test
    void createsApprovalRequiredPlanThenRunsItExplicitly() throws Exception {
        FactorResearchAgentRun planned = run("AWAITING_APPROVAL");
        FactorResearchAgentRun completed = run("COMPLETED");
        when(service.createPlan(eq(7L), any(FactorIdentity.class), isNull(), isNull())).thenReturn(planned);
        when(service.approveAndRun(9L)).thenReturn(completed); when(service.get(9L)).thenReturn(completed);

        mvc.perform(post("/api/factor-research/agent-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":7,\"factorNamespace\":\"capital\",\"factorCode\":\"MAIN_FLOW_SHARE\",\"factorVersion\":\"1.0.0\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/factor-research/agent-runs/9"))
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL")).andExpect(jsonPath("$.maxLlmCalls").value(0));
        mvc.perform(post("/api/factor-research/agent-runs/9/approve"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/factor-research/agent-runs/9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidenceHash").value("abc"));
    }

    private FactorResearchAgentRun run(String status) { FactorResearchAgentRun value = new FactorResearchAgentRun(); value.setId(9L); value.setDatasetId(7L); value.setDatasetFingerprint("sha"); value.setFactor(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0")); value.setQuestion("可靠？"); value.setStatus(status); value.setPlan(Arrays.asList("检查数据")); value.setAllowedTools(Arrays.asList("inspect_dataset")); value.setMaxToolCalls(4); value.setMaxLlmCalls(0); value.setMaxRunSeconds(60); value.setEvidenceJson("{}"); value.setEvidenceHash("abc"); value.setFindingJson("{}"); return value; }
}

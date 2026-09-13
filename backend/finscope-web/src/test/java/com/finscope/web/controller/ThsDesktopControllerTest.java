package com.finscope.web.controller;

import com.finscope.common.enums.desktopths.ThsCaptureStatus;
import com.finscope.domain.desktopths.ThsSnapshot;
import com.finscope.service.desktopths.ThsDesktopService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ThsDesktopControllerTest {
    @Test
    void returnsCaptureStateInsideExistingApiEnvelope() throws Exception {
        ThsDesktopService service = mock(ThsDesktopService.class);
        ThsSnapshot snapshot = new ThsSnapshot();
        snapshot.setStatus(ThsCaptureStatus.NO_WINDOW);
        snapshot.setMessage("请展开同花顺主窗口");
        when(service.capture()).thenReturn(snapshot);
        ThsDesktopController controller = new ThsDesktopController();
        ReflectionTestUtils.setField(controller, "service", service);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/api/desktop-ths/capture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NO_WINDOW"));
        verify(service, times(1)).capture();
    }
}

package com.finscope.service.news;

import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewsWorkbenchCapabilitiesTest {
    @Test
    void configuredKeyStillRespectsBothDisableSwitches() {
        NewsWorkbenchCapabilities capabilities = new NewsWorkbenchCapabilities();
        ReflectionTestUtils.setField(capabilities, "llm",
                new com.finscope.rpc.llm.OpenAiCompatibleLlmClient(
                        true, "http://127.0.0.1:1", "test-key", "test-model", 100, 0));
        assertFalse(capabilities.isModelEnabled());
        ReflectionTestUtils.setField(capabilities, "modelEnabled", true);
        assertTrue(capabilities.isModelEnabled());
        ReflectionTestUtils.setField(capabilities, "llm",
                new com.finscope.rpc.llm.OpenAiCompatibleLlmClient(
                        false, "http://127.0.0.1:1", "test-key", "test-model", 100, 0));
        assertFalse(capabilities.isModelEnabled());
    }

    @Test
    void requiresBothExplicitOptInAndConfiguredClient() {
        NewsWorkbenchCapabilities capabilities = new NewsWorkbenchCapabilities();
        LlmChatClient llm = mock(LlmChatClient.class);
        ReflectionTestUtils.setField(capabilities, "llm", llm);
        assertFalse(capabilities.isModelEnabled());
        verifyNoInteractions(llm);
        ReflectionTestUtils.setField(capabilities, "modelEnabled", true);
        assertFalse(capabilities.isModelEnabled());
        when(llm.isConfigured()).thenReturn(true);
        assertTrue(capabilities.isModelEnabled());
    }
}

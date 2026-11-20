package com.finscope.service.news;

import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.llm.NewsAiLunaClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewsWorkbenchCapabilitiesTest {
    @Test
    void enablingManualModelsDoesNotEnableAutomaticCalls() {
        NewsWorkbenchCapabilities capabilities = new NewsWorkbenchCapabilities();
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        ReflectionTestUtils.setField(capabilities, "llm", llm);
        ReflectionTestUtils.setField(capabilities, "modelEnabled", true);
        assertTrue(capabilities.isModelEnabled());
        assertFalse(capabilities.isAutomaticModelEnabled());
        ReflectionTestUtils.setField(capabilities, "automaticModelEnabled", true);
        assertTrue(capabilities.isAutomaticModelEnabled());
        ReflectionTestUtils.setField(capabilities, "modelEnabled", false);
        assertFalse(capabilities.isAutomaticModelEnabled());
    }

    @Test
    void interpretationUsesOnlyTheNewsKeyAndRequiresExplicitAutomaticOptIn() {
        NewsWorkbenchCapabilities capabilities = new NewsWorkbenchCapabilities();
        NewsAiLunaClient luna = mock(NewsAiLunaClient.class);
        LlmChatClient global = mock(LlmChatClient.class);
        ReflectionTestUtils.setField(capabilities, "luna", luna);
        ReflectionTestUtils.setField(capabilities, "llm", global);
        ReflectionTestUtils.setField(capabilities, "modelEnabled", true);
        when(global.isConfigured()).thenReturn(true);
        assertFalse(capabilities.isInterpretationEnabled());

        when(global.isConfigured()).thenReturn(false);
        when(luna.isConfigured()).thenReturn(true);
        assertTrue(capabilities.isInterpretationEnabled());
        assertFalse(capabilities.isAutomaticInterpretationEnabled());
        ReflectionTestUtils.setField(capabilities, "automaticModelEnabled", true);
        assertTrue(capabilities.isAutomaticInterpretationEnabled());
        ReflectionTestUtils.setField(capabilities, "modelEnabled", false);
        assertFalse(capabilities.isAutomaticInterpretationEnabled());
    }

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

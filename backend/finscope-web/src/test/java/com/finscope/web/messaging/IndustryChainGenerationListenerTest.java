package com.finscope.web.messaging;

import com.finscope.domain.industrychain.IndustryChainGenerationMessage;
import com.finscope.service.industrychain.IndustryChainGenerationExecutor;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IndustryChainGenerationListenerTest {
    @Test
    void delegatesValidVersionedMessageToGenerationExecutor() {
        IndustryChainGenerationExecutor executor = mock(IndustryChainGenerationExecutor.class);
        IndustryChainGenerationListener listener = new IndustryChainGenerationListener(executor);
        IndustryChainGenerationMessage message = IndustryChainGenerationMessage.requested(7L, 11L);

        listener.consume(message);

        verify(executor).executeRequested(7L, 11L, message.getEventId());
    }

    @Test
    void ignoresInvalidOrUnsupportedMessages() {
        IndustryChainGenerationExecutor executor = mock(IndustryChainGenerationExecutor.class);
        IndustryChainGenerationListener listener = new IndustryChainGenerationListener(executor);
        IndustryChainGenerationMessage invalid = IndustryChainGenerationMessage.requested(7L, 11L);
        invalid.setEventVersion(99);

        listener.consume(invalid);
        listener.consume(null);

        verify(executor, never()).executeRequested(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }
}

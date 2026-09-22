package com.finscope.web.sse;

import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ViewRevisionSseRegistryTest {
    @Test
    void timeoutCompletesAndRemovesSubscriberWhileAllowingReconnect() throws Exception {
        StreamController controller = new StreamController();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler()).build();
        MvcResult result = mvc.perform(get("/stream"))
                .andExpect(request().asyncStarted()).andReturn();
        controller.registry.heartbeat();
        MockAsyncContext context = (MockAsyncContext) result.getRequest().getAsyncContext();
        for (AsyncListener listener : context.getListeners()) {
            listener.onTimeout(new AsyncEvent(context));
        }
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        Collection<?> subscribers = (Collection<?>) org.springframework.test.util.ReflectionTestUtils
                .getField(controller.registry, "subscribers");
        assertEquals(0, subscribers.size());
        mvc.perform(get("/stream")).andExpect(request().asyncStarted());
        controller.registry.heartbeat();
        assertEquals(1, subscribers.size());
    }

    @RestController
    static class StreamController {
        private final ViewRevisionSseRegistry registry = new ViewRevisionSseRegistry();

        @GetMapping("/stream")
        SseEmitter stream() {
            return registry.subscribe();
        }
    }
}

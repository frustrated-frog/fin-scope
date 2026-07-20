package com.finscope.service.topic;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.topic.TopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicServiceExceptionTest {

    @Test
    void throwsTypedChineseExceptionWhenTopicDoesNotExist() {
        TopicRepository repository = mock(TopicRepository.class);
        when(repository.findById(999L)).thenReturn(Optional.empty());
        TopicService service = new TopicService();
        ReflectionTestUtils.setField(service, "topicRepository", repository);

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class, () -> service.detail(999L));

        assertEquals("主题不存在：999", error.getMessage());
    }
}

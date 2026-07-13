package com.finscope.service.source;

import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceServiceTest {
    @Test
    void installsRecommendedNewsSourcesWithoutCreatingDuplicates() {
        SourceRepository repository = mock(SourceRepository.class);
        SourceService service = new SourceService();
        ReflectionTestUtils.setField(service, "sourceRepository", repository);
        List<Source> installed = new ArrayList<Source>();
        when(repository.findAll()).thenReturn(Collections.<Source>emptyList(), installed);
        when(repository.save(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            source.setId((long) installed.size() + 1L);
            installed.add(source);
            return source;
        });
        when(repository.update(anyLong(), any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(1);
            source.setId(invocation.getArgument(0));
            return source;
        });

        List<Source> first = service.installRecommendedNewsSources();
        List<Source> second = service.installRecommendedNewsSources();

        assertEquals(4, first.size());
        assertEquals(4, second.size());
        assertTrue(first.stream().allMatch(Source::isEnabled));
        assertTrue(first.stream().allMatch(source -> "RSS".equals(source.getType())));
        assertTrue(first.stream().allMatch(source -> source.getMaxItemsPerRun() > 0
                && source.getMaxItemsPerRun() <= 5));
        verify(repository, times(4)).save(any(Source.class));
        verify(repository, times(4)).update(anyLong(), any(Source.class));
    }
}

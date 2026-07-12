package com.finscope.service.research;

import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.research.ResearchThesis;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchThesisServiceTest {
    @Test
    void createsOpenCompanyThesisWithRequiredFields() {
        ResearchThesisRepository repository = mock(ResearchThesisRepository.class);
        ResearchThesisService service = new ResearchThesisService();
        ReflectionTestUtils.setField(service, "researchThesisRepository", repository);
        when(repository.save(any(ResearchThesis.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("英伟达盈利预期是否仍在上修？");
        thesis.setSubjectType("COMPANY");
        thesis.setSubjectName("英伟达");
        thesis.setSubjectCode("NVDA");

        ResearchThesis created = service.create(thesis);

        assertEquals("OPEN", created.getStatus());
        verify(repository).save(thesis);
    }

    @Test
    void rejectsUnsupportedSubjectType() {
        ResearchThesisService service = new ResearchThesisService();
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion("问题");
        thesis.setSubjectType("FUND");
        thesis.setSubjectName("某基金");

        assertThrows(IllegalArgumentException.class, () -> service.create(thesis));
    }
}
